package app.danakube.danaranks.database;

import app.danakube.danaranks.core.profile.HistoryEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HistoryRepository {
    private final DatabaseManager dbManager;

    public HistoryRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public CompletableFuture<Void> logHistory(UUID uuid, String type, int eloChange, int newElo, String description) {
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO " + dbManager.getTablePrefix() + "history (uuid, type, elo_change, new_elo, description, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = dbManager.getConnection();
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
        }, dbManager.getExecutor());
    }

    public CompletableFuture<List<HistoryEntry>> fetchHistory(UUID uuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<HistoryEntry> list = new ArrayList<>();
            String query = "SELECT * FROM " + dbManager.getTablePrefix() + "history WHERE uuid = ? ORDER BY timestamp DESC, id DESC LIMIT ?";
            try (Connection conn = dbManager.getConnection();
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
        }, dbManager.getExecutor());
    }
}
