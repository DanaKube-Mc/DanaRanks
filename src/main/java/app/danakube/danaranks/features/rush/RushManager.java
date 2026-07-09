package app.danakube.danaranks.features.rush;

import app.danakube.danaranks.core.DanaRanks;
import app.danakube.danaranks.core.profile.EloService;
import app.danakube.danaranks.core.profile.PlayerProfile;
import app.danakube.danaranks.database.DatabaseManager;
import app.danakube.danaranks.database.HistoryRepository;
import app.danakube.danaranks.database.ProfileRepository;
import app.danakube.danaranks.features.quota.ObjectiveConfig;
import app.danakube.danaranks.features.quota.QuotaConfig;
import app.danakube.danaranks.features.quota.QuotaConfigLoader;
import app.danakube.danaranks.features.quota.QuotaScheduler;
import app.danakube.danaranks.features.quota.QuotaService;
import app.danakube.danaranks.features.rush.ui.DiscordWebhook;
import app.danakube.danaranks.features.rush.ui.RushBossBar;
import app.danakube.danaranks.api.event.DanaRushStartEvent;
import app.danakube.danaranks.api.event.DanaRushEndEvent;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class RushManager {

    private final DanaRanks plugin;
    private final RushBossBar visualManager;
    private final Logger logger;
    private final RushEventState state = new RushEventState();
    private final RushScoreTracker scoreTracker = new RushScoreTracker(state);

    private Map<UUID, PlayerProfile> profileCacheOverride = null;
    private DatabaseManager databaseManagerOverride = null;
    private ProfileRepository profileRepositoryOverride = null;
    private HistoryRepository historyRepositoryOverride = null;
    private EloService eloServiceOverride = null;

    public void setProfileCacheOverride(Map<UUID, PlayerProfile> cache) {
        this.profileCacheOverride = cache;
    }

    public void setDatabaseManagerOverride(DatabaseManager db) {
        this.databaseManagerOverride = db;
    }

    private Map<UUID, PlayerProfile> getProfileCacheMap() {
        if (profileCacheOverride != null) return profileCacheOverride;
        return plugin != null ? plugin.getProfileCache().getBackingMap() : new HashMap<>();
    }

    private ProfileRepository getProfileRepository() {
        if (profileRepositoryOverride != null) return profileRepositoryOverride;
        if (databaseManagerOverride != null) {
            return new ProfileRepository(databaseManagerOverride);
        }
        return plugin != null ? plugin.getProfileRepository() : null;
    }

    private HistoryRepository getHistoryRepository() {
        if (historyRepositoryOverride != null) return historyRepositoryOverride;
        if (databaseManagerOverride != null) {
            return new HistoryRepository(databaseManagerOverride);
        }
        return plugin != null ? plugin.getHistoryRepository() : null;
    }

    private EloService getEloService() {
        if (eloServiceOverride != null) return eloServiceOverride;
        return plugin != null ? plugin.getEloService() : new EloService(null, getHistoryRepository());
    }

    private int dailySetupHour = 8;
    private int minStartHour = 12;
    private int maxStartHour = 22;
    private int minDurationMinutes = 20;
    private int maxDurationMinutes = 60;
    private int preAnnounceMinutes = 30;
    private List<String> eligibleResources = new ArrayList<>();
    private String discordWebhookUrl = "";

    private final Map<String, RankSetting> rankSettings = new HashMap<>();

    public static class RankSetting {
        public double eloFactor;
        public double gainMultiplier;
        public double lossMultiplier;

        public RankSetting(double eloFactor, double gainMultiplier, double lossMultiplier) {
            this.eloFactor = eloFactor;
            this.gainMultiplier = gainMultiplier;
            this.lossMultiplier = lossMultiplier;
        }
    }

    public RushManager(DanaRanks plugin) {
        this.plugin = plugin;
        this.visualManager = new RushBossBar(plugin);
        this.logger = plugin != null ? plugin.getLogger() : Logger.getLogger("RushManager");
    }

    public void loadConfig(FileConfiguration config) {
        this.dailySetupHour = config.getInt("rush.daily-setup-hour", 8);
        this.minStartHour = config.getInt("rush.start-window.min-hour", 12);
        this.maxStartHour = config.getInt("rush.start-window.max-hour", 22);
        this.minDurationMinutes = config.getInt("rush.duration-range.min-minutes", 20);
        this.maxDurationMinutes = config.getInt("rush.duration-range.max-minutes", 60);
        this.preAnnounceMinutes = config.getInt("rush.pre-announce-minutes", 30);
        this.eligibleResources = config.getStringList("rush.eligible-resources");
        this.discordWebhookUrl = config.getString("rush.discord-webhook-url", "");

        String[] levels = {"fer", "bronze", "argent", "or", "platine"};
        for (String level : levels) {
            double factor = config.getDouble("rush.rank-settings." + level + ".elo-factor", 30.0);
            double gainMult = config.getDouble("rush.rank-settings." + level + ".gain-multiplier", 1.0);
            double lossMult = config.getDouble("rush.rank-settings." + level + ".loss-multiplier", 1.0);
            rankSettings.put(level.toLowerCase(), new RankSetting(factor, gainMult, lossMult));
        }

        visualManager.loadConfig(config);
    }

    public void startScheduler() {
        if (plugin == null) return;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            LocalDateTime now = LocalDateTime.now();
            Instant nowInstant = now.atZone(ZoneId.systemDefault()).toInstant();

            if (now.getHour() == dailySetupHour && now.getMinute() == 0 && now.getSecond() == 0) {
                if (state.getLastPlannedDate() == null || !state.getLastPlannedDate().equals(now.toLocalDate())) {
                    setupDaily(now);
                }
            }

            tickRush(nowInstant);
        }, 20L, 20L);
    }

    public void setupDaily(LocalDateTime now) {
        RushScheduler.planNextRush(state, eligibleResources, minStartHour, maxStartHour, minDurationMinutes, maxDurationMinutes, now);
        
        logger.info("[Rush] Daily Rush setup completed. Resource: " + state.getDailyResource() + 
                ", Start: " + state.getStartTime() + ", Duration: " + state.getDurationMinutes() + "m.");

        announceRegistration();
    }

    private void announceRegistration() {
        if (plugin == null) return;
        LocalDateTime start = state.getStartTime();
        String timeStr = "";
        if (start != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            timeStr = start.format(formatter);
        }
        String durationStr = String.valueOf(state.getDurationMinutes());
        String resourceName = plugin.getResourceDisplayName(state.getDailyResource());

        Map<String, String> placeholders = Map.of(
                "%resource%", resourceName,
                "%time%", timeStr,
                "%duration%", durationStr
        );

        Bukkit.broadcast(getMessageComponent("rush-planned-announcement",
                "<blue>[Rush] Le Rush quotidien sur la ressource <gold>%resource%</gold> est planifié ! Tapez <yellow>/rush join</yellow> pour vous inscrire !</blue>",
                placeholders));
        sendDiscordWebhook(formatMessage("rush-discord-planned",
                "[Rush] Le Rush sur la ressource %resource% est planifié pour aujourd'hui ! Les inscriptions sont ouvertes via /rush join !",
                placeholders));
    }

    public boolean registerPlayer(UUID uuid, Instant now) {
        if (!state.isDailyPlanned() || state.getStartTime() == null) {
            return false;
        }

        Instant startInstant = state.getStartTime().atZone(ZoneId.systemDefault()).toInstant();
        Instant endInstant = startInstant.plusSeconds(state.getDurationMinutes() * 60L);

        if (now.isAfter(endInstant)) {
            return false;
        }

        if (scoreTracker.isRegistered(uuid)) {
            return false;
        }

        scoreTracker.registerPlayer(uuid);
        logger.info("[Rush] Player registered: " + uuid + " at " + now);
        return true;
    }

    public boolean unregisterPlayer(UUID uuid) {
        if (!scoreTracker.isRegistered(uuid)) {
            return false;
        }
        scoreTracker.unregisterPlayer(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            visualManager.hideActiveBar(player);
        }
        logger.info("[Rush] Player unregistered (left the event): " + uuid);
        return true;
    }

    public double getPlayerScore(UUID uuid) {
        if (!scoreTracker.isRegistered(uuid)) {
            return -1.0;
        }
        return scoreTracker.getScore(uuid);
    }

    public int getRegisteredPlayersCount() {
        return state.getRegisteredScores().size();
    }

    public boolean isDailyPlanned() {
        return state.isDailyPlanned();
    }

    public String getDailyResource() {
        return state.getDailyResource();
    }

    public LocalDateTime getStartTime() {
        return state.getStartTime();
    }

    public int getDurationMinutes() {
        return state.getDurationMinutes();
    }

    public int getPreAnnounceMinutes() {
        return preAnnounceMinutes;
    }

    public RushEventState getState() {
        return state;
    }

    public RushBossBar getVisualManager() {
        return visualManager;
    }

    public boolean isRegistrationOpen(Instant now) {
        if (!state.isDailyPlanned() || state.getStartTime() == null) return false;
        Instant startInstant = state.getStartTime().atZone(ZoneId.systemDefault()).toInstant();
        Instant endInstant = startInstant.plusSeconds(state.getDurationMinutes() * 60L);
        return state.isRegistrationOpen() && !now.isAfter(endInstant);
    }

    public boolean isRushActive(Instant now) {
        if (!state.isDailyPlanned() || state.getStartTime() == null) return false;
        Instant startInstant = state.getStartTime().atZone(ZoneId.systemDefault()).toInstant();
        Instant endInstant = startInstant.plusSeconds(state.getDurationMinutes() * 60L);
        return !now.isBefore(startInstant) && !now.isAfter(endInstant);
    }

    public void handleResourceGain(UUID uuid, String resource, double amount, Instant now) {
        if (!state.isDailyPlanned() || state.getDailyResource() == null) return;
        String normalizedResource = resource.replace("-", "_");
        String normalizedDaily = state.getDailyResource().replace("-", "_");

        if (!normalizedResource.equalsIgnoreCase(normalizedDaily)) return;
        if (!isRushActive(now)) return;
        if (!scoreTracker.isRegistered(uuid)) return;

        if (isTriggeredByAdminCommand()) {
            logger.warning("[Rush] Blocked administrative resource gain for " + uuid);
            return;
        }

        scoreTracker.incrementScore(uuid, amount);

        if (plugin != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Instant startInstant = state.getStartTime().atZone(ZoneId.systemDefault()).toInstant();
                long totalSecs = state.getDurationMinutes() * 60L;
                long elapsedSecs = now.getEpochSecond() - startInstant.getEpochSecond();
                long remainingSecs = totalSecs - elapsedSecs;
                float progress = (float) remainingSecs / totalSecs;

                visualManager.showOrUpdateActiveBar(player, formatTime(remainingSecs), getPlayerScore(uuid), progress);
            }
        }
    }

    public static boolean isTriggeredByAdminCommand() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String className = element.getClassName();
            String methodName = element.getMethodName();

            if (className.contains("org.junit") || className.contains("org.apache.maven.surefire")) {
                continue;
            }
            if (className.contains("AdminCommandSimulator")) {
                return true;
            }
            if (className.startsWith("org.bukkit.command.") || 
                className.startsWith("com.mojang.brigadier.") || 
                className.contains("CommandDispatcher") || 
                className.contains("VanillaCommandWrapper") ||
                (className.equals("org.bukkit.Bukkit") && methodName.equals("dispatchCommand"))) {
                return true;
            }
        }
        return false;
    }

    public void tickRush(Instant now) {
        if (!state.isDailyPlanned() || state.getStartTime() == null) return;

        Instant startInstant = state.getStartTime().atZone(ZoneId.systemDefault()).toInstant();
        Instant preAnnounceInstant = startInstant.minusSeconds(preAnnounceMinutes * 60L);
        Instant endInstant = startInstant.plusSeconds(state.getDurationMinutes() * 60L);

        if (now.isAfter(endInstant)) {
            if (state.isRushActive() || state.isDailyPlanned()) {
                endRush(now);
            }
            return;
        }

        if (!now.isBefore(startInstant) && now.isBefore(endInstant)) {
            if (!state.isRushActive()) {
                state.setRushActive(true);
                visualManager.hideAnnounceBar();
                sendDiscordWebhook(formatMessage("rush-discord-started",
                        "[Rush] Le Rush quotidien sur la ressource %resource% vient de démarrer pour %duration% minutes !",
                        Map.of("%resource%", plugin.getResourceDisplayName(state.getDailyResource()), "%duration%", String.valueOf(state.getDurationMinutes()))));
                try {
                    if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                        Bukkit.getPluginManager().callEvent(new DanaRushStartEvent(state.getDailyResource(), state.getDurationMinutes(), startInstant));
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }

            long totalSecs = state.getDurationMinutes() * 60L;
            long elapsedSecs = now.getEpochSecond() - startInstant.getEpochSecond();
            long remainingSecs = totalSecs - elapsedSecs;
            float progress = (float) remainingSecs / totalSecs;

            for (UUID uuid : state.getRegisteredScores().keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    visualManager.showOrUpdateActiveBar(player, formatTime(remainingSecs), getPlayerScore(uuid), progress);
                }
            }
        }
        else if (!now.isBefore(preAnnounceInstant) && now.isBefore(startInstant)) {
            long remainingSecs = startInstant.getEpochSecond() - now.getEpochSecond();
            visualManager.showAnnounceBar(formatTime(remainingSecs), plugin.getResourceDisplayName(state.getDailyResource()));

            if (!state.isDiscordAnnounced()) {
                state.setDiscordAnnounced(true);
                sendDiscordWebhook(formatMessage("rush-discord-pre-announce",
                        "[Rush] Le Rush sur la ressource %resource% démarrera dans %time% minutes ! Tapez /rush join en jeu !",
                        Map.of("%resource%", plugin.getResourceDisplayName(state.getDailyResource()), "%time%", String.valueOf(preAnnounceMinutes))));
            }
        }
    }

    public CompletableFuture<Void> endRush(Instant now) {
        logger.info("[Rush] Ending daily rush event.");
        state.setRushActive(false);
        state.setDailyPlanned(false);
        state.setRegistrationOpen(false);

        visualManager.hideAnnounceBar();
        visualManager.clearAllActiveBars();

        if (Bukkit.getServer() != null) {
            Bukkit.broadcast(getMessageComponent("admin-rush-ended",
                    "<blue>[Rush] Le Rush d'aujourd'hui est terminé ! Calcul des ELO en cours...</blue>",
                    java.util.Collections.emptyMap()));
        }

        Map<UUID, Double> activeParticipants = new HashMap<>();
        for (Map.Entry<UUID, Double> entry : state.getRegisteredScores().entrySet()) {
            if (entry.getValue() > 0.0) {
                activeParticipants.put(entry.getKey(), entry.getValue());
            }
        }

        if (activeParticipants.isEmpty()) {
            logger.info("[Rush] No participant reached a score > 0. Rush closed without ELO distribution.");
            state.getRegisteredScores().clear();
            return CompletableFuture.completedFuture(null);
        }

        List<PlayerProfile> resolvedProfiles = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (UUID uuid : activeParticipants.keySet()) {
            PlayerProfile profile = getProfileCacheMap().get(uuid);
            if (profile != null) {
                resolvedProfiles.add(profile);
            } else if (getProfileRepository() != null) {
                CompletableFuture<Void> fut = getProfileRepository().loadProfile(uuid, "OfflinePlayer")
                        .thenAccept(offlineProfileOpt -> {
                            offlineProfileOpt.ifPresent(offlineProfile -> {
                                synchronized (resolvedProfiles) {
                                    resolvedProfiles.add(offlineProfile);
                                }
                            });
                        });
                futures.add(fut);
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenCompose(v -> processEloRedistribution(resolvedProfiles, activeParticipants));
    }

    private String getRawMessage(String key, String defaultValue) {
        if (plugin != null && plugin.getMessageManager() != null) {
            return plugin.getMessageManager().getRawMessage(key, defaultValue);
        }
        return defaultValue;
    }

    private String formatMessage(String key, String defaultValue, Map<String, String> placeholders) {
        String raw = getRawMessage(key, defaultValue);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace(entry.getKey(), entry.getValue());
        }
        return raw;
    }

    private Component getMessageComponent(String key, String defaultValue, Map<String, String> placeholders) {
        String raw = formatMessage(key, defaultValue, placeholders);
        return MiniMessage.miniMessage().deserialize(raw);
    }

    private CompletableFuture<Void> processEloRedistribution(List<PlayerProfile> profiles, Map<UUID, Double> scores) {
        Map<String, RushEloCalculator.TierSettings> calculatorSettings = new HashMap<>();
        for (Map.Entry<String, RankSetting> entry : rankSettings.entrySet()) {
            RankSetting s = entry.getValue();
            calculatorSettings.put(entry.getKey(), new RushEloCalculator.TierSettings(s.eloFactor, s.gainMultiplier, s.lossMultiplier));
        }

        Map<String, List<PlayerProfile>> tiers = RushEloCalculator.groupProfilesByTier(profiles);
        List<PlayerProfile> orphans = new ArrayList<>();
        Map<UUID, Integer> eloChanges = new HashMap<>();

        for (Map.Entry<String, List<PlayerProfile>> entry : tiers.entrySet()) {
            String tier = entry.getKey();
            List<PlayerProfile> tierPlayers = entry.getValue();
            RushEloCalculator.TierSettings settings = calculatorSettings.get(tier);
            double eloFactor = settings != null ? settings.getEloFactor() : 30.0;

            if (tierPlayers.size() >= 2) {
                RushEloCalculator.calculateIntraRankEloChanges(tierPlayers, scores, eloFactor, eloChanges);
            } else if (tierPlayers.size() == 1) {
                orphans.add(tierPlayers.getFirst());
            }
        }

        Map<UUID, Double> orphanPercentageScores = new HashMap<>();
        QuotaConfig quotaConfig = null;
        QuotaScheduler quotaScheduler = null;
        QuotaService quotaService = null;
        if (plugin != null && plugin.getQuotaService() != null) {
            quotaService = plugin.getQuotaService();
            quotaConfig = quotaService.getQuotaConfig();
            quotaScheduler = quotaService.getQuotaScheduler();
        }
        String resource = state.getDailyResource();

        for (PlayerProfile p : orphans) {
            double rawScore = scores.getOrDefault(p.getUuid(), 0.0);
            double target = 1000.0;
            int periodDays = 1;
            if (quotaConfig != null && resource != null) {
                ObjectiveConfig objConfig = 
                        QuotaConfigLoader.getObjectiveConfig(quotaConfig, p.getRankLevel(), resource);
                if (objConfig != null) {
                    target = objConfig.target();
                }
            }
            if (quotaService != null && quotaScheduler != null) {
                int level = quotaService.getLevelFromRank(p.getRankLevel());
                periodDays = quotaScheduler.getPeriodDays(level);
            }
            if (periodDays <= 0) {
                periodDays = 1;
            }
            double dailyTarget = target / periodDays;
            double percentage = rawScore / (dailyTarget > 0.0 ? dailyTarget : 1000.0);
            orphanPercentageScores.put(p.getUuid(), percentage);
        }

        RushEloCalculator.calculateOrphanEloChanges(orphans, orphanPercentageScores, eloChanges, calculatorSettings);

        try {
            if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                Bukkit.getPluginManager().callEvent(new DanaRushEndEvent(state.getDailyResource(), scores, eloChanges));
            }
        } catch (Exception e) {
            // Ignore in tests
        }

        return applyAndSaveEloChanges(profiles, eloChanges, scores);
    }

    private CompletableFuture<Void> applyAndSaveEloChanges(List<PlayerProfile> profiles, Map<UUID, Integer> eloChanges, Map<UUID, Double> scores) {
        List<CompletableFuture<Void>> dbFutures = new ArrayList<>();

        try {
            broadcastRushSummary(profiles, eloChanges, scores);
        } catch (Exception e) {
            // Ignorer silencieusement
        }

        for (PlayerProfile profile : profiles) {
            int change = eloChanges.getOrDefault(profile.getUuid(), 0);
            
            // Apply ELO changes using EloService
            getEloService().addElo(profile, change, "RUSH");

            String textSign = change >= 0 ? "+" : "";
            String summaryMsg = textSign + change + " ELO";

            if (getProfileRepository() != null) {
                boolean isOnline = getProfileCacheMap().containsKey(profile.getUuid());
                if (!isOnline) {
                    profile.getQuotaProgress().put("rush_pending_summary", summaryMsg);
                }
                
                dbFutures.add(getProfileRepository().saveProfile(profile));
            }

            if (Bukkit.getServer() != null) {
                try {
                    Player onlinePlayer = Bukkit.getPlayer(profile.getUuid());
                    if (onlinePlayer != null) {
                        String key = change >= 0 ? "rush-ended-win" : "rush-ended-loss";
                        String fallback = change >= 0 
                            ? "<blue>[Rush] L'événement s'est terminé ! Vous avez gagné <green>%change%</green> !"
                            : "<blue>[Rush] L'événement s'est terminé ! Vous avez perdu <red>%change%</red> !";
                        onlinePlayer.sendMessage(getMessageComponent(key, fallback, Map.of("%change%", textSign + change + " ELO")));
                    }
                } catch (Exception e) {
                    // Safe fallback in tests
                }
            }
        }

        state.getRegisteredScores().clear();
        return CompletableFuture.allOf(dbFutures.toArray(new CompletableFuture[0]));
    }

    public CompletableFuture<Void> checkOfflineSummary(PlayerProfile profile, Consumer<String> messageSender) {
        if (profile.getQuotaProgress().containsKey("rush_pending_summary")) {
            String summary = (String) profile.getQuotaProgress().remove("rush_pending_summary");

            boolean isWin = !summary.startsWith("-");
            String key = isWin ? "rush-ended-win" : "rush-ended-loss";
            String fallback = isWin 
                ? "<blue>[Rush] L'événement s'est terminé ! Vous avez gagné <green>%change%</green> !"
                : "<blue>[Rush] L'événement s'est terminé ! Vous avez perdu <red>%change%</red> !";
            
            messageSender.accept(formatMessage(key, fallback, Map.of("%change%", summary)));

            if (getProfileRepository() != null) {
                return getProfileRepository().saveProfile(profile);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private void sendDiscordWebhook(String content) {
        DiscordWebhook.sendDiscordWebhook(discordWebhookUrl, content, logger);
    }

    public String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    public void forceStartRush(String resource, int durationMinutes) {
        LocalDateTime now = LocalDateTime.now();
        Instant nowInstant = now.atZone(ZoneId.systemDefault()).toInstant();

        state.setDailyPlanned(true);
        state.setLastPlannedDate(now.toLocalDate());
        state.setDailyResource(resource);
        state.setDurationMinutes(durationMinutes);
        state.setStartTime(now);
        state.setRushActive(true);
        scoreTracker.clear();

        visualManager.hideAnnounceBar();
        sendDiscordWebhook(formatMessage("rush-discord-started",
                "[Rush] Le Rush quotidien sur la ressource %resource% vient de démarrer pour %duration% minutes !",
                Map.of("%resource%", plugin.getResourceDisplayName(resource), "%duration%", String.valueOf(durationMinutes), "%time%", String.valueOf(durationMinutes))));
        try {
            if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                Bukkit.getPluginManager().callEvent(new DanaRushStartEvent(resource, durationMinutes, nowInstant));
            }
        } catch (Exception e) {
            // Ignore
        }
        if (Bukkit.getServer() != null) {
            Bukkit.broadcast(getMessageComponent("admin-rush-started",
                    "<blue>[Rush] Un Rush compétitif vient de commencer sur la ressource <gold>%resource%</gold> pour <yellow>%duration%</yellow> minutes ! Tapez <green>/rush join</green> pour y participer !</blue>",
                    Map.of("%resource%", plugin.getResourceDisplayName(resource), "%duration%", String.valueOf(durationMinutes), "%time%", String.valueOf(durationMinutes))));
        }
    }

    public void forceStopRush() {
        state.setRushActive(false);
        state.setDailyPlanned(false);
        scoreTracker.clear();
        visualManager.clearAllActiveBars();
        visualManager.hideAnnounceBar();
        if (Bukkit.getServer() != null) {
            Bukkit.broadcast(getMessageComponent("admin-rush-stopped",
                    "<red>[Rush] Le Rush en cours a été arrêté prématurément par un administrateur. Aucune récompense ne sera distribuée.</red>",
                    java.util.Collections.emptyMap()));
        }
    }

    public void reloadRushPlan() {
        state.setRushActive(false);
        state.setDailyPlanned(false);
        scoreTracker.clear();
        visualManager.clearAllActiveBars();
        visualManager.hideAnnounceBar();
        setupDaily(LocalDateTime.now());
    }

    public void forceScheduleRush(String resource, int durationMinutes, int delayMinutes) {
        LocalDateTime start = LocalDateTime.now().plusMinutes(delayMinutes);
        state.setDailyPlanned(true);
        state.setLastPlannedDate(start.toLocalDate());
        state.setDailyResource(resource);
        state.setDurationMinutes(durationMinutes);
        state.setStartTime(start);
        state.setRushActive(false);
        state.setDiscordAnnounced(false);
        scoreTracker.clear();

        visualManager.hideAnnounceBar();
        visualManager.clearAllActiveBars();

        sendDiscordWebhook(formatMessage("rush-discord-scheduled-admin",
                "[Rush] Un administrateur a planifié un Rush sur la ressource %resource% qui commencera dans %delay% minutes !",
                Map.of("%resource%", plugin.getResourceDisplayName(resource), "%delay%", String.valueOf(delayMinutes))));

        if (Bukkit.getServer() != null) {
            Bukkit.broadcast(getMessageComponent("admin-rush-planned",
                    "<blue>[Rush] Un administrateur a planifié un Rush sur la ressource <gold>%resource%</gold> qui commencera dans <yellow>%delay%</yellow> minutes ! Tapez <green>/rush join</green> pour y participer !</blue>",
                    Map.of("%resource%", plugin.getResourceDisplayName(resource), "%delay%", String.valueOf(delayMinutes), "%duration%", String.valueOf(durationMinutes))));
        }
    }

    private void broadcastRushSummary(List<PlayerProfile> profiles, Map<UUID, Integer> eloChanges, Map<UUID, Double> scores) {
        Map<UUID, Double> percentages = new HashMap<>();
        QuotaConfig quotaConfig = null;
        QuotaScheduler quotaScheduler = null;
        QuotaService quotaService = null;
        if (plugin != null && plugin.getQuotaService() != null) {
            quotaService = plugin.getQuotaService();
            quotaConfig = quotaService.getQuotaConfig();
            quotaScheduler = quotaService.getQuotaScheduler();
        }
        String resource = state.getDailyResource();

        Map<UUID, PlayerProfile> profileMap = new HashMap<>();
        for (PlayerProfile p : profiles) {
            profileMap.put(p.getUuid(), p);
            double rawScore = scores.getOrDefault(p.getUuid(), 0.0);
            double target = 1000.0;
            int periodDays = 1;
            if (quotaConfig != null && resource != null) {
                ObjectiveConfig objConfig = 
                        QuotaConfigLoader.getObjectiveConfig(quotaConfig, p.getRankLevel(), resource);
                if (objConfig != null) {
                    target = objConfig.target();
                }
            }
            if (quotaService != null && quotaScheduler != null) {
                int level = quotaService.getLevelFromRank(p.getRankLevel());
                periodDays = quotaScheduler.getPeriodDays(level);
            }
            if (periodDays <= 0) {
                periodDays = 1;
            }
            double dailyTarget = target / periodDays;
            percentages.put(p.getUuid(), rawScore / (dailyTarget > 0.0 ? dailyTarget : 1000.0));
        }

        List<UUID> rankedUuids = new ArrayList<>(scores.keySet());
        rankedUuids.sort((id1, id2) -> Double.compare(percentages.getOrDefault(id2, 0.0), percentages.getOrDefault(id1, 0.0)));

        Map<UUID, Integer> rankings = new HashMap<>();
        for (int i = 0; i < rankedUuids.size(); i++) {
            rankings.put(rankedUuids.get(i), i + 1);
        }

        class SummaryEntry {
            final String name;
            final int rank;
            final double points;
            final int eloChange;

            SummaryEntry(String name, int rank, double points, int eloChange) {
                this.name = name;
                this.rank = rank;
                this.points = points;
                this.eloChange = eloChange;
            }
        }

        List<SummaryEntry> entries = new ArrayList<>();
        for (PlayerProfile profile : profiles) {
            UUID uuid = profile.getUuid();
            int rank = rankings.getOrDefault(uuid, 99);
            double points = scores.getOrDefault(uuid, 0.0);
            int eloChange = eloChanges.getOrDefault(uuid, 0);
            entries.add(new SummaryEntry(profile.getPlayerName(), rank, points, eloChange));
        }

        entries.sort((e1, e2) -> Integer.compare(e2.eloChange, e1.eloChange));

        if (Bukkit.getServer() != null) {
            Component header = plugin.getMessageManager().getMessageComponent("rush-summary-header",
                    "<gold><b>[Rush] Résumé de l'événement de Rush :</b></gold>");
            Bukkit.getServer().broadcast(header);

            for (SummaryEntry entry : entries) {
                String eloSign = entry.eloChange >= 0 ? "+" : "-";
                String eloColor = entry.eloChange >= 0 ? "green" : "red";

                Component line = plugin.getMessageManager().getMessageComponent("rush-summary-format",
                        "<yellow>#%pos%</yellow> <white><hover:show_text:'<gray>Cliquez pour voir le profil de %player%</gray>'><click:run_command:'/profile %player%'>%player%</click></hover></white> - <aqua>%score% pts</aqua> (<%color%>%sign%%change% ELO</%color>)",
                        Map.of(
                            "%pos%", String.valueOf(entry.rank),
                            "%player%", entry.name,
                            "%score%", String.format("%.0f", entry.points),
                            "%color%", eloColor,
                            "%sign%", eloSign,
                            "%change%", String.valueOf(Math.abs(entry.eloChange))
                        ));
                Bukkit.getServer().broadcast(line);
            }
        }
    }

    public void printRushInfo(CommandSender sender) {
        if (plugin == null || plugin.getMessageManager() == null) {
            return;
        }
        if (!state.isDailyPlanned()) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("rush-info-no-active", "<red>[Rush] Aucun Rush planifié ou en cours.</red>"));
            return;
        }
        sender.sendMessage(plugin.getMessageManager().getMessageComponent("rush-info-header", "<blue>--- Informations sur le Rush ---</blue>"));
        sender.sendMessage(plugin.getMessageManager().getMessageComponent("rush-info-resource", "<white>Ressource : <green>%resource%</green></white>", Map.of("%resource%", plugin.getResourceDisplayName(state.getDailyResource()))));
        sender.sendMessage(plugin.getMessageManager().getMessageComponent("rush-info-duration", "<white>Durée : <yellow>%duration% minutes</yellow></white>", Map.of("%duration%", String.valueOf(state.getDurationMinutes()))));
        sender.sendMessage(plugin.getMessageManager().getMessageComponent("rush-info-status", "<white>Statut : <yellow>%status%</yellow></white>", Map.of("%status%", state.isRushActive() ? "En cours" : "Attente de démarrage")));
        sender.sendMessage(plugin.getMessageManager().getMessageComponent("rush-info-registered-count", "<white>Nombre d'inscrits : <yellow>%count%</yellow></white>", Map.of("%count%", String.valueOf(getRegisteredPlayersCount()))));

        if (getRegisteredPlayersCount() > 0) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("rush-info-scores-header", "<white>Scores des inscrits :</white>"));
            Map<UUID, Double> scores = state.getRegisteredScores();
            scores.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .forEach(e -> {
                    String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
                    String nameStr = name != null ? name : e.getKey().toString();
                    sender.sendMessage(plugin.getMessageManager().getMessageComponent("rush-info-score-line",
                            " - <white>%player%</white> : <green>%score% pts</green>",
                            Map.of("%player%", nameStr, "%score%", String.format("%.0f", e.getValue()))));
                });
        }
    }
}
