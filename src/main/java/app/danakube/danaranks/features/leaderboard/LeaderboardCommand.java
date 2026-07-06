package app.danakube.danaranks.features.leaderboard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LeaderboardCommand implements CommandExecutor {

    // TODO: Implement the /ranks top command
    // - Retrieve top player profiles or display leaderboard GUI
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player) {
            // TODO: Open LeaderboardGUI
        } else {
            sender.sendMessage("Only players can open the leaderboard GUI.");
        }
        return true;
    }
}
