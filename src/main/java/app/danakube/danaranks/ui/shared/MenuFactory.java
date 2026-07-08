package app.danakube.danaranks.ui.shared;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;

public class MenuFactory implements Listener {

    public MenuFactory(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static class CustomHolder implements InventoryHolder {
        private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
        private Inventory inventory;

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setAction(int slot, Consumer<InventoryClickEvent> action) {
            actions.put(slot, action);
        }

        public Consumer<InventoryClickEvent> getAction(int slot) {
            return actions.get(slot);
        }
    }

    public Inventory createInventory(String title, int size, CustomHolder holder) {
        Component componentTitle = MiniMessage.miniMessage().deserialize(title);
        Inventory inv = Bukkit.createInventory(holder, size, componentTitle);
        holder.setInventory(inv);
        return inv;
    }

    public static ItemStack createItem(Material material, String name, List<String> lore) {
        return createItem(material, name, lore, null, null, null);
    }

    public static ItemStack createItem(Material material, String name, List<String> lore, Integer customModelData, String skullTexture, org.bukkit.OfflinePlayer skullOwner) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                Component nameComponent = MiniMessage.miniMessage()
                        .deserialize(name)
                        .decoration(TextDecoration.ITALIC, false);
                meta.displayName(nameComponent);
            }
            if (lore != null) {
                List<Component> serializedLore = lore.stream()
                        .map(line -> MiniMessage.miniMessage()
                                .deserialize(line)
                                .decoration(TextDecoration.ITALIC, false))
                        .toList();
                meta.lore(serializedLore);
            }
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
            if (material == Material.PLAYER_HEAD && meta instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
                if (skullTexture != null && !skullTexture.isEmpty()) {
                    String base64Texture;
                    if (skullTexture.startsWith("http://") || skullTexture.startsWith("https://")) {
                        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + skullTexture + "\"}}}";
                        base64Texture = java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } else {
                        base64Texture = skullTexture;
                    }
                    try {
                        com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(java.util.UUID.randomUUID(), null);
                        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", base64Texture));
                        skullMeta.setPlayerProfile(profile);
                    } catch (Exception e) {
                        // ignore
                    }
                } else if (skullOwner != null) {
                    skullMeta.setOwningPlayer(skullOwner);
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack loadItem(org.bukkit.configuration.ConfigurationSection section, Material defaultMaterial) {
        return loadItem(section, defaultMaterial, null, null);
    }

    public static ItemStack loadItem(org.bukkit.configuration.ConfigurationSection section, Material defaultMaterial, Map<String, String> placeholders, org.bukkit.OfflinePlayer skullOwnerFallback) {
        if (section == null) {
            return createItem(defaultMaterial, null, null, null, null, skullOwnerFallback);
        }
        String materialStr = section.getString("material");
        Material material = materialStr != null ? Material.matchMaterial(materialStr) : defaultMaterial;
        if (material == null) material = defaultMaterial;
        if (material == null) material = Material.STONE;

        String name = section.contains("name") ? section.getString("name") : null;
        List<String> lore = section.contains("lore") ? section.getStringList("lore") : null;
        Integer cmd = null;
        if (section.contains("custom-model-data")) {
            String cmdRaw = section.getString("custom-model-data");
            if (cmdRaw != null) {
                if (placeholders != null) {
                    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                        cmdRaw = cmdRaw.replace(entry.getKey(), entry.getValue());
                    }
                }
                try {
                    cmd = Integer.parseInt(cmdRaw.trim());
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        String texture = section.getString("skull-texture");
        if (texture != null && placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                texture = texture.replace(entry.getKey(), entry.getValue());
            }
        }


        // Placeholders replacement
        if (placeholders != null) {
            if (name != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    name = name.replace(entry.getKey(), entry.getValue());
                }
            }
            if (lore != null && !lore.isEmpty()) {
                lore = lore.stream().map(line -> {
                    for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                        line = line.replace(entry.getKey(), entry.getValue());
                    }
                    return line;
                }).toList();
            }
        }

        return createItem(material, name, lore, cmd, texture, skullOwnerFallback);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CustomHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            Consumer<InventoryClickEvent> action = holder.getAction(slot);
            if (action != null) {
                action.accept(event);
            }
        }
    }
}
