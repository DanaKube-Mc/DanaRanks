package app.danakube.danaranks;

import app.danakube.danaranks.database.DatabaseManager;
import app.danakube.danaranks.hook.LuckPermsHook;
import app.danakube.danaranks.lang.MessageManager;
import app.danakube.danaranks.listener.PlayerConnectionListener;
import app.danakube.danaranks.profile.PlayerProfile;
import app.danakube.danaranks.quota.QuotaManager;
import app.danakube.danaranks.tracker.JobXpTracker;
import app.danakube.danaranks.tracker.LumensGainedTracker;
import app.danakube.danaranks.tracker.LumensSpentTracker;
import app.danakube.danaranks.tracker.ToolXpTracker;
import app.danakube.danaranks.tracker.TrackerRegistry;
import app.danakube.danaranks.tracker.VanillaXpTracker;

import app.danakube.danaranks.quota.RushManager;
import app.danakube.danaranks.command.RushCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DanaRanks extends JavaPlugin {
    private static DanaRanks instance;
    private DatabaseManager databaseManager;
    private LuckPermsHook luckPermsHook;
    private MessageManager messageManager;
    private QuotaManager quotaManager;
    private TrackerRegistry trackerRegistry;
    private RushManager rushManager;
    private final Map<UUID, PlayerProfile> profileCache = new ConcurrentHashMap<>();

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

        // Initialize QuotaManager and TrackerRegistry
        quotaManager = new QuotaManager();
        quotaManager.loadConfig(config, getLogger());
        QuotaManager.setInstance(quotaManager);

        trackerRegistry = new TrackerRegistry(this);
        trackerRegistry.registerTracker(new LumensGainedTracker());
        trackerRegistry.registerTracker(new LumensSpentTracker());
        trackerRegistry.registerTracker(new JobXpTracker());
        trackerRegistry.registerTracker(new ToolXpTracker());
        trackerRegistry.registerTracker(new VanillaXpTracker("gained"));
        trackerRegistry.registerTracker(new VanillaXpTracker("spent"));

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

        String trackName = config.getString("luckperms.track-name", "danaranks");
        if (getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            luckPermsHook = new LuckPermsHook(trackName);
            getLogger().info(messageManager.getMessage("luckperms-registered", "LuckPerms hook successfully registered."));
        } else {
            getLogger().warning(messageManager.getMessage("luckperms-not-found", "LuckPerms not found! Promotions will be disabled."));
        }

        PlayerProfile.setPromotionCallback((uuid, ranks) -> {
            if (luckPermsHook != null) {
                luckPermsHook.promote(uuid, ranks);
            }
        });

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        // Initialize RushManager
        rushManager = new RushManager(this);
        rushManager.loadConfig(config);
        rushManager.startScheduler();

        // Register Command
        getCommand("rush").setExecutor(new RushCommand(this, rushManager));

        getLogger().info(messageManager.getMessage("plugin-enabled", "DanaRanks has been enabled!"));
    }

    @Override
    public void onDisable() {
        PlayerProfile.setPromotionCallback(null);
        if (databaseManager != null) {
            databaseManager.close();
        }
        profileCache.clear();
        QuotaManager.setInstance(null);
        quotaManager = null;
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

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    public void setLuckPermsHook(LuckPermsHook luckPermsHook) {
        this.luckPermsHook = luckPermsHook;
    }

    public Map<UUID, PlayerProfile> getProfileCache() {
        return profileCache;
    }

    public QuotaManager getQuotaManager() {
        return quotaManager;
    }

    public TrackerRegistry getTrackerRegistry() {
        return trackerRegistry;
    }

    public RushManager getRushManager() {
        return rushManager;
    }
}
