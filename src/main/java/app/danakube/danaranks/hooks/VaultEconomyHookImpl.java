package app.danakube.danaranks.hooks;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public class VaultEconomyHookImpl implements EconomyHook {
    private Economy economy;

    public VaultEconomyHookImpl() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
                RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
                if (rsp != null) {
                    this.economy = rsp.getProvider();
                }
            }
        } catch (NoClassDefFoundError | Exception e) {
            this.economy = null;
        }
    }

    public VaultEconomyHookImpl(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        if (economy == null) return false;
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return economy.has(op, amount);
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        if (economy == null) return false;
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return economy.withdrawPlayer(op, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(UUID uuid, double amount) {
        if (economy == null) return false;
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return economy.depositPlayer(op, amount).transactionSuccess();
    }
}
