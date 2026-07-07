package app.danakube.danaranks.features.quota;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class QuotaConfigLoader {

    public static QuotaConfig load(FileConfiguration config, Logger logger) {
        double surplusMultiplier = 10.0;
        double scalingMultiplier = 1.15;
        String refDateStr = "2026-07-03";
        int resetHour = 4;
        Map<String, ObjectiveConfig> baseObjectives = new HashMap<>();

        baseObjectives.put("lumens_gained", new ObjectiveConfig("lumens_gained", 1000, 5, 10, 0));
        baseObjectives.put("job_xp_all", new ObjectiveConfig("job_xp_all", 500, 5, 10, 0));

        if (config == null) {
            return new QuotaConfig(surplusMultiplier, scalingMultiplier, refDateStr, resetHour, 10, 20, 0, -1, baseObjectives);
        }

        int hr = config.getInt("reset.hour", 4);
        if (hr < 0 || hr > 23) {
            if (logger != null) {
                logger.warning("[DanaRanks] Invalid reset.hour: " + hr + ". Using default: 4");
            }
        } else {
            resetHour = hr;
        }

        String refDate = config.getString("reset.reference-date", "2026-07-03");
        try {
            LocalDate.parse(refDate);
            refDateStr = refDate;
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("[DanaRanks] Invalid reset.reference-date format (expected YYYY-MM-DD): " + refDate + ". Using default: 2026-07-03");
            }
        }

        double mult = config.getDouble("quotas-settings.surplus-multiplier", 10.0);
        if (mult < 1.0) {
            if (logger != null) {
                logger.warning("[DanaRanks] Invalid quotas-settings.surplus-multiplier: " + mult + ". Using default: 10.0");
            }
        } else {
            surplusMultiplier = mult;
        }

        double scaleMult = config.getDouble("quotas-settings.scaling.multiplier-per-rank", 1.15);
        if (scaleMult < 1.0) {
            if (logger != null) {
                logger.warning("[DanaRanks] Invalid quotas-settings.scaling.multiplier-per-rank: " + scaleMult + ". Using default: 1.15");
            }
        } else {
            scalingMultiplier = scaleMult;
        }

        int globalBaseElo = config.getInt("quotas-settings.base-rank-1.base-elo", -1);
        int globalMaxSurplusElo = config.getInt("quotas-settings.base-rank-1.max-surplus-elo", -1);
        int globalFailPenalty = config.getInt("quotas-settings.base-rank-1.fail-penalty", -1);
        int maxObjectives = config.getInt("quotas-settings.base-rank-1.max-objectives",
                config.getInt("quotas-settings.base-rank-1.nb-objectifs-max", -1));

        if (config.contains("quotas-settings.base-rank-1.objectives")) {
            baseObjectives.clear();
            ConfigurationSection section = config.getConfigurationSection("quotas-settings.base-rank-1.objectives");
            if (section != null) {
                int sumBaseElo = 0;
                int sumMaxSurplus = 0;
                int sumFailPen = 0;
                boolean hasIndividualElo = false;

                for (String key : section.getKeys(false)) {
                    String path = "quotas-settings.base-rank-1.objectives." + key;
                    double target = config.getDouble(path + ".target", 1000);
                    int baseElo = config.getInt(path + ".base-elo", -1);
                    int maxSurplus = config.getInt(path + ".max-surplus-elo", -1);
                    int failPen = config.getInt(path + ".fail-penalty", -1);

                    if (baseElo != -1 || maxSurplus != -1 || failPen != -1) {
                        hasIndividualElo = true;
                    }

                    int finalBaseElo = baseElo != -1 ? baseElo : 5;
                    int finalMaxSurplus = maxSurplus != -1 ? maxSurplus : 10;
                    int finalFailPen = failPen != -1 ? failPen : 0;

                    sumBaseElo += finalBaseElo;
                    sumMaxSurplus += finalMaxSurplus;
                    sumFailPen += finalFailPen;

                    String normalizedKey = key.replace("-", "_");
                    baseObjectives.put(normalizedKey, new ObjectiveConfig(normalizedKey, target, finalBaseElo, finalMaxSurplus, finalFailPen));
                }

                if (globalBaseElo == -1) {
                    globalBaseElo = hasIndividualElo ? sumBaseElo : 10;
                }
                if (globalMaxSurplusElo == -1) {
                    globalMaxSurplusElo = hasIndividualElo ? sumMaxSurplus : 20;
                }
                if (globalFailPenalty == -1) {
                    globalFailPenalty = hasIndividualElo ? sumFailPen : 0;
                }
            }
        } else {
            if (globalBaseElo == -1) globalBaseElo = 10;
            if (globalMaxSurplusElo == -1) globalMaxSurplusElo = 20;
            if (globalFailPenalty == -1) globalFailPenalty = 0;
        }

        return new QuotaConfig(surplusMultiplier, scalingMultiplier, refDateStr, resetHour,
                globalBaseElo, globalMaxSurplusElo, globalFailPenalty, maxObjectives, baseObjectives);
    }

    public static Map<String, ObjectiveConfig> getObjectivesForRank(QuotaConfig quotaConfig, int rank) {
        Map<String, ObjectiveConfig> map = new HashMap<>();
        for (ObjectiveConfig base : quotaConfig.baseObjectives().values()) {
            double scaledTarget = Math.round(base.target() * Math.pow(quotaConfig.scalingMultiplierPerRank(), rank - 1));
            map.put(base.name(), new ObjectiveConfig(base.name(), scaledTarget, base.baseElo(), base.maxSurplusElo(), base.failPenalty()));
        }
        return map;
    }

    public static ObjectiveConfig getObjectiveConfig(QuotaConfig quotaConfig, int rank, String resource) {
        String normalizedResource = resource.replace("-", "_");
        ObjectiveConfig base = quotaConfig.baseObjectives().get(normalizedResource);
        if (base == null) return null;
        double scaledTarget = Math.round(base.target() * Math.pow(quotaConfig.scalingMultiplierPerRank(), rank - 1));
        return new ObjectiveConfig(base.name(), scaledTarget, base.baseElo(), base.maxSurplusElo(), base.failPenalty());
    }
}
