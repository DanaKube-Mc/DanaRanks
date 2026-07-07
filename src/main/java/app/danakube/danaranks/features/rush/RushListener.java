package app.danakube.danaranks.features.rush;

import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.time.ZoneId;

public class RushListener implements Listener {
    private final DanaRanks plugin;
    private final RushManager rushManager;

    public RushListener(DanaRanks plugin, RushManager rushManager) {
        this.plugin = plugin;
        this.rushManager = rushManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (rushManager == null) return;

        plugin.getProfileCache().getProfile(player.getUniqueId()).ifPresent(profile -> {
            rushManager.checkOfflineSummary(profile, msg -> 
                player.sendMessage(MiniMessage.miniMessage().deserialize(msg))
            );
        });

        Instant now = Instant.now();
        RushEventState state = rushManager.getState();
        if (state.isDailyPlanned() && state.getStartTime() != null) {
            Instant startInstant = state.getStartTime().atZone(ZoneId.systemDefault()).toInstant();
            Instant endInstant = startInstant.plusSeconds(state.getDurationMinutes() * 60L);
            Instant preAnnounceInstant = startInstant.minusSeconds(rushManager.getPreAnnounceMinutes() * 60L);

            if (now.isAfter(endInstant)) {
                return;
            }

            if (!now.isBefore(startInstant) && now.isBefore(endInstant)) {
                if (state.getRegisteredScores().containsKey(player.getUniqueId())) {
                    long totalSecs = state.getDurationMinutes() * 60L;
                    long elapsedSecs = now.getEpochSecond() - startInstant.getEpochSecond();
                    long remainingSecs = totalSecs - elapsedSecs;
                    float progress = (float) remainingSecs / totalSecs;
                    double score = state.getRegisteredScores().getOrDefault(player.getUniqueId(), 0.0);
                    rushManager.getVisualManager().showOrUpdateActiveBar(player, rushManager.formatTime(remainingSecs), score, progress);
                }
            } else if (!now.isBefore(preAnnounceInstant) && now.isBefore(startInstant)) {
                long remainingSecs = startInstant.getEpochSecond() - now.getEpochSecond();
                rushManager.getVisualManager().showAnnounceBar(rushManager.formatTime(remainingSecs), state.getDailyResource());
            }
        }
    }
}
