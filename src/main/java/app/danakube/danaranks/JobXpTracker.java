package app.danakube.danaranks;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import su.nightexpress.excellentjobs.api.events.ExcellentJobXPCheckEvent;

public class JobXpTracker implements ResourceTracker {

    @Override
    public String getResourceName() {
        return "job_xp";
    }

    @EventHandler
    public void onJobXpGain(ExcellentJobXPCheckEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        double amount = event.getAmount();
        if (amount <= 0) return;

        DanaRanks plugin = DanaRanks.getInstance();
        if (plugin != null) {
            PlayerProfile profile = plugin.getProfileCache().get(player.getUniqueId());
            if (profile != null) {
                QuotaManager qm = QuotaManager.getInstance();
                if (qm != null) {
                    qm.incrementProgress(profile, getResourceName(), amount);
                }
            }
        }
    }
}
