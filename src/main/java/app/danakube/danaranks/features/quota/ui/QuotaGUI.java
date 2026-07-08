package app.danakube.danaranks.features.quota.ui;

import app.danakube.danaranks.core.DanaRanks;
import app.danakube.danaranks.core.profile.PlayerProfile;
import app.danakube.danaranks.features.quota.ObjectiveConfig;
import app.danakube.danaranks.ui.shared.MenuFactory;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.*;
import app.danakube.danaranks.core.profile.ui.ProfileGUI;

public class QuotaGUI {
    private final DanaRanks plugin;

    public QuotaGUI(DanaRanks plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        FileConfiguration config = plugin.getGuiConfig();
        if (config == null) return;

        String title = config.getString("menus.quota.title", "<dark_gray>Vos Quotas Périodiques");
        int size = config.getInt("menus.quota.size", 27);

        MenuFactory.CustomHolder holder = new MenuFactory.CustomHolder();
        Inventory inv = plugin.getMenuFactory().createInventory(title, size, holder);

        Optional<PlayerProfile> profileOpt = plugin.getProfileCache().getProfile(player.getUniqueId());
        if (profileOpt.isEmpty()) return;
        PlayerProfile profile = profileOpt.get();

        // 1. Bordure
        ItemStack borderItem = MenuFactory.loadItem(config.getConfigurationSection("menus.quota.items.border"), Material.GRAY_STAINED_GLASS_PANE);
        List<Integer> borderSlots = config.getIntegerList("menus.quota.items.border.slots");
        for (int slot : borderSlots) {
            if (slot >= 0 && slot < size) {
                inv.setItem(slot, borderItem);
            }
        }

        int clockSlot = config.getInt("menus.quota.items.clock.slot", 22);

        int periodDays = plugin.getQuotaService().getQuotaScheduler().getPeriodDays(plugin.getQuotaService().getLevelFromRank(profile.getRankLevel()));
        Instant nextReset = plugin.getQuotaService().getQuotaScheduler().getNextResetInstant(periodDays, Instant.now());
        long diffSecs = nextReset.getEpochSecond() - Instant.now().getEpochSecond();
        long days = diffSecs / 86400;
        long hours = (diffSecs % 86400) / 3600;
        long minutes = (diffSecs % 3600) / 60;
        long seconds = diffSecs % 60;
        String timeRemaining;
        if (days > 0) {
            timeRemaining = String.format("%dj %02dh %02dm", days, hours, minutes);
        } else {
            timeRemaining = String.format("%02dh %02dm %02ds", hours, minutes, seconds);
        }

        ItemStack clockItem = MenuFactory.loadItem(
                config.getConfigurationSection("menus.quota.items.clock"),
                Material.CLOCK,
                Map.of("%time_remaining%", timeRemaining),
                null
        );
        if (clockSlot >= 0 && clockSlot < size) {
            inv.setItem(clockSlot, clockItem);
        }

        // Bouton retour profile (Slot 18 par défaut)
        int profileButtonSlot = config.getInt("menus.quota.items.profile-button.slot", 18);
        ItemStack profileButtonItem = MenuFactory.loadItem(
                config.getConfigurationSection("menus.quota.items.profile-button"),
                Material.BARRIER,
                Map.of("%player%", player.getName()),
                player
        );
        if (profileButtonSlot >= 0 && profileButtonSlot < size) {
            inv.setItem(profileButtonSlot, profileButtonItem);
            holder.setAction(profileButtonSlot, event -> new ProfileGUI(plugin).open(player));
        }

        Map<String, ObjectiveConfig> objectives = plugin.getQuotaService().getProgressTracker().getActiveObjectives(profile);

        String format = config.getString("menus.quota.objectives.format", "<yellow>%resource% : <gray>%bar% %progress%/%target% (<gold>%percentage%</gold>)");
        String barSymbolFilled = config.getString("menus.quota.objectives.bar-symbol-filled", "█");
        String barSymbolEmpty = config.getString("menus.quota.objectives.bar-symbol-empty", "░");
        int barSize = config.getInt("menus.quota.objectives.bar-size", 10);

        List<Integer> objectiveSlots = config.getIntegerList("menus.quota.slots");
        if (objectiveSlots.isEmpty()) objectiveSlots = config.getIntegerList("menus.quota.slot");
        if (objectiveSlots.isEmpty()) objectiveSlots = config.getIntegerList("menus.quota.objectives.slots");
        if (objectiveSlots.isEmpty()) objectiveSlots = config.getIntegerList("menus.quota.objectives.slot");

        int objIdx = 0;
        int currentSlot = 0;

        for (ObjectiveConfig obj : objectives.values()) {
            int targetSlot;
            if (!objectiveSlots.isEmpty()) {
                if (objIdx >= objectiveSlots.size()) break;
                targetSlot = objectiveSlots.get(objIdx++);
            } else {
                while (currentSlot < size && (borderSlots.contains(currentSlot) || currentSlot == clockSlot || currentSlot == profileButtonSlot)) {
                    currentSlot++;
                }
                if (currentSlot >= size) break;
                targetSlot = currentSlot++;
            }

            double progress = plugin.getQuotaService().getProgressTracker().getProgress(profile, obj.name());
            double target = obj.target();
            int percentage = (int) Math.min(100, Math.round((progress / target) * 100));

            int filledCount = (int) Math.round((progress / target) * barSize);
            filledCount = Math.max(0, Math.min(barSize, filledCount));
            String bar = barSymbolFilled.repeat(filledCount) + barSymbolEmpty.repeat(barSize - filledCount);

            String formattedName = format.replace("%resource%", plugin.getResourceDisplayName(obj.name()))
                    .replace("%progress%", String.format("%.0f", progress))
                    .replace("%target%", String.format("%.0f", target))
                    .replace("%percentage%", percentage + "%")
                    .replace("%bar%", bar);

            String baseRewardFormat = config.getString("menus.quota.format.base-reward", "<gray>Base reward : <gold>+%elo% ELO</gold></gray>");
            String maxSurplusFormat = config.getString("menus.quota.format.max-surplus", "<gray>Max surplus : <gold>+%elo% ELO</gold></gray>");
            String failPenaltyFormat = config.getString("menus.quota.format.fail-penalty", "<gray>Failure penalty : <red>-%elo% ELO</red></gray>");

            List<String> lore = new ArrayList<>();
            lore.add(baseRewardFormat.replace("%elo%", String.valueOf(obj.baseElo())));
            lore.add(maxSurplusFormat.replace("%elo%", String.valueOf(obj.maxSurplusElo())));
            if (obj.failPenalty() > 0) {
                lore.add(failPenaltyFormat.replace("%elo%", String.valueOf(obj.failPenalty())));
            }

            Material objMat = null;
            if (obj.material() != null) {
                objMat = Material.matchMaterial(obj.material());
            }
            if (objMat == null) {
                objMat = Material.PAPER;
                if (obj.name().contains("xp")) {
                    objMat = Material.EXPERIENCE_BOTTLE;
                } else if (obj.name().contains("lumens")) {
                    objMat = Material.GOLD_NUGGET;
                }
            }

            Integer cmd = obj.customModelData();

            ItemStack objItem = MenuFactory.createItem(objMat, formattedName, lore, cmd, null, null);
            if (targetSlot >= 0 && targetSlot < size) {
                inv.setItem(targetSlot, objItem);
            }
        }

        player.openInventory(inv);
    }
}
