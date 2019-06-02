package com.bgsoftware.superiorskyblock.api.events;

import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@SuppressWarnings("unused")
public class IslandCreateEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final SuperiorPlayer superiorPlayer;
    private final Island island;
    private boolean teleport = true;
    private boolean cancelled = false;

    public IslandCreateEvent(SuperiorPlayer superiorPlayer, Island island){
        this.superiorPlayer = superiorPlayer;
        this.island = island;
    }

    public SuperiorPlayer getPlayer() {
        return superiorPlayer;
    }

    public Island getIsland() {
        return island;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void setTeleport(boolean teleport) {
        this.teleport = teleport;
    }

    public boolean canTeleport() {
        return teleport;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

}
