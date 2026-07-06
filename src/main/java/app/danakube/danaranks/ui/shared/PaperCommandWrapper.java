package app.danakube.danaranks.ui.shared;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A wrapper class that adapts standard Bukkit CommandExecutor and TabCompleter
 * implementations into a programmatic Command that can be registered in the CommandMap.
 */
public class PaperCommandWrapper extends Command {
    private final CommandExecutor executor;
    private final @Nullable TabCompleter tabCompleter;

    public PaperCommandWrapper(
            @NotNull String name,
            @NotNull String description,
            @NotNull String usageMessage,
            @NotNull List<String> aliases,
            @NotNull CommandExecutor executor,
            @Nullable TabCompleter tabCompleter
    ) {
        super(name, description, usageMessage, aliases);
        this.executor = executor;
        this.tabCompleter = tabCompleter;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        return executor.onCommand(sender, this, commandLabel, args);
    }

    @Override
    public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String alias,
            @NotNull String[] args
    ) throws IllegalArgumentException {
        if (tabCompleter != null) {
            List<String> completions = tabCompleter.onTabComplete(sender, this, alias, args);
            if (completions != null) {
                return completions;
            }
        }
        return Collections.emptyList();
    }
}
