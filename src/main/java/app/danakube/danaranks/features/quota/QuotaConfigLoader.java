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
        baseObjectives.put("job_xp", new ObjectiveConfig("job_xp", 500, 5, 10, 0));

        if (config == null) {
            return new QuotaConfig(surplusMultiplier, scalingMultiplier, refDateStr, resetHour, baseObjectives);
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

        if (config.contains("quotas-settings.base-rank-1.objectives")) {
            baseObjectives.clear();
            ConfigurationSection section = config.getConfigurationSection("quotas-settings.base-rank-1.objectives");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    String path = "quotas-settings.base-rank-1.objectives." + key;
                    double target = config.getDouble(path + ".target", 1000);
                    int baseElo = config.getInt(path + ".base-elo", 5);
                    int maxSurplus = config.getInt(path + ".max-surplus-elo", 10);
                    int failPen = config.getInt(path + ".fail-penalty", 0);
                    
                    String normalizedKey = key.replace("-", "_");
                    baseObjectives.put(normalizedKey, new ObjectiveConfig(normalizedKey, target, baseElo, maxSurplus, failPen));
                }
            }
        }

        return new QuotaConfig(surplusMultiplier, scalingMultiplier, refDateStr, resetHour, baseObjectives);
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
