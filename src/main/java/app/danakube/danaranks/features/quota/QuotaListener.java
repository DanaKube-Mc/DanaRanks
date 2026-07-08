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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import app.danakube.danaranks.features.rush.RushManager;

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
                player.sendMessage(plugin.getMessageManager().getMessageComponent("quota-pending-summary",
                        "<blue>[Quotas] Vos quotas ont expiré pendant votre absence. Variation d'ELO : <%color%>%sign%%change%</%color%></blue>",
                        Map.of("%color%", eloColor, "%sign%", eloSign, "%change%", summary)));
            }

            // 2. Affichage des quotas actuels et de leur progression
            try {
                int activeRank = plugin.getQuotaService().getProgressTracker().getActiveQuotaRank(profile);
                Map<String, ObjectiveConfig> objectives = plugin.getQuotaService().getProgressTracker().getActiveObjectives(profile);
                player.sendMessage(plugin.getMessageManager().getMessageComponent("quota-join-header",
                        "<aqua><b>[Quotas] Vos objectifs actuels (Rang %rank%) :</b></aqua>", Map.of("%rank%", plugin.getRankDisplayName(activeRank))));
                for (ObjectiveConfig obj : objectives.values()) {
                    double progress = plugin.getQuotaService().getProgressTracker().getProgress(profile, obj.name());
                    player.sendMessage(plugin.getMessageManager().getMessageComponent("quota-join-objective-line",
                            " - <yellow>%resource% : %progress% / %target%</yellow>",
                            Map.of("%resource%", obj.name(), "%progress%", String.format("%.0f", progress), "%target%", String.format("%.0f", obj.target()))));
                }
            } catch (Exception e) {
                // Ignore safe fallback
            }

            // 3. Annonce si un Rush quotidien est planifié aujourd'hui
            try {
                RushManager rm = plugin.getRushManager();
                if (rm != null && rm.isDailyPlanned()) {
                    LocalDateTime start = rm.getStartTime();
                    if (start != null) {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
                        String timeStr = start.format(formatter);
                        player.sendMessage(plugin.getMessageManager().getMessageComponent("quota-rush-planned-announcement",
                                "<gold>[Rush] Un Rush quotidien est planifié aujourd'hui à <yellow>%time%</yellow> ! Ne le manquez pas !</gold>",
                                Map.of("%time%", timeStr)));
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
