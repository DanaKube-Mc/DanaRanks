package app.danakube.danaranks.database;

import app.danakube.danaranks.core.profile.PlayerProfile;
import app.danakube.danaranks.features.leaderboard.LeaderboardEntry;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ProfileRepository {
    private final DatabaseManager dbManager;

    public ProfileRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public CompletableFuture<Optional<PlayerProfile>> loadProfile(UUID uuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT * FROM " + dbManager.getTablePrefix() + "profiles WHERE uuid = ?";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String dbName = rs.getString("player_name");
                        String resolvedName = dbName;
                        if (resolvedName == null || resolvedName.isEmpty() || resolvedName.equalsIgnoreCase("OfflinePlayer")) {
                            if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("OfflinePlayer")) {
                                resolvedName = name;
                            } else {
                                resolvedName = "Joueur";
                            }
                        }

                        int rank = rs.getInt("rank_level");
                        int elo = rs.getInt("elo");
                        Timestamp ts = rs.getTimestamp("last_reset");
                        Instant lastReset = ts != null ? ts.toInstant() : Instant.now();
                        String quotaJson = rs.getString("quota_progress");
                        Map<String, Object> quotaProgress = parseJsonToMap(quotaJson);
                        return Optional.of(new PlayerProfile(uuid, resolvedName, rank, elo, lastReset, quotaProgress));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error loading profile", e);
            }
            return Optional.empty();
        }, dbManager.getExecutor());
    }

    public CompletableFuture<Void> saveProfile(PlayerProfile profile) {
        return CompletableFuture.runAsync(() -> {
            String replaceQuery = "REPLACE INTO " + dbManager.getTablePrefix() + "profiles (uuid, player_name, rank_level, elo, last_reset, quota_progress) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(replaceQuery)) {
                ps.setString(1, profile.getUuid().toString());
                ps.setString(2, profile.getPlayerName());
                ps.setInt(3, profile.getRankLevel());
                ps.setInt(4, profile.getElo());
                ps.setTimestamp(5, Timestamp.from(profile.getLastReset()));

                Map<String, Object> snapshot;
                synchronized (profile) {
                    snapshot = new HashMap<>(profile.getQuotaProgress());
                }
                ps.setString(6, dbManager.getGson().toJson(snapshot));
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error saving profile", e);
            }
        }, dbManager.getExecutor());
    }

    public static Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            com.google.gson.JsonElement element = com.google.gson.JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                return new HashMap<>();
            }
            Map<String, Object> result = new HashMap<>();
            com.google.gson.JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) {
                result.put(entry.getKey(), convertJsonElement(entry.getValue()));
            }
            return result;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private static Object convertJsonElement(com.google.gson.JsonElement elem) {
        if (elem == null || elem.isJsonNull()) {
            return null;
        } else if (elem.isJsonObject()) {
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> entry : elem.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), convertJsonElement(entry.getValue()));
            }
            return map;
        } else if (elem.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (com.google.gson.JsonElement item : elem.getAsJsonArray()) {
                list.add(convertJsonElement(item));
            }
            return list;
        } else if (elem.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive prim = elem.getAsJsonPrimitive();
            if (prim.isBoolean()) {
                return prim.getAsBoolean();
            } else if (prim.isNumber()) {
                Number num = prim.getAsNumber();
                return num.doubleValue();
            } else {
                return prim.getAsString();
            }
        }
        return null;
    }

    public CompletableFuture<List<LeaderboardEntry>> getLeaderboard(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> list = new ArrayList<>();
            String query = "SELECT uuid, player_name, rank_level, elo FROM " + dbManager.getTablePrefix() + "profiles ORDER BY rank_level DESC, elo DESC LIMIT ?";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String name = rs.getString("player_name");
                        int rank = rs.getInt("rank_level");
                        int elo = rs.getInt("elo");
                        list.add(new LeaderboardEntry(uuid, name, rank, elo));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error fetching leaderboard", e);
            }
            return list;
        }, dbManager.getExecutor());
    }
}
