package su.nightexpress.excellentjobs.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import su.nightexpress.excellentjobs.job.model.Job;

public abstract class GrindObjectiveEvent extends Event {
    protected final Player player;
    protected final Job job;

    protected GrindObjectiveEvent(Player player, Job job) {
        this.player = player;
        this.job = job;
    }

    public Player getPlayer() {
        return this.player;
    }

    public Job getJob() {
        return this.job;
    }
}
