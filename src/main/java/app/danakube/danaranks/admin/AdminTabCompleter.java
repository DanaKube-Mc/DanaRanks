package app.danakube.danaranks.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AdminTabCompleter implements TabCompleter {

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("danaranks.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("admin".startsWith(input)) {
                return List.of("admin");
            }
            return Collections.emptyList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            List<String> list = List.of("setrank", "setelo", "addelo", "removeelo", "resetquota", "rush", "reload");
            return filterCompletions(list, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            String sub = args[1].toLowerCase();
            if (sub.equals("rush")) {
                return filterCompletions(List.of("start", "stop", "reload"), args[2]);
            } else if (List.of("setrank", "setelo", "addelo", "removeelo", "resetquota").contains(sub)) {
                List<String> playerNames = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    playerNames.add(player.getName());
                }
                return filterCompletions(playerNames, args[2]);
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("rush") && args[2].equalsIgnoreCase("start")) {
            List<String> resources = List.of("lumens-gained", "lumens-spent", "job-xp", "tool-xp", "vanilla-xp-gained", "vanilla-xp-spent");
            return filterCompletions(resources, args[3]);
        }

        return Collections.emptyList();
    }

    private List<String> filterCompletions(List<String> items, String input) {
        String lower = input.toLowerCase();
        List<String> list = new ArrayList<>();
        for (String s : items) {
            if (s.toLowerCase().startsWith(lower)) {
                list.add(s);
            }
        }
        return list;
    }
}
