package app.danakube.danaranks.tracker;

import app.danakube.danaranks.DanaRanks;
import app.danakube.danaranks.profile.PlayerProfile;
import app.danakube.danaranks.quota.QuotaManager;
import app.danakube.danaranks.rush.RushManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import app.danakube.danatools.event.ToolXpGainEvent;

public class ToolXpTracker implements ResourceTracker {

    @Override
    public String getResourceName() {
        return "tool_xp";
    }

    @EventHandler
    public void onToolXpGain(ToolXpGainEvent event) {
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
                if (plugin.getRushManager() != null) {
                    plugin.getRushManager().handleResourceGain(player.getUniqueId(), getResourceName(), amount, java.time.Instant.now());
                }
            }
        }
    }
}
