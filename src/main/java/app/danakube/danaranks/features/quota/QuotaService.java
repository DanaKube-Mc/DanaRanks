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
        this.progressTracker.setQuotaService(this);
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
        RankBracket bracket = QuotaConfigLoader.getBracketForRank(quotaConfig, rank);
        return bracket.failPenalty();
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

        int startElo = profile.getElo();
        int startRank = profile.getRankLevel();
        int startCumulative = (startRank - 1) * 100 + startElo;

        // Évaluer le PREMIER cycle expiré avec la progression réelle accumulée
        double firstCycleEloChange = 0;
        Map<String, ObjectiveConfig> objectives = progressTracker.getActiveObjectives(profile);
        for (ObjectiveConfig obj : objectives.values()) {
            double current = progressTracker.getProgress(profile, obj.name());
            double target = obj.target();
            if (current >= target) {
                if (obj.maxSurplusElo() > obj.baseElo()) {
                    double diff = current - target;
                    double maxDiff = target * (quotaConfig.surplusMultiplier() - 1.0);
                    double fraction = maxDiff > 0 ? Math.min(1.0, diff / maxDiff) : 0;
                    double surplus = fraction * (obj.maxSurplusElo() - obj.baseElo());
                    firstCycleEloChange += surplus;
                }
            } else {
                if (level >= 3 && obj.failPenalty() > 0) {
                    double fractionMissing = (target - current) / target;
                    double loss = Math.round(fractionMissing * obj.failPenalty());
                    firstCycleEloChange -= loss;
                }
            }
        }

        int firstCycleEloChangeInt = (int) Math.round(firstCycleEloChange);
        if (firstCycleEloChangeInt != 0 && eloService != null) {
            eloService.addElo(profile, firstCycleEloChangeInt, firstCycleEloChangeInt > 0 ? "QUOTA_SURPLUS" : "QUOTA_FAILURE");
        }

        // Appliquer la pénalité de déchéance complète pour les cycles manqués SUIVANTS (car 0 progression)
        if (level >= 3 && missedCycles > 1) {
            int nextActiveRank = profile.getRankLevel();
            for (int i = 1; i < missedCycles; i++) {
                double penalty = getCycleFailurePenalty(nextActiveRank);
                int penaltyInt = (int) Math.round(penalty);
                if (penaltyInt > 0 && eloService != null) {
                    eloService.addElo(profile, -penaltyInt, "QUOTA_DECAY");
                }
                nextActiveRank = profile.getRankLevel();
            }
        }

        progressTracker.resetQuotaProgress(profile, profile.getRankLevel());
        Instant lastResetEffective = quotaScheduler.getLastResetEffectiveInstant(level, now);
        profile.setLastReset(lastResetEffective);

        int endElo = profile.getElo();
        int endRank = profile.getRankLevel();
        int endCumulative = (endRank - 1) * 100 + endElo;
        int netEloChange = endCumulative - startCumulative;

        if (netEloChange != 0) {
            String sign = netEloChange > 0 ? "+" : "";
            profile.getQuotaProgress().put("quota_pending_summary", sign + netEloChange + " ELO");
        }
    }

    public void processGlobalReset(PlayerProfile profile, Instant now) {
        int level = getLevelFromRank(profile.getRankLevel());

        double totalSurplusElo = 0;
        double totalLossElo = 0;

        Map<String, ObjectiveConfig> objectives = progressTracker.getActiveObjectives(profile);
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
