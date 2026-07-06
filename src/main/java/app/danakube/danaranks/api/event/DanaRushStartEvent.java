package app.danakube.danaranks.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event triggered when a Dana Rush event starts.
 */
public class DanaRushStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String resource;
    private final int durationMinutes;

    public DanaRushStartEvent(String resource, int durationMinutes) {
        this.resource = resource;
        this.durationMinutes = durationMinutes;
    }

    public String getResource() {
        return resource;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
