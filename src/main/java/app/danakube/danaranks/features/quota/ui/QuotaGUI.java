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

        boolean useCustomBar = config.contains("menus.quota.progress-bar") || config.contains("menus.quota.objectives.progress-bar");
        int barLength = config.getInt("menus.quota.progress-bar.length", 10);
        if (barLength <= 0) {
            barLength = config.getInt("menus.quota.objectives.progress-bar.length", 10);
        }
        String barSymbol = config.getString("menus.quota.progress-bar.symbol", config.getString("menus.quota.objectives.progress-bar.symbol", "█"));
        String colorFilled = config.getString("menus.quota.progress-bar.color-filled", config.getString("menus.quota.objectives.progress-bar.color-filled", ""));
        String colorSurplus = config.getString("menus.quota.progress-bar.color-surplus", config.getString("menus.quota.objectives.progress-bar.color-surplus", ""));
        String colorEmpty = config.getString("menus.quota.progress-bar.color-empty", config.getString("menus.quota.objectives.progress-bar.color-empty", ""));
        boolean showPercentageInside = config.getBoolean("menus.quota.progress-bar.show-percentage-inside", config.getBoolean("menus.quota.objectives.progress-bar.show-percentage-inside", false));

        List<Integer> objectiveSlots = config.getIntegerList("menus.quota.slots");
        if (objectiveSlots.isEmpty()) objectiveSlots = config.getIntegerList("menus.quota.slot");
        if (objectiveSlots.isEmpty()) objectiveSlots = config.getIntegerList("menus.quota.objectives.slots");
        if (objectiveSlots.isEmpty()) objectiveSlots = config.getIntegerList("menus.quota.objectives.slot");

        List<String> loreTemplate = config.getStringList("menus.quota.objectives.lore");
        if (loreTemplate.isEmpty()) {
            loreTemplate = config.getStringList("menus.quota.format.lore");
        }

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
            double surplusMultiplier = plugin.getQuotaService().getQuotaConfig().surplusMultiplier();

            // Calcul du pourcentage sur l'échelle 0% à 200%
            int percentage;
            if (progress <= target) {
                percentage = (int) Math.min(100, Math.round((progress / target) * 100));
            } else {
                double diff = progress - target;
                double maxDiff = target * (surplusMultiplier - 1.0);
                double fraction = maxDiff > 0 ? Math.min(1.0, diff / maxDiff) : 0;
                percentage = 100 + (int) Math.round(fraction * 100);
            }

            String bar;
            if (useCustomBar) {
                String surplusColorToUse = colorSurplus.isEmpty() ? colorFilled : colorSurplus;

                if (percentage <= 100) {
                    // Phase 1 : Remplissage de la base de 0% à 100% (filled vs empty)
                    int filled = (int) Math.round((percentage / 100.0) * barLength);
                    filled = Math.max(0, Math.min(barLength, filled));
                    int empty = barLength - filled;

                    if (showPercentageInside) {
                        int leftLength = barLength / 2;
                        int rightLength = barLength - leftLength;

                        int leftFilled = Math.min(filled, leftLength);
                        int leftEmpty = leftLength - leftFilled;

                        int rightFilled = Math.max(0, filled - leftLength);
                        int rightEmpty = rightLength - rightFilled;

                        String leftFilledStr = leftFilled > 0 ? colorFilled + barSymbol.repeat(leftFilled) + closeTag(colorFilled) : "";
                        String leftEmptyStr = leftEmpty > 0 ? colorEmpty + barSymbol.repeat(leftEmpty) + closeTag(colorEmpty) : "";
                        String leftStr = leftFilledStr + leftEmptyStr;

                        String rightFilledStr = rightFilled > 0 ? colorFilled + barSymbol.repeat(rightFilled) + closeTag(colorFilled) : "";
                        String rightEmptyStr = rightEmpty > 0 ? colorEmpty + barSymbol.repeat(rightEmpty) + closeTag(colorEmpty) : "";
                        String rightStr = rightFilledStr + rightEmptyStr;

                        bar = leftStr  + percentage  + rightStr;
                    } else {
                        String filledStr = filled > 0 ? colorFilled + barSymbol.repeat(filled) + closeTag(colorFilled) : "";
                        String emptyStr = empty > 0 ? colorEmpty + barSymbol.repeat(empty) + closeTag(colorEmpty) : "";
                        bar = filledStr + emptyStr;
                    }
                } else {
                    // Phase 2 : Remplissage du surplus de 100% à 200% (surplus vs filled)
                    int surplusPercentage = percentage - 100;
                    int filledSurplus = (int) Math.round((surplusPercentage / 100.0) * barLength);
                    filledSurplus = Math.max(0, Math.min(barLength, filledSurplus));
                    int filledBase = barLength - filledSurplus;

                    if (showPercentageInside) {
                        int leftLength = barLength / 2;
                        int rightLength = barLength - leftLength;

                        int leftSurplus = Math.min(filledSurplus, leftLength);
                        int leftBase = leftLength - leftSurplus;

                        int rightSurplus = Math.max(0, filledSurplus - leftLength);
                        int rightBase = rightLength - rightSurplus;

                        String leftSurplusStr = leftSurplus > 0 ? surplusColorToUse + barSymbol.repeat(leftSurplus) + closeTag(surplusColorToUse) : "";
                        String leftBaseStr = leftBase > 0 ? colorFilled + barSymbol.repeat(leftBase) + closeTag(colorFilled) : "";
                        String leftStr = leftSurplusStr + leftBaseStr;

                        String rightSurplusStr = rightSurplus > 0 ? surplusColorToUse + barSymbol.repeat(rightSurplus) + closeTag(surplusColorToUse) : "";
                        String rightBaseStr = rightBase > 0 ? colorFilled + barSymbol.repeat(rightBase) + closeTag(colorFilled) : "";
                        String rightStr = rightSurplusStr + rightBaseStr;

                        bar = leftStr  + percentage  + rightStr;
                    } else {
                        String surplusStr = filledSurplus > 0 ? surplusColorToUse + barSymbol.repeat(filledSurplus) + closeTag(surplusColorToUse) : "";
                        String baseStr = filledBase > 0 ? colorFilled + barSymbol.repeat(filledBase) + closeTag(colorFilled) : "";
                        bar = surplusStr + baseStr;
                    }
                }
            } else {
                int filledCount = (int) Math.round((progress / target) * barSize);
                filledCount = Math.max(0, Math.min(barSize, filledCount));
                bar = barSymbolFilled.repeat(filledCount) + barSymbolEmpty.repeat(barSize - filledCount);
            }

            String formattedName = format.replace("%resource%", plugin.getResourceDisplayName(obj.name()))
                    .replace("%progress%", String.format("%.0f", progress))
                    .replace("%target%", String.format("%.0f", target))
                    .replace("%percentage%", percentage + "%")
                    .replace("%bar%", bar);

            List<String> lore = new ArrayList<>();
            if (loreTemplate.isEmpty()) {
                String baseRewardFormat = config.getString("menus.quota.format.base-reward", "<gray>Base reward : <gold>+%elo% ELO</gold></gray>");
                String maxSurplusFormat = config.getString("menus.quota.format.max-surplus", "<gray>Max surplus : <gold>+%elo% ELO</gold></gray>");
                String failPenaltyFormat = config.getString("menus.quota.format.fail-penalty", "<gray>Failure penalty : <red>-%elo% ELO</red></gray>");

                lore.add(baseRewardFormat.replace("%elo%", String.valueOf(obj.baseElo())));
                lore.add(maxSurplusFormat.replace("%elo%", String.valueOf(obj.maxSurplusElo())));
                if (obj.failPenalty() > 0) {
                    lore.add(failPenaltyFormat.replace("%elo%", String.valueOf(obj.failPenalty())));
                }
            } else {
                for (String line : loreTemplate) {
                    if (line.contains("%fail_penalty%") && obj.failPenalty() <= 0) {
                        continue;
                    }
                    lore.add(line
                            .replace("%base_elo%", String.valueOf(obj.baseElo()))
                            .replace("%max_surplus%", String.valueOf(obj.maxSurplusElo()))
                            .replace("%fail_penalty%", String.valueOf(obj.failPenalty()))
                            .replace("%progress%", String.format("%.0f", progress))
                            .replace("%target%", String.format("%.0f", target))
                            .replace("%percentage%", percentage + "%")
                            .replace("%bar%", bar)
                    );
                }
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

    private String closeTag(String tag) {
        if (tag == null || tag.isEmpty()) return "";
        if (tag.contains("gradient")) return "</gradient>";
        if (tag.contains("color")) return "</color>";
        if (tag.startsWith("<#")) return "";
        if (tag.startsWith("<") && tag.endsWith(">")) {
            String name = tag.substring(1, tag.length() - 1);
            if (name.contains(":")) {
                name = name.split(":")[0];
            }
            return "</" + name + ">";
        }
        return "";
    }
}
