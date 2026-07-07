package app.danakube.danaranks.features.quota;

import java.util.Map;

public record QuotaConfig(
    double surplusMultiplier,
    double scalingMultiplierPerRank,
    String refDateStr,
    int resetHour,
    int globalBaseElo,
    int globalMaxSurplusElo,
    int globalFailPenalty,
    int maxObjectives,
    Map<String, ObjectiveConfig> baseObjectives
) {}
