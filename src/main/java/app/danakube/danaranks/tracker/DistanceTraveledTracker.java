package app.danakube.danaranks.tracker;

import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DistanceTraveledTracker implements ResourceTracker {
    private final DanaRanks plugin;
    private final Map<UUID, Double> distanceBuffer = new ConcurrentHashMap<>();

    public DistanceTraveledTracker(DanaRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getResourceName() {
        return "distance_traveled";
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }

        Player player = event.getPlayer();
        // Exclusion des élytres
        if (player.isGliding()) {
            return;
        }

        // Exclusion des bateaux et minecarts (les montures comme chevaux/cochons/arbres sont autorisées)
        if (player.isInsideVehicle()) {
            Entity vehicle = player.getVehicle();
            if (vehicle instanceof Boat || vehicle instanceof Minecart) {
                return;
            }
        }

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        // Anti-téléportation / respawn
        if (dist <= 0 || dist > 10.0) {
            return;
        }

        UUID uuid = player.getUniqueId();
        double current = distanceBuffer.merge(uuid, dist, Double::sum);

        // Dispatcher par palier de 10 blocs pour une performance optimale
        if (current >= 10.0) {
            int blocks = (int) Math.floor(current);
            distanceBuffer.put(uuid, current - blocks);
            dispatchDistance(player, blocks);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Double remaining = distanceBuffer.remove(uuid);
        if (remaining != null && remaining >= 1.0) {
            dispatchDistance(event.getPlayer(), (int) Math.floor(remaining));
        }
    }

    private void dispatchDistance(Player player, int blocks) {
        if (blocks <= 0) return;
        plugin.getProfileCache().getProfile(player.getUniqueId()).ifPresent(profile -> {
            plugin.getQuotaService().getProgressTracker().incrementProgress(profile, plugin.getQuotaService().getQuotaConfig(), getResourceName(), blocks);
            if (plugin.getRushManager() != null) {
                plugin.getRushManager().handleResourceGain(player.getUniqueId(), getResourceName(), blocks, Instant.now());
            }
        });
    }
}
