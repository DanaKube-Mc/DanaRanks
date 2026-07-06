package app.danakube.danaranks.features.quota;

import app.danakube.danaranks.core.DanaRanks;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class QuotaCommand implements CommandExecutor {
    private final DanaRanks plugin;
    private final QuotaService quotaService;

    public QuotaCommand(DanaRanks plugin, QuotaService quotaService) {
        this.plugin = plugin;
        this.quotaService = quotaService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (label.equalsIgnoreCase("quota")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can view their quotas.");
                return true;
            }
            player.sendMessage("§a[Quota] Vos quotas de grade sont actifs !");
            return true;
        }
        return false;
    }
}
