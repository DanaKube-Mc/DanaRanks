package app.danakube.danaranks.admin;

import app.danakube.danaranks.core.DanaRanks;
import app.danakube.danaranks.core.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class AdminCommandExecutor implements CommandExecutor {
    private final DanaRanks plugin;

    public AdminCommandExecutor(DanaRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("danaranks.admin")) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("no-permission", "<red>Vous n'avez pas la permission d'exécuter cette commande."));
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("admin")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[1].toLowerCase();

        switch (sub) {
            case "setrank":
                handleSetRank(sender, args);
                break;
            case "setelo":
                handleSetElo(sender, args);
                break;
            case "addelo":
                handleAddElo(sender, args);
                break;
            case "removeelo":
                handleRemoveElo(sender, args);
                break;
            case "resetquota":
                handleResetQuota(sender, args);
                break;
            case "forcequota":
                handleForceQuota(sender, args);
                break;
            case "rush":
                handleRush(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void getProfileOrLoad(String targetName, Consumer<PlayerProfile> callback, CommandSender sender) {
        Player onlinePlayer = Bukkit.getPlayer(targetName);
        if (onlinePlayer != null) {
            Optional<PlayerProfile> profileOpt = plugin.getProfileCache().getProfile(onlinePlayer.getUniqueId());
            if (profileOpt.isPresent()) {
                callback.accept(profileOpt.get());
                return;
            }
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
        UUID uuid = offlinePlayer.getUniqueId();
        plugin.getProfileRepository().loadProfile(uuid, targetName).thenAccept(opt -> {
            PlayerProfile profile = opt.orElseGet(() -> new PlayerProfile(uuid, targetName));
            callback.accept(profile);
            plugin.getProfileRepository().saveProfile(profile).exceptionally(ex -> {
                sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-db-save-error",
                        "<red>Erreur lors de la sauvegarde du profil hors ligne : %error%</red>", Map.of("%error%", ex.getMessage())));
                return null;
            });
        }).exceptionally(ex -> {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-db-load-error",
                    "<red>Impossible de charger les données du joueur hors ligne : %error%</red>", Map.of("%error%", ex.getMessage())));
            return null;
        });
    }

    private void handleSetRank(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-usage-setrank", "<red>Usage: /danaranks admin setrank <joueur> <niveau></red>"));
            return;
        }

        String targetName = args[2];
        int newRank;
        try {
            newRank = Integer.parseInt(args[3]);
            if (newRank < 1 || newRank > 50) {
                sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-rank-out-of-bounds", "<red>Le niveau doit être compris entre 1 et 50.</red>"));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-rank-invalid-int", "<red>Le niveau doit être un nombre entier.</red>"));
            return;
        }

        getProfileOrLoad(targetName, profile -> {
            int oldRank = profile.getRankLevel();
            profile.setRankLevel(newRank);

            int diff = newRank - oldRank;
            if (diff > 0) {
                if (plugin.getPermissionHook() != null) {
                    plugin.getPermissionHook().promote(profile.getUuid(), diff);
                }
            } else if (diff < 0) {
                if (plugin.getPermissionHook() != null) {
                    plugin.getPermissionHook().demote(profile.getUuid(), -diff);
                }
            }

            if (diff != 0 && plugin.getHistoryRepository() != null) {
                plugin.getHistoryRepository().logHistory(profile.getUuid(), "RANK_CHANGE", 0, profile.getElo(), oldRank + " -> " + newRank);
            }

            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-rank-set-success",
                    "<green>Grade de %player% défini à %newrank% (anciennement %oldrank%).</green>",
                    Map.of("%player%", targetName, "%newrank%", String.valueOf(newRank), "%oldrank%", String.valueOf(oldRank))));
        }, sender);
    }

    private void handleSetElo(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-usage-setelo", "<red>Usage: /danaranks admin setelo <joueur> <valeur></red>"));
            return;
        }

        String targetName = args[2];
        int targetElo;
        try {
            targetElo = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-elo-invalid-int", "<red>La valeur ELO doit être un nombre entier.</red>"));
            return;
        }

        getProfileOrLoad(targetName, profile -> {
            int oldCumulative = (profile.getRankLevel() - 1) * 100 + profile.getElo();
            int newCumulative = (profile.getRankLevel() - 1) * 100 + targetElo;
            int diff = newCumulative - oldCumulative;

            plugin.getEloService().addElo(profile, diff, "Admin setelo", true);
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-elo-set-success",
                    "<green>ELO de %player% défini à %elo%.</green>", Map.of("%player%", targetName, "%elo%", String.valueOf(targetElo))));
        }, sender);
    }

    private void handleAddElo(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-usage-addelo", "<red>Usage: /danaranks admin addelo <joueur> <valeur></red>"));
            return;
        }

        String targetName = args[2];
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
            if (amount < 0) {
                sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-elo-value-positive", "<red>La valeur doit être positive.</red>"));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-value-invalid-int", "<red>La valeur doit être un nombre entier.</red>"));
            return;
        }

        getProfileOrLoad(targetName, profile -> {
            plugin.getEloService().addElo(profile, amount, "Admin addelo", true);
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-elo-add-success",
                    "<green>Ajouté %amount% ELO à %player%.</green>", Map.of("%amount%", String.valueOf(amount), "%player%", targetName)));
        }, sender);
    }

    private void handleRemoveElo(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-usage-removeelo", "<red>Usage: /danaranks admin removeelo <joueur> <valeur></red>"));
            return;
        }

        String targetName = args[2];
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
            if (amount < 0) {
                sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-elo-value-positive", "<red>La valeur doit être positive.</red>"));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-value-invalid-int", "<red>La valeur doit être un nombre entier.</red>"));
            return;
        }

        getProfileOrLoad(targetName, profile -> {
            plugin.getEloService().addElo(profile, -amount, "Admin removeelo", true);
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-elo-remove-success",
                    "<red>Retiré %amount% ELO à %player%.</red>", Map.of("%amount%", String.valueOf(amount), "%player%", targetName)));
        }, sender);
    }

    private void handleResetQuota(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-usage-resetquota", "<red>Usage: /danaranks admin resetquota <joueur></red>"));
            return;
        }

        String targetName = args[2];
        getProfileOrLoad(targetName, profile -> {
            int activeRank = plugin.getQuotaService().getProgressTracker().getActiveQuotaRank(profile);
            plugin.getQuotaService().getProgressTracker().resetQuotaProgress(profile, activeRank);
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-quota-reset-success",
                    "<green>Quotas de %player% réinitialisés pour le rang actif %rank%.</green>", Map.of("%player%", targetName, "%rank%", plugin.getRankDisplayName(activeRank))));
        }, sender);
    }

    private void handleForceQuota(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-usage-forcequota", "<red>Usage: /danaranks admin forcequota <joueur></red>"));
            return;
        }

        String targetName = args[2];
        getProfileOrLoad(targetName, profile -> {
            plugin.getQuotaService().processGlobalReset(profile, Instant.now());
            plugin.getProfileRepository().saveProfile(profile).thenRun(() -> {
                sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-quota-forced-success",
                        "<green>Cycle de quota pour %player% terminé et réinitialisé avec succès.</green>", Map.of("%player%", targetName)));
            }).exceptionally(ex -> {
                sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-db-save-error",
                        "<red>Erreur lors de la sauvegarde : %error%</red>", Map.of("%error%", ex.getMessage())));
                return null;
            });
        }, sender);
    }

    private void handleRush(CommandSender sender, String[] args) {
        Player pSender = (sender instanceof Player p) ? p : null;
        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponentForPlayer("admin-usage-rush", "<red>Usage: /danaranks admin rush <start|stop|end|info|add|leave|reload></red>", pSender));
            return;
        }

        String subRush = args[2].toLowerCase();

        switch (subRush) {
            case "start":
                if (args.length < 5) {
                    sender.sendMessage(plugin.getMessageManager().getMessageComponentForPlayer("admin-usage-rush-start", "<red>Usage: /danaranks admin rush start <ressource> <durée> [délai_lancement]</red>", pSender));
                    return;
                }
                String res = args[3];
                int duration;
                try {
                    duration = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getMessageManager().getMessageComponentForPlayer("admin-rush-duration-invalid", "<red>La durée doit être un entier (en minutes).</red>", pSender));
                    return;
                }

                int delay = 0;
                if (args.length >= 6) {
                    delay = parseDelayMinutes(args[5]);
                    if (delay < 0) {
                        sender.sendMessage(plugin.getMessageManager().getMessageComponentForPlayer("admin-rush-delay-invalid",
                                "<red>Délai invalide. Formats acceptés: minutes (ex: 5), heures (ex: 1h), secondes (ex: 30s).</red>", pSender));
                        return;
                    }
                }

                if (delay > 0) {
                    plugin.getRushManager().forceScheduleRush(res, duration, delay);
                    sender.sendMessage(plugin.getMessageManager().getMessageComponentForPlayer("admin-rush-planned-confirm",
                            "<green>Rush planifié sur la ressource %resource% (durée: %duration%m) dans %delay% minutes.</green>",
                            Map.of("%resource%", plugin.getResourceDisplayName(res), "%duration%", String.valueOf(duration), "%delay%", String.valueOf(delay)), pSender));
                } else {
                    plugin.getRushManager().forceStartRush(res, duration);
                    sender.sendMessage(plugin.getMessageManager().getMessageComponentForPlayer("admin-rush-started-confirm",
                            "<green>Rush forcé démarré sur la ressource %resource% pendant %duration% minutes.</green>",
                            Map.of(
                                "%resource%", plugin.getResourceDisplayName(res),
                                "%duration%", String.valueOf(duration),
                                "%time%", String.valueOf(duration)
                            ), pSender));
                }
                break;
            case "stop":
                plugin.getRushManager().forceStopRush();
                sender.sendMessage(plugin.getMessageManager().getMessageComponentForPlayer("admin-rush-stopped-confirm", "<green>Rush arrêté de force (sans distribution).</green>", pSender));
                break;
            case "end":
                plugin.getRushManager().endRush(Instant.now());
                sender.sendMessage(plugin.getMessageManager().getMessageComponentForPlayer("admin-rush-ended-confirm", "<green>Rush terminé normalement (distribution ELO et résumé lancés).</green>", pSender));
                break;
            case "info":
                plugin.getRushManager().printRushInfo(sender);
                break;
            case "add":
                if (args.length < 4) {
                    sender.sendMessage(plugin.getMessageManager().getMessageComponentForPlayer("admin-usage-rush-add", "<red>Usage: /danaranks admin rush add <joueur></red>", pSender));
                    return;
                }
                Player playerToAdd = Bukkit.getPlayer(args[3]);
                if (playerToAdd == null) {
                    sender.sendMessage(plugin.getMessageManager().getMessageComponentForPlayer("admin-player-not-found", "<red>Joueur introuvable ou hors-ligne.</red>", pSender));
                    return;
                }
                boolean addSuccess = plugin.getRushManager().registerPlayer(playerToAdd.getUniqueId(), Instant.now());
                if (addSuccess) {
                    sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-rush-player-added",
                            "<green>Joueur %player% ajouté de force au Rush.</green>", Map.of("%player%", playerToAdd.getName())));
                } else {
                    sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-rush-player-add-failed",
                            "<red>Impossible d'ajouter le joueur (déjà inscrit ou Rush non planifié).</red>"));
                }
                break;
            case "leave":
                if (args.length < 4) {
                    sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-usage-rush-leave", "<red>Usage: /danaranks admin rush leave <joueur></red>"));
                    return;
                }
                OfflinePlayer playerToLeave = Bukkit.getOfflinePlayer(args[3]);
                boolean leaveSuccess = plugin.getRushManager().unregisterPlayer(playerToLeave.getUniqueId());
                if (leaveSuccess) {
                    sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-rush-player-removed",
                            "<green>Joueur %player% retiré de force du Rush.</green>", Map.of("%player%", playerToLeave.getName() != null ? playerToLeave.getName() : args[3])));
                } else {
                    sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-rush-player-remove-failed",
                            "<red>Impossible de retirer le joueur (non inscrit).</red>"));
                }
                break;
            case "reload":
                plugin.getRushManager().reloadRushPlan();
                sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-rush-reload-success", "<green>Planification du Rush rechargée.</green>"));
                break;
            default:
                sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-usage-rush", "<red>Usage: /danaranks admin rush <start|stop|end|info|add|leave|reload></red>"));
                break;
        }
    }

    private int parseDelayMinutes(String input) {
        try {
            if (input.endsWith("m")) {
                return Integer.parseInt(input.substring(0, input.length() - 1));
            } else if (input.endsWith("h")) {
                return Integer.parseInt(input.substring(0, input.length() - 1)) * 60;
            } else if (input.endsWith("s")) {
                int secs = Integer.parseInt(input.substring(0, input.length() - 1));
                return Math.max(1, secs / 60);
            } else {
                return Integer.parseInt(input);
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.reloadGuiConfig();
        plugin.getMessageManager().loadMessages();
        sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-reload-success", "<green>Configuration, GUIs et Langues de DanaRanks correctement rechargées !</green>"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessageComponent("admin-help",
                "<blue>--- Commandes Administrateur DanaRanks ---</blue>\n" +
                " - <white>/danaranks admin setrank <joueur> <niveau></white>\n" +
                " - <white>/danaranks admin setelo <joueur> <valeur></white>\n" +
                " - <white>/danaranks admin addelo <joueur> <valeur></white>\n" +
                " - <white>/danaranks admin removeelo <joueur> <valeur></white>\n" +
                " - <white>/danaranks admin resetquota <joueur></white>\n" +
                " - <white>/danaranks admin forcequota <joueur></white>\n" +
                " - <white>/danaranks admin rush start <ressource> <durée> [délai]</white>\n" +
                " - <white>/danaranks admin rush stop</white>\n" +
                " - <white>/danaranks admin rush end</white>\n" +
                " - <white>/danaranks admin rush info</white>\n" +
                " - <white>/danaranks admin rush add <joueur></white>\n" +
                " - <white>/danaranks admin rush leave <joueur></white>\n" +
                " - <white>/danaranks admin rush reload</white>\n" +
                " - <white>/danaranks admin reload</white>"));
    }
}
