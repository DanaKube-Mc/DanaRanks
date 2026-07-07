package app.danakube.danaranks.features.leaderboard;

import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LeaderboardCommand implements CommandExecutor {
    private final DanaRanks plugin;

    public LeaderboardCommand(DanaRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("chat")) {
            printTop10(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            printTop10(sender);
            return true;
        }

        new LeaderboardGUI(plugin).open(player, 0);
        return true;
    }

    private void printTop10(CommandSender sender) {
        java.util.List<LeaderboardEntry> top = plugin.getLeaderboardManager().getCachedLeaderboard();
        sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gold><b>Classement Global (Top 10) :</b></gold>"));
        int count = Math.min(10, top.size());
        if (count == 0) {
            sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gray>Aucun joueur dans le classement pour le moment.</gray>"));
            return;
        }
        for (int i = 0; i < count; i++) {
            LeaderboardEntry entry = top.get(i);
            sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                    String.format("<yellow>#%d</yellow> <white>%s</white> - <gold>Rang %d (%d ELO)</gold>",
                            i + 1, entry.playerName(), entry.rankLevel(), entry.elo())
            ));
        }
    }
}
