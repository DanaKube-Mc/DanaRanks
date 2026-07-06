package app.danakube.danaranks.tracker;

import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import su.nightexpress.excellenteconomy.api.events.ChangeBalanceEvent;

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

        double amount = event.getAmount();
        if (amount >= 0) return;

        String reason = event.getReason();
        if (reason != null) {
            String lower = reason.toLowerCase();
            if (lower.contains("pay") || lower.contains("transfer")) {
                return;
            }
        }

        double spentAmount = Math.abs(amount);

        plugin.getProfileCache().getProfile(player.getUniqueId()).ifPresent(profile -> {
            plugin.getQuotaService().getProgressTracker().incrementProgress(profile, plugin.getQuotaService().getQuotaConfig(), getResourceName(), spentAmount);
            if (plugin.getRushManager() != null) {
                plugin.getRushManager().handleResourceGain(player.getUniqueId(), getResourceName(), spentAmount, java.time.Instant.now());
            }
        });
    }
}
