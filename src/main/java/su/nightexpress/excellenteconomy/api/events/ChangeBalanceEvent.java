package su.nightexpress.excellenteconomy.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ChangeBalanceEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final double amount;
    private final String reason;

    public ChangeBalanceEvent(Player player, double amount, String reason) {
        this.player = player;
        this.amount = amount;
        this.reason = reason;
    }

    public Player getPlayer() {
        return player;
    }

    public double getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
