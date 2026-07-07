package su.nightexpress.excellenteconomy.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ChangeBalanceEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final double oldAmount;
    private final double newAmount;

    public ChangeBalanceEvent(@NotNull Player player, double oldAmount, double newAmount) {
        this.player = player;
        this.oldAmount = oldAmount;
        this.newAmount = newAmount;
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }

    public double getOldAmount() {
        return oldAmount;
    }

    public double getNewAmount() {
        return newAmount;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
