package app.danakube.danaranks.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event triggered when a player's ELO changes.
 */
public class PlayerEloChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int oldElo;
    private final int newElo;
    private final int changeAmount;
    private final String reason;

    public PlayerEloChangeEvent(Player player, int oldElo, int newElo, int changeAmount, String reason) {
        this.player = player;
        this.oldElo = oldElo;
        this.newElo = newElo;
        this.changeAmount = changeAmount;
        this.reason = reason;
    }

    public Player getPlayer() {
        return player;
    }

    public int getOldElo() {
        return oldElo;
    }

    public int getNewElo() {
        return newElo;
    }

    public int getChangeAmount() {
        return changeAmount;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
