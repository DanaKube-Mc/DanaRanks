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
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Component nameComponent = MiniMessage.miniMessage()
                    .deserialize(name)
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(nameComponent);
            if (lore != null) {
                List<Component> serializedLore = lore.stream()
                        .map(line -> MiniMessage.miniMessage()
                                .deserialize(line)
                                .decoration(TextDecoration.ITALIC, false))
                        .toList();
                meta.lore(serializedLore);
            }
            item.setItemMeta(meta);
        }
        return item;
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
