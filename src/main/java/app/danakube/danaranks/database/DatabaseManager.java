package app.danakube.danaranks.database;

import com.google.gson.Gson;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public Gson getGson() {
        return gson;
    }

    public ExecutorService getExecutor() {
        return executor;
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
}
