package app.danakube.danaranks.hooks;

import org.bukkit.Bukkit;

public class DanaToolsHook {
    private final boolean enabled;

    public DanaToolsHook() {
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("DanaTools");
    }

    public boolean isEnabled() {
        return enabled;
    }
}
