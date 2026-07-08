package app.danakube.danaranks.features.leaderboard;

import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

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
        List<LeaderboardEntry> top = plugin.getLeaderboardManager().getCachedLeaderboard();
        sender.sendMessage(plugin.getMessageManager().getMessageComponent("leaderboard-chat-header", "<gold><b>Classement Global (Top 10) :</b></gold>"));
        int count = Math.min(10, top.size());
        if (count == 0) {
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("leaderboard-chat-empty", "<gray>Aucun joueur dans le classement pour le moment.</gray>"));
            return;
        }
        for (int i = 0; i < count; i++) {
            LeaderboardEntry entry = top.get(i);
            sender.sendMessage(plugin.getMessageManager().getMessageComponent("leaderboard-chat-format",
                    "<yellow>#%pos%</yellow> <white>%player%</white> - <gold>Rang %rank% (%elo% ELO)</gold>",
                    Map.of("%pos%", String.valueOf(i + 1),
                           "%player%", entry.playerName(),
                           "%rank%", plugin.getRankDisplayName(entry.rankLevel()),
                           "%elo%", String.valueOf(entry.elo()))));
        }
    }
}
