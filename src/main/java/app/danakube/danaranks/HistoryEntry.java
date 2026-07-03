package app.danakube.danaranks;

import java.time.Instant;
import java.util.UUID;

public class HistoryEntry {
    private final int id;
    private final UUID uuid;
    private final Instant timestamp;
    private final String type;
    private final int eloChange;
    private final int newElo;
    private final String description;

    public HistoryEntry(int id, UUID uuid, Instant timestamp, String type, int eloChange, int newElo, String description) {
        this.id = id;
        this.uuid = uuid;
        this.timestamp = timestamp;
        this.type = type;
        this.eloChange = eloChange;
        this.newElo = newElo;
        this.description = description;
    }

    public int getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }

    public int getEloChange() {
        return eloChange;
    }

    public int getNewElo() {
        return newElo;
    }

    public String getDescription() {
        return description;
    }
}
