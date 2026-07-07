package app.danakube.danaranks.tracker;

import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import su.nightexpress.excellenteconomy.api.event.ChangeBalanceEvent;

public class LumensSpentTracker implements ResourceTracker {
    private final DanaRanks plugin;

    public LumensSpentTracker(DanaRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getResourceName() {
        return "lumens_spent";
    }

    @EventHandler
    public void onBalanceChange(ChangeBalanceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // Anti-Abus / Anti-Cheat (Filtrage QuickShop, AxTrade...)
        java.util.List<String> blocked = plugin.getConfig().getStringList("anti-abuse.blocked-plugin-packages");
        if (blocked != null) {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                String className = element.getClassName().toLowerCase();
                for (String pkg : blocked) {
                    if (className.contains(pkg.toLowerCase())) {
                        return; // Ignorer la transaction
                    }
                }
            }
        }

        double amount = event.getNewAmount() - event.getOldAmount();
        if (amount >= 0) return;

        double spentAmount = Math.abs(amount);

        plugin.getProfileCache().getProfile(player.getUniqueId()).ifPresent(profile -> {
            plugin.getQuotaService().getProgressTracker().incrementProgress(profile, plugin.getQuotaService().getQuotaConfig(), getResourceName(), spentAmount);
            if (plugin.getRushManager() != null) {
                plugin.getRushManager().handleResourceGain(player.getUniqueId(), getResourceName(), spentAmount, java.time.Instant.now());
            }
        });
    }
}
