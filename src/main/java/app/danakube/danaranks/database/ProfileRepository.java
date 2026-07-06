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
                        int rank = rs.getInt("rank_level");
                        int elo = rs.getInt("elo");
                        Timestamp ts = rs.getTimestamp("last_reset");
                        Instant lastReset = ts != null ? ts.toInstant() : Instant.now();
                        String quotaJson = rs.getString("quota_progress");
                        
                        Map<String, Object> quotaProgress = new HashMap<>();
                        if (quotaJson != null && !quotaJson.isEmpty()) {
                            Type type = new TypeToken<Map<String, Object>>(){}.getType();
                            quotaProgress = dbManager.getGson().fromJson(quotaJson, type);
                        }
                        return Optional.of(new PlayerProfile(uuid, name, rank, elo, lastReset, quotaProgress));
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
                ps.setString(6, dbManager.getGson().toJson(profile.getQuotaProgress()));
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error saving profile", e);
            }
        }, dbManager.getExecutor());
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
