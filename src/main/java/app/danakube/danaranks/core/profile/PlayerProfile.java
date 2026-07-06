package app.danakube.danaranks.core.profile;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProfile {
    private final UUID uuid;
    private final String playerName;
    private int rankLevel;
    private int elo;
    private Instant lastReset;
    private Map<String, Object> quotaProgress;

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

    public int getObjectiveProgress(String objectiveId) {
        Object val = this.quotaProgress.getOrDefault(objectiveId, 0);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return 0;
    }
}
