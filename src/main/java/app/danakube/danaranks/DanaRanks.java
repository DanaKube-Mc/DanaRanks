package app.danakube.danaranks;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DanaRanks extends JavaPlugin {
    private static DanaRanks instance;
    private DatabaseManager databaseManager;
    private LuckPermsHook luckPermsHook;
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
            getLogger().info("LuckPerms hook successfully registered.");
        } else {
            getLogger().warning("LuckPerms not found! Promotions will be disabled.");
        }

        PlayerProfile.setPromotionCallback((uuid, ranks) -> {
            if (luckPermsHook != null) {
                luckPermsHook.promote(uuid, ranks);
            }
        });

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);

        getLogger().info("DanaRanks has been enabled!");
    }

    @Override
    public void onDisable() {
        PlayerProfile.setPromotionCallback(null);
        if (databaseManager != null) {
            databaseManager.close();
        }
        profileCache.clear();
        instance = null;
        getLogger().info("DanaRanks has been disabled!");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
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
}
