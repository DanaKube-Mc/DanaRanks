package app.danakube.danaranks.quota;

import app.danakube.danaranks.DanaRanks;
import app.danakube.danaranks.database.DatabaseManager;
import app.danakube.danaranks.profile.PlayerProfile;
import app.danakube.danaranks.tracker.RushVisualManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class RushManager {

    private final DanaRanks plugin;
    private final RushVisualManager visualManager;
    private final Logger logger;
    private Map<UUID, PlayerProfile> profileCacheOverride = null;
    private DatabaseManager databaseManagerOverride = null;

    public void setProfileCacheOverride(Map<UUID, PlayerProfile> cache) {
        this.profileCacheOverride = cache;
    }

    public void setDatabaseManagerOverride(DatabaseManager db) {
        this.databaseManagerOverride = db;
    }

    private Map<UUID, PlayerProfile> getProfileCache() {
        if (profileCacheOverride != null) return profileCacheOverride;
        return plugin != null ? plugin.getProfileCache() : new HashMap<>();
    }

    private DatabaseManager getDatabaseManager() {
        if (databaseManagerOverride != null) return databaseManagerOverride;
        return plugin != null ? plugin.getDatabaseManager() : null;
    }

    // Configuration keys
    private int dailySetupHour = 8;
    private int minStartHour = 12;
    private int maxStartHour = 22;
    private int minDurationMinutes = 20;
    private int maxDurationMinutes = 60;
    private int preAnnounceMinutes = 30;
    private List<String> eligibleResources = new ArrayList<>();
    private String discordWebhookUrl = "";

    // ELO Config mappings
    private final Map<String, RankSetting> rankSettings = new HashMap<>();

    // State parameters
    private boolean dailyPlanned = false;
    private LocalDate lastPlannedDate = null;
    private String dailyResource = null;
    private LocalDateTime startTime = null;
    private int durationMinutes = 0;
    private boolean registrationOpen = false;
    private boolean rushActive = false;
    private boolean discordAnnounced = false;

    // Player states: scores are kept in memory to preserve them across disconnects
    private final Map<UUID, Double> registeredScores = new ConcurrentHashMap<>();

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
        this.visualManager = new RushVisualManager(plugin);
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

        // Load rank parameters
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

            // Planning daily draw at 08h00
            if (now.getHour() == dailySetupHour && now.getMinute() == 0 && now.getSecond() == 0) {
                if (lastPlannedDate == null || !lastPlannedDate.equals(now.toLocalDate())) {
                    setupDaily(now);
                }
            }

            tickRush(nowInstant);
        }, 20L, 20L); // Check every second
    }

    public void setupDaily(LocalDateTime now) {
        if (eligibleResources.isEmpty()) {
            logger.warning("No eligible resources configured for Daily Rush!");
            return;
        }

        Random rand = new Random();
        this.dailyResource = eligibleResources.get(rand.nextInt(eligibleResources.size()));

        // Start hour between minStartHour and maxStartHour (inclusive/exclusive)
        int hour = minStartHour + rand.nextInt(Math.max(1, maxStartHour - minStartHour + 1));
        int minute = rand.nextInt(60);
        this.startTime = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);

        this.durationMinutes = minDurationMinutes + rand.nextInt(Math.max(1, maxDurationMinutes - minDurationMinutes + 1));

        this.registeredScores.clear();
        this.dailyPlanned = true;
        this.lastPlannedDate = now.toLocalDate();
        this.registrationOpen = true;
        this.rushActive = false;
        this.discordAnnounced = false;

        logger.info("[Rush] Daily Rush setup completed. Resource: " + dailyResource + 
                ", Start: " + startTime + ", Duration: " + durationMinutes + "m.");

        // Announce immediately in-game and send discord hook
        announceRegistration();
    }

    private void announceRegistration() {
        if (plugin == null) return;
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(
                "<blue>[Rush] Le Rush quotidien sur la ressource <gold>" + dailyResource + "</gold> est planifié ! Tapez <yellow>/rush join</yellow> pour vous inscrire !"
        ));
        sendDiscordWebhook("[Rush] Le Rush sur la ressource " + dailyResource + " est planifié pour aujourd'hui ! Les inscriptions sont ouvertes via /rush join !");
    }

    public boolean registerPlayer(UUID uuid, Instant now) {
        if (!dailyPlanned || startTime == null) {
            return false;
        }

        Instant startInstant = startTime.atZone(ZoneId.systemDefault()).toInstant();
        Instant endInstant = startInstant.plusSeconds(durationMinutes * 60L);

        // Reject if Rush is already finished
        if (now.isAfter(endInstant)) {
            return false;
        }

        if (registeredScores.containsKey(uuid)) {
            return false;
        }

        registeredScores.put(uuid, 0.0);
        logger.info("[Rush] Player registered: " + uuid + " at " + now);
        return true;
    }

    public double getPlayerScore(UUID uuid) {
        return registeredScores.getOrDefault(uuid, 0.0);
    }

    public int getRegisteredPlayersCount() {
        return registeredScores.size();
    }

    public boolean isDailyPlanned() {
        return dailyPlanned;
    }

    public String getDailyResource() {
        return dailyResource;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public boolean isRegistrationOpen(Instant now) {
        if (!dailyPlanned || startTime == null) return false;
        Instant startInstant = startTime.atZone(ZoneId.systemDefault()).toInstant();
        Instant endInstant = startInstant.plusSeconds(durationMinutes * 60L);
        return registrationOpen && !now.isAfter(endInstant);
    }

    public boolean isRushActive(Instant now) {
        if (!dailyPlanned || startTime == null) return false;
        Instant startInstant = startTime.atZone(ZoneId.systemDefault()).toInstant();
        Instant endInstant = startInstant.plusSeconds(durationMinutes * 60L);
        return !now.isBefore(startInstant) && !now.isAfter(endInstant);
    }

    public void handleResourceGain(UUID uuid, String resource, double amount, Instant now) {
        if (!dailyPlanned || dailyResource == null) return;
        // Normalize resource names (replace - with _ to match trackers)
        String normalizedResource = resource.replace("-", "_");
        String normalizedDaily = dailyResource.replace("-", "_");

        if (!normalizedResource.equalsIgnoreCase(normalizedDaily)) return;
        if (!isRushActive(now)) return;
        if (!registeredScores.containsKey(uuid)) return;

        // Anti-cheat check
        if (isTriggeredByAdminCommand()) {
            logger.warning("[Rush] Blocked administrative resource gain for " + uuid);
            return;
        }

        registeredScores.computeIfPresent(uuid, (key, current) -> current + amount);

        // Live visual update if player is online
        if (plugin != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Instant startInstant = startTime.atZone(ZoneId.systemDefault()).toInstant();
                long totalSecs = durationMinutes * 60L;
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
        if (!dailyPlanned || startTime == null) return;

        Instant startInstant = startTime.atZone(ZoneId.systemDefault()).toInstant();
        Instant preAnnounceInstant = startInstant.minusSeconds(preAnnounceMinutes * 60L);
        Instant endInstant = startInstant.plusSeconds(durationMinutes * 60L);

        if (now.isAfter(endInstant)) {
            if (rushActive || dailyPlanned) {
                endRush(now);
            }
            return;
        }

        // Active Rush
        if (!now.isBefore(startInstant) && now.isBefore(endInstant)) {
            if (!rushActive) {
                rushActive = true;
                visualManager.hideAnnounceBar();
                sendDiscordWebhook("[Rush] Le Rush quotidien sur la ressource " + dailyResource + " vient de démarrer pour " + durationMinutes + " minutes !");
            }

            // Update active boss bars for online players
            long totalSecs = durationMinutes * 60L;
            long elapsedSecs = now.getEpochSecond() - startInstant.getEpochSecond();
            long remainingSecs = totalSecs - elapsedSecs;
            float progress = (float) remainingSecs / totalSecs;

            for (UUID uuid : registeredScores.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    visualManager.showOrUpdateActiveBar(player, formatTime(remainingSecs), getPlayerScore(uuid), progress);
                }
            }
        }
        // Pre-Announce Window
        else if (!now.isBefore(preAnnounceInstant) && now.isBefore(startInstant)) {
            long remainingSecs = startInstant.getEpochSecond() - now.getEpochSecond();
            visualManager.showAnnounceBar(formatTime(remainingSecs), dailyResource);

            if (!discordAnnounced) {
                discordAnnounced = true;
                sendDiscordWebhook("[Rush] Le Rush sur la ressource " + dailyResource + " démarrera dans " + preAnnounceMinutes + " minutes ! Tapez /rush join en jeu !");
            }
        }
    }

    public CompletableFuture<Void> endRush(Instant now) {
        logger.info("[Rush] Ending daily rush event.");
        this.rushActive = false;
        this.dailyPlanned = false;
        this.registrationOpen = false;

        visualManager.hideAnnounceBar();
        visualManager.clearAllActiveBars();

        // 1. Filter players with score > 0
        Map<UUID, Double> activeParticipants = new HashMap<>();
        for (Map.Entry<UUID, Double> entry : registeredScores.entrySet()) {
            if (entry.getValue() > 0.0) {
                activeParticipants.put(entry.getKey(), entry.getValue());
            }
        }

        if (activeParticipants.isEmpty()) {
            logger.info("[Rush] No participant reached a score > 0. Rush closed without ELO distribution.");
            registeredScores.clear();
            return CompletableFuture.completedFuture(null);
        }

        // We need to resolve all profiles (both online from cache and offline from DB)
        // Group players by rank tier
        Map<String, List<PlayerProfile>> tiers = new HashMap<>();
        tiers.put("fer", new ArrayList<>());
        tiers.put("bronze", new ArrayList<>());
        tiers.put("argent", new ArrayList<>());
        tiers.put("or", new ArrayList<>());
        tiers.put("platine", new ArrayList<>());

        List<PlayerProfile> resolvedProfiles = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (UUID uuid : activeParticipants.keySet()) {
            PlayerProfile profile = null;
            if (getProfileCache().containsKey(uuid)) {
                profile = getProfileCache().get(uuid);
            }
            if (profile != null) {
                resolvedProfiles.add(profile);
            } else if (getDatabaseManager() != null) {
                // Load offline profile
                final UUID finalUuid = uuid;
                CompletableFuture<Void> fut = getDatabaseManager().loadProfile(uuid, "OfflinePlayer")
                        .thenAccept(offlineProfile -> {
                            synchronized (resolvedProfiles) {
                                resolvedProfiles.add(offlineProfile);
                            }
                        });
                futures.add(fut);
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenCompose(v -> processEloRedistribution(resolvedProfiles, activeParticipants));
    }

    private CompletableFuture<Void> processEloRedistribution(List<PlayerProfile> profiles, Map<UUID, Double> scores) {
        Map<String, List<PlayerProfile>> tiers = new HashMap<>();
        tiers.put("fer", new ArrayList<>());
        tiers.put("bronze", new ArrayList<>());
        tiers.put("argent", new ArrayList<>());
        tiers.put("or", new ArrayList<>());
        tiers.put("platine", new ArrayList<>());

        // Phase 1: Group by Level
        for (PlayerProfile profile : profiles) {
            String tierName = getTierName(profile.getRankLevel());
            tiers.get(tierName).add(profile);
        }

        List<PlayerProfile> orphans = new ArrayList<>();
        Map<UUID, Integer> eloChanges = new HashMap<>();
        List<CompletableFuture<Void>> dbFutures = new ArrayList<>();

        // Phase 2: Intra-Rank Evaluation
        for (Map.Entry<String, List<PlayerProfile>> entry : tiers.entrySet()) {
            String tier = entry.getKey();
            List<PlayerProfile> tierPlayers = entry.getValue();
            RankSetting settings = rankSettings.get(tier);
            double eloFactor = settings != null ? settings.eloFactor : 30.0;

            if (tierPlayers.size() >= 2) {
                // Calculate max score
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
            } else if (tierPlayers.size() == 1) {
                orphans.add(tierPlayers.getFirst());
            }
        }

        // Phase 3: Cross-Rank Evaluation (Orphans)
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
                    RankSetting settings = rankSettings.get(tier);
                    double mult = 1.0;
                    if (settings != null) {
                        mult = (changeRawInt >= 0) ? settings.gainMultiplier : settings.lossMultiplier;
                    }

                    double finalChangeRaw = changeRawInt * mult;
                    int finalChange = (finalChangeRaw >= 0) ? (int) Math.round(finalChangeRaw) : -((int) Math.round(Math.abs(finalChangeRaw)));

                    eloChanges.put(p.getUuid(), finalChange);
                }
            }
        } else if (orphans.size() == 1) {
            eloChanges.put(orphans.getFirst().getUuid(), 0);
        }

        // Phase 4: Apply and Persist
        for (PlayerProfile profile : profiles) {
            int change = eloChanges.getOrDefault(profile.getUuid(), 0);
            profile.addElo(change);

            String textSign = change >= 0 ? "+" : "";
            String summaryMsg = textSign + change + " ELO";

            // Save history & profile in DB
            if (getDatabaseManager() != null) {
                DatabaseManager db = getDatabaseManager();
                dbFutures.add(db.logHistory(profile.getUuid(), "RUSH", change, profile.getElo(), "Rush Event - Resource: " + dailyResource));
                
                // If player is offline (not in cache), flag profile for offline notification
                boolean isOnline = getProfileCache().containsKey(profile.getUuid());
                if (!isOnline) {
                    profile.getQuotaProgress().put("rush_pending_summary", summaryMsg);
                }
                
                dbFutures.add(db.saveProfile(profile));
            }

            // Online notification immediately
            if (Bukkit.getServer() != null) {
                try {
                    Player onlinePlayer = Bukkit.getPlayer(profile.getUuid());
                    if (onlinePlayer != null) {
                        String colorTag = change >= 0 ? "<green>" : "<red>";
                        onlinePlayer.sendMessage(MiniMessage.miniMessage().deserialize(
                                "<blue>[Rush] L'événement s'est terminé ! Vous avez " + (change >= 0 ? "gagné" : "perdu") + " " + colorTag + textSign + change + " ELO</color> !"
                        ));
                    }
                } catch (Exception e) {
                    // Safe fallback in tests
                }
            }
        }

        registeredScores.clear();
        return CompletableFuture.allOf(dbFutures.toArray(new CompletableFuture[0]));
    }

    private String getTierName(int rankLevel) {
        if (rankLevel <= 10) return "fer";
        if (rankLevel <= 20) return "bronze";
        if (rankLevel <= 30) return "argent";
        if (rankLevel <= 40) return "or";
        return "platine";
    }

    public CompletableFuture<Void> checkOfflineSummary(PlayerProfile profile, Consumer<String> messageSender) {
        if (profile.getQuotaProgress().containsKey("rush_pending_summary")) {
            String summary = (String) profile.getQuotaProgress().remove("rush_pending_summary");
            
            // Send recap message
            boolean isWin = !summary.startsWith("-");
            String colorTag = isWin ? "<green>" : "<red>";
            String actionWord = isWin ? "gagné" : "perdu";
            
            messageSender.accept("<blue>[Rush] L'événement s'est terminé ! Vous avez " + actionWord + " " + colorTag + summary + "</color> !");

            // Save updated profile
            if (getDatabaseManager() != null) {
                return getDatabaseManager().saveProfile(profile);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    private void sendDiscordWebhook(String content) {
        if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                URL url = URI.create(discordWebhookUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = "{\"content\": \"" + content + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    // Success
                } else {
                    logger.warning("[Rush Webhook] Request failed: Status " + code);
                }
            } catch (Exception e) {
                logger.warning("[Rush Webhook] Error sending discord webhook: " + e.getMessage());
            }
        });
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }
}
