package app.danakube.danaranks.tracker;

import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import su.nightexpress.excellentjobs.api.event.GrindRewardEvent;
import su.nightexpress.excellentjobs.api.grind.GrindObjectiveProperty;
import java.time.Instant;

public class JobXpTracker implements ResourceTracker {
    private final DanaRanks plugin;

    public JobXpTracker(DanaRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getResourceName() {
        return "job_xp";
    }

    @EventHandler
    public void onJobXpGain(GrindRewardEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        double amount = event.getReward().get(GrindObjectiveProperty.XP);
        if (amount <= 0) return;

        plugin.getProfileCache().getProfile(player.getUniqueId()).ifPresent(profile -> {
            plugin.getQuotaService().getProgressTracker().incrementProgress(profile, plugin.getQuotaService().getQuotaConfig(), getResourceName(), amount);
            if (plugin.getRushManager() != null) {
                plugin.getRushManager().handleResourceGain(player.getUniqueId(), getResourceName(), amount, Instant.now());
            }
        });
    }
}
