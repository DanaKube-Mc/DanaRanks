package app.danakube.danaranks.ui;

import org.bukkit.OfflinePlayer;
import me.clip.placeholderapi.PlaceholderAPI;

public class PlaceholderAPIHook {
    public static String setPlaceholders(OfflinePlayer player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
