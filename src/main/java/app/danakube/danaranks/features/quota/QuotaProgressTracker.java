package app.danakube.danaranks.features.quota;

import app.danakube.danaranks.core.profile.EloService;
import app.danakube.danaranks.core.profile.PlayerProfile;

import java.util.HashMap;
import java.util.Map;

public class QuotaProgressTracker {
    private final EloService eloService;

    public QuotaProgressTracker(EloService eloService) {
        this.eloService = eloService;
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
    public void incrementProgress(PlayerProfile profile, QuotaConfig quotaConfig, String resource, double amount) {
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

        checkBaseEloReward(profile, quotaConfig, normalized, newValue);

        // Annonces de progression de quota
        if (org.bukkit.Bukkit.getServer() != null) {
            org.bukkit.entity.Player onlinePlayer = org.bukkit.Bukkit.getPlayer(profile.getUuid());
            if (onlinePlayer != null) {
                try {
                app.danakube.danaranks.core.DanaRanks plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(app.danakube.danaranks.core.DanaRanks.class);
                if (plugin != null) {
                    org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
                    java.util.List<Integer> milestones = config.getIntegerList("quotas-settings.announce-milestones");
                    if (milestones == null || milestones.isEmpty()) {
                        milestones = java.util.List.of(50, 100);
                    }

                    int activeRank = getActiveQuotaRank(profile);
                    ObjectiveConfig obj = QuotaConfigLoader.getObjectiveConfig(quotaConfig, activeRank, normalized);
                    if (obj != null) {
                        double target = obj.target();
                        double oldVal = current;

                        Map<String, Object> progressData = profile.getQuotaProgress();
                        Map<String, Object> announcedMap;
                        Object announcedObj = progressData.get("announced_milestones");
                        if (announcedObj instanceof Map) {
                            announcedMap = (Map<String, Object>) announcedObj;
                        } else {
                            announcedMap = new HashMap<>();
                            progressData.put("announced_milestones", announcedMap);
                        }

                        @SuppressWarnings("unchecked")
                        java.util.List<Integer> announcedList = (java.util.List<Integer>) announcedMap.computeIfAbsent(normalized, k -> new java.util.ArrayList<Integer>());

                        for (int milestone : milestones) {
                            double milestoneTarget = target * (milestone / 100.0);
                            if (newValue >= milestoneTarget && oldVal < milestoneTarget && !announcedList.contains(milestone)) {
                                announcedList.add(milestone);
                                String msg;
                                if (milestone >= 100) {
                                    msg = String.format("<green>[Quotas] Objectif %s atteint ! (+%d ELO)</green>", obj.name(), obj.baseElo());
                                } else {
                                    msg = String.format("<yellow>[Quotas] Progression : %s à %d%% (%.0f/%.0f)</yellow>", obj.name(), milestone, newValue, target);
                                }
                                onlinePlayer.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(msg));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // S'exécute silencieusement en cas d'absence du plugin (tests)
            }
        }
    }
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

    public void checkBaseEloReward(PlayerProfile profile, QuotaConfig quotaConfig, String resource, double newValue) {
        String normalized = resource.replace("-", "_");
        if (isBaseRewarded(profile, normalized)) {
            return;
        }

        int activeRank = getActiveQuotaRank(profile);
        ObjectiveConfig obj = QuotaConfigLoader.getObjectiveConfig(quotaConfig, activeRank, normalized);
        if (obj == null) {
            return;
        }

        if (newValue >= obj.target()) {
            setBaseRewarded(profile, normalized, true);
            if (eloService != null) {
                eloService.addElo(profile, obj.baseElo(), "BASE_ELO");
            }
        }
    }

    public void resetQuotaProgress(PlayerProfile profile, int activeRank) {
        profile.getQuotaProgress().clear();
        setActiveQuotaRank(profile, activeRank);
        profile.getQuotaProgress().put("progress", new HashMap<String, Double>());
        profile.getQuotaProgress().put("base_rewarded", new HashMap<String, Boolean>());
    }
}
