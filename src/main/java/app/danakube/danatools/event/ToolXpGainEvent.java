package app.danakube.danatools.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ToolXpGainEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final double xpGained;

    public ToolXpGainEvent(Player player, double xpGained) {
        this.player = player;
        this.xpGained = xpGained;
    }

    public Player getPlayer() {
        return player;
    }

    public double getAmount() {
        return xpGained;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
