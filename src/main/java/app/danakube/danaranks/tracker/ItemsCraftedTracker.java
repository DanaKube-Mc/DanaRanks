package app.danakube.danaranks.tracker;

import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;

public class ItemsCraftedTracker implements ResourceTracker {
    private final DanaRanks plugin;

    public ItemsCraftedTracker(DanaRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getResourceName() {
        return "items_crafted";
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack result = event.getRecipe().getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }

        int amountCreated = calculateCraftedAmount(event, player, result);
        if (amountCreated <= 0) return;

        plugin.getProfileCache().getProfile(player.getUniqueId()).ifPresent(profile -> {
            plugin.getQuotaService().getProgressTracker().incrementProgress(profile, plugin.getQuotaService().getQuotaConfig(), getResourceName(), amountCreated);
            if (plugin.getRushManager() != null) {
                plugin.getRushManager().handleResourceGain(player.getUniqueId(), getResourceName(), amountCreated, Instant.now());
            }
        });
    }

    private int calculateCraftedAmount(CraftItemEvent event, Player player, ItemStack result) {
        int baseAmount = result.getAmount();
        if (!event.isShiftClick()) {
            return baseAmount;
        }

        if (!(event.getInventory() instanceof CraftingInventory craftInv)) {
            return baseAmount;
        }

        int minIngredient = Integer.MAX_VALUE;
        for (ItemStack matrixItem : craftInv.getMatrix()) {
            if (matrixItem != null && !matrixItem.getType().isAir()) {
                minIngredient = Math.min(minIngredient, matrixItem.getAmount());
            }
        }

        if (minIngredient == Integer.MAX_VALUE || minIngredient <= 0) {
            return baseAmount;
        }

        int maxFit = 0;
        int maxStack = result.getMaxStackSize();
        for (ItemStack invItem : player.getInventory().getStorageContents()) {
            if (invItem == null || invItem.getType() == Material.AIR) {
                maxFit += maxStack;
            } else if (invItem.isSimilar(result)) {
                maxFit += Math.max(0, maxStack - invItem.getAmount());
            }
        }

        int craftsThatFit = maxFit / baseAmount;
        int actualCrafts = Math.min(minIngredient, craftsThatFit);
        return Math.max(baseAmount, actualCrafts * baseAmount);
    }
}
