package app.danakube.danaranks.tracker;

import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import java.time.Instant;
import java.util.List;

public class VanillaXpTracker implements ResourceTracker {
    private final DanaRanks plugin;
    private final String mode;

    public VanillaXpTracker(DanaRanks plugin, String mode) {
        this.plugin = plugin;
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

        // Anti-Abus / Anti-Cheat (Filtrage QuickShop, AxTrade...)
        List<String> blocked = plugin.getConfig().getStringList("anti-abuse.blocked-plugin-packages");
        if (blocked != null) {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                String className = element.getClassName().toLowerCase();
                for (String pkg : blocked) {
                    if (className.contains(pkg.toLowerCase())) {
                        return; // Ignorer la transaction
                    }
                }
            }
        }

        double amount = event.getAmount();
        if (amount <= 0) return;

        plugin.getProfileCache().getProfile(player.getUniqueId()).ifPresent(profile -> {
            plugin.getQuotaService().getProgressTracker().incrementProgress(profile, plugin.getQuotaService().getQuotaConfig(), getResourceName(), amount);
            if (plugin.getRushManager() != null) {
                plugin.getRushManager().handleResourceGain(player.getUniqueId(), getResourceName(), amount, Instant.now());
            }
        });
    }

    @EventHandler
    public void onLevelChange(PlayerLevelChangeEvent event) {
        if (!mode.equals("spent")) return;

        Player player = event.getPlayer();
        if (player == null) return;

        // Anti-Abus / Anti-Cheat (Filtrage QuickShop, AxTrade...)
        List<String> blocked = plugin.getConfig().getStringList("anti-abuse.blocked-plugin-packages");
        if (blocked != null) {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                String className = element.getClassName().toLowerCase();
                for (String pkg : blocked) {
                    if (className.contains(pkg.toLowerCase())) {
                        return; // Ignorer la transaction
                    }
                }
            }
        }

        int oldLevel = event.getOldLevel();
        int newLevel = event.getNewLevel();

        if (newLevel >= oldLevel) return;

        double xpSpent = getXpDifference(oldLevel, newLevel);
        if (xpSpent <= 0) return;

        plugin.getProfileCache().getProfile(player.getUniqueId()).ifPresent(profile -> {
            plugin.getQuotaService().getProgressTracker().incrementProgress(profile, plugin.getQuotaService().getQuotaConfig(), getResourceName(), xpSpent);
            if (plugin.getRushManager() != null) {
                plugin.getRushManager().handleResourceGain(player.getUniqueId(), getResourceName(), xpSpent, Instant.now());
            }
        });
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
