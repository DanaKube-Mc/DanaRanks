package app.danakube.danaranks.features.quota;

import java.util.List;
import java.util.Map;

public record QuotaConfig(
    double surplusMultiplier,
    double scalingMultiplierPerRank,
    String refDateStr,
    int resetHour,
    List<RankBracket> rankBrackets,
    Map<String, ObjectiveConfig> baseObjectives
) {}
