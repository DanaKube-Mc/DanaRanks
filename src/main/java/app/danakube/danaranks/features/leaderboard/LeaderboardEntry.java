package app.danakube.danaranks.features.leaderboard;

import java.util.UUID;

public record LeaderboardEntry(UUID uuid, String playerName, int rankLevel, int elo) {}
