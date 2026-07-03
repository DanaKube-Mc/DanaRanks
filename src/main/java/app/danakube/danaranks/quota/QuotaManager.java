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

    private final QuotaGenerator quotaGenerator = new QuotaGenerator();
    private ResetCalculator resetCalculator;
    private double surplusMultiplier = 10.0;
    private String refDateStr = "2026-07-03";
    private int resetHour = 4;

    public static QuotaManager getInstance() {
        return instance;
    }

    public static void setInstance(QuotaManager manager) {
        instance = manager;
    }

    public QuotaManager() {
        this.resetCalculator = new ResetCalculator(refDateStr, resetHour);
    }

    public void loadConfig(FileConfiguration config, Logger logger) {
        if (config == null) {
            return;
        }

        int hr = config.getInt("reset.hour", 4);
        if (hr < 0 || hr > 23) {
            if (logger != null) {
                logger.warning("[DanaRanks] Invalid reset.hour: " + hr + ". Using default: 4");
            }
            this.resetHour = 4;
        } else {
            this.resetHour = hr;
        }

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

        this.resetCalculator = new ResetCalculator(refDateStr, resetHour);

        double mult = config.getDouble("quotas-settings.surplus-multiplier", 10.0);
        if (mult < 1.0) {
            if (logger != null) {
                logger.warning("[DanaRanks] Invalid quotas-settings.surplus-multiplier: " + mult + ". Using default: 10.0");
            }
            this.surplusMultiplier = 10.0;
        } else {
            this.surplusMultiplier = mult;
        }

        quotaGenerator.loadConfig(config, logger);
    }

    public Map<String, ObjectiveConfig> getObjectivesForRank(int rank) {
        return quotaGenerator.getObjectivesForRank(rank);
    }

    public ObjectiveConfig getObjectiveConfig(int rank, String resource) {
        return quotaGenerator.getObjectiveConfig(rank, resource);
    }

    public int getLevelFromRank(int rankLevel) {
        if (rankLevel <= 10) return 1;
        if (rankLevel <= 20) return 2;
        if (rankLevel <= 30) return 3;
        if (rankLevel <= 40) return 4;
        return 5;
    }

    public int getPeriodDays(int level) {
        return resetCalculator.getPeriodDays(level);
    }

    public Instant getNextResetInstant(int periodDays, Instant now) {
        return resetCalculator.getNextResetInstant(periodDays, now);
    }

    public Instant getLastResetEffectiveInstant(int level, Instant now) {
        return resetCalculator.getLastResetEffectiveInstant(level, now);
    }

    public int getMissedCycles(int level, Instant lastReset, Instant now) {
        return resetCalculator.getMissedCycles(level, lastReset, now);
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
        return quotaGenerator.getScalingMultiplierPerRank();
    }

    public String getRefDateStr() {
        return refDateStr;
    }

    public int getResetHour() {
        return resetHour;
    }

    public QuotaGenerator getQuotaGenerator() {
        return quotaGenerator;
    }

    public ResetCalculator getResetCalculator() {
        return resetCalculator;
    }
}
