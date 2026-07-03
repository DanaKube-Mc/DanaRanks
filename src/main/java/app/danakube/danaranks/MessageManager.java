package app.danakube.danaranks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

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
                String value = config.getString("messages." + key);
                if (value != null) {
                    messages.put(key, value);
                }
            }
        }
    }

    private void createDefaultLangFile(File file) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.kick-database-error", "&c[DanaRanks] Impossible de charger vos données de rang. Veuillez vous reconnecter.");
        config.set("messages.no-permission", "&cVous n'avez pas la permission d'exécuter cette commande.");
        config.set("messages.profile-loaded", "&aVotre profil de rang a été correctement chargé !");
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

    public String getRawMessage(String key, String defaultValue) {
        return messages.getOrDefault(key, defaultValue);
    }

    public String getMessage(String key, String defaultValue) {
        String raw = messages.get(key);
        if (raw == null) {
            raw = defaultValue;
        }
        return translateColorCodes(raw);
    }

    public String getMessage(String key) {
        return getMessage(key, "");
    }

    public Component getMessageComponent(String key, String defaultValue) {
        String raw = messages.get(key);
        if (raw == null) {
            raw = defaultValue;
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }

    public Component getMessageComponent(String key) {
        return getMessageComponent(key, "");
    }

    private String translateColorCodes(String message) {
        if (message == null) {
            return null;
        }
        return message.replace('&', '§');
    }
}
