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
        return "job_xp_all";
    }

    @EventHandler
    public void onJobXpGain(GrindRewardEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        double amount = event.getReward().get(GrindObjectiveProperty.XP);
        if (amount <= 0) return;

        String jobId = event.getJob().getId().toLowerCase().replace("-", "_");

        plugin.getProfileCache().getProfile(player.getUniqueId()).ifPresent(profile -> {
            // 1. XP de métier global (job-xp-all)
            plugin.getQuotaService().getProgressTracker().incrementProgress(profile, plugin.getQuotaService().getQuotaConfig(), "job_xp_all", amount);
            if (plugin.getRushManager() != null) {
                plugin.getRushManager().handleResourceGain(player.getUniqueId(), "job_xp_all", amount, Instant.now());
            }

            // 2. XP de métier spécifique (job-xp-<jobId>)
            String specific = "job_xp_" + jobId;
            plugin.getQuotaService().getProgressTracker().incrementProgress(profile, plugin.getQuotaService().getQuotaConfig(), specific, amount);
            if (plugin.getRushManager() != null) {
                plugin.getRushManager().handleResourceGain(player.getUniqueId(), specific, amount, Instant.now());
            }
        });
    }
}
