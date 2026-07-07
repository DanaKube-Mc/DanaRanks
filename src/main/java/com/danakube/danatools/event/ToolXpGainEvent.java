package com.danakube.danatools.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ToolXpGainEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final double xpGained;

    public ToolXpGainEvent(@NotNull Player player, double xpGained) {
        this.player = player;
        this.xpGained = xpGained;
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }

    public double getAmount() {
        return xpGained;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
