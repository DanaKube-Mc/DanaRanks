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
import app.danakube.danaranks.features.quota.ui.QuotaGUI;

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
        ItemStack borderItem = MenuFactory.loadItem(config.getConfigurationSection("menus.profile.items.border"), Material.GRAY_STAINED_GLASS_PANE);
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
        ItemStack headItem = MenuFactory.loadItem(
                config.getConfigurationSection("menus.profile.items.player-head"),
                Material.PLAYER_HEAD,
                Map.of(
                        "%player%", player.getName(),
                        "%rank%", plugin.getRankDisplayName(profile.getRankLevel()),
                        "%elo%", String.valueOf(profile.getElo()),
                        "%position%", positionStr
                ),
                player
        );
        if (headSlot >= 0 && headSlot < size) {
            inv.setItem(headSlot, headItem);
        }

        // 4. Frise des Rangs (Option B)
        int currentRank = profile.getRankLevel();

        Material pastMat = Material.matchMaterial(config.getString("menus.profile.timeline.materials.past", "GRAY_DYE"));
        Material currentMat = Material.matchMaterial(config.getString("menus.profile.timeline.materials.current", "GOLD_BLOCK"));
        Material futureMat = Material.matchMaterial(config.getString("menus.profile.timeline.materials.future", "RED_DYE"));

        if (pastMat == null) pastMat = Material.GRAY_DYE;
        if (currentMat == null) currentMat = Material.GOLD_BLOCK;
        if (futureMat == null) futureMat = Material.RED_DYE;

        List<Integer> timelineSlots = config.getIntegerList("menus.profile.timeline.slots");
        if (timelineSlots.isEmpty()) {
            timelineSlots = new ArrayList<>();
            int timelineRow = config.getInt("menus.profile.timeline.row", 2);
            int startSlot = timelineRow * 9;
            for (int k = 0; k < 9; k++) {
                timelineSlots.add(startSlot + k);
            }
        }

        int N = timelineSlots.size();
        int currentSlotVal = config.getInt("menus.profile.timeline.current-slot", -1);
        int currentSlotIdx = timelineSlots.indexOf(currentSlotVal);
        if (currentSlotIdx == -1) {
            currentSlotIdx = N / 2;
        }

        int startRank = currentRank - currentSlotIdx;
        int endRank = startRank + N - 1;

        if (startRank < 1) {
            startRank = 1;
        } else if (endRank > 50) {
            startRank = 50 - N + 1;
        }
        if (startRank < 1) startRank = 1;

        for (int k = 0; k < N; k++) {
            int targetSlot = timelineSlots.get(k);
            int rankForSlot = startRank + k;

            if (rankForSlot >= 1 && rankForSlot <= 50) {
                Material itemMat;
                String rankName = plugin.getRankDisplayName(rankForSlot);
                String prefix;
                if (rankForSlot < currentRank) {
                    itemMat = pastMat;
                    prefix = "<gray>" + rankName + " (Validé)";
                } else if (rankForSlot == currentRank) {
                    itemMat = currentMat;
                    prefix = "<gold>" + rankName + " (Actuel)";
                } else {
                    itemMat = futureMat;
                    prefix = "<red>" + rankName + " (Futur)";
                }

                ItemStack timelineItem = MenuFactory.createItem(itemMat, prefix, List.of("<gray>Progression du parcours"));
                if (targetSlot >= 0 && targetSlot < size) {
                    inv.setItem(targetSlot, timelineItem);
                }
            }
        }

        // 5. Bouton Historique (Slot 31)
        int historySlot = config.getInt("menus.profile.items.history-button.slot", 31);
        ItemStack historyItem = MenuFactory.loadItem(config.getConfigurationSection("menus.profile.items.history-button"), Material.BOOK);
        if (historySlot >= 0 && historySlot < size) {
            inv.setItem(historySlot, historyItem);
            holder.setAction(historySlot, event -> new HistoryGUI(plugin).open(player, 1));
        }

        // 6. Bouton Classement (Slot 33)
        int leaderboardSlot = config.getInt("menus.profile.items.leaderboard-button.slot", 33);
        ItemStack leaderboardItem = MenuFactory.loadItem(config.getConfigurationSection("menus.profile.items.leaderboard-button"), Material.GOLD_INGOT);
        if (leaderboardSlot >= 0 && leaderboardSlot < size) {
            inv.setItem(leaderboardSlot, leaderboardItem);
            holder.setAction(leaderboardSlot, event -> new LeaderboardGUI(plugin).open(player, 0));
        }

        // 7. Bouton Quota (Slot 32)
        int quotaSlot = config.getInt("menus.profile.items.quota-button.slot", 32);
        ItemStack quotaItem = MenuFactory.loadItem(config.getConfigurationSection("menus.profile.items.quota-button"), Material.CHEST);
        if (quotaSlot >= 0 && quotaSlot < size) {
            inv.setItem(quotaSlot, quotaItem);
            holder.setAction(quotaSlot, event -> new QuotaGUI(plugin).open(player));
        }

        player.openInventory(inv);
    }
}
