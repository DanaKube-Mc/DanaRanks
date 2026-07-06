package app.danakube.danaranks.admin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class AdminCommandExecutor implements CommandExecutor {

    // TODO: Standardise all admin subcommands under /danaranks admin [subcommand]
    // - Subcommands: resetquota, endrush, setupdaily, reload, etc.
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // TODO: Parse subcommand and execute
        return true;
    }
}
