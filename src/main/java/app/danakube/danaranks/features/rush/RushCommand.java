package app.danakube.danaranks.features.rush;

import app.danakube.danaranks.core.DanaRanks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RushCommand extends Command {

    private final DanaRanks plugin;
    private final RushManager rushManager;

    public RushCommand(DanaRanks plugin, RushManager rushManager) {
        super("rush");
        this.plugin = plugin;
        this.rushManager = rushManager;
        setAliases(List.of("ranksrush", "dailyrush"));
        setDescription("Commande principale pour le Rush quotidien.");
        setPermission("danaranks.command.rush");
    }

    private Component getMessage(String key, String defaultValue) {
        return getMessage(key, defaultValue, Collections.emptyMap(), null);
    }


    private Component getMessage(String key, String defaultValue, Player player) {
        return getMessage(key, defaultValue, Collections.emptyMap(), player);
    }

    private Component getMessage(String key, String defaultValue, Map<String, String> placeholders, Player player) {
        if (plugin != null && plugin.getMessageManager() != null) {
            return plugin.getMessageManager().getMessageComponentForPlayer(key, defaultValue, placeholders, player);
        }
        String raw = defaultValue;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace(entry.getKey(), entry.getValue());
        }
        return MiniMessage.miniMessage().deserialize(raw);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMessage("rush-player-only", "<red>Seuls les joueurs peuvent utiliser cette commande.</red>"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "join":
                handleJoin(player);
                break;
            case "leave":
                handleLeave(player);
                break;
            case "status":
                handleStatus(player);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void handleLeave(Player player) {
        boolean success = rushManager.unregisterPlayer(player.getUniqueId());
        if (success) {
            player.sendMessage(getMessage("rush-left", "<green>[Rush] Vous avez quitté le Rush en cours. Vos points ont été réinitialisés.</green>", player));
        } else {
            player.sendMessage(getMessage("rush-leave-failed", "<red>[Rush] Vous n'êtes pas inscrit au Rush actuel.</red>", player));
        }
    }

    private void handleJoin(Player player) {
        if (!rushManager.isDailyPlanned()) {
            player.sendMessage(getMessage("rush-no-active", "<red>[Rush] Aucun événement de Rush n'est planifié pour le moment.</red>", player));
            return;
        }

        Instant now = Instant.now();
        boolean success = rushManager.registerPlayer(player.getUniqueId(), now);

        if (success) {
            LocalDateTime startTime = rushManager.getStartTime();
            int duration = rushManager.getDurationMinutes();
            String resource = rushManager.getDailyResource();

            String formattedStart = "";
            String delayStr = "";

            if (startTime != null) {
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                formattedStart = startTime.format(timeFormatter);

                Instant startInstant = startTime.atZone(ZoneId.systemDefault()).toInstant();
                if (now.isBefore(startInstant)) {
                    long diffMinutes = (startInstant.getEpochSecond() - now.getEpochSecond()) / 60;
                    if (diffMinutes < 1) {
                        long diffSeconds = startInstant.getEpochSecond() - now.getEpochSecond();
                        delayStr = diffSeconds + "s";
                    } else {
                        delayStr = diffMinutes + " min";
                    }
                } else {
                    if (plugin.getMessageManager() != null) {
                        delayStr = plugin.getMessageManager().getRawMessage("rush-join-already-started", "Déjà en cours");
                    } else {
                        delayStr = "Déjà en cours";
                    }
                }
            }

            player.sendMessage(getMessage("rush-joined",
                    "<green>[Rush] Inscription réussie ! Votre score commence à 0. Bonne chance !</green>",
                    Map.of(
                        "%time%", formattedStart,
                        "%delay%", delayStr,
                        "%duration%", String.valueOf(duration),
                        "%resource%", plugin.getResourceDisplayName(resource)
                    ), player));
        } else {
            if (rushManager.getPlayerScore(player.getUniqueId()) >= 0.0 && rushManager.getRegisteredPlayersCount() > 0) {
                player.sendMessage(getMessage("rush-already-joined", "<yellow>[Rush] Vous êtes déjà inscrit à l'événement du jour !</yellow>", player));
            } else {
                player.sendMessage(getMessage("rush-join-failed", "<red>[Rush] Impossible de s'inscrire. L'événement est peut-être terminé ou non démarré.</red>", player));
            }
        }
    }

    private void handleStatus(Player player) {
        if (!rushManager.isDailyPlanned()) {
            player.sendMessage(getMessage("rush-status-no-active", "<blue>[Rush] Statut : Aucun Rush planifié pour aujourd'hui.</blue>", player));
            return;
        }

        String resource = rushManager.getDailyResource();
        LocalDateTime startTime = rushManager.getStartTime();
        int duration = rushManager.getDurationMinutes();

        Instant now = Instant.now();
        Instant startInstant = startTime.atZone(ZoneId.systemDefault()).toInstant();
        Instant endInstant = startInstant.plusSeconds(duration * 60L);

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String formattedStart = startTime.format(timeFormatter);

        if (now.isBefore(startInstant)) {
            player.sendMessage(getMessage("rush-status-announced", 
                    "<blue>[Rush] Le Rush (Ressource: <gold>%resource%</gold>) commencera à <yellow>%time%</yellow> pour une durée de <yellow>%duration%</yellow> minutes.\nTapez <green>/rush join</green> pour participer !</blue>",
                    Map.of("%resource%", plugin.getResourceDisplayName(resource), "%time%", formattedStart, "%duration%", String.valueOf(duration)), player));
        } else if (now.isAfter(endInstant)) {
            player.sendMessage(getMessage("rush-status-finished", "<blue>[Rush] L'événement d'aujourd'hui est terminé !</blue>", player));
        } else {
            double score = rushManager.getPlayerScore(player.getUniqueId());
            boolean isRegistered = score >= 0.0 && rushManager.getRegisteredPlayersCount() > 0;
            
            long remainingMinutes = (endInstant.getEpochSecond() - now.getEpochSecond()) / 60;
            if (remainingMinutes < 1) remainingMinutes = 1;

            if (isRegistered) {
                player.sendMessage(getMessage("rush-status-active-registered", 
                        "<blue>[Rush] L'événement est en cours ! Temps restant : <yellow>%time%</yellow> minutes.\nVotre Score actuel : <gold>%score%</gold></blue>",
                        Map.of(
                            "%time%", String.valueOf(remainingMinutes),
                            "%score%", String.format("%.0f", score),
                            "%resource%", plugin.getResourceDisplayName(resource)
                        ), player));
            } else {
                player.sendMessage(getMessage("rush-status-active-unregistered", 
                        "<blue>[Rush] L'événement est en cours ! Temps restant : <yellow>%time%</yellow> minutes.\nVous n'êtes pas inscrit ! Tapez <green>/rush join</green> pour commencer à marquer des points !</blue>",
                        Map.of(
                            "%time%", String.valueOf(remainingMinutes),
                            "%resource%", plugin.getResourceDisplayName(resource)
                        ), player));
            }
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(getMessage("rush-help", 
                "<blue>[Rush] Commandes disponibles :\n" +
                " - <yellow>/rush join</yellow> : S'inscrire au Rush quotidien.\n" +
                " - <yellow>/rush leave</yellow> : Quitter le Rush en cours.\n" +
                " - <yellow>/rush status</yellow> : Voir le statut du Rush du jour.</blue>", player));
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("join");
            list.add("leave");
            list.add("status");
            
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();
            for (String s : list) {
                if (s.startsWith(input)) {
                    completions.add(s);
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
