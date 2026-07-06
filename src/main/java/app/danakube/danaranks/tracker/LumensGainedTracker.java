package app.danakube.danaranks.tracker;

import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import su.nightexpress.excellenteconomy.api.events.ChangeBalanceEvent;

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

        double amount = event.getAmount();
        if (amount <= 0) return;

        String reason = event.getReason();
        if (reason == null) return;
        reason = reason.toLowerCase();

        if (reason.contains("job") || reason.contains("admin_shop") || reason.contains("shop")) {
            plugin.getProfileCache().getProfile(player.getUniqueId()).ifPresent(profile -> {
                plugin.getQuotaService().getProgressTracker().incrementProgress(profile, plugin.getQuotaService().getQuotaConfig(), getResourceName(), amount);
                if (plugin.getRushManager() != null) {
                    plugin.getRushManager().handleResourceGain(player.getUniqueId(), getResourceName(), amount, java.time.Instant.now());
                }
            });
        }
    }
}
