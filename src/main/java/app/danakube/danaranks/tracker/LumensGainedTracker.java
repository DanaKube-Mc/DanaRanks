package app.danakube.danaranks.tracker;

import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import su.nightexpress.excellenteconomy.api.event.ChangeBalanceEvent;
import java.time.Instant;

public class LumensGainedTracker implements ResourceTracker {
    private final DanaRanks plugin;

    public LumensGainedTracker(DanaRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getResourceName() {
        return "lumens_gained";
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
        if (amount <= 0) return;

        plugin.getProfileCache().getProfile(player.getUniqueId()).ifPresent(profile -> {
            plugin.getQuotaService().getProgressTracker().incrementProgress(profile, plugin.getQuotaService().getQuotaConfig(), getResourceName(), amount);
            if (plugin.getRushManager() != null) {
                plugin.getRushManager().handleResourceGain(player.getUniqueId(), getResourceName(), amount, Instant.now());
            }
        });
    }
}
