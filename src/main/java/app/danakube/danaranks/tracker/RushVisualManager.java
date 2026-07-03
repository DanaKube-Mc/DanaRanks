package app.danakube.danaranks.tracker;

import app.danakube.danaranks.DanaRanks;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RushVisualManager {

    private final DanaRanks plugin;
    
    // Boss bar d'annonce globale
    private BossBar announceBar;
    private String announceTitleTemplate;
    private BossBar.Color announceColor;
    private BossBar.Overlay announceOverlay;

    // Boss bars actives pour chaque joueur inscrit
    private final Map<UUID, BossBar> activeBars = new HashMap<>();
    private String activeTitleTemplate;
    private BossBar.Color activeColor;
    private BossBar.Overlay activeOverlay;

    public RushVisualManager(DanaRanks plugin) {
        this.plugin = plugin;
    }

    public void loadConfig(FileConfiguration config) {
        // Annonce
        String announceColStr = config.getString("rush.bossbar.announce.color", "BLUE");
        String announceStyleStr = config.getString("rush.bossbar.announce.style", "PROGRESS");
        this.announceTitleTemplate = config.getString("rush.bossbar.announce.title", "<blue>[Rush] Début du Rush dans %time% (Ressource: %resource%) - Tapez /rush join !");
        this.announceColor = parseColor(announceColStr);
        this.announceOverlay = parseOverlay(announceStyleStr);

        // Active
        String activeColStr = config.getString("rush.bossbar.active.color", "RED");
        String activeStyleStr = config.getString("rush.bossbar.active.style", "PROGRESS");
        this.activeTitleTemplate = config.getString("rush.bossbar.active.title", "<red>[Rush] Temps restant : %time% - Votre Score : <gold>%score%</gold>");
        this.activeColor = parseColor(activeColStr);
        this.activeOverlay = parseOverlay(activeStyleStr);
    }

    private BossBar.Color parseColor(String colorStr) {
        try {
            return BossBar.Color.valueOf(colorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BossBar.Color.BLUE;
        }
    }

    private BossBar.Overlay parseOverlay(String styleStr) {
        try {
            return BossBar.Overlay.valueOf(styleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    public void showAnnounceBar(String timeStr, String resourceName) {
        if (announceBar == null) {
            Component title = MiniMessage.miniMessage().deserialize(
                    announceTitleTemplate.replace("%time%", timeStr).replace("%resource%", resourceName)
            );
            announceBar = BossBar.bossBar(title, 1.0f, announceColor, announceOverlay);
        } else {
            Component title = MiniMessage.miniMessage().deserialize(
                    announceTitleTemplate.replace("%time%", timeStr).replace("%resource%", resourceName)
            );
            announceBar.name(title);
        }

        // Montrer à tous les joueurs en ligne
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(announceBar);
        }
    }

    public void hideAnnounceBar() {
        if (announceBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(announceBar);
            }
            announceBar = null;
        }
    }

    public void showOrUpdateActiveBar(Player player, String timeStr, double score, float progress) {
        UUID uuid = player.getUniqueId();
        BossBar bar = activeBars.get(uuid);
        
        String formattedScore = String.format("%.0f", score);
        Component title = MiniMessage.miniMessage().deserialize(
                activeTitleTemplate.replace("%time%", timeStr).replace("%score%", formattedScore)
        );

        float safeProgress = Math.max(0.0f, Math.min(1.0f, progress));

        if (bar == null) {
            bar = BossBar.bossBar(title, safeProgress, activeColor, activeOverlay);
            activeBars.put(uuid, bar);
            player.showBossBar(bar);
        } else {
            bar.name(title);
            bar.progress(safeProgress);
            player.showBossBar(bar); // S'assurer qu'elle s'affiche s'il vient de se reconnecter
        }
    }

    public void removePlayerActiveBar(Player player) {
        BossBar bar = activeBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void clearAllActiveBars() {
        for (Map.Entry<UUID, BossBar> entry : activeBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        activeBars.clear();
    }
}
