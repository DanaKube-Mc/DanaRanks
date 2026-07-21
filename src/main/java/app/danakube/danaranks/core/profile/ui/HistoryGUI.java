package app.danakube.danaranks.core.profile.ui;

import app.danakube.danaranks.core.DanaRanks;
import app.danakube.danaranks.core.profile.HistoryEntry;
import app.danakube.danaranks.core.profile.PlayerProfile;
import app.danakube.danaranks.ui.shared.MenuFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HistoryGUI {
    private final DanaRanks plugin;

    public HistoryGUI(DanaRanks plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        FileConfiguration config = plugin.getGuiConfig();
        if (config == null) return;

        String title = config.getString("menus.history.title", "Historique d'ELO de %player% (Page %page%)")
                .replace("%player%", player.getName())
                .replace("%page%", String.valueOf(page));
        int size = config.getInt("menus.history.size", 54);

        MenuFactory.CustomHolder holder = new MenuFactory.CustomHolder();
        Inventory inv = plugin.getMenuFactory().createInventory(title, size, holder);

        // 1. Bordure
        ItemStack borderItem = MenuFactory.loadItem(config.getConfigurationSection("menus.history.items.border"), Material.GRAY_STAINED_GLASS_PANE);
        List<Integer> borderSlots = config.getIntegerList("menus.history.items.border.slots");
        for (int slot : borderSlots) {
            if (slot >= 0 && slot < size) {
                inv.setItem(slot, borderItem);
            }
        }

        // 2. Fetch history and group by day
        plugin.getHistoryRepository().fetchHistory(player.getUniqueId(), 200).thenAccept(historyEntries -> {
            PlayerProfile profileOpt = plugin.getProfileCache().getProfile(player.getUniqueId()).orElse(null);
            int currentRank = profileOpt != null ? profileOpt.getRankLevel() : 1;
            int currentElo = profileOpt != null ? profileOpt.getElo() : 0;

            int cumulativeElo = currentRank == 50 ? 4900 + currentElo : (currentRank - 1) * 100 + currentElo;

            Map<HistoryEntry, Integer> entryRanks = new HashMap<>();
            int tempCumulativeElo = cumulativeElo;
            for (HistoryEntry entry : historyEntries) {
                int entryRank = tempCumulativeElo >= 4900 ? 50 : (tempCumulativeElo / 100) + 1;
                entryRank = Math.max(1, Math.min(50, entryRank));
                entryRanks.put(entry, entryRank);
                tempCumulativeElo -= entry.eloChange();
            }

            // Group by LocalDate
            Map<LocalDate, List<HistoryEntry>> grouped = new LinkedHashMap<>();
            for (HistoryEntry entry : historyEntries) {
                LocalDate date = entry.timestamp().atZone(ZoneId.systemDefault()).toLocalDate();
                grouped.computeIfAbsent(date, k -> new ArrayList<>()).add(entry);
            }

            List<LocalDate> sortedDates = new ArrayList<>(grouped.keySet());

            // Navigation slots
            int prevSlot = config.getInt("menus.history.items.prev-page.slot", 47);
            int nextSlot = config.getInt("menus.history.items.next-page.slot", 51);
            int backSlot = config.getInt("menus.history.items.back-profile.slot", 49);

            // Résolution dynamique des slots de l'historique
            List<Integer> historySlots = config.getIntegerList("menus.history.slots");
            if (historySlots.isEmpty()) {
                historySlots = new ArrayList<>();
                List<Integer> excludedSlots = new ArrayList<>(borderSlots);
                excludedSlots.add(prevSlot);
                excludedSlots.add(nextSlot);
                excludedSlots.add(backSlot);

                for (int s = 0; s < size; s++) {
                    if (!excludedSlots.contains(s)) {
                        historySlots.add(s);
                    }
                }
            }

            int itemsPerPage = historySlots.size();
            int totalPages = (int) Math.ceil((double) sortedDates.size() / itemsPerPage);
            if (totalPages == 0) totalPages = 1;

            int finalPage = Math.max(1, Math.min(page, totalPages));
            int startIndex = (finalPage - 1) * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, sortedDates.size());

            // 3. Remplir l'inventaire avec les journées
            int slotIndex = 0;
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRANCE);
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

            ConfigurationSection dayItemSection = config.getConfigurationSection("menus.history.items.day-item");

            for (int i = startIndex; i < endIndex; i++) {
                LocalDate date = sortedDates.get(i);
                List<HistoryEntry> dayEntries = new ArrayList<>(grouped.get(date));
                int dayEndRank = entryRanks.getOrDefault(dayEntries.get(0), currentRank);
                Collections.reverse(dayEntries);

                List<String> lore = new ArrayList<>();
                int dayTotal = 0;
                String entryFormat = config.getString("menus.history.format.entry", "<gray>• %time% | %description% : %color%%sign%%elo% ELO</gray>");
                String rankChangeFormat = config.getString("menus.history.format.rank-change", "<gray>• %time% | <#ffeea2><b>Passage de Grade :</b></#ffeea2> %old_rank% ➔ %new_rank%</gray>");
                String totalFormat = config.getString("menus.history.format.total", "<gray>Total Journée : <gold>%sign%%total% ELO</gold></gray>");

                for (HistoryEntry entry : dayEntries) {
                    String time = entry.timestamp().atZone(ZoneId.systemDefault()).format(timeFormatter);
                    
                    if ("RANK_CHANGE".equalsIgnoreCase(entry.type())) {
                        String[] parts = entry.description().split(" -> ");
                        if (parts.length == 2) {
                            try {
                                int oldR = Integer.parseInt(parts[0]);
                                int newR = Integer.parseInt(parts[1]);
                                String oldRStr = plugin.getRankDisplayName(oldR);
                                String newRStr = plugin.getRankDisplayName(newR);

                                String formattedRankChange = rankChangeFormat
                                        .replace("%time%", time)
                                        .replace("%old_rank%", oldRStr)
                                        .replace("%new_rank%", newRStr);
                                lore.add(formattedRankChange);
                            } catch (NumberFormatException e) {
                                String formattedEntry = entryFormat
                                        .replace("%time%", time)
                                        .replace("%description%", entry.description())
                                        .replace("%color%", "")
                                        .replace("%sign%", "")
                                        .replace("%elo%", "");
                                lore.add(formattedEntry);
                            }
                        } else {
                            String formattedEntry = entryFormat
                                    .replace("%time%", time)
                                    .replace("%description%", entry.description())
                                    .replace("%color%", "")
                                    .replace("%sign%", "")
                                    .replace("%elo%", "");
                            lore.add(formattedEntry);
                        }
                    } else {
                        String desc = entry.description();
                        String displayName = desc;

                        if (desc.contains(":")) {
                            String[] parts = desc.split(":", 2);
                            String prefix = parts[0];
                            String key = parts[1];

                            String fullKey = "history-description." + prefix + "." + key;
                            String prefixKey = "history-description." + prefix;

                            String translated = plugin.getMessageManager().getMessage(fullKey, null);
                            if (translated == null) {
                                translated = plugin.getMessageManager().getMessage(prefixKey, null);
                                if (translated != null) {
                                    String resourceName = plugin.getResourceDisplayName(key);
                                    if (resourceName.equals(key)) {
                                        resourceName = plugin.getMessageManager().getMessage("quota-objective." + key, key);
                                    }
                                    displayName = translated.replace("%quota%", resourceName).replace("%resource%", resourceName);
                                } else {
                                    String resourceName = plugin.getResourceDisplayName(key);
                                    if (resourceName.equals(key)) {
                                        resourceName = plugin.getMessageManager().getMessage("quota-objective." + key, key);
                                    }
                                    displayName = prefix + " : " + resourceName;
                                }
                            } else {
                                displayName = translated;
                            }
                        } else {
                            displayName = plugin.getResourceDisplayName(desc);
                            if (displayName.equals(desc)) {
                                displayName = plugin.getMessageManager().getMessage("history-description." + desc, desc);
                            }
                        }

                        String color = entry.eloChange() >= 0 ? "<green>" : "<red>";
                        String sign = entry.eloChange() >= 0 ? "+" : "-";
                        String formattedEntry = entryFormat
                                .replace("%time%", time)
                                .replace("%description%", displayName)
                                .replace("%color%", color)
                                .replace("%sign%", sign)
                                .replace("%elo%", String.valueOf(Math.abs(entry.eloChange())));
                        lore.add(formattedEntry);
                        dayTotal += entry.eloChange();
                    }
                }
                lore.add(" ");
                String totalColor = dayTotal >= 0 ? "<green>" : "<red>";
                String totalSign = dayTotal >= 0 ? "+" : "-";
                String formattedTotal = totalFormat
                        .replace("%color%", totalColor)
                        .replace("%sign%", totalSign)
                        .replace("%total%", String.valueOf(Math.abs(dayTotal)));
                lore.add(formattedTotal);

                ItemStack dayItem;
                if (dayItemSection != null) {
                    String matStr = dayItemSection.getString("material", "PAPER");
                    Material mat = Material.matchMaterial(matStr);
                    if (mat == null) mat = Material.PAPER;

                    String nameFormat = dayItemSection.getString("name", "<yellow>%date%</yellow>");
                    String name = nameFormat.replace("%date%", date.format(dateFormatter));

                    Integer cmdVal = null;
                    if (dayItemSection.contains("custom-model-data")) {
                        String cmdStr = dayItemSection.getString("custom-model-data");
                        if ("%rank_cmd%".equalsIgnoreCase(cmdStr)) {
                            cmdVal = plugin.getRankCustomModelData(dayEndRank);
                        } else {
                            try {
                                cmdVal = Integer.parseInt(cmdStr);
                            } catch (NumberFormatException e) {
                                // Ignore
                            }
                        }
                    }

                    String texture = dayItemSection.getString("skull-texture");
                    if (texture != null) {
                        texture = texture.replace("%player%", player.getName());
                    }

                    dayItem = MenuFactory.createItem(
                            mat,
                            name,
                            lore,
                            cmdVal,
                            texture,
                            mat == Material.PLAYER_HEAD ? player : null
                    );
                } else {
                    dayItem = MenuFactory.createItem(
                            Material.PAPER,
                            "<yellow>" + date.format(dateFormatter) + "</yellow>",
                            lore
                    );
                }

                int targetSlot = historySlots.get(slotIndex++);
                if (targetSlot >= 0 && targetSlot < size) {
                    inv.setItem(targetSlot, dayItem);
                }
            }

            // 4. Navigation
            // Page précédente (Slot 47)
            if (finalPage > 1) {
                ItemStack prevItem = MenuFactory.loadItem(config.getConfigurationSection("menus.history.items.prev-page"), Material.ARROW);
                inv.setItem(prevSlot, prevItem);
                int finalPrevPage = finalPage - 1;
                holder.setAction(prevSlot, event -> open(player, finalPrevPage));
            }

            // Page suivante (Slot 51)
            if (finalPage < totalPages) {
                ItemStack nextItem = MenuFactory.loadItem(config.getConfigurationSection("menus.history.items.next-page"), Material.ARROW);
                inv.setItem(nextSlot, nextItem);
                int finalNextPage = finalPage + 1;
                holder.setAction(nextSlot, event -> open(player, finalNextPage));
            }

            // Retour au Profil (Slot 49)
            ItemStack backItem = MenuFactory.loadItem(
                    config.getConfigurationSection("menus.history.items.back-profile"),
                    Material.BARRIER,
                    Map.of("%player%", player.getName()),
                    player
            );
            inv.setItem(backSlot, backItem);
            holder.setAction(backSlot, event -> new ProfileGUI(plugin).open(player));

            // Ouvrir l'inventaire sur le thread principal de Bukkit
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inv));
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error opening history GUI: " + ex.getMessage());
            return null;
        });
    }
}
