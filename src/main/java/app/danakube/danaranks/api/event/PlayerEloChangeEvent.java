package app.danakube.danaranks.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/**
 * Event triggered when a player's ELO changes.
 */
public class PlayerEloChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerUuid;
    private final int eloChange;
    private final int newElo;
    private final String reason;

    public PlayerEloChangeEvent(UUID playerUuid, int eloChange, int newElo, String reason) {
        this.playerUuid = playerUuid;
        this.eloChange = eloChange;
        this.newElo = newElo;
        this.reason = reason;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public int getEloChange() {
        return eloChange;
    }

    public int getNewElo() {
        return newElo;
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
