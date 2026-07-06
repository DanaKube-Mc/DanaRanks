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
            sender.sendMessage("§cVous n'avez pas la permission d'exécuter cette commande.");
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

        // Hors ligne
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
        UUID uuid = offlinePlayer.getUniqueId();
        plugin.getProfileRepository().loadProfile(uuid, targetName).thenAccept(opt -> {
            PlayerProfile profile = opt.orElseGet(() -> new PlayerProfile(uuid, targetName));
            callback.accept(profile);
            plugin.getProfileRepository().saveProfile(profile).exceptionally(ex -> {
                sender.sendMessage("§cErreur lors de la sauvegarde du profil hors ligne : " + ex.getMessage());
                return null;
            });
        }).exceptionally(ex -> {
            sender.sendMessage("§cImpossible de charger les données du joueur hors ligne : " + ex.getMessage());
            return null;
        });
    }

    private void handleSetRank(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /danaranks admin setrank <joueur> <niveau>");
            return;
        }

        String targetName = args[2];
        int newRank;
        try {
            newRank = Integer.parseInt(args[3]);
            if (newRank < 1 || newRank > 50) {
                sender.sendMessage("§cLe niveau doit être compris entre 1 et 50.");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLe niveau doit être un nombre entier.");
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

            sender.sendMessage("§aGrade de " + targetName + " défini à " + newRank + " (anciennement " + oldRank + ").");
        }, sender);
    }

    private void handleSetElo(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /danaranks admin setelo <joueur> <valeur>");
            return;
        }

        String targetName = args[2];
        int targetElo;
        try {
            targetElo = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLa valeur ELO doit être un nombre entier.");
            return;
        }

        getProfileOrLoad(targetName, profile -> {
            int oldCumulative = (profile.getRankLevel() - 1) * 100 + profile.getElo();
            int newCumulative = (profile.getRankLevel() - 1) * 100 + targetElo;
            int diff = newCumulative - oldCumulative;

            plugin.getEloService().addElo(profile, diff, "Admin setelo", true);
            sender.sendMessage("§aELO de " + targetName + " défini à " + targetElo + ".");
        }, sender);
    }

    private void handleAddElo(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /danaranks admin addelo <joueur> <valeur>");
            return;
        }

        String targetName = args[2];
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
            if (amount < 0) {
                sender.sendMessage("§cLa valeur doit être positive.");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLa valeur doit être un nombre entier.");
            return;
        }

        getProfileOrLoad(targetName, profile -> {
            plugin.getEloService().addElo(profile, amount, "Admin addelo", true);
            sender.sendMessage("§aAjouté " + amount + " ELO à " + targetName + ".");
        }, sender);
    }

    private void handleRemoveElo(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /danaranks admin removeelo <joueur> <valeur>");
            return;
        }

        String targetName = args[2];
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
            if (amount < 0) {
                sender.sendMessage("§cLa valeur doit être positive.");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLa valeur doit être un nombre entier.");
            return;
        }

        getProfileOrLoad(targetName, profile -> {
            plugin.getEloService().addElo(profile, -amount, "Admin removeelo", true);
            sender.sendMessage("§cRetiré " + amount + " ELO à " + targetName + ".");
        }, sender);
    }

    private void handleResetQuota(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /danaranks admin resetquota <joueur>");
            return;
        }

        String targetName = args[2];
        getProfileOrLoad(targetName, profile -> {
            int activeRank = plugin.getQuotaService().getProgressTracker().getActiveQuotaRank(profile);
            plugin.getQuotaService().getProgressTracker().resetQuotaProgress(profile, activeRank);
            sender.sendMessage("§aQuotas de " + targetName + " réinitialisés pour le rang actif " + activeRank + ".");
        }, sender);
    }

    private void handleRush(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /danaranks admin rush <start|stop|reload>");
            return;
        }

        String subRush = args[2].toLowerCase();

        switch (subRush) {
            case "start":
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /danaranks admin rush start <ressource> <durée>");
                    return;
                }
                String res = args[3];
                int duration;
                try {
                    duration = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cLa durée doit être un entier (en minutes).");
                    return;
                }
                plugin.getRushManager().forceStartRush(res, duration);
                sender.sendMessage("§aRush forcé démarré sur la ressource " + res + " pendant " + duration + " minutes.");
                break;
            case "stop":
                plugin.getRushManager().forceStopRush();
                sender.sendMessage("§aRush arrêté.");
                break;
            case "reload":
                plugin.getRushManager().reloadRushPlan();
                sender.sendMessage("§aPlanification du Rush rechargée.");
                break;
            default:
                sender.sendMessage("§cUsage: /danaranks admin rush <start|stop|reload>");
                break;
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.reloadGuiConfig();
        plugin.getMessageManager().loadMessages();
        sender.sendMessage("§aConfiguration, GUIs et Langues de DanaRanks correctement rechargées !");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§b--- Commandes Administrateur DanaRanks ---");
        sender.sendMessage("§f - /danaranks admin setrank <joueur> <niveau>");
        sender.sendMessage("§f - /danaranks admin setelo <joueur> <valeur>");
        sender.sendMessage("§f - /danaranks admin addelo <joueur> <valeur>");
        sender.sendMessage("§f - /danaranks admin removeelo <joueur> <valeur>");
        sender.sendMessage("§f - /danaranks admin resetquota <joueur>");
        sender.sendMessage("§f - /danaranks admin rush start <ressource> <durée>");
        sender.sendMessage("§f - /danaranks admin rush stop");
        sender.sendMessage("§f - /danaranks admin rush reload");
        sender.sendMessage("§f - /danaranks admin reload");
    }
}
