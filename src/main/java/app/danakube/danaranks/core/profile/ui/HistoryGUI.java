package app.danakube.danaranks.core.profile.ui;

import app.danakube.danaranks.core.DanaRanks;
import app.danakube.danaranks.core.profile.HistoryEntry;
import app.danakube.danaranks.ui.shared.MenuFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
        Material borderMat = Material.matchMaterial(config.getString("menus.history.items.border.material", "GRAY_STAINED_GLASS_PANE"));
        if (borderMat == null) borderMat = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack borderItem = MenuFactory.createItem(borderMat, " ", null);
        List<Integer> borderSlots = config.getIntegerList("menus.history.items.border.slots");
        for (int slot : borderSlots) {
            if (slot >= 0 && slot < size) {
                inv.setItem(slot, borderItem);
            }
        }

        // 2. Fetch history and group by day
        plugin.getHistoryRepository().fetchHistory(player.getUniqueId(), 200).thenAccept(historyEntries -> {
            // Group by LocalDate
            Map<LocalDate, List<HistoryEntry>> grouped = new LinkedHashMap<>();
            for (HistoryEntry entry : historyEntries) {
                LocalDate date = entry.timestamp().atZone(ZoneId.systemDefault()).toLocalDate();
                grouped.computeIfAbsent(date, k -> new ArrayList<>()).add(entry);
            }

            List<LocalDate> sortedDates = new ArrayList<>(grouped.keySet());
            // Les dates sont déjà triées du plus récent au plus ancien car fetchHistory est ORDER BY timestamp DESC

            int itemsPerPage = 45; // 5 lignes
            int totalPages = (int) Math.ceil((double) sortedDates.size() / itemsPerPage);
            if (totalPages == 0) totalPages = 1;

            int finalPage = Math.max(1, Math.min(page, totalPages));
            int startIndex = (finalPage - 1) * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, sortedDates.size());

            // 3. Remplir l'inventaire avec les journées
            int slotIndex = 0;
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRANCE);
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

            for (int i = startIndex; i < endIndex; i++) {
                LocalDate date = sortedDates.get(i);
                List<HistoryEntry> dayEntries = new ArrayList<>(grouped.get(date));
                // Chronologique : de la plus ancienne à la plus récente de la journée
                Collections.reverse(dayEntries);

                List<String> lore = new ArrayList<>();
                int dayTotal = 0;
                String entryFormat = config.getString("menus.history.format.entry", "<gray>• %time% | %description% : %color%%sign%%elo% ELO</gray>");
                String totalFormat = config.getString("menus.history.format.total", "<gray>Total Journée : <gold>%sign%%total% ELO</gold></gray>");

                for (HistoryEntry entry : dayEntries) {
                    String time = entry.timestamp().atZone(ZoneId.systemDefault()).format(timeFormatter);
                    String color = entry.eloChange() >= 0 ? "<green>" : "<red>";
                    String sign = entry.eloChange() >= 0 ? "+" : "-";
                    String formattedEntry = entryFormat
                            .replace("%time%", time)
                            .replace("%description%", entry.description())
                            .replace("%color%", color)
                            .replace("%sign%", sign)
                            .replace("%elo%", String.valueOf(Math.abs(entry.eloChange())));
                    lore.add(formattedEntry);
                    dayTotal += entry.eloChange();
                }
                lore.add(" ");
                String totalColor = dayTotal >= 0 ? "<green>" : "<red>";
                String totalSign = dayTotal >= 0 ? "+" : "-";
                String formattedTotal = totalFormat
                        .replace("%color%", totalColor)
                        .replace("%sign%", totalSign)
                        .replace("%total%", String.valueOf(Math.abs(dayTotal)));
                lore.add(formattedTotal);

                ItemStack paper = MenuFactory.createItem(
                        Material.PAPER,
                        "<yellow>" + date.format(dateFormatter) + "</yellow>",
                        lore
                );
                inv.setItem(slotIndex++, paper);
            }

            // 4. Navigation (Slots 45 à 53)
            // Page précédente (Slot 47)
            if (finalPage > 1) {
                int prevSlot = config.getInt("menus.history.items.prev-page.slot", 47);
                Material prevMat = Material.matchMaterial(config.getString("menus.history.items.prev-page.material", "ARROW"));
                if (prevMat == null) prevMat = Material.ARROW;
                ItemStack prevItem = MenuFactory.createItem(prevMat, config.getString("menus.history.items.prev-page.name", "<yellow>Page Précédente"), config.getStringList("menus.history.items.prev-page.lore"));
                inv.setItem(prevSlot, prevItem);
                int finalPrevPage = finalPage - 1;
                holder.setAction(prevSlot, event -> open(player, finalPrevPage));
            }

            // Page suivante (Slot 51)
            if (finalPage < totalPages) {
                int nextSlot = config.getInt("menus.history.items.next-page.slot", 51);
                Material nextMat = Material.matchMaterial(config.getString("menus.history.items.next-page.material", "ARROW"));
                if (nextMat == null) nextMat = Material.ARROW;
                ItemStack nextItem = MenuFactory.createItem(nextMat, config.getString("menus.history.items.next-page.name", "<yellow>Page Suivante"), config.getStringList("menus.history.items.next-page.lore"));
                inv.setItem(nextSlot, nextItem);
                int finalNextPage = finalPage + 1;
                holder.setAction(nextSlot, event -> open(player, finalNextPage));
            }

            // Retour au Profil (Slot 49)
            int backSlot = config.getInt("menus.history.items.back-profile.slot", 49);
            Material backMat = Material.matchMaterial(config.getString("menus.history.items.back-profile.material", "BARRIER"));
            if (backMat == null) backMat = Material.BARRIER;
            ItemStack backItem = MenuFactory.createItem(backMat, config.getString("menus.history.items.back-profile.name", "<red>Retour au Profil"), config.getStringList("menus.history.items.back-profile.lore"));
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
