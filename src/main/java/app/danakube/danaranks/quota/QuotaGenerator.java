package app.danakube.danaranks.quota;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class QuotaGenerator {
    private final Map<String, BaseObjective> baseObjectives = new HashMap<>();
    private double scalingMultiplierPerRank = 1.15;

    public static class BaseObjective {
        private final String name;
        private final double baseTarget;
        private final int baseElo;
        private final int maxSurplusElo;
        private final int failPenalty;

        public BaseObjective(String name, double baseTarget, int baseElo, int maxSurplusElo, int failPenalty) {
            this.name = name;
            this.baseTarget = baseTarget;
            this.baseElo = baseElo;
            this.maxSurplusElo = maxSurplusElo;
            this.failPenalty = failPenalty;
        }

        public String getName() {
            return name;
        }

        public double getBaseTarget() {
            return baseTarget;
        }

        public int getBaseElo() {
            return baseElo;
        }

        public int getMaxSurplusElo() {
            return maxSurplusElo;
        }

        public int getFailPenalty() {
            return failPenalty;
        }
    }

    public QuotaGenerator() {
        baseObjectives.put("lumens_gained", new BaseObjective("lumens_gained", 1000, 5, 10, 0));
        baseObjectives.put("job_xp", new BaseObjective("job_xp", 500, 5, 10, 0));
    }

    public void loadConfig(FileConfiguration config, Logger logger) {
        double scaleMult = config.getDouble("quotas-settings.scaling.multiplier-per-rank", 1.15);
        if (scaleMult < 1.0) {
            if (logger != null) {
                logger.warning("[DanaRanks] Invalid quotas-settings.scaling.multiplier-per-rank: " + scaleMult + ". Using default: 1.15");
            }
            this.scalingMultiplierPerRank = 1.15;
        } else {
            this.scalingMultiplierPerRank = scaleMult;
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
                    baseObjectives.put(normalizedKey, new BaseObjective(normalizedKey, target, baseElo, maxSurplus, failPen));
                }
            }
        }
    }

    public Map<String, ObjectiveConfig> getObjectivesForRank(int rank) {
        Map<String, ObjectiveConfig> map = new HashMap<>();
        for (BaseObjective base : baseObjectives.values()) {
            double scaledTarget = Math.round(base.baseTarget * Math.pow(scalingMultiplierPerRank, rank - 1));
            map.put(base.name, new ObjectiveConfig(base.name, scaledTarget, base.baseElo, base.maxSurplusElo, base.failPenalty));
        }
        return map;
    }

    public ObjectiveConfig getObjectiveConfig(int rank, String resource) {
        String normalizedResource = resource.replace("-", "_");
        BaseObjective base = baseObjectives.get(normalizedResource);
        if (base == null) return null;
        double scaledTarget = Math.round(base.baseTarget * Math.pow(scalingMultiplierPerRank, rank - 1));
        return new ObjectiveConfig(base.name, scaledTarget, base.baseElo, base.maxSurplusElo, base.failPenalty);
    }

    public Map<String, BaseObjective> getBaseObjectives() {
        return baseObjectives;
    }

    public double getScalingMultiplierPerRank() {
        return scalingMultiplierPerRank;
    }
}
