package pl.betoncraft.betonquest.events;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import pl.betoncraft.betonquest.Instruction;
import pl.betoncraft.betonquest.api.QuestCompassTargetChangeEvent;
import pl.betoncraft.betonquest.api.QuestEvent;
import pl.betoncraft.betonquest.config.Config;
import pl.betoncraft.betonquest.config.ConfigPackage;
import pl.betoncraft.betonquest.exceptions.InstructionParseException;
import pl.betoncraft.betonquest.exceptions.QuestRuntimeException;
import pl.betoncraft.betonquest.utils.LogUtils;
import pl.betoncraft.betonquest.utils.PlayerConverter;
import pl.betoncraft.betonquest.utils.location.CompoundLocation;

import java.util.Locale;
import java.util.logging.Level;

/**
 * Adds a compass specific tag to the player.
 */
@SuppressWarnings("PMD.CommentRequired")
public class CompassEvent extends QuestEvent {

    private final Action action;
    private final String compass;
    private CompoundLocation compassLocation;

    public CompassEvent(final Instruction instruction) throws InstructionParseException {
        super(instruction, true);
        persistent = true;

        action = instruction.getEnum(Action.class);
        compass = instruction.next();

        // Check if compass is valid
        for (final ConfigPackage pack : Config.getPackages().values()) {
            final ConfigurationSection section = pack.getMain().getConfig().getConfigurationSection("compass");
            if (section != null && section.contains(compass)) {
                compassLocation = new CompoundLocation(pack.getName(), pack.getString("main.compass." + compass + ".location"));
                break;
            }
        }
        if (compassLocation == null) {
            throw new InstructionParseException("Invalid compass location: " + compass);
        }
    }

    @Override
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    protected Void execute(final String playerID) throws QuestRuntimeException {
        switch (action) {
            case ADD:
            case DEL:
                // Add Tag to player
                try {
                    new TagEvent(new Instruction(instruction.getPackage(), null, "tag " + action.toString().toLowerCase(Locale.ROOT) + " compass-" + compass)).handle(playerID);
                } catch (InstructionParseException e) {
                    LogUtils.getLogger().log(Level.WARNING, "Failed to tag player with compass point: " + compass);
                    LogUtils.logThrowable(e);
                }
                return null;
            case SET:
                final Location location;
                try {
                    location = compassLocation.getLocation(playerID);
                } catch (final QuestRuntimeException e) {
                    LogUtils.getLogger().log(Level.WARNING, "Failed to set compass: " + compass);
                    LogUtils.logThrowable(e);
                    return null;
                }

                final Player player = PlayerConverter.getPlayer(playerID);
                if (player != null) {
                    final QuestCompassTargetChangeEvent event = new QuestCompassTargetChangeEvent(player, location);
                    Bukkit.getServer().getPluginManager().callEvent(event);
                    if (!event.isCancelled()) {
                        player.setCompassTarget(location);
                    }
                }
        }
        return null;
    }

    public enum Action {
        ADD,
        DEL,
        SET
    }
}
