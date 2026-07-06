package app.danakube.danaranks.core.profile;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProfileBuilder {
    private UUID uuid = UUID.randomUUID();
    private String name = "TestPlayer";
    private int rank = 1;
    private int elo = 0;
    private Instant lastReset = Instant.now();
    private Map<String, Object> quotaProgress = new HashMap<>();

    public static PlayerProfileBuilder aProfile() {
        return new PlayerProfileBuilder();
    }

    public PlayerProfileBuilder uuid(UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    public PlayerProfileBuilder name(String name) {
        this.name = name;
        return this;
    }

    public PlayerProfileBuilder rank(int rank) {
        this.rank = rank;
        return this;
    }

    public PlayerProfileBuilder elo(int elo) {
        this.elo = elo;
        return this;
    }

    public PlayerProfileBuilder lastReset(Instant lastReset) {
        this.lastReset = lastReset;
        return this;
    }

    public PlayerProfileBuilder quotaProgress(Map<String, Object> quotaProgress) {
        this.quotaProgress = quotaProgress;
        return this;
    }

    public PlayerProfile build() {
        return new PlayerProfile(uuid, name, rank, elo, lastReset, quotaProgress);
    }
}
