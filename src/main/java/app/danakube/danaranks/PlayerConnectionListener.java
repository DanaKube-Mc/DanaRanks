package app.danakube.danaranks;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;

public class PlayerConnectionListener implements Listener {
    private final DanaRanks plugin;

    public PlayerConnectionListener(DanaRanks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        try {
            PlayerProfile profile = plugin.getDatabaseManager().loadProfile(uuid, name).join();
            if (profile != null) {
                QuotaManager qm = QuotaManager.getInstance();
                if (qm != null) {
                    qm.handleOfflineCatchUp(profile, Instant.now());
                }
                plugin.getProfileCache().put(uuid, profile);
            }
        } catch (CompletionException | NullPointerException e) {
            plugin.getLogger().severe("Failed to load player profile for " + name + " (" + uuid + "): " + e.getMessage());
            plugin.getProfileCache().remove(uuid);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        PlayerProfile profile = plugin.getProfileCache().get(uuid);
        if (profile == null) {
            Component kickMessage = plugin.getMessageManager().getMessageComponent(
                    "kick-database-error",
                    "&c[DanaRanks] Impossible de charger vos données de rang. Veuillez vous reconnecter."
            );
            player.kick(kickMessage);
        } else {
            Component loadedMessage = plugin.getMessageManager().getMessageComponent(
                    "profile-loaded",
                    "&aVotre profil de rang a été correctement chargé !"
            );
            player.sendMessage(loadedMessage);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerProfile profile = plugin.getProfileCache().remove(uuid);
        if (profile != null) {
            plugin.getDatabaseManager().saveProfile(profile).exceptionally(ex -> {
                plugin.getLogger().severe("Failed to save profile for " + uuid + " on quit: " + ex.getMessage());
                return null;
            });
        }
    }
}
