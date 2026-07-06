package app.danakube.danaranks.core.profile;

import java.time.Instant;
import java.util.UUID;

public record HistoryEntry(int id, UUID uuid, Instant timestamp, String type, int eloChange, int newElo, String description) {}
