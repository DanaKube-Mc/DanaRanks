package app.danakube.danaranks.tracker;

import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;

import java.time.Instant;

public class MobsKilledTracker implements ResourceTracker {
    private final DanaRanks plugin;

    public MobsKilledTracker(DanaRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getResourceName() {
        return "mobs_killed";
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }

        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }

        plugin.getProfileCache().getProfile(killer.getUniqueId()).ifPresent(profile -> {
            plugin.getQuotaService().getProgressTracker().incrementProgress(profile, plugin.getQuotaService().getQuotaConfig(), getResourceName(), 1.0);
            if (plugin.getRushManager() != null) {
                plugin.getRushManager().handleResourceGain(killer.getUniqueId(), getResourceName(), 1.0, Instant.now());
            }
        });
    }
}
