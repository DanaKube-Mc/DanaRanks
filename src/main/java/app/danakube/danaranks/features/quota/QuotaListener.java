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
            PlayerProfile profile = profileOpt.get();
            Component loadedMessage = plugin.getMessageManager().getMessageComponent(
                    "profile-loaded",
                    "&aVotre profil de rang a été correctement chargé !"
            );
            player.sendMessage(loadedMessage);

            // 1. Résumé ELO du reset quota hors-ligne
            if (profile.getQuotaProgress().containsKey("quota_pending_summary")) {
                String summary = (String) profile.getQuotaProgress().remove("quota_pending_summary");
                boolean isGain = !summary.startsWith("-");
                String eloColor = isGain ? "green" : "red";
                String eloSign = isGain ? "+" : "";
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        String.format("<blue>[Quotas] Vos quotas ont expiré pendant votre absence. Variation d'ELO : <%s>%s%s</%s></blue>",
                                eloColor, eloSign, summary, eloColor)
                ));
            }

            // 2. Affichage des quotas actuels et de leur progression
            try {
                int activeRank = plugin.getQuotaService().getProgressTracker().getActiveQuotaRank(profile);
                java.util.Map<String, ObjectiveConfig> objectives = QuotaConfigLoader.getObjectivesForRank(plugin.getQuotaService().getQuotaConfig(), activeRank);
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<aqua><b>[Quotas] Vos objectifs actuels (Rang " + activeRank + ") :</b></aqua>"
                ));
                for (ObjectiveConfig obj : objectives.values()) {
                    double progress = plugin.getQuotaService().getProgressTracker().getProgress(profile, obj.name());
                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                            String.format(" - <yellow>%s : %.0f / %.0f</yellow>", obj.name(), progress, obj.target())
                    ));
                }
            } catch (Exception e) {
                // Ignore safe fallback
            }

            // 3. Annonce si un Rush quotidien est planifié aujourd'hui
            try {
                app.danakube.danaranks.features.rush.RushManager rm = plugin.getRushManager();
                if (rm != null && rm.isDailyPlanned()) {
                    java.time.LocalDateTime start = rm.getStartTime();
                    if (start != null) {
                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
                        String timeStr = start.format(formatter);
                        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                                String.format("<gold>[Rush] Un Rush quotidien est planifié aujourd'hui à <yellow>%s</yellow> ! Ne le manquez pas !</gold>", timeStr)
                        ));
                    }
                }
            } catch (Exception e) {
                // Ignore safe fallback
            }
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
