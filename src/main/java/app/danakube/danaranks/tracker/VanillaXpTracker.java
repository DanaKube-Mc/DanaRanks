package app.danakube.danaranks.tracker;

import app.danakube.danaranks.DanaRanks;
import app.danakube.danaranks.profile.PlayerProfile;
import app.danakube.danaranks.quota.QuotaManager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;

public class VanillaXpTracker implements ResourceTracker {
    private final String mode; // "gained" ou "spent"

    public VanillaXpTracker(String mode) {
        this.mode = mode.toLowerCase();
    }

    @Override
    public String getResourceName() {
        return "vanilla_xp_" + mode;
    }

    @EventHandler
    public void onExpChange(PlayerExpChangeEvent event) {
        if (!mode.equals("gained")) return;

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

    @EventHandler
    public void onLevelChange(PlayerLevelChangeEvent event) {
        if (!mode.equals("spent")) return;

        Player player = event.getPlayer();
        if (player == null) return;

        int oldLevel = event.getOldLevel();
        int newLevel = event.getNewLevel();

        // On ne s'intéresse qu'aux pertes de niveaux (dépenses)
        if (newLevel >= oldLevel) return;

        double xpSpent = getXpDifference(oldLevel, newLevel);
        if (xpSpent <= 0) return;

        DanaRanks plugin = DanaRanks.getInstance();
        if (plugin != null) {
            PlayerProfile profile = plugin.getProfileCache().get(player.getUniqueId());
            if (profile != null) {
                QuotaManager qm = QuotaManager.getInstance();
                if (qm != null) {
                    qm.incrementProgress(profile, getResourceName(), xpSpent);
                }
                if (plugin.getRushManager() != null) {
                    plugin.getRushManager().handleResourceGain(player.getUniqueId(), getResourceName(), xpSpent, java.time.Instant.now());
                }
            }
        }
    }

    private double getXpDifference(int fromLevel, int toLevel) {
        return getXpAtLevel(fromLevel) - getXpAtLevel(toLevel);
    }

    private int getXpAtLevel(int level) {
        if (level <= 15) {
            return level * level + 6 * level;
        } else if (level <= 30) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        } else {
            return (int) (4.5 * level * level - 162.5 * level + 2220);
        }
    }
}
