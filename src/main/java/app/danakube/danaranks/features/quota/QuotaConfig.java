package app.danakube.danaranks.features.quota;

import java.util.Map;

public record QuotaConfig(
    double surplusMultiplier,
    double scalingMultiplierPerRank,
    String refDateStr,
    int resetHour,
    Map<String, ObjectiveConfig> baseObjectives
) {}
