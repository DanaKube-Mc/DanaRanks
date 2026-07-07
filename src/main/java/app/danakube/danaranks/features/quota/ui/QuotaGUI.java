package app.danakube.danaranks.features.quota.ui;

import app.danakube.danaranks.core.DanaRanks;
import app.danakube.danaranks.core.profile.PlayerProfile;
import app.danakube.danaranks.features.quota.ObjectiveConfig;
import app.danakube.danaranks.features.quota.QuotaConfigLoader;
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

        Material borderMat = Material.matchMaterial(config.getString("menus.quota.items.border.material", "GRAY_STAINED_GLASS_PANE"));
        if (borderMat == null) borderMat = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack borderItem = MenuFactory.createItem(borderMat, " ", null);
        List<Integer> borderSlots = config.getIntegerList("menus.quota.items.border.slots");
        for (int slot : borderSlots) {
            if (slot >= 0 && slot < size) {
                inv.setItem(slot, borderItem);
            }
        }

        Material clockMat = Material.matchMaterial(config.getString("menus.quota.items.clock.material", "CLOCK"));
        if (clockMat == null) clockMat = Material.CLOCK;
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

        String clockName = config.getString("menus.quota.items.clock.name", "<aqua>Temps restant").replace("%time_remaining%", timeRemaining);
        List<String> clockLore = config.getStringList("menus.quota.items.clock.lore");
        List<String> formattedClockLore = clockLore.stream()
                .map(line -> line.replace("%time_remaining%", timeRemaining))
                .toList();
        ItemStack clockItem = MenuFactory.createItem(clockMat, clockName, formattedClockLore);
        if (clockSlot >= 0 && clockSlot < size) {
            inv.setItem(clockSlot, clockItem);
        }

        // Bouton retour profile (Slot 18 par défaut)
        int profileButtonSlot = config.getInt("menus.quota.items.profile-button.slot", 18);
        Material profileButtonMat = Material.matchMaterial(config.getString("menus.quota.items.profile-button.material", "BARRIER"));
        if (profileButtonMat == null) profileButtonMat = Material.BARRIER;
        String profileButtonName = config.getString("menus.quota.items.profile-button.name", "<red>Retour au Profil");
        List<String> profileButtonLore = config.getStringList("menus.quota.items.profile-button.lore");
        ItemStack profileButtonItem = MenuFactory.createItem(profileButtonMat, profileButtonName, profileButtonLore);
        if (profileButtonSlot >= 0 && profileButtonSlot < size) {
            inv.setItem(profileButtonSlot, profileButtonItem);
            holder.setAction(profileButtonSlot, event -> new ProfileGUI(plugin).open(player));
        }

        int activeRank = plugin.getQuotaService().getProgressTracker().getActiveQuotaRank(profile);
        Map<String, ObjectiveConfig> objectives = QuotaConfigLoader.getObjectivesForRank(plugin.getQuotaService().getQuotaConfig(), activeRank);

        String format = config.getString("menus.quota.objectives.format", "<yellow>%resource% : <gray>%bar% %progress%/%target% (<gold>%percentage%</gold>)");
        String barSymbolFilled = config.getString("menus.quota.objectives.bar-symbol-filled", "█");
        String barSymbolEmpty = config.getString("menus.quota.objectives.bar-symbol-empty", "░");
        int barSize = config.getInt("menus.quota.objectives.bar-size", 10);

        int currentSlot = 0;
        for (ObjectiveConfig obj : objectives.values()) {
            while (currentSlot < size && (borderSlots.contains(currentSlot) || currentSlot == clockSlot || currentSlot == profileButtonSlot)) {
                currentSlot++;
            }
            if (currentSlot >= size) break;

            double progress = plugin.getQuotaService().getProgressTracker().getProgress(profile, obj.name());
            double target = obj.target();
            int percentage = (int) Math.min(100, Math.round((progress / target) * 100));

            int filledCount = (int) Math.round((progress / target) * barSize);
            filledCount = Math.max(0, Math.min(barSize, filledCount));
            String bar = barSymbolFilled.repeat(filledCount) + barSymbolEmpty.repeat(barSize - filledCount);

            String formattedName = format.replace("%resource%", obj.name())
                    .replace("%progress%", String.format("%.0f", progress))
                    .replace("%target%", String.format("%.0f", target))
                    .replace("%percentage%", percentage + "%")
                    .replace("%bar%", bar);

            List<String> lore = new ArrayList<>();
            lore.add("<gray>Base reward : <gold>+" + obj.baseElo() + " ELO</gold>");
            lore.add("<gray>Max surplus : <gold>+" + obj.maxSurplusElo() + " ELO</gold>");
            if (obj.failPenalty() > 0) {
                lore.add("<gray>Failure penalty : <red>-" + obj.failPenalty() + " ELO</red>");
            }

            Material objMat = Material.PAPER;
            if (obj.name().contains("xp")) {
                objMat = Material.EXPERIENCE_BOTTLE;
            } else if (obj.name().contains("lumens")) {
                objMat = Material.GOLD_NUGGET;
            }

            ItemStack objItem = MenuFactory.createItem(objMat, formattedName, lore);
            inv.setItem(currentSlot, objItem);
            currentSlot++;
        }

        player.openInventory(inv);
    }
}
