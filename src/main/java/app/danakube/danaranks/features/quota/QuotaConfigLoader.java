package app.danakube.danaranks.features.quota;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
            List<RankBracket> defaultBrackets = new ArrayList<>();
            defaultBrackets.add(new RankBracket(1, 50, 10, 20, 0, 2));
            return new QuotaConfig(surplusMultiplier, scalingMultiplier, refDateStr, resetHour, defaultBrackets, baseObjectives);
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

        // Parsing rank brackets
        List<RankBracket> brackets = new ArrayList<>();
        ConfigurationSection ranksSec = config.getConfigurationSection("quotas-settings.ranks");
        if (ranksSec != null) {
            for (String key : ranksSec.getKeys(false)) {
                ConfigurationSection bracketSec = ranksSec.getConfigurationSection(key);
                if (bracketSec == null) continue;

                int minRank = 1;
                int maxRank = 50;
                if (key.contains("-")) {
                    String[] parts = key.split("-");
                    try {
                        minRank = Integer.parseInt(parts[0].trim());
                        maxRank = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        if (logger != null) {
                            logger.warning("[DanaRanks] Invalid rank range format: " + key);
                        }
                    }
                } else {
                    try {
                        minRank = Integer.parseInt(key.trim());
                        maxRank = minRank;
                    } catch (NumberFormatException e) {
                        if (logger != null) {
                            logger.warning("[DanaRanks] Invalid rank value: " + key);
                        }
                    }
                }

                int baseElo = bracketSec.getInt("base-elo", 10);
                int maxSurplusElo = bracketSec.getInt("max-surplus-elo", 20);
                int failPenalty = bracketSec.getInt("fail-penalty", 0);
                int maxObjectives = bracketSec.getInt("max-objectives", 2);

                brackets.add(new RankBracket(minRank, maxRank, baseElo, maxSurplusElo, failPenalty, maxObjectives));
            }
        }
        if (brackets.isEmpty()) {
            brackets.add(new RankBracket(1, 50, 10, 20, 0, 2));
        }
        brackets.sort(java.util.Comparator.comparingInt(b -> b.minRank()));

        ConfigurationSection resourcesSection = config.getConfigurationSection("resources");
        if (resourcesSection == null) {
            resourcesSection = config.getConfigurationSection("ressources");
        }

        if (resourcesSection != null) {
            baseObjectives.clear();
            for (String key : resourcesSection.getKeys(false)) {
                ConfigurationSection itemSec = resourcesSection.getConfigurationSection(key);
                if (itemSec == null) continue;

                double target = itemSec.getDouble("target", 1000);
                int baseElo = itemSec.getInt("base-elo", -1);
                if (baseElo == -1) baseElo = itemSec.getInt("base_elo", -1);

                int maxSurplus = itemSec.getInt("max-surplus-elo", -1);
                if (maxSurplus == -1) maxSurplus = itemSec.getInt("max_surplus_elo", -1);

                int failPen = itemSec.getInt("fail-penalty", -1);
                if (failPen == -1) failPen = itemSec.getInt("fail_penalty", -1);

                String material = itemSec.getString("material");
                if (material == null) material = itemSec.getString("mateiral");

                Integer cmd = itemSec.contains("custom-model-data") ? itemSec.getInt("custom-model-data") : null;
                if (cmd == null) {
                    cmd = itemSec.contains("custom_model_data") ? itemSec.getInt("custom_model_data") : null;
                }

                int finalBaseElo = baseElo != -1 ? baseElo : 5;
                int finalMaxSurplus = maxSurplus != -1 ? maxSurplus : 10;
                int finalFailPen = failPen != -1 ? failPen : 0;

                String normalizedKey = key.replace("-", "_");
                baseObjectives.put(normalizedKey, new ObjectiveConfig(normalizedKey, target, finalBaseElo, finalMaxSurplus, finalFailPen, material, cmd));
            }
        } else if (config.contains("quotas-settings.base-rank-1.objectives")) {
            baseObjectives.clear();
            ConfigurationSection section = config.getConfigurationSection("quotas-settings.base-rank-1.objectives");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    String path = "quotas-settings.base-rank-1.objectives." + key;
                    double target = config.getDouble(path + ".target", 1000);
                    int baseElo = config.getInt(path + ".base-elo", -1);
                    int maxSurplus = config.getInt(path + ".max-surplus-elo", -1);
                    int failPen = config.getInt(path + ".fail-penalty", -1);

                    int finalBaseElo = baseElo != -1 ? baseElo : 5;
                    int finalMaxSurplus = maxSurplus != -1 ? maxSurplus : 10;
                    int finalFailPen = failPen != -1 ? failPen : 0;

                    String normalizedKey = key.replace("-", "_");
                    baseObjectives.put(normalizedKey, new ObjectiveConfig(normalizedKey, target, finalBaseElo, finalMaxSurplus, finalFailPen));
                }
            }
        }

        return new QuotaConfig(surplusMultiplier, scalingMultiplier, refDateStr, resetHour, brackets, baseObjectives);
    }

    public static RankBracket getBracketForRank(QuotaConfig quotaConfig, int rank) {
        if (quotaConfig.rankBrackets() == null || quotaConfig.rankBrackets().isEmpty()) {
            return new RankBracket(1, 50, 10, 20, 0, 2);
        }

        for (RankBracket bracket : quotaConfig.rankBrackets()) {
            if (bracket.contains(rank)) {
                return bracket;
            }
        }

        RankBracket first = quotaConfig.rankBrackets().get(0);
        if (rank < first.minRank()) {
            return first;
        }

        RankBracket last = quotaConfig.rankBrackets().get(quotaConfig.rankBrackets().size() - 1);
        if (rank > last.maxRank()) {
            return last;
        }

        return first;
    }

    public static Map<String, ObjectiveConfig> getObjectivesForRank(QuotaConfig quotaConfig, int rank) {
        Map<String, ObjectiveConfig> map = new HashMap<>();
        RankBracket bracket = getBracketForRank(quotaConfig, rank);
        int activeCount = bracket.maxObjectives();
        if (activeCount <= 0) {
            activeCount = quotaConfig.baseObjectives().size();
        }
        int baseEloEach = activeCount > 0 ? (int) Math.round((double) bracket.baseElo() / activeCount) : 0;
        int maxSurplusEach = activeCount > 0 ? (int) Math.round((double) bracket.maxSurplusElo() / activeCount) : 0;
        int failPenaltyEach = activeCount > 0 ? (int) Math.round((double) bracket.failPenalty() / activeCount) : 0;

        for (ObjectiveConfig base : quotaConfig.baseObjectives().values()) {
            double scaledTarget = Math.round(base.target() * Math.pow(quotaConfig.scalingMultiplierPerRank(), rank - 1));
            map.put(base.name(), new ObjectiveConfig(base.name(), scaledTarget, baseEloEach, maxSurplusEach, failPenaltyEach, base.material(), base.customModelData()));
        }
        return map;
    }

    public static ObjectiveConfig getObjectiveConfig(QuotaConfig quotaConfig, int rank, String resource) {
        String normalizedResource = resource.replace("-", "_");
        ObjectiveConfig base = quotaConfig.baseObjectives().get(normalizedResource);
        if (base == null) return null;
        RankBracket bracket = getBracketForRank(quotaConfig, rank);
        int activeCount = bracket.maxObjectives();
        if (activeCount <= 0) {
            activeCount = quotaConfig.baseObjectives().size();
        }
        int baseEloEach = activeCount > 0 ? (int) Math.round((double) bracket.baseElo() / activeCount) : 0;
        int maxSurplusEach = activeCount > 0 ? (int) Math.round((double) bracket.maxSurplusElo() / activeCount) : 0;
        int failPenaltyEach = activeCount > 0 ? (int) Math.round((double) bracket.failPenalty() / activeCount) : 0;

        double scaledTarget = Math.round(base.target() * Math.pow(quotaConfig.scalingMultiplierPerRank(), rank - 1));
        return new ObjectiveConfig(base.name(), scaledTarget, baseEloEach, maxSurplusEach, failPenaltyEach, base.material(), base.customModelData());
    }
}
