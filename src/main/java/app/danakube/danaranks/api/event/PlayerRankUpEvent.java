package app.danakube.danaranks.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/**
 * Event triggered when a player ranks up.
 */
public class PlayerRankUpEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerUuid;
    private final int ranksGained;
    private final int newRank;

    public PlayerRankUpEvent(UUID playerUuid, int ranksGained, int newRank) {
        this.playerUuid = playerUuid;
        this.ranksGained = ranksGained;
        this.newRank = newRank;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public int getRanksGained() {
        return ranksGained;
    }

    public int getNewRank() {
        return newRank;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
