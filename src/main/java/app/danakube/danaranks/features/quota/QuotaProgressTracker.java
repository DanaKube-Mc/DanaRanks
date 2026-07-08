package app.danakube.danaranks.features.quota;

import app.danakube.danaranks.core.profile.EloService;
import app.danakube.danaranks.core.profile.PlayerProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class QuotaProgressTracker {
    private final EloService eloService;
    private QuotaService quotaService;

    public QuotaProgressTracker(EloService eloService) {
        this.eloService = eloService;
    }

    public void setQuotaService(QuotaService quotaService) {
        this.quotaService = quotaService;
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

        Map<String, ObjectiveConfig> active = getActiveObjectives(profile);
        if (!active.containsKey(normalized)) {
            return;
        }

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

        if (Bukkit.getServer() != null) {
            Player onlinePlayer = Bukkit.getPlayer(profile.getUuid());
            if (onlinePlayer != null) {
                try {
                    DanaRanks plugin = JavaPlugin.getPlugin(DanaRanks.class);
                    if (plugin != null) {
                        FileConfiguration config = plugin.getConfig();
                        List<Integer> milestones = config.getIntegerList("quotas-settings.announce-milestones");
                        if (milestones == null || milestones.isEmpty()) {
                            milestones = List.of(50, 100);
                        }

                        ObjectiveConfig obj = getActiveObjectives(profile).get(normalized);
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

                            List<Integer> announcedList = (List<Integer>) announcedMap.computeIfAbsent(normalized, k -> new ArrayList<>());

                            for (int milestone : milestones) {
                                double milestoneTarget = target * (milestone / 100.0);
                                if (newValue >= milestoneTarget && oldVal < milestoneTarget && !announcedList.contains(milestone)) {
                                    announcedList.add(milestone);
                                    if (milestone >= 100) {
                                        onlinePlayer.sendMessage(plugin.getMessageManager().getMessageComponent("quota-milestone-reached",
                                                "<green>[Quotas] Objectif %objective% atteint ! (+%elo% ELO)</green>",
                                                Map.of("%objective%", plugin.getResourceDisplayName(obj.name()), "%elo%", String.valueOf(obj.baseElo()))));
                                    } else {
                                        onlinePlayer.sendMessage(plugin.getMessageManager().getMessageComponent("quota-milestone-progress",
                                                "<yellow>[Quotas] Progression : %objective% à %milestone%%% (%progress%/%target%)</yellow>",
                                                Map.of("%objective%", plugin.getResourceDisplayName(obj.name()), "%milestone%", String.valueOf(milestone),
                                                        "%progress%", String.format("%.0f", newValue), "%target%", String.format("%.0f", target))));
                                    }
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

        ObjectiveConfig obj = getActiveObjectives(profile).get(normalized);
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
        initializeActiveObjectives(profile, activeRank);
    }

    public Map<String, ObjectiveConfig> getActiveObjectives(PlayerProfile profile) {
        Map<String, Object> progress = profile.getQuotaProgress();
        Object activeObj = progress.get("active_objectives");
        if (activeObj instanceof Map) {
            Map<String, ObjectiveConfig> result = new HashMap<>();
            Map<?, ?> map = (Map<?, ?>) activeObj;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String name = (String) entry.getKey();
                Map<?, ?> objData = (Map<?, ?>) entry.getValue();

                double target = ((Number) objData.get("target")).doubleValue();
                int baseElo = ((Number) objData.get("baseElo")).intValue();
                int maxSurplusElo = ((Number) objData.get("maxSurplusElo")).intValue();
                int failPenalty = ((Number) objData.get("failPenalty")).intValue();

                String normalized = name.replace("-", "_");
                String material = null;
                Integer cmd = null;
                if (quotaService != null && quotaService.getQuotaConfig() != null) {
                    ObjectiveConfig base = quotaService.getQuotaConfig().baseObjectives().get(normalized);
                    if (base != null) {
                        material = base.material();
                        cmd = base.customModelData();
                    }
                }

                result.put(name, new ObjectiveConfig(name, target, baseElo, maxSurplusElo, failPenalty, material, cmd));
            }
            return result;
        }

        int activeRank = getActiveQuotaRank(profile);
        initializeActiveObjectives(profile, activeRank);
        return getActiveObjectives(profile);
    }

    public void initializeActiveObjectives(PlayerProfile profile, int rank) {
        QuotaConfig quotaConfig = null;
        if (quotaService != null) {
            quotaConfig = quotaService.getQuotaConfig();
        } else {
            try {
                DanaRanks ranksInstance = JavaPlugin.getPlugin(DanaRanks.class);
                if (ranksInstance != null && ranksInstance.getQuotaService() != null) {
                    quotaConfig = ranksInstance.getQuotaService().getQuotaConfig();
                }
            } catch (Exception e) {
                // ignore
            }
        }

        if (quotaConfig == null) {
            Map<String, Map<String, Object>> activeMap = new HashMap<>();
            List<String> names = List.of("lumens_gained", "job_xp_all");
            for (String name : names) {
                Map<String, Object> objData = new HashMap<>();
                objData.put("name", name);
                objData.put("target", name.equals("lumens_gained") ? 1000.0 : 500.0);
                objData.put("baseElo", 5);
                objData.put("maxSurplusElo", 10);
                objData.put("failPenalty", 0);
                activeMap.put(name, objData);
            }
            profile.getQuotaProgress().put("active_objectives", activeMap);
            return;
        }


        List<ObjectiveConfig> potential = new ArrayList<>();
        for (ObjectiveConfig base : quotaConfig.baseObjectives().values()) {
            double scaledTarget = Math.round(base.target() * Math.pow(quotaConfig.scalingMultiplierPerRank(), rank - 1));
            potential.add(new ObjectiveConfig(base.name(), scaledTarget, base.baseElo(), base.maxSurplusElo(), base.failPenalty()));
        }

        int maxObj = quotaConfig.maxObjectives();
        if (maxObj <= 0) {
            maxObj = potential.size();
        }

        List<ObjectiveConfig> selected = new ArrayList<>();
        if (potential.size() <= maxObj) {
            selected.addAll(potential);
        } else {
            List<ObjectiveConfig> copy = new ArrayList<>(potential);
            java.util.Collections.shuffle(copy);
            for (int i = 0; i < maxObj; i++) {
                selected.add(copy.get(i));
            }
        }

        int activeCount = selected.size();
        int baseEloEach = activeCount > 0 ? (int) Math.round((double) quotaConfig.globalBaseElo() / activeCount) : 0;
        int maxSurplusEach = activeCount > 0 ? (int) Math.round((double) quotaConfig.globalMaxSurplusElo() / activeCount) : 0;
        int failPenaltyEach = activeCount > 0 ? (int) Math.round((double) quotaConfig.globalFailPenalty() / activeCount) : 0;

        Map<String, Map<String, Object>> activeMap = new HashMap<>();
        for (ObjectiveConfig obj : selected) {
            Map<String, Object> objData = new HashMap<>();
            objData.put("name", obj.name());
            objData.put("target", obj.target());
            objData.put("baseElo", baseEloEach);
            objData.put("maxSurplusElo", maxSurplusEach);
            objData.put("failPenalty", failPenaltyEach);
            activeMap.put(obj.name(), objData);
        }

        profile.getQuotaProgress().put("active_objectives", activeMap);
    }
}
