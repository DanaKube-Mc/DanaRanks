package app.danakube.danaranks;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import su.nightexpress.excellenteconomy.api.events.ChangeBalanceEvent;

public class LumensSpentTracker implements ResourceTracker {

    @Override
    public String getResourceName() {
        return "lumens_spent";
    }

    @EventHandler
    public void onBalanceChange(ChangeBalanceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        double amount = event.getAmount();
        // Ne conserve que les dépenses (amount < 0)
        if (amount >= 0) return;

        String reason = event.getReason();
        if (reason != null) {
            String lower = reason.toLowerCase();
            if (lower.contains("pay") || lower.contains("transfer")) {
                return;
            }
        }

        double spentAmount = Math.abs(amount);

        DanaRanks plugin = DanaRanks.getInstance();
        if (plugin != null) {
            PlayerProfile profile = plugin.getProfileCache().get(player.getUniqueId());
            if (profile != null) {
                QuotaManager qm = QuotaManager.getInstance();
                if (qm != null) {
                    qm.incrementProgress(profile, getResourceName(), spentAmount);
                }
            }
        }
    }
}
