package app.danakube.danaranks.ui;

import app.danakube.danaranks.core.DanaRanks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class MessageManager {
    @FunctionalInterface
    public interface ResourceSaver {
        void saveResource(String resourcePath, boolean replace);
    }

    private final File dataFolder;
    private final Logger logger;
    private final ResourceSaver resourceSaver;
    private final Map<String, String> messages = new HashMap<>();

    public MessageManager(DanaRanks plugin) {
        this(plugin.getDataFolder(), plugin.getLogger(), plugin::saveResource);
    }

    public MessageManager(File dataFolder, Logger logger, ResourceSaver resourceSaver) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.resourceSaver = resourceSaver;
        loadMessages();
    }

    public void loadMessages() {
        messages.clear();
        File langDir = new File(dataFolder, "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        File langFile = new File(langDir, "fr.yml");
        if (!langFile.exists()) {
            try {
                resourceSaver.saveResource("lang/fr.yml", false);
            } catch (IllegalArgumentException | NullPointerException e) {
                createDefaultLangFile(langFile);
            }
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(langFile);
        if (config.isConfigurationSection("messages")) {
            for (String key : config.getConfigurationSection("messages").getKeys(false)) {
                String path = "messages." + key;
                if (config.isList(path)) {
                    messages.put(key, String.join("\n", config.getStringList(path)));
                } else {
                    String value = config.getString(path);
                    if (value != null) {
                        messages.put(key, value);
                    }
                }
            }
        }
        if (config.isConfigurationSection("resources")) {
            for (String key : config.getConfigurationSection("resources").getKeys(false)) {
                String path = "resources." + key;
                if (config.isList(path)) {
                    messages.put("resources." + key, String.join("\n", config.getStringList(path)));
                } else {
                    String value = config.getString(path);
                    if (value != null) {
                        messages.put("resources." + key, value);
                    }
                }
            }
        }
    }

    private void createDefaultLangFile(File file) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.prefix", "<b><gradient:#F82F04:#FFEE2E>DanaKube</gradient> <#c0c0c0>|</b> <#d4d9d8>");
        config.set("messages.kick-database-error", "%prefixe%<red>Impossible de charger vos données de rang. Veuillez vous reconnecter.");
        config.set("messages.no-permission", "%prefixe%<red>Vous n'avez pas la permission d'exécuter cette commande.");
        config.set("messages.profile-loaded", "%prefixe%<green>Votre profil de rang a été correctement chargé !");
        config.set("messages.plugin-enabled", "[DanaRanks] DanaRanks has been enabled!");
        config.set("messages.plugin-disabled", "[DanaRanks] DanaRanks has been disabled!");
        config.set("messages.luckperms-registered", "LuckPerms hook successfully registered.");
        config.set("messages.luckperms-not-found", "LuckPerms not found! Promotions will be disabled.");
        try {
            config.save(file);
        } catch (IOException e) {
            if (logger != null) {
                logger.severe("Could not save default lang/fr.yml: " + e.getMessage());
            }
        }
    }

    private String resolvePrefix(String raw) {
        if (raw == null) return null;
        String prefix = messages.get("prefix");
        if (prefix == null) {
            prefix = "";
        }
        return raw.replace("%prefix%", prefix).replace("%prefixe%", prefix);
    }

    private String applyPlaceholderAPI(OfflinePlayer player, String text) {
        if (player != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                return PlaceholderAPIHook.setPlaceholders(player, text);
            } catch (Throwable t) {
                // Ignore
            }
        }
        return text;
    }

    public String getRawMessage(String key, String defaultValue) {
        return getRawMessageForPlayer(key, defaultValue, (OfflinePlayer) null);
    }

    public String getRawMessageForPlayer(String key, String defaultValue, OfflinePlayer player) {
        String raw = messages.getOrDefault(key, defaultValue);
        raw = resolvePrefix(raw);
        return applyPlaceholderAPI(player, raw);
    }

    public String getRawMessageForPlayer(String key, String defaultValue, Player player) {
        return getRawMessageForPlayer(key, defaultValue, (OfflinePlayer) player);
    }

    public String getMessage(String key, String defaultValue) {
        return getMessageForPlayer(key, defaultValue, (OfflinePlayer) null);
    }

    public String getMessageForPlayer(String key, String defaultValue, OfflinePlayer player) {
        String raw = messages.get(key);
        if (raw == null) {
            raw = defaultValue;
        }
        if (raw == null) {
            return null;
        }
        raw = resolvePrefix(raw);
        raw = applyPlaceholderAPI(player, raw);
        Component component = MiniMessage.miniMessage().deserialize(raw);
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    public String getMessageForPlayer(String key, String defaultValue, Player player) {
        return getMessageForPlayer(key, defaultValue, (OfflinePlayer) player);
    }

    public String getMessage(String key) {
        return getMessageForPlayer(key, "", (OfflinePlayer) null);
    }

    public String getMessageForPlayer(String key, Player player) {
        return getMessageForPlayer(key, "", (OfflinePlayer) player);
    }

    public Component getMessageComponent(String key, String defaultValue) {
        return getMessageComponentForPlayer(key, defaultValue, (OfflinePlayer) null);
    }

    public Component getMessageComponentForPlayer(String key, String defaultValue, OfflinePlayer player) {
        String raw = messages.get(key);
        if (raw == null) {
            raw = defaultValue;
        }
        if (raw == null) {
            return Component.empty();
        }
        raw = resolvePrefix(raw);
        raw = applyPlaceholderAPI(player, raw);
        return MiniMessage.miniMessage().deserialize(raw);
    }

    public Component getMessageComponentForPlayer(String key, String defaultValue, Player player) {
        return getMessageComponentForPlayer(key, defaultValue, (OfflinePlayer) player);
    }

    public Component getMessageComponent(String key, String defaultValue, Map<String, String> placeholders) {
        return getMessageComponentForPlayer(key, defaultValue, placeholders, (OfflinePlayer) null);
    }

    public Component getMessageComponentForPlayer(String key, String defaultValue, Map<String, String> placeholders, OfflinePlayer player) {
        String raw = messages.get(key);
        if (raw == null) {
            raw = defaultValue;
        }
        if (raw == null) {
            return Component.empty();
        }
        raw = resolvePrefix(raw);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace(entry.getKey(), entry.getValue());
            }
        }
        raw = applyPlaceholderAPI(player, raw);
        return MiniMessage.miniMessage().deserialize(raw);
    }

    public Component getMessageComponentForPlayer(String key, String defaultValue, Map<String, String> placeholders, Player player) {
        return getMessageComponentForPlayer(key, defaultValue, placeholders, (OfflinePlayer) player);
    }

    public Component getMessageComponent(String key) {
        return getMessageComponentForPlayer(key, "", (OfflinePlayer) null);
    }

    public Component getMessageComponentForPlayer(String key, Player player) {
        return getMessageComponentForPlayer(key, "", (OfflinePlayer) player);
    }
}

