package pl.betoncraft.betonquest.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Should be fired when the compass calls the setCompassTarget method
 */
@SuppressWarnings({"PMD.DataClass", "PMD.CommentRequired"})
public class QuestCompassTargetChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location location;
    private boolean cancelled;

    public QuestCompassTargetChangeEvent(final Player player, final Location location) {
        super();
        this.player = player;
        this.location = location;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        cancelled = cancel;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getLocation() {
        return location;
    }
}
