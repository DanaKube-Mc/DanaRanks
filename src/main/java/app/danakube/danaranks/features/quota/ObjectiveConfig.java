package app.danakube.danaranks.features.quota;

public record ObjectiveConfig(String name, double target, int baseElo, int maxSurplusElo, int failPenalty) {}
