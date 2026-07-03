package app.danakube.danaranks.quota;

import app.danakube.danaranks.DanaRanks;
import app.danakube.danaranks.profile.PlayerProfile;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class QuotaManager {
    private static QuotaManager instance;

    private final Map<String, BaseObjective> baseObjectives = new HashMap<>();
    private double surplusMultiplier = 10.0;
    private double scalingMultiplierPerRank = 1.15;
    private String refDateStr = "2026-07-03";
    private int resetHour = 4;

    private static class BaseObjective {
        final String name;
        final double baseTarget;
        final int baseElo;
        final int maxSurplusElo;
        final int failPenalty;

        BaseObjective(String name, double baseTarget, int baseElo, int maxSurplusElo, int failPenalty) {
            this.name = name;
            this.baseTarget = baseTarget;
            this.baseElo = baseElo;
            this.maxSurplusElo = maxSurplusElo;
            this.failPenalty = failPenalty;
        }
    }

    public static QuotaManager getInstance() {
        return instance;
    }

    public static void setInstance(QuotaManager manager) {
        instance = manager;
    }

    public QuotaManager() {
        // Constructeur par défaut avec des objectifs par défaut
        baseObjectives.put("lumens_gained", new BaseObjective("lumens_gained", 1000, 5, 10, 0));
        baseObjectives.put("job_xp", new BaseObjective("job_xp", 500, 5, 10, 0));
    }

    public void loadConfig(FileConfiguration config, Logger logger) {
        if (config == null) {
            return;
        }

        // Hour
        int hr = config.getInt("reset.hour", 4);
        if (hr < 0 || hr > 23) {
            if (logger != null) {
                logger.warning("[DanaRanks] Invalid reset.hour: " + hr + ". Using default: 4");
            }
            this.resetHour = 4;
        } else {
            this.resetHour = hr;
        }

        // Reference Date
        String refDate = config.getString("reset.reference-date", "2026-07-03");
        try {
            LocalDate.parse(refDate);
            this.refDateStr = refDate;
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("[DanaRanks] Invalid reset.reference-date format (expected YYYY-MM-DD): " + refDate + ". Using default: 2026-07-03");
            }
            this.refDateStr = "2026-07-03";
        }

        // Surplus Multiplier
        double mult = config.getDouble("quotas-settings.surplus-multiplier", 10.0);
        if (mult < 1.0) {
            if (logger != null) {
                logger.warning("[DanaRanks] Invalid quotas-settings.surplus-multiplier: " + mult + ". Using default: 10.0");
            }
            this.surplusMultiplier = 10.0;
        } else {
            this.surplusMultiplier = mult;
        }

        // Scaling multiplier
        double scaleMult = config.getDouble("quotas-settings.scaling.multiplier-per-rank", 1.15);
        if (scaleMult < 1.0) {
            if (logger != null) {
                logger.warning("[DanaRanks] Invalid quotas-settings.scaling.multiplier-per-rank: " + scaleMult + ". Using default: 1.15");
            }
            this.scalingMultiplierPerRank = 1.15;
        } else {
            this.scalingMultiplierPerRank = scaleMult;
        }

        // Objectives
        if (config.contains("quotas-settings.base-rank-1.objectives")) {
            baseObjectives.clear();
            org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("quotas-settings.base-rank-1.objectives");
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

    public int getLevelFromRank(int rankLevel) {
        if (rankLevel <= 10) return 1;
        if (rankLevel <= 20) return 2;
        if (rankLevel <= 30) return 3;
        if (rankLevel <= 40) return 4;
        return 5;
    }

    public int getPeriodDays(int level) {
        switch (level) {
            case 1: return 1;
            case 2: return 2;
            case 3: return 3;
            case 4: return 4;
            case 5: return 5;
            default: return 1;
        }
    }

    public Instant getNextResetInstant(int periodDays, Instant now) {
        LocalDateTime refDateTime = LocalDateTime.of(LocalDate.parse(refDateStr), LocalTime.of(resetHour, 0));
        Instant refInstant = refDateTime.atZone(ZoneId.systemDefault()).toInstant();

        long secondsBetween = now.getEpochSecond() - refInstant.getEpochSecond();
        long joursEcoules;
        if (secondsBetween < 0) {
            joursEcoules = (long) Math.floor((double) secondsBetween / 86400.0);
        } else {
            joursEcoules = secondsBetween / 86400;
        }

        long prochainResetJours;
        if (joursEcoules < 0) {
            prochainResetJours = ((joursEcoules / periodDays)) * periodDays;
        } else {
            prochainResetJours = ((joursEcoules / periodDays) + 1) * periodDays;
        }

        return refInstant.plusSeconds(prochainResetJours * 86400);
    }

    public Instant getLastResetEffectiveInstant(int level, Instant now) {
        int periodDays = getPeriodDays(level);
        LocalDateTime refDateTime = LocalDateTime.of(LocalDate.parse(refDateStr), LocalTime.of(resetHour, 0));
        Instant refInstant = refDateTime.atZone(ZoneId.systemDefault()).toInstant();

        long secondsBetween = now.getEpochSecond() - refInstant.getEpochSecond();
        long joursEcoules;
        if (secondsBetween < 0) {
            joursEcoules = (long) Math.floor((double) secondsBetween / 86400.0);
        } else {
            joursEcoules = secondsBetween / 86400;
        }

        long dernierResetJours;
        if (joursEcoules < 0) {
            dernierResetJours = ((joursEcoules / periodDays) - 1) * periodDays;
        } else {
            dernierResetJours = (joursEcoules / periodDays) * periodDays;
        }

        return refInstant.plusSeconds(dernierResetJours * 86400);
    }

    public int getMissedCycles(int level, Instant lastReset, Instant now) {
        int periodDays = getPeriodDays(level);
        LocalDateTime refDateTime = LocalDateTime.of(LocalDate.parse(refDateStr), LocalTime.of(resetHour, 0));
        Instant refInstant = refDateTime.atZone(ZoneId.systemDefault()).toInstant();

        long lastResetSeconds = lastReset.getEpochSecond() - refInstant.getEpochSecond();
        long nowSeconds = now.getEpochSecond() - refInstant.getEpochSecond();

        long lastResetDays = lastResetSeconds < 0 ? (long) Math.floor((double) lastResetSeconds / 86400.0) : lastResetSeconds / 86400;
        long nowDays = nowSeconds < 0 ? (long) Math.floor((double) nowSeconds / 86400.0) : nowSeconds / 86400;

        long kLast = lastResetDays / periodDays;
        long kNow = nowDays / periodDays;

        if (lastResetDays < 0) {
            kLast = (long) Math.floor((double) lastResetDays / periodDays);
        }
        if (nowDays < 0) {
            kNow = (long) Math.floor((double) nowDays / periodDays);
        }

        return (int) Math.max(0, kNow - kLast);
    }

    public int getActiveQuotaRank(PlayerProfile profile) {
        Object activeRankObj = profile.getQuotaProgress().get("active_rank");
        if (activeRankObj instanceof Number) {
            return ((Number) activeRankObj).intValue();
        }
        return profile.getRankLevel();
    }

    public void setActiveQuotaRank(PlayerProfile profile, int rank) {
        profile.getQuotaProgress().put("active_rank", rank);
    }

    public double getProgress(PlayerProfile profile, String resource) {
        String normalized = resource.replace("-", "_");
        Object progressMapObj = profile.getQuotaProgress().get("progress");
        if (progressMapObj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) progressMapObj;
            Object val = map.get(normalized);
            if (val instanceof Number) {
                return ((Number) val).doubleValue();
            }
        } else {
            Object val = profile.getQuotaProgress().get(normalized);
            if (val instanceof Number) {
                return ((Number) val).doubleValue();
            }
        }
        return 0.0;
    }

    @SuppressWarnings("unchecked")
    public void incrementProgress(PlayerProfile profile, String resource, double amount) {
        String normalized = resource.replace("-", "_");
        Map<String, Object> quotaProgress = profile.getQuotaProgress();
        Map<String, Double> progressMap;
        Object progressMapObj = quotaProgress.get("progress");
        if (progressMapObj instanceof Map) {
            progressMap = (Map<String, Double>) progressMapObj;
        } else {
            progressMap = new HashMap<>();
            quotaProgress.put("progress", progressMap);
        }

        double current = progressMap.getOrDefault(normalized, 0.0);
        if (current == 0.0 && quotaProgress.containsKey(normalized)) {
            Object val = quotaProgress.get(normalized);
            if (val instanceof Number) {
                current = ((Number) val).doubleValue();
                quotaProgress.remove(normalized);
            }
        }

        double newValue = current + amount;
        progressMap.put(normalized, newValue);

        checkBaseEloReward(profile, normalized, newValue);
    }

    public boolean isBaseRewarded(PlayerProfile profile, String resource) {
        String normalized = resource.replace("-", "_");
        Object rewardedMapObj = profile.getQuotaProgress().get("base_rewarded");
        if (rewardedMapObj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) rewardedMapObj;
            Object val = map.get(normalized);
            if (val instanceof Boolean) {
                return (Boolean) val;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public void setBaseRewarded(PlayerProfile profile, String resource, boolean rewarded) {
        String normalized = resource.replace("-", "_");
        Map<String, Object> quotaProgress = profile.getQuotaProgress();
        Map<String, Boolean> rewardedMap;
        Object rewardedMapObj = quotaProgress.get("base_rewarded");
        if (rewardedMapObj instanceof Map) {
            rewardedMap = (Map<String, Boolean>) rewardedMapObj;
        } else {
            rewardedMap = new HashMap<>();
            quotaProgress.put("base_rewarded", rewardedMap);
        }
        rewardedMap.put(normalized, rewarded);
    }

    public void checkBaseEloReward(PlayerProfile profile, String resource, double newValue) {
        String normalized = resource.replace("-", "_");
        if (isBaseRewarded(profile, normalized)) {
            return;
        }

        int activeRank = getActiveQuotaRank(profile);
        ObjectiveConfig obj = getObjectiveConfig(activeRank, normalized);
        if (obj == null) {
            return;
        }

        if (newValue >= obj.getTarget()) {
            setBaseRewarded(profile, normalized, true);
            int oldElo = profile.getElo();
            profile.addElo(obj.getBaseElo());
            int newElo = profile.getElo();

            if (DanaRanks.getInstance() != null && DanaRanks.getInstance().getDatabaseManager() != null) {
                DanaRanks.getInstance().getDatabaseManager().logHistory(
                    profile.getUuid(),
                    "BASE_ELO",
                    newElo - oldElo,
                    newElo,
                    "Reached 100% target for " + normalized
                );
            }
        }
    }

    public double getCycleFailurePenalty(int rank) {
        double penalty = 0;
        Map<String, ObjectiveConfig> objectives = getObjectivesForRank(rank);
        for (ObjectiveConfig obj : objectives.values()) {
            penalty += obj.getFailPenalty();
        }
        return penalty;
    }

    public void resetQuotaProgress(PlayerProfile profile, int activeRank) {
        profile.getQuotaProgress().clear();
        setActiveQuotaRank(profile, activeRank);
        profile.getQuotaProgress().put("progress", new HashMap<String, Double>());
        profile.getQuotaProgress().put("base_rewarded", new HashMap<String, Boolean>());
    }

    public void handleOfflineCatchUp(PlayerProfile profile, Instant now) {
        int level = getLevelFromRank(profile.getRankLevel());
        int missedCycles = getMissedCycles(level, profile.getLastReset(), now);
        if (missedCycles <= 0) {
            // Si le quota n'a pas encore été initialisé (quotaProgress vide), on l'initialise
            if (profile.getQuotaProgress().isEmpty()) {
                resetQuotaProgress(profile, profile.getRankLevel());
            }
            return;
        }

        if (level >= 3) {
            int activeRank = getActiveQuotaRank(profile);
            for (int i = 0; i < missedCycles; i++) {
                double penalty = getCycleFailurePenalty(activeRank);
                int penaltyInt = (int) Math.round(penalty);
                if (penaltyInt > 0) {
                    int oldElo = profile.getElo();
                    profile.addElo(-penaltyInt);
                    int newElo = profile.getElo();
                    int actualChange = newElo - oldElo;

                    if (DanaRanks.getInstance() != null && DanaRanks.getInstance().getDatabaseManager() != null) {
                        DanaRanks.getInstance().getDatabaseManager().logHistory(
                            profile.getUuid(),
                            "QUOTA_DECAY",
                            actualChange,
                            newElo,
                            "Quota failed due to absence"
                        );
                    }
                }
                activeRank = profile.getRankLevel();
            }
        }

        resetQuotaProgress(profile, profile.getRankLevel());
        Instant lastResetEffective = getLastResetEffectiveInstant(level, now);
        profile.setLastReset(lastResetEffective);
    }

    public void processGlobalReset(PlayerProfile profile, Instant now) {
        int activeRank = getActiveQuotaRank(profile);
        int level = getLevelFromRank(profile.getRankLevel());

        double totalSurplusElo = 0;
        double totalLossElo = 0;

        Map<String, ObjectiveConfig> objectives = getObjectivesForRank(activeRank);
        for (ObjectiveConfig obj : objectives.values()) {
            double current = getProgress(profile, obj.getName());
            double target = obj.getTarget();

            if (current >= target) {
                if (obj.getMaxSurplusElo() > obj.getBaseElo()) {
                    double diff = current - target;
                    double maxDiff = target * (surplusMultiplier - 1.0);
                    double fraction = maxDiff > 0 ? Math.min(1.0, diff / maxDiff) : 0;
                    double surplus = fraction * (obj.getMaxSurplusElo() - obj.getBaseElo());
                    totalSurplusElo += surplus;
                }
            } else {
                if (level >= 3 && obj.getFailPenalty() > 0) {
                    double fractionMissing = (target - current) / target;
                    double loss = Math.round(fractionMissing * obj.getFailPenalty());
                    totalLossElo += loss;
                }
            }
        }

        int eloChange = 0;
        String description = "";
        
        int surplusInt = (int) Math.round(totalSurplusElo);
        if (surplusInt > 0) {
            eloChange += surplusInt;
            description += "Quota surplus: +" + surplusInt + " ELO. ";
        }
        
        int lossInt = (int) Math.round(totalLossElo);
        if (lossInt > 0) {
            eloChange -= lossInt;
            description += "Quota failure penalty: -" + lossInt + " ELO. ";
        }

        if (eloChange != 0) {
            int oldElo = profile.getElo();
            profile.addElo(eloChange);
            int newElo = profile.getElo();
            int actualChange = newElo - oldElo;

            if (DanaRanks.getInstance() != null && DanaRanks.getInstance().getDatabaseManager() != null) {
                DanaRanks.getInstance().getDatabaseManager().logHistory(
                    profile.getUuid(),
                    eloChange > 0 ? "QUOTA_SURPLUS" : "QUOTA_FAILURE",
                    actualChange,
                    newElo,
                    description.trim()
                );
            }
        }

        resetQuotaProgress(profile, profile.getRankLevel());
        Instant lastResetEffective = getLastResetEffectiveInstant(level, now);
        profile.setLastReset(lastResetEffective);
    }

    public double getSurplusMultiplier() {
        return surplusMultiplier;
    }

    public double getScalingMultiplierPerRank() {
        return scalingMultiplierPerRank;
    }

    public String getRefDateStr() {
        return refDateStr;
    }

    public int getResetHour() {
        return resetHour;
    }
}
