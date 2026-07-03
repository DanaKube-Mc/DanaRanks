package app.danakube.danaranks.database;

import app.danakube.danaranks.profile.PlayerProfile;
import app.danakube.danaranks.profile.HistoryEntry;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseManager {
    private final String dbType;
    private final String tablePrefix;
    private final HikariDataSource dataSource;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public DatabaseManager(String sqliteUrl) {
        this.dbType = "SQLITE";
        this.tablePrefix = "danaranks_";
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(sqliteUrl);
        config.setMaximumPoolSize(1);
        config.setPoolName("DanaRanks-SQLite-Pool");
        this.dataSource = new HikariDataSource(config);
        
        createTables();
    }

    public DatabaseManager(String dbType, String host, int port, String database, String username, String password, String prefix, int maxPoolSize, long maxLifetime, File dataFolder) {
        this.dbType = dbType.toUpperCase();
        this.tablePrefix = prefix != null ? prefix : "danaranks_";

        HikariConfig config = new HikariConfig();
        if (this.dbType.equals("MYSQL")) {
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true");
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(maxPoolSize);
            config.setMaxLifetime(maxLifetime);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.setPoolName("DanaRanks-MySQL-Pool");
        } else {
            if (dataFolder != null && !dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            String path = dataFolder != null ? new File(dataFolder, "database.db").getAbsolutePath() : "database.db";
            config.setJdbcUrl("jdbc:sqlite:" + path);
            config.setMaximumPoolSize(1);
            config.setPoolName("DanaRanks-SQLite-Pool");
        }
        this.dataSource = new HikariDataSource(config);
        createTables();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        executor.shutdown();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private void createTables() {
        String profilesTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "profiles (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "player_name VARCHAR(16) NOT NULL, " +
                "rank_level INT DEFAULT 1, " +
                "elo INT DEFAULT 0, " +
                "last_reset TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "quota_progress TEXT" +
                ");";

        String autoIncrementKeyword = dbType.equals("MYSQL") ? "AUTO_INCREMENT" : "AUTOINCREMENT";
        String historyTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "history (" +
                "id INTEGER PRIMARY KEY " + autoIncrementKeyword + ", " +
                "uuid VARCHAR(36) NOT NULL, " +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "type VARCHAR(32) NOT NULL, " +
                "elo_change INT NOT NULL, " +
                "new_elo INT NOT NULL, " +
                "description VARCHAR(255) NOT NULL" +
                ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(profilesTable);
            stmt.execute(historyTable);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public CompletableFuture<PlayerProfile> loadProfile(UUID uuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT * FROM " + tablePrefix + "profiles WHERE uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int rank = rs.getInt("rank_level");
                        int elo = rs.getInt("elo");
                        Timestamp ts = rs.getTimestamp("last_reset");
                        Instant lastReset = ts != null ? ts.toInstant() : Instant.now();
                        String quotaJson = rs.getString("quota_progress");
                        
                        Map<String, Object> quotaProgress = new HashMap<>();
                        if (quotaJson != null && !quotaJson.isEmpty()) {
                            Type type = new TypeToken<Map<String, Object>>(){}.getType();
                            quotaProgress = gson.fromJson(quotaJson, type);
                        }
                        return new PlayerProfile(uuid, name, rank, elo, lastReset, quotaProgress);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error loading profile", e);
            }
            return new PlayerProfile(uuid, name);
        }, executor);
    }

    public CompletableFuture<Void> saveProfile(PlayerProfile profile) {
        return CompletableFuture.runAsync(() -> {
            String replaceQuery = "REPLACE INTO " + tablePrefix + "profiles (uuid, player_name, rank_level, elo, last_reset, quota_progress) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(replaceQuery)) {
                ps.setString(1, profile.getUuid().toString());
                ps.setString(2, profile.getPlayerName());
                ps.setInt(3, profile.getRankLevel());
                ps.setInt(4, profile.getElo());
                ps.setTimestamp(5, Timestamp.from(profile.getLastReset()));
                ps.setString(6, gson.toJson(profile.getQuotaProgress()));
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error saving profile", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> logHistory(UUID uuid, String type, int eloChange, int newElo, String description) {
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO " + tablePrefix + "history (uuid, type, elo_change, new_elo, description, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, type);
                ps.setInt(3, eloChange);
                ps.setInt(4, newElo);
                ps.setString(5, description);
                ps.setTimestamp(6, Timestamp.from(Instant.now()));
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error logging history", e);
            }
        }, executor);
    }

    public CompletableFuture<List<HistoryEntry>> fetchHistory(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<HistoryEntry> list = new ArrayList<>();
            String query = "SELECT * FROM " + tablePrefix + "history WHERE uuid = ? ORDER BY timestamp DESC, id DESC LIMIT ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        Timestamp ts = rs.getTimestamp("timestamp");
                        Instant timestamp = ts != null ? ts.toInstant() : Instant.now();
                        String type = rs.getString("type");
                        int eloChange = rs.getInt("elo_change");
                        int newElo = rs.getInt("new_elo");
                        String description = rs.getString("description");
                        list.add(new HistoryEntry(id, uuid, timestamp, type, eloChange, newElo, description));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error fetching history", e);
            }
            return list;
        }, executor);
    }
}
