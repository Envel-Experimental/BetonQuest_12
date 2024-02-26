package pl.betoncraft.betonquest.objectives;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerFishEvent.State;
import org.bukkit.inventory.ItemStack;
import pl.betoncraft.betonquest.BetonQuest;
import pl.betoncraft.betonquest.Instruction;
import pl.betoncraft.betonquest.api.Objective;
import pl.betoncraft.betonquest.config.Config;
import pl.betoncraft.betonquest.exceptions.InstructionParseException;
import pl.betoncraft.betonquest.exceptions.QuestRuntimeException;
import pl.betoncraft.betonquest.utils.BlockSelector;
import pl.betoncraft.betonquest.utils.LogUtils;
import pl.betoncraft.betonquest.utils.PlayerConverter;

import java.util.Locale;
import java.util.logging.Level;

/**
 * Requires the player to catch the fish.
 */
@SuppressWarnings("PMD.CommentRequired")
public class FishObjective extends Objective implements Listener {

    private final int amount;
    private final boolean notify;
    private final int notifyInterval;
    private final BlockSelector blockSelector;

    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    public FishObjective(final Instruction instruction) throws InstructionParseException {
        super(instruction);
        template = FishData.class;
        blockSelector = new BlockSelector(instruction.next());
        amount = instruction.getInt();
        if (amount <= 0) {
            throw new InstructionParseException("Fish amount cannot be less than 0");
        }
        notifyInterval = instruction.getInt(instruction.getOptional("notify"), 1);
        notify = instruction.hasArgument("notify") || notifyInterval > 1;
    }

    @SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.NPathComplexity"})
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFishCatch(final PlayerFishEvent event) {
        if (event.getState() != State.CAUGHT_FISH) {
            return;
        }
        final String playerID = PlayerConverter.getID(event.getPlayer());
        if (!containsPlayer(playerID)) {
            return;
        }
        if (event.getCaught() == null) {
            return;
        }
        if (event.getCaught().getType() != EntityType.DROPPED_ITEM) {
            return;
        }
        final ItemStack item = ((Item) event.getCaught()).getItemStack();
        if (!blockSelector.match(item.getType())) {
            return;
        }
        final FishData data = (FishData) dataMap.get(playerID);
        if (checkConditions(playerID)) {
            data.catchFish(item.getAmount());
        }
        if (data.getAmount() <= 0) {
            completeObjective(playerID);
        } else if (notify && data.getAmount() % notifyInterval == 0) {
            try {
                Config.sendNotify(instruction.getPackage().getName(), playerID, "fish_to_catch", new String[]{String.valueOf(data.getAmount())},
                        "fish_to_catch,info");
            } catch (final QuestRuntimeException exception) {
                try {
                    LogUtils.getLogger().log(Level.WARNING, "The notify system was unable to play a sound for the 'fish_to_catch' category in '" + instruction.getObjective().getFullID() + "'. Error was: '" + exception.getMessage() + "'");
                } catch (final InstructionParseException exep) {
                    LogUtils.logThrowableReport(exep);
                }
            }
        }
    }

    @Override
    public String getProperty(final String name, final String playerID) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "amount":
                return Integer.toString(amount - ((FishData) dataMap.get(playerID)).getAmount());
            case "left":
                return Integer.toString(((FishData) dataMap.get(playerID)).getAmount());
            case "total":
                return Integer.toString(amount);
            default:
                return "";
        }
    }

    @Override
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, BetonQuest.getInstance());
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public String getDefaultDataInstruction() {
        return Integer.toString(amount);
    }

    public static class FishData extends ObjectiveData {

        private int amount;

        public FishData(final String instruction, final String playerID, final String objID) {
            super(instruction, playerID, objID);
            amount = Integer.parseInt(instruction);
        }

        public void catchFish(final int caughtAmount) {
            amount -= caughtAmount;
            update();
        }

        public int getAmount() {
            return amount;
        }

        @Override
        public String toString() {
            return String.valueOf(amount);
        }

    }
}
