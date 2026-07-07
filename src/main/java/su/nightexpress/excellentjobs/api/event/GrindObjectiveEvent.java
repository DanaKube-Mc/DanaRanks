package su.nightexpress.excellentjobs.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public abstract class GrindObjectiveEvent extends Event {
    protected final Player player;

    protected GrindObjectiveEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return this.player;
    }
}
