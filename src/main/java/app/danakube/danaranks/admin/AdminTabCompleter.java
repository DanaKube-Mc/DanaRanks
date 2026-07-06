package app.danakube.danaranks.admin;

import org.bukkit.command.Command;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AdminTabCompleter implements TabCompleter {

    // TODO: Provide autocompletion for /danaranks admin [subcommand]
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        // TODO: Auto-complete based on permissions and subcommand depth
        return null;
    }
}
