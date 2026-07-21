package app.danakube.danaranks.features.leaderboard;

import app.danakube.danaranks.core.DanaRanks;
import app.danakube.danaranks.core.profile.PlayerProfile;
import app.danakube.danaranks.core.profile.ui.ProfileGUI;
import app.danakube.danaranks.ui.shared.MenuFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

        String filterKey = filterLevel == 0 ? "filter-global" : "filter-lvl" + filterLevel;
        String title = config.getString("menus.leaderboard.items." + filterKey + ".title");
        if (title == null || title.isEmpty()) {
            title = config.getString("menus.leaderboard.title", "Classement Global (Top 50)");
            if (filterLevel > 0) {
                title = title + " - Niveau " + filterLevel;
            }
        }
        int size = config.getInt("menus.leaderboard.size", 54);

        MenuFactory.CustomHolder holder = new MenuFactory.CustomHolder();
        Inventory inv = plugin.getMenuFactory().createInventory(title, size, holder);

        ItemStack borderItem = MenuFactory.loadItem(config.getConfigurationSection("menus.leaderboard.items.border"), Material.GRAY_STAINED_GLASS_PANE);
        List<Integer> borderSlots = config.getIntegerList("menus.leaderboard.items.border.slots");
        for (int slot : borderSlots) {
            if (slot >= 0 && slot < size) {
                inv.setItem(slot, borderItem);
            }
        }

        int backSlot = config.getInt("menus.leaderboard.items.profile-button.slot", 49);
        ItemStack backItem = MenuFactory.loadItem(
                config.getConfigurationSection("menus.leaderboard.items.profile-button"),
                Material.BARRIER,
                Map.of("%player%", player.getName()),
                player
        );
        if (backSlot >= 0 && backSlot < size) {
            inv.setItem(backSlot, backItem);
            holder.setAction(backSlot, event -> new ProfileGUI(plugin).open(player));
        }

        setupFilterButton(inv, holder, player, config, "filter-global", 0, filterLevel);
        setupFilterButton(inv, holder, player, config, "filter-lvl1", 1, filterLevel);
        setupFilterButton(inv, holder, player, config, "filter-lvl2", 2, filterLevel);
        setupFilterButton(inv, holder, player, config, "filter-lvl3", 3, filterLevel);
        setupFilterButton(inv, holder, player, config, "filter-lvl4", 4, filterLevel);
        setupFilterButton(inv, holder, player, config, "filter-lvl5", 5, filterLevel);

        int selfPlayerSlot = config.getInt("menus.leaderboard.items.player.slot", -1);
        List<LeaderboardEntry> top = plugin.getLeaderboardManager() != null ? plugin.getLeaderboardManager().getCachedLeaderboard() : Collections.emptyList();
        
        if (selfPlayerSlot >= 0 && selfPlayerSlot < size && config.contains("menus.leaderboard.items.player")) {
            PlayerProfile selfProfile = plugin.getProfileCache().getProfile(player.getUniqueId()).orElse(null);
            if (selfProfile != null) {
                int selfPos = -1;
                for (int g = 0; g < top.size(); g++) {
                    if (top.get(g).uuid().equals(player.getUniqueId())) {
                        selfPos = g + 1;
                        break;
                    }
                }
                String selfPosStr = selfPos > 0 ? String.valueOf(selfPos) : ">50";

                Map<String, String> selfPlaceholders = Map.of(
                        "%pos%", selfPosStr,
                        "%position%", selfPosStr,
                        "%player%", player.getName(),
                        "%rank%", plugin.getRankDisplayName(selfProfile.getRankLevel()),
                        "%elo%", String.valueOf(selfProfile.getElo())
                );

                ItemStack selfItem = MenuFactory.loadItem(
                        config.getConfigurationSection("menus.leaderboard.items.player"),
                        Material.PLAYER_HEAD,
                        selfPlaceholders,
                        player
                );
                inv.setItem(selfPlayerSlot, selfItem);
            }
        }

        List<LeaderboardEntry> filtered = new ArrayList<>();
        for (LeaderboardEntry entry : top) {
            int level = plugin.getQuotaService().getLevelFromRank(entry.rankLevel());
            if (filterLevel == 0 || level == filterLevel) {
                filtered.add(entry);
            }
        }

        List<Integer> leaderboardSlots = config.getIntegerList("menus.leaderboard.slots");
        if (leaderboardSlots.isEmpty()) {
            leaderboardSlots = new ArrayList<>();
            List<Integer> excludedSlots = new ArrayList<>(borderSlots);
            if (backSlot >= 0) excludedSlots.add(backSlot);
            if (selfPlayerSlot >= 0) excludedSlots.add(selfPlayerSlot);

            for (String fk : List.of("filter-global", "filter-lvl1", "filter-lvl2", "filter-lvl3", "filter-lvl4", "filter-lvl5")) {
                int fSlot = config.getInt("menus.leaderboard.items." + fk + ".slot", -1);
                if (fSlot >= 0) excludedSlots.add(fSlot);
            }

            for (int s = 0; s < size; s++) {
                if (!excludedSlots.contains(s)) {
                    leaderboardSlots.add(s);
                }
            }
        }

        ConfigurationSection entrySection = config.getConfigurationSection("menus.leaderboard.items.leaderboard-player");
        int itemsCount = Math.min(leaderboardSlots.size(), filtered.size());

        for (int i = 0; i < itemsCount; i++) {
            int slot = leaderboardSlots.get(i);
            if (slot < 0 || slot >= size) continue;

            LeaderboardEntry entry = filtered.get(i);
            int rankNum = i + 1;

            int globalRankNum = -1;
            for (int g = 0; g < top.size(); g++) {
                if (top.get(g).uuid().equals(entry.uuid())) {
                    globalRankNum = g + 1;
                    break;
                }
            }
            String globalRankStr = globalRankNum > 0 ? String.valueOf(globalRankNum) : ">50";

            Map<String, String> entryPlaceholders = Map.of(
                    "%pos%", String.valueOf(rankNum),
                    "%position%", String.valueOf(rankNum),
                    "%global_pos%", globalRankStr,
                    "%global_position%", globalRankStr,
                    "%player%", entry.playerName(),
                    "%rank%", plugin.getRankDisplayName(entry.rankLevel()),
                    "%elo%", String.valueOf(entry.elo())
            );

            ItemStack item;
            if (entrySection != null) {
                String matStr = entrySection.getString("material", "PLAYER_HEAD");
                Material mat = Material.matchMaterial(matStr);
                if (mat == null) mat = Material.PLAYER_HEAD;

                item = MenuFactory.loadItem(entrySection, mat, entryPlaceholders, Bukkit.getOfflinePlayer(entry.uuid()));
            } else {
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
                        .replace("%pos%", String.valueOf(rankNum))
                        .replace("%player%", entry.playerName());

                List<String> lore = entryLoreFormat.stream()
                        .map(line -> line
                                .replace("%position%", String.valueOf(rankNum))
                                .replace("%pos%", String.valueOf(rankNum))
                                .replace("%player%", entry.playerName())
                                .replace("%rank%", plugin.getRankDisplayName(entry.rankLevel()))
                                .replace("%elo%", String.valueOf(entry.elo()))
                        )
                        .toList();

                item = MenuFactory.createItem(
                        mat,
                        name,
                        lore,
                        null,
                        null,
                        Bukkit.getOfflinePlayer(entry.uuid())
                );
            }
            inv.setItem(slot, item);
        }

        player.openInventory(inv);
    }

    private void setupFilterButton(Inventory inv, MenuFactory.CustomHolder holder, Player player, FileConfiguration config, String key, int targetLevel, int activeLevel) {
        int slot = config.getInt("menus.leaderboard.items." + key + ".slot", -1);
        ConfigurationSection section = config.getConfigurationSection("menus.leaderboard.items." + key);
        if (section == null || slot < 0 || slot >= inv.getSize()) return;

        String materialStr = section.getString("material");
        Material mat = materialStr != null ? Material.matchMaterial(materialStr) : Material.PAPER;
        if (mat == null) mat = Material.PAPER;

        String name = section.getString("name", "Filtre");
        List<String> lore = new ArrayList<>(section.getStringList("lore"));
        Integer cmd = section.contains("custom-model-data") ? section.getInt("custom-model-data") : null;
        String texture = section.getString("skull-texture");

        if (targetLevel == activeLevel) {
            name = name + " <#91f251>(Actif)</#91f251>";
        }

        ItemStack item = MenuFactory.createItem(mat, name, lore, cmd, texture, null);
        inv.setItem(slot, item);
        holder.setAction(slot, event -> open(player, targetLevel));
    }
}
