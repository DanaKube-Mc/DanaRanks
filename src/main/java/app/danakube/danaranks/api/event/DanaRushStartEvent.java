package app.danakube.danaranks.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.time.Instant;

/**
 * Event triggered when a Dana Rush event starts.
 */
public class DanaRushStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String resource;
    private final int durationMinutes;
    private final Instant startTime;

    public DanaRushStartEvent(String resource, int durationMinutes, Instant startTime) {
        this.resource = resource;
        this.durationMinutes = durationMinutes;
        this.startTime = startTime;
    }

    public String getResource() {
        return resource;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Instant getStartTime() {
        return startTime;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
