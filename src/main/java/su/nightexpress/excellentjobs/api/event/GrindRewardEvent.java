package su.nightexpress.excellentjobs.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentjobs.api.grind.GrindReward;

import su.nightexpress.excellentjobs.job.model.Job;

public class GrindRewardEvent extends GrindObjectiveEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final GrindReward reward;
    private boolean cancelled;

    public GrindRewardEvent(Player player, Job job, GrindReward reward) {
        super(player, job);
        this.reward = reward;
    }

    public GrindReward getReward() {
        return this.reward;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
