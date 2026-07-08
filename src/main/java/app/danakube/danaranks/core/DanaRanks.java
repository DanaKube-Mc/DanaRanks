package app.danakube.danaranks.core;

import app.danakube.danaranks.core.profile.EloService;
import app.danakube.danaranks.core.profile.ProfileCache;
import app.danakube.danaranks.database.DatabaseManager;
import app.danakube.danaranks.database.HistoryRepository;
import app.danakube.danaranks.database.ProfileRepository;
import app.danakube.danaranks.features.quota.QuotaListener;
import app.danakube.danaranks.features.quota.QuotaService;
import app.danakube.danaranks.features.quota.QuotaProgressTracker;
import app.danakube.danaranks.features.rush.RushListener;
import app.danakube.danaranks.features.rush.RushManager;
import app.danakube.danaranks.features.rush.RushCommand;
import app.danakube.danaranks.hooks.LuckPermsHookImpl;
import app.danakube.danaranks.hooks.PermissionHook;
import app.danakube.danaranks.ui.MessageManager;
import app.danakube.danaranks.tracker.JobXpTracker;
import app.danakube.danaranks.tracker.LumensGainedTracker;
import app.danakube.danaranks.tracker.LumensSpentTracker;
import app.danakube.danaranks.tracker.ToolXpTracker;
import app.danakube.danaranks.tracker.TrackerRegistry;
import app.danakube.danaranks.tracker.VanillaXpTracker;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import app.danakube.danaranks.ui.shared.PaperCommandWrapper;
import app.danakube.danaranks.ui.shared.MenuFactory;
import app.danakube.danaranks.features.leaderboard.LeaderboardManager;
import app.danakube.danaranks.admin.AdminCommandExecutor;
import app.danakube.danaranks.admin.AdminTabCompleter;
import app.danakube.danaranks.core.profile.ProfileCommand;
import app.danakube.danaranks.features.quota.QuotaCommand;
import app.danakube.danaranks.features.leaderboard.LeaderboardCommand;
import java.util.List;

public final class DanaRanks extends JavaPlugin {
    private static DanaRanks instance;
    
    private DatabaseManager databaseManager;
    private ProfileRepository profileRepository;
    private HistoryRepository historyRepository;
    private ProfileCache profileCache;
    private PermissionHook permissionHook;
    private EloService eloService;
    private QuotaService quotaService;
    private RushManager rushManager;
    private TrackerRegistry trackerRegistry;
    private MessageManager messageManager;
    private FileConfiguration guiConfig;
    private MenuFactory menuFactory;
    private LeaderboardManager leaderboardManager;

    public static DanaRanks getInstance() {
        return instance;
    }

    public static void setInstance(DanaRanks plugin) {
        instance = plugin;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        FileConfiguration config = getConfig();

        messageManager = new MessageManager(this);
        
        saveResource("gui.yml", false);
        File guiFile = new File(getDataFolder(), "gui.yml");
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);

        menuFactory = new MenuFactory(this);

        String dbType = config.getString("database.type", "SQLITE");
        if (dbType.equalsIgnoreCase("MYSQL")) {
            String host = config.getString("database.mysql.host", "localhost");
            int port = config.getInt("database.mysql.port", 3306);
            String database = config.getString("database.mysql.database", "danaranks");
            String username = config.getString("database.mysql.username", "root");
            String password = config.getString("database.mysql.password", "");
            String prefix = config.getString("database.mysql.prefix", "danaranks_");
            int maxPoolSize = config.getInt("database.mysql.pool.maximum-pool-size", 10);
            long maxLifetime = config.getLong("database.mysql.pool.max-lifetime", 1800000);
            databaseManager = new DatabaseManager("MYSQL", host, port, database, username, password, prefix, maxPoolSize, maxLifetime, getDataFolder());
        } else {
            databaseManager = new DatabaseManager("SQLITE", null, 0, null, null, null, null, 0, 0, getDataFolder());
        }

        profileRepository = new ProfileRepository(databaseManager);
        historyRepository = new HistoryRepository(databaseManager);

        leaderboardManager = new LeaderboardManager(profileRepository);
        leaderboardManager.updateLeaderboard();
        getServer().getPluginManager().registerEvents(leaderboardManager, this);

