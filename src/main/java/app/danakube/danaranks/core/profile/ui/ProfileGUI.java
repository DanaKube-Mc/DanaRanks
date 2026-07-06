package app.danakube.danaranks.core.profile.ui;

import app.danakube.danaranks.core.DanaRanks;
import app.danakube.danaranks.core.profile.PlayerProfile;
import app.danakube.danaranks.features.leaderboard.LeaderboardEntry;
import app.danakube.danaranks.features.leaderboard.LeaderboardGUI;
import app.danakube.danaranks.ui.shared.MenuFactory;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ProfileGUI {
    private final DanaRanks plugin;

    public ProfileGUI(DanaRanks plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        FileConfiguration config = plugin.getGuiConfig();
        if (config == null) return;

        String title = config.getString("menus.profile.title", "Profil de %player%").replace("%player%", player.getName());
        int size = config.getInt("menus.profile.size", 45);

        MenuFactory.CustomHolder holder = new MenuFactory.CustomHolder();
        Inventory inv = plugin.getMenuFactory().createInventory(title, size, holder);

        Optional<PlayerProfile> profileOpt = plugin.getProfileCache().getProfile(player.getUniqueId());
        if (profileOpt.isEmpty()) return;
        PlayerProfile profile = profileOpt.get();

        // 1. Bordure
        Material borderMat = Material.matchMaterial(config.getString("menus.profile.items.border.material", "GRAY_STAINED_GLASS_PANE"));
        if (borderMat == null) borderMat = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack borderItem = MenuFactory.createItem(borderMat, " ", null);
        List<Integer> borderSlots = config.getIntegerList("menus.profile.items.border.slots");
        for (int slot : borderSlots) {
            if (slot >= 0 && slot < size) {
                inv.setItem(slot, borderItem);
            }
        }

        // 2. Trouver la position du joueur dans le classement
        int position = -1;
        List<LeaderboardEntry> top = plugin.getLeaderboardManager() != null ? plugin.getLeaderboardManager().getCachedLeaderboard() : Collections.emptyList();
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).uuid().equals(player.getUniqueId())) {
                position = i + 1;
                break;
            }
        }
        String positionStr = position > 0 ? String.valueOf(position) : ">50";

        // 3. Tête de joueur (Slot 13)
        int headSlot = config.getInt("menus.profile.items.player-head.slot", 13);
        String headName = config.getString("menus.profile.items.player-head.name", "<gold>%player%").replace("%player%", player.getName());
        List<String> headLore = config.getStringList("menus.profile.items.player-head.lore");
        List<String> formattedHeadLore = headLore.stream()
                .map(line -> line.replace("%player%", player.getName())
                        .replace("%rank%", String.valueOf(profile.getRankLevel()))
                        .replace("%elo%", String.valueOf(profile.getElo()))
                        .replace("%position%", positionStr))
                .toList();
        ItemStack headItem = MenuFactory.createItem(Material.PLAYER_HEAD, headName, formattedHeadLore);
        if (headSlot >= 0 && headSlot < size) {
            inv.setItem(headSlot, headItem);
        }

        // 4. Frise des Rangs (Option B)
        int timelineRow = config.getInt("menus.profile.timeline.row", 2);
        int startSlot = timelineRow * 9; // Ligne 3 = slots 18 à 26
        int currentRank = profile.getRankLevel();

        Material pastMat = Material.matchMaterial(config.getString("menus.profile.timeline.materials.past", "GRAY_DYE"));
        Material currentMat = Material.matchMaterial(config.getString("menus.profile.timeline.materials.current", "GOLD_BLOCK"));
        Material futureMat = Material.matchMaterial(config.getString("menus.profile.timeline.materials.future", "RED_DYE"));

        if (pastMat == null) pastMat = Material.GRAY_DYE;
        if (currentMat == null) currentMat = Material.GOLD_BLOCK;
        if (futureMat == null) futureMat = Material.RED_DYE;

        for (int k = 0; k < 9; k++) {
            int targetSlot = startSlot + k;
            int rankForSlot;

            if (currentRank <= 5) {
                rankForSlot = 1 + k;
            } else if (currentRank >= 46) {
                rankForSlot = 42 + k;
            } else {
                rankForSlot = currentRank + (k - 4);
            }

            if (rankForSlot >= 1 && rankForSlot <= 50) {
                Material itemMat;
                String prefix;
                if (rankForSlot < currentRank) {
                    itemMat = pastMat;
                    prefix = "<gray>Rang " + rankForSlot + " (Validé)";
                } else if (rankForSlot == currentRank) {
                    itemMat = currentMat;
                    prefix = "<gold>Rang " + rankForSlot + " (Actuel)";
                } else {
                    itemMat = futureMat;
                    prefix = "<red>Rang " + rankForSlot + " (Futur)";
                }

                ItemStack timelineItem = MenuFactory.createItem(itemMat, prefix, List.of("<gray>Progression du parcours"));
                inv.setItem(targetSlot, timelineItem);
            }
        }

        // 5. Bouton Historique (Slot 31)
        int historySlot = config.getInt("menus.profile.items.history-button.slot", 31);
        Material historyMat = Material.matchMaterial(config.getString("menus.profile.items.history-button.material", "BOOK"));
        if (historyMat == null) historyMat = Material.BOOK;
        String historyName = config.getString("menus.profile.items.history-button.name", "<aqua>Historique d'ELO");
        List<String> historyLore = config.getStringList("menus.profile.items.history-button.lore");
        ItemStack historyItem = MenuFactory.createItem(historyMat, historyName, historyLore);
        if (historySlot >= 0 && historySlot < size) {
            inv.setItem(historySlot, historyItem);
            holder.setAction(historySlot, event -> new HistoryGUI(plugin).open(player, 1));
        }

        // 6. Bouton Classement (Slot 33)
        int leaderboardSlot = config.getInt("menus.profile.items.leaderboard-button.slot", 33);
        Material leaderboardMat = Material.matchMaterial(config.getString("menus.profile.items.leaderboard-button.material", "GOLD_INGOT"));
        if (leaderboardMat == null) leaderboardMat = Material.GOLD_INGOT;
        String leaderboardName = config.getString("menus.profile.items.leaderboard-button.name", "<gold>Classement Global");
        List<String> leaderboardLore = config.getStringList("menus.profile.items.leaderboard-button.lore");
        ItemStack leaderboardItem = MenuFactory.createItem(leaderboardMat, leaderboardName, leaderboardLore);
        if (leaderboardSlot >= 0 && leaderboardSlot < size) {
            inv.setItem(leaderboardSlot, leaderboardItem);
            holder.setAction(leaderboardSlot, event -> new LeaderboardGUI(plugin).open(player));
        }

        player.openInventory(inv);
    }
}
