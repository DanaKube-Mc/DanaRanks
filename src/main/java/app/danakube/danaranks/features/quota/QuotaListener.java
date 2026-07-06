package app.danakube.danaranks.features.quota;

import app.danakube.danaranks.core.DanaRanks;
import app.danakube.danaranks.core.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

public class QuotaListener implements Listener {
    private final DanaRanks plugin;

    public QuotaListener(DanaRanks plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        try {
            Optional<PlayerProfile> profileOpt = plugin.getProfileRepository().loadProfile(uuid, name).join();
            PlayerProfile profile = profileOpt.orElseGet(() -> new PlayerProfile(uuid, name));
            
            QuotaService qs = plugin.getQuotaService();
            if (qs != null) {
                qs.handleOfflineCatchUp(profile, Instant.now());
            }
            plugin.getProfileCache().putProfile(profile);
        } catch (CompletionException | NullPointerException e) {
            plugin.getLogger().severe("Failed to load player profile for " + name + " (" + uuid + "): " + e.getMessage());
            plugin.getProfileCache().removeProfile(uuid);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Optional<PlayerProfile> profileOpt = plugin.getProfileCache().getProfile(uuid);
        if (profileOpt.isEmpty()) {
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
        Optional<PlayerProfile> profileOpt = plugin.getProfileCache().removeProfile(uuid);
        profileOpt.ifPresent(profile -> {
            plugin.getProfileRepository().saveProfile(profile).exceptionally(ex -> {
                plugin.getLogger().severe("Failed to save profile for " + uuid + " on quit: " + ex.getMessage());
                return null;
            });
        });
    }
}
