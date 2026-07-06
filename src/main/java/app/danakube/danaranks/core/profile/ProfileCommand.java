package app.danakube.danaranks.core.profile;

import app.danakube.danaranks.core.DanaRanks;
import app.danakube.danaranks.core.profile.ui.ProfileGUI;
import app.danakube.danaranks.features.leaderboard.LeaderboardGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProfileCommand implements CommandExecutor, TabCompleter {
    private final DanaRanks plugin;

    public ProfileCommand(DanaRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can execute this command.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("ranks")) {
            if (args.length > 0) {
                String sub = args[0].toLowerCase();
                if (sub.equals("profile")) {
                    new ProfileGUI(plugin).open(player);
                    return true;
                } else if (sub.equals("top")) {
                    new LeaderboardGUI(plugin).open(player);
                    return true;
                }
            }
            new ProfileGUI(plugin).open(player);
            return true;
        }

        // Si c'est /profile
        new ProfileGUI(plugin).open(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("ranks") && args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("profile");
            list.add("top");
            
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
