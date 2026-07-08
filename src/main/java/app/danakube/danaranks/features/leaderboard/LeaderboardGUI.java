package app.danakube.danaranks.features.leaderboard;

import app.danakube.danaranks.core.DanaRanks;
import app.danakube.danaranks.core.profile.ui.ProfileGUI;
import app.danakube.danaranks.ui.shared.MenuFactory;
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
        Material borderMat = Material.matchMaterial(config.getString("menus.leaderboard.items.border.material", "GRAY_STAINED_GLASS_PANE"));
        if (borderMat == null) borderMat = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack borderItem = MenuFactory.createItem(borderMat, " ", null);
        List<Integer> borderSlots = config.getIntegerList("menus.leaderboard.items.border.slots");
        for (int slot : borderSlots) {
            if (slot >= 0 && slot < size) {
                inv.setItem(slot, borderItem);
            }
        }

        // 2. Bouton retour (Slot 49)
        int backSlot = config.getInt("menus.leaderboard.items.profile-button.slot", 49);
        Material backMat = Material.matchMaterial(config.getString("menus.leaderboard.items.profile-button.material", "BARRIER"));
        if (backMat == null) backMat = Material.BARRIER;
        ItemStack backItem = MenuFactory.createItem(
                backMat,
                config.getString("menus.leaderboard.items.profile-button.name", "<red>Retour au Profil"),
                config.getStringList("menus.leaderboard.items.profile-button.lore")
        );
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

            List<String> lore = new ArrayList<>();
            lore.add("<gray>Grade : <gold>" + plugin.getRankDisplayName(entry.rankLevel()) + "</gold>");
            lore.add("<gray>ELO : <gold>" + entry.elo() + "/100</gold>");

            ItemStack item = MenuFactory.createItem(
                    mat,
                    "<gold>#" + rankNum + " | " + entry.playerName() + "</gold>",
                    lore
            );
            inv.setItem(currentSlot++, item);
        }

        player.openInventory(inv);
    }

    private void setupFilterButton(Inventory inv, MenuFactory.CustomHolder holder, Player player, FileConfiguration config, String key, int targetLevel, int activeLevel) {
        int slot = config.getInt("menus.leaderboard.items." + key + ".slot");
        Material mat = Material.matchMaterial(config.getString("menus.leaderboard.items." + key + ".material", "PAPER"));
        if (mat == null) mat = Material.PAPER;
        String name = config.getString("menus.leaderboard.items." + key + ".name", "Filtre");
        List<String> lore = new ArrayList<>(config.getStringList("menus.leaderboard.items." + key + ".lore"));

        if (targetLevel == activeLevel) {
            name = name + " <green>(Actif)</green>";
            lore.add("<green>Filtre actif</green>");
        }

        ItemStack item = MenuFactory.createItem(mat, name, lore);
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, item);
            holder.setAction(slot, event -> open(player, targetLevel));
        }
    }
}
