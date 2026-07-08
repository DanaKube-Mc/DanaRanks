package app.danakube.danaranks.features.leaderboard;

import app.danakube.danaranks.core.DanaRanks;
import app.danakube.danaranks.core.profile.ui.ProfileGUI;
import app.danakube.danaranks.ui.shared.MenuFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardGUI {
    private final DanaRanks plugin;

    public LeaderboardGUI(DanaRanks plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int filterLevel) {
        FileConfiguration config = plugin.getGuiConfig();
        if (config == null) return;

        String title = config.getString("menus.leaderboard.title", "Classement Global (Top 50)");
        if (filterLevel > 0) {
            title = title + " - Niveau " + filterLevel;
        }
        int size = config.getInt("menus.leaderboard.size", 54);

        MenuFactory.CustomHolder holder = new MenuFactory.CustomHolder();
        Inventory inv = plugin.getMenuFactory().createInventory(title, size, holder);

        // 1. Bordure
        ItemStack borderItem = MenuFactory.loadItem(config.getConfigurationSection("menus.leaderboard.items.border"), Material.GRAY_STAINED_GLASS_PANE);
        List<Integer> borderSlots = config.getIntegerList("menus.leaderboard.items.border.slots");
        for (int slot : borderSlots) {
            if (slot >= 0 && slot < size) {
                inv.setItem(slot, borderItem);
            }
        }

        // 2. Bouton retour (Slot 49)
        int backSlot = config.getInt("menus.leaderboard.items.profile-button.slot", 49);
        ItemStack backItem = MenuFactory.loadItem(config.getConfigurationSection("menus.leaderboard.items.profile-button"), Material.BARRIER);
        if (backSlot >= 0 && backSlot < size) {
            inv.setItem(backSlot, backItem);
            holder.setAction(backSlot, event -> new ProfileGUI(plugin).open(player));
        }

        // 2b. Boutons de filtres
        setupFilterButton(inv, holder, player, config, "filter-global", 0, filterLevel);
        setupFilterButton(inv, holder, player, config, "filter-lvl1", 1, filterLevel);
        setupFilterButton(inv, holder, player, config, "filter-lvl2", 2, filterLevel);
        setupFilterButton(inv, holder, player, config, "filter-lvl3", 3, filterLevel);
        setupFilterButton(inv, holder, player, config, "filter-lvl4", 4, filterLevel);
        setupFilterButton(inv, holder, player, config, "filter-lvl5", 5, filterLevel);

        // 3. Liste des entrées (Top 50 filtré)
        List<LeaderboardEntry> top = plugin.getLeaderboardManager() != null ? plugin.getLeaderboardManager().getCachedLeaderboard() : new ArrayList<>();
        List<LeaderboardEntry> filtered = new ArrayList<>();
        for (LeaderboardEntry entry : top) {
            int level = plugin.getQuotaService().getLevelFromRank(entry.rankLevel());
            if (filterLevel == 0 || level == filterLevel) {
                filtered.add(entry);
            }
        }

        int currentSlot = 0;
        int itemsCount = Math.min(45, filtered.size()); // Max 45 items (5 lignes de 9)

        // Liste des slots de filtre pour éviter de les écraser
        List<Integer> filterSlots = List.of(45, 46, 47, 48, 50, 51);

        for (int i = 0; i < itemsCount; i++) {
            while (currentSlot < size && (borderSlots.contains(currentSlot) || currentSlot == backSlot || filterSlots.contains(currentSlot))) {
                currentSlot++;
            }
            if (currentSlot >= size) break;

            LeaderboardEntry entry = filtered.get(i);
            int rankNum = i + 1;

            Material mat = Material.PLAYER_HEAD;
            if (rankNum == 1) mat = Material.GOLD_BLOCK;
            else if (rankNum == 2) mat = Material.IRON_BLOCK;
            else if (rankNum == 3) mat = Material.COPPER_BLOCK;

            String entryNameFormat = config.getString("menus.leaderboard.format.entry-name", "<gold>#%position% | %player%</gold>");
            List<String> entryLoreFormat = config.getStringList("menus.leaderboard.format.entry-lore");
            if (entryLoreFormat.isEmpty()) {
                entryLoreFormat = List.of(
                        "<gray>Grade : <gold>%rank%</gold>",
                        "<gray>ELO : <gold>%elo%/100</gold>"
                );
            }

            String name = entryNameFormat
                    .replace("%position%", String.valueOf(rankNum))
                    .replace("%player%", entry.playerName());

            List<String> lore = entryLoreFormat.stream()
                    .map(line -> line
                            .replace("%position%", String.valueOf(rankNum))
                            .replace("%player%", entry.playerName())
                            .replace("%rank%", plugin.getRankDisplayName(entry.rankLevel()))
                            .replace("%elo%", String.valueOf(entry.elo()))
                    )
                    .toList();

            ItemStack item = MenuFactory.createItem(
                    mat,
                    name,
                    lore,
                    null,
                    null,
                    Bukkit.getOfflinePlayer(entry.uuid())
            );
            inv.setItem(currentSlot++, item);
        }

        player.openInventory(inv);
    }

    private void setupFilterButton(Inventory inv, MenuFactory.CustomHolder holder, Player player, FileConfiguration config, String key, int targetLevel, int activeLevel) {
        int slot = config.getInt("menus.leaderboard.items." + key + ".slot");
        org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("menus.leaderboard.items." + key);
        if (section == null) return;

        String materialStr = section.getString("material");
        Material mat = materialStr != null ? Material.matchMaterial(materialStr) : Material.PAPER;
        if (mat == null) mat = Material.PAPER;

        String name = section.getString("name", "Filtre");
        List<String> lore = new ArrayList<>(section.getStringList("lore"));
        Integer cmd = section.contains("custom-model-data") ? section.getInt("custom-model-data") : null;
        String texture = section.getString("skull-texture");

        if (targetLevel == activeLevel) {
            name = name + " <green>(Actif)</green>";
            lore.add("<green>Filtre actif</green>");
        }

        ItemStack item = MenuFactory.createItem(mat, name, lore, cmd, texture, null);
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, item);
            holder.setAction(slot, event -> open(player, targetLevel));
        }
    }
}
