package app.danakube.danaranks.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event triggered when a player ranks up.
 */
public class PlayerRankUpEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int oldRankLevel;
    private final int newRankLevel;

    public PlayerRankUpEvent(Player player, int oldRankLevel, int newRankLevel) {
        this.player = player;
        this.oldRankLevel = oldRankLevel;
        this.newRankLevel = newRankLevel;
    }

    public Player getPlayer() {
        return player;
    }

    public int getOldRankLevel() {
        return oldRankLevel;
    }

    public int getNewRankLevel() {
        return newRankLevel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
