package app.danakube.danaranks.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.Map;
import java.util.UUID;

/**
 * Event triggered when a Dana Rush event ends.
 */
public class DanaRushEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String resource;
    private final Map<UUID, Double> scores;
    private final Map<UUID, Integer> eloChanges;

    public DanaRushEndEvent(String resource, Map<UUID, Double> scores, Map<UUID, Integer> eloChanges) {
        this.resource = resource;
        this.scores = scores;
        this.eloChanges = eloChanges;
    }

    public String getResource() {
        return resource;
    }

    public Map<UUID, Double> getScores() {
        return scores;
    }

    public Map<UUID, Integer> getEloChanges() {
        return eloChanges;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
