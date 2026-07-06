package app.danakube.danaranks.features.quota;

import app.danakube.danaranks.core.profile.EloService;
import app.danakube.danaranks.core.profile.PlayerProfile;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Instant;
import java.util.Map;
import java.util.logging.Logger;

public class QuotaService {
    private final EloService eloService;
    private final QuotaProgressTracker progressTracker;
    private QuotaConfig quotaConfig;
    private QuotaScheduler quotaScheduler;

    public QuotaService(EloService eloService, QuotaProgressTracker progressTracker) {
        this.eloService = eloService;
        this.progressTracker = progressTracker;
        this.quotaConfig = QuotaConfigLoader.load(null, null);
        this.quotaScheduler = new QuotaScheduler(quotaConfig.refDateStr(), quotaConfig.resetHour());
    }

    public void loadConfig(FileConfiguration config, Logger logger) {
        this.quotaConfig = QuotaConfigLoader.load(config, logger);
        this.quotaScheduler = new QuotaScheduler(quotaConfig.refDateStr(), quotaConfig.resetHour());
    }

    public QuotaConfig getQuotaConfig() {
        return quotaConfig;
    }

    public QuotaScheduler getQuotaScheduler() {
        return quotaScheduler;
    }

    public QuotaProgressTracker getProgressTracker() {
        return progressTracker;
    }

    public int getLevelFromRank(int rankLevel) {
        if (rankLevel <= 10) return 1;
        if (rankLevel <= 20) return 2;
        if (rankLevel <= 30) return 3;
        if (rankLevel <= 40) return 4;
        return 5;
    }

    public double getCycleFailurePenalty(int rank) {
        double penalty = 0;
        Map<String, ObjectiveConfig> objectives = QuotaConfigLoader.getObjectivesForRank(quotaConfig, rank);
        for (ObjectiveConfig obj : objectives.values()) {
            penalty += obj.failPenalty();
        }
        return penalty;
    }

    public void handleOfflineCatchUp(PlayerProfile profile, Instant now) {
        int level = getLevelFromRank(profile.getRankLevel());
        int missedCycles = quotaScheduler.getMissedCycles(level, profile.getLastReset(), now);
        if (missedCycles <= 0) {
            if (profile.getQuotaProgress().isEmpty()) {
                progressTracker.resetQuotaProgress(profile, profile.getRankLevel());
            }
            return;
        }

        if (level >= 3) {
            int activeRank = progressTracker.getActiveQuotaRank(profile);
            for (int i = 0; i < missedCycles; i++) {
                double penalty = getCycleFailurePenalty(activeRank);
                int penaltyInt = (int) Math.round(penalty);
                if (penaltyInt > 0 && eloService != null) {
                    eloService.addElo(profile, -penaltyInt, "QUOTA_DECAY");
                }
                activeRank = profile.getRankLevel();
            }
        }

        progressTracker.resetQuotaProgress(profile, profile.getRankLevel());
        Instant lastResetEffective = quotaScheduler.getLastResetEffectiveInstant(level, now);
        profile.setLastReset(lastResetEffective);
    }

    public void processGlobalReset(PlayerProfile profile, Instant now) {
        int activeRank = progressTracker.getActiveQuotaRank(profile);
        int level = getLevelFromRank(profile.getRankLevel());

        double totalSurplusElo = 0;
        double totalLossElo = 0;

        Map<String, ObjectiveConfig> objectives = QuotaConfigLoader.getObjectivesForRank(quotaConfig, activeRank);
        for (ObjectiveConfig obj : objectives.values()) {
            double current = progressTracker.getProgress(profile, obj.name());
            double target = obj.target();

            if (current >= target) {
                if (obj.maxSurplusElo() > obj.baseElo()) {
                    double diff = current - target;
                    double maxDiff = target * (quotaConfig.surplusMultiplier() - 1.0);
                    double fraction = maxDiff > 0 ? Math.min(1.0, diff / maxDiff) : 0;
                    double surplus = fraction * (obj.maxSurplusElo() - obj.baseElo());
                    totalSurplusElo += surplus;
                }
            } else {
                if (level >= 3 && obj.failPenalty() > 0) {
                    double fractionMissing = (target - current) / target;
                    double loss = Math.round(fractionMissing * obj.failPenalty());
                    totalLossElo += loss;
                }
            }
        }

        int eloChange = 0;
        int surplusInt = (int) Math.round(totalSurplusElo);
        if (surplusInt > 0) {
            eloChange += surplusInt;
        }
        int lossInt = (int) Math.round(totalLossElo);
        if (lossInt > 0) {
            eloChange -= lossInt;
        }

        if (eloChange != 0 && eloService != null) {
            eloService.addElo(profile, eloChange, eloChange > 0 ? "QUOTA_SURPLUS" : "QUOTA_FAILURE");
        }

        progressTracker.resetQuotaProgress(profile, profile.getRankLevel());
        Instant lastResetEffective = quotaScheduler.getLastResetEffectiveInstant(level, now);
        profile.setLastReset(lastResetEffective);
    }
}