        int updateInterval = config.getInt("leaderboard.update-interval", 5);
        long ticks = Math.max(1, updateInterval) * 1200L;
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            leaderboardManager.updateLeaderboard();
        }, ticks, ticks);

        String trackName = config.getString("luckperms.track-name", "danaranks");
        if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            permissionHook = new LuckPermsHookImpl(trackName);
            getLogger().info(messageManager.getMessage("luckperms-registered", "LuckPerms hook successfully registered."));
        } else {
            getLogger().warning(messageManager.getMessage("luckperms-not-found", "LuckPerms not found! Promotions will be disabled."));
        }

        profileCache = new ProfileCache();
        eloService = new EloService(permissionHook, historyRepository);

        QuotaProgressTracker progressTracker = new QuotaProgressTracker(eloService);
        quotaService = new QuotaService(eloService, progressTracker);
        quotaService.loadConfig(config, getLogger());

        trackerRegistry = new TrackerRegistry(this);
        trackerRegistry.registerTracker(new LumensGainedTracker(this));
        trackerRegistry.registerTracker(new LumensSpentTracker(this));
        trackerRegistry.registerTracker(new JobXpTracker(this));
        trackerRegistry.registerTracker(new ToolXpTracker(this));
        trackerRegistry.registerTracker(new VanillaXpTracker(this, "gained"));
        trackerRegistry.registerTracker(new VanillaXpTracker(this, "spent"));

        getServer().getPluginManager().registerEvents(new QuotaListener(this), this);

        rushManager = new RushManager(this);
        rushManager.loadConfig(config);
        rushManager.startScheduler();

        getServer().getPluginManager().registerEvents(new RushListener(this, rushManager), this);

        getServer().getCommandMap().register("danaranks", new RushCommand(this, rushManager));

        // Enregistrement de la commande d'administration principale (/danaranks admin)
        getServer().getCommandMap().register("danaranks", new PaperCommandWrapper(
                "danaranks",
                "Commande d'administration principale de DanaRanks",
                "/danaranks admin",
                List.of("ranks", "dr"),
                new AdminCommandExecutor(this),
                new AdminTabCompleter()
        ));

        // Enregistrement de la commande joueur /profile
        ProfileCommand profileCmd = new ProfileCommand(this);
        getServer().getCommandMap().register("danaranks", new PaperCommandWrapper(
                "profile",
                "Affiche votre profil de rangs",
                "/profile",
                List.of(),
                profileCmd,
                profileCmd
        ));

        // Enregistrement de la commande joueur /quota
        getServer().getCommandMap().register("danaranks", new PaperCommandWrapper(
                "quota",
                "Affiche vos objectifs de quota",
                "/quota",
                List.of(),
                new QuotaCommand(this),
                null
        ));

        // Enregistrement de la commande joueur /leaderboard
        getServer().getCommandMap().register("danaranks", new PaperCommandWrapper(
                "leaderboard",
                "Affiche le classement global",
                "/leaderboard",
                List.of("ranksmap", "topranks", "rankstop"),
                new LeaderboardCommand(this),
                null
        ));

        getLogger().info(messageManager.getMessage("plugin-enabled", "DanaRanks has been enabled!"));
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (profileCache != null) {
            profileCache.clear();
        }
        quotaService = null;
        rushManager = null;
        trackerRegistry = null;
        instance = null;
        if (messageManager != null) {
            getLogger().info(messageManager.getMessage("plugin-disabled", "DanaRanks has been disabled!"));
        } else {
            getLogger().info("DanaRanks has been disabled!");
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ProfileRepository getProfileRepository() {
        return profileRepository;
    }

    public HistoryRepository getHistoryRepository() {
        return historyRepository;
    }

    public ProfileCache getProfileCache() {
        return profileCache;
    }

    public PermissionHook getPermissionHook() {
        return permissionHook;
    }

    public EloService getEloService() {
        return eloService;
    }

    public QuotaService getQuotaService() {
        return quotaService;
    }

    public RushManager getRushManager() {
        return rushManager;
    }

    public TrackerRegistry getTrackerRegistry() {
        return trackerRegistry;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public FileConfiguration getGuiConfig() {
        return guiConfig;
    }

    public MenuFactory getMenuFactory() {
        return menuFactory;
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public void reloadGuiConfig() {
        File guiFile = new File(getDataFolder(), "gui.yml");
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
    }

    public String getRankDisplayName(int level) {
        FileConfiguration config = getConfig();
        String customName = config.getString("rank-names." + level);
        if (customName != null) {
            return customName;
        }
        return "Rang " + level;
    }

    public Integer getRankCustomModelData(int level) {
        FileConfiguration config = getConfig();
        if (config.contains("rank-custom-model-data." + level)) {
            return config.getInt("rank-custom-model-data." + level);
        }
        return null;
    }

    public String getResourceDisplayName(String resourceId) {
        String translated = getMessageManager().getMessage("resources." + resourceId, null);
        if (translated != null && !translated.isEmpty()) {
            return translated;
        }
        return resourceId;
    }
}
