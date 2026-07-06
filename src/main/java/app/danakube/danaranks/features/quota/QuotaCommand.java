package app.danakube.danaranks.features.quota;

import app.danakube.danaranks.core.DanaRanks;
import app.danakube.danaranks.features.quota.ui.QuotaGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class QuotaCommand implements CommandExecutor {
    private final DanaRanks plugin;

    public QuotaCommand(DanaRanks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can view their quotas.");
            return true;
        }
        new QuotaGUI(plugin).open(player);
        return true;
    }
}
