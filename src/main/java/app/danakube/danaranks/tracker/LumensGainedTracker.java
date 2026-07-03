package app.danakube.danaranks.tracker;

import app.danakube.danaranks.DanaRanks;
import app.danakube.danaranks.profile.PlayerProfile;
import app.danakube.danaranks.quota.QuotaManager;
import app.danakube.danaranks.rush.RushManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import su.nightexpress.excellenteconomy.api.events.ChangeBalanceEvent;

public class LumensGainedTracker implements ResourceTracker {

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
            DanaRanks plugin = DanaRanks.getInstance();
            if (plugin != null) {
                PlayerProfile profile = plugin.getProfileCache().get(player.getUniqueId());
                if (profile != null) {
                    QuotaManager qm = QuotaManager.getInstance();
                    if (qm != null) {
                        qm.incrementProgress(profile, getResourceName(), amount);
                    }
                    if (plugin.getRushManager() != null) {
                        plugin.getRushManager().handleResourceGain(player.getUniqueId(), getResourceName(), amount, java.time.Instant.now());
                    }
                }
            }
        }
    }
}
