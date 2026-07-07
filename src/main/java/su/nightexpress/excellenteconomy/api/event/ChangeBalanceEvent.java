package su.nightexpress.excellenteconomy.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ChangeBalanceEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final double amount;
    private final String reason;

    public ChangeBalanceEvent(@NotNull Player player, double amount, @NotNull String reason) {
        this.player = player;
        this.amount = amount;
        this.reason = reason;
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }

    public double getAmount() {
        return amount;
    }

    @NotNull
    public String getReason() {
        return reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
