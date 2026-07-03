package app.danakube.danaranks;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class PlayerProfile {
    private static BiConsumer<UUID, Integer> promotionCallback;

    private final UUID uuid;
    private final String playerName;
    private int rankLevel;
    private int elo;
    private Instant lastReset;
    private Map<String, Object> quotaProgress;

    public static void setPromotionCallback(BiConsumer<UUID, Integer> callback) {
        promotionCallback = callback;
    }

    public PlayerProfile(UUID uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.rankLevel = 1;
        this.elo = 0;
        this.lastReset = Instant.now();
        this.quotaProgress = new HashMap<>();
    }

    public PlayerProfile(UUID uuid, String playerName, int rankLevel, int elo, Instant lastReset, Map<String, Object> quotaProgress) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.rankLevel = rankLevel;
        this.elo = elo;
        this.lastReset = lastReset;
        this.quotaProgress = quotaProgress != null ? quotaProgress : new HashMap<>();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public synchronized int getRankLevel() {
        return rankLevel;
    }

    public synchronized void setRankLevel(int rankLevel) {
        this.rankLevel = rankLevel;
    }

    public synchronized int getElo() {
        return elo;
    }

    public synchronized void setElo(int elo) {
        this.elo = elo;
    }

    public Instant getLastReset() {
        return lastReset;
    }

    public void setLastReset(Instant lastReset) {
        this.lastReset = lastReset;
    }

    public Map<String, Object> getQuotaProgress() {
        return quotaProgress;
    }

    public void setQuotaProgress(Map<String, Object> quotaProgress) {
        this.quotaProgress = quotaProgress;
    }

    public synchronized int addElo(int amount) {
        int totalElo = this.elo + amount;
        if (totalElo < 0) {
            this.elo = 0;
            return 0;
        }

        int ranksGained = totalElo / 100;
        this.elo = totalElo % 100;

        if (ranksGained > 0) {
            int oldRank = this.rankLevel;
            this.rankLevel = Math.min(50, this.rankLevel + ranksGained);
            int actualRanksGained = this.rankLevel - oldRank;
            
            if (actualRanksGained > 0) {
                if (promotionCallback != null) {
                    try {
                        promotionCallback.accept(this.uuid, actualRanksGained);
                    } catch (Exception e) {
                        // Suppress exceptions in test environment
                    }
                }
            }
        }
        return ranksGained;
    }
}
