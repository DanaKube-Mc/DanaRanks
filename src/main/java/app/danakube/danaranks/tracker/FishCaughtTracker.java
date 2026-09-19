package app.danakube.danaranks.tracker;

import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerFishEvent;

import java.time.Instant;

public class FishCaughtTracker implements ResourceTracker {
    private final DanaRanks plugin;

    public FishCaughtTracker(DanaRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getResourceName() {
        return "fish_caught";
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || event.getCaught() == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) return;

        plugin.getProfileCache().getProfile(player.getUniqueId()).ifPresent(profile -> {
            plugin.getQuotaService().getProgressTracker().incrementProgress(profile, plugin.getQuotaService().getQuotaConfig(), getResourceName(), 1.0);
            if (plugin.getRushManager() != null) {
                plugin.getRushManager().handleResourceGain(player.getUniqueId(), getResourceName(), 1.0, Instant.now());
            }
        });
    }
}
