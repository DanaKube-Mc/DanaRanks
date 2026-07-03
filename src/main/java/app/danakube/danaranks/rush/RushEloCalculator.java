package app.danakube.danaranks.rush;

import app.danakube.danaranks.profile.PlayerProfile;
import java.util.*;

public class RushEloCalculator {

    public static class TierSettings {
        private final double eloFactor;
        private final double gainMultiplier;
        private final double lossMultiplier;

        public TierSettings(double eloFactor, double gainMultiplier, double lossMultiplier) {
            this.eloFactor = eloFactor;
            this.gainMultiplier = gainMultiplier;
            this.lossMultiplier = lossMultiplier;
        }

        public double getEloFactor() {
            return eloFactor;
        }

        public double getGainMultiplier() {
            return gainMultiplier;
        }

        public double getLossMultiplier() {
            return lossMultiplier;
        }
    }

    public static Map<String, List<PlayerProfile>> groupProfilesByTier(List<PlayerProfile> profiles) {
        Map<String, List<PlayerProfile>> tiers = new HashMap<>();
        tiers.put("fer", new ArrayList<>());
        tiers.put("bronze", new ArrayList<>());
        tiers.put("argent", new ArrayList<>());
        tiers.put("or", new ArrayList<>());
        tiers.put("platine", new ArrayList<>());

        for (PlayerProfile profile : profiles) {
            String tierName = getTierName(profile.getRankLevel());
            tiers.get(tierName).add(profile);
        }
        return tiers;
    }

    public static String getTierName(int rankLevel) {
        if (rankLevel <= 10) return "fer";
        if (rankLevel <= 20) return "bronze";
        if (rankLevel <= 30) return "argent";
        if (rankLevel <= 40) return "or";
        return "platine";
    }

    public static void calculateIntraRankEloChanges(
            List<PlayerProfile> tierPlayers,
            Map<UUID, Double> scores,
            double eloFactor,
            Map<UUID, Integer> eloChanges
    ) {
        double maxScore = 0;
        for (PlayerProfile p : tierPlayers) {
            double s = scores.getOrDefault(p.getUuid(), 0.0);
            if (s > maxScore) maxScore = s;
        }

        if (maxScore == 0.0) {
            for (PlayerProfile p : tierPlayers) {
                eloChanges.put(p.getUuid(), 0);
            }
        } else {
            double sumR = 0;
            Map<UUID, Double> rValues = new HashMap<>();
            for (PlayerProfile p : tierPlayers) {
                double r = scores.getOrDefault(p.getUuid(), 0.0) / maxScore;
                rValues.put(p.getUuid(), r);
                sumR += r;
            }
            double meanR = sumR / tierPlayers.size();

            for (PlayerProfile p : tierPlayers) {
                double d = rValues.get(p.getUuid()) - meanR;
                d = Math.round(d * 1000000.0) / 1000000.0;
                double changeRaw = eloFactor * d;
                int change = (changeRaw >= 0) ? (int) Math.round(changeRaw) : -((int) Math.round(Math.abs(changeRaw)));
                eloChanges.put(p.getUuid(), change);
            }
        }
    }

    public static void calculateOrphanEloChanges(
            List<PlayerProfile> orphans,
            Map<UUID, Double> scores,
            Map<UUID, Integer> eloChanges,
            Map<String, TierSettings> tierSettingsMap
    ) {
        if (orphans.size() >= 2) {
            double maxScore = 0;
            for (PlayerProfile p : orphans) {
                double s = scores.getOrDefault(p.getUuid(), 0.0);
                if (s > maxScore) maxScore = s;
            }

            if (maxScore == 0.0) {
                for (PlayerProfile p : orphans) {
                    eloChanges.put(p.getUuid(), 0);
                }
            } else {
                double sumR = 0;
                Map<UUID, Double> rValues = new HashMap<>();
                for (PlayerProfile p : orphans) {
                    double r = scores.getOrDefault(p.getUuid(), 0.0) / maxScore;
                    rValues.put(p.getUuid(), r);
                    sumR += r;
                }
                double meanR = sumR / orphans.size();

                for (PlayerProfile p : orphans) {
                    double d = rValues.get(p.getUuid()) - meanR;
                    d = Math.round(d * 1000000.0) / 1000000.0;
                    double changeRaw = 30.0 * d;
                    int changeRawInt = (changeRaw >= 0) ? (int) Math.round(changeRaw) : -((int) Math.round(Math.abs(changeRaw)));

                    String tier = getTierName(p.getRankLevel());
                    TierSettings settings = tierSettingsMap.get(tier);
                    double mult = 1.0;
                    if (settings != null) {
                        mult = (changeRawInt >= 0) ? settings.getGainMultiplier() : settings.getLossMultiplier();
                    }

                    double finalChangeRaw = changeRawInt * mult;
                    int finalChange = (finalChangeRaw >= 0) ? (int) Math.round(finalChangeRaw) : -((int) Math.round(Math.abs(finalChangeRaw)));

                    eloChanges.put(p.getUuid(), finalChange);
                }
            }
        } else if (orphans.size() == 1) {
            eloChanges.put(orphans.getFirst().getUuid(), 0);
        }
    }
}
