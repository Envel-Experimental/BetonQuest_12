package pl.betoncraft.betonquest.events;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.betoncraft.betonquest.BetonQuest;
import pl.betoncraft.betonquest.Instruction;
import pl.betoncraft.betonquest.api.QuestEvent;
import pl.betoncraft.betonquest.config.Config;
import pl.betoncraft.betonquest.exceptions.InstructionParseException;
import pl.betoncraft.betonquest.exceptions.QuestRuntimeException;
import pl.betoncraft.betonquest.utils.LogUtils;
import pl.betoncraft.betonquest.utils.PlayerConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Removes items from player's inventory and/or backpack
 *
 * @deprecated Only needed because the 1.12.X item system cannot work with special items like the ones from MMOItems.
 * Will be removed in 2.0 in favor of the new item system. Then the {@link TakeEvent} will be able to handle everything.
 */
@Deprecated
@SuppressWarnings("PMD.CommentRequired")
public abstract class AbstractTakeEvent extends QuestEvent {

    protected final boolean notify;
    protected final List<CheckType> checkOrder;

    public AbstractTakeEvent(final Instruction instruction) throws InstructionParseException {
        super(instruction, true);
        checkOrder = getCheckOrder(instruction);
        notify = instruction.hasArgument("notify");
    }

    private List<CheckType> getCheckOrder(final Instruction instruction) throws InstructionParseException {
        final String order = instruction.getOptional("invOrder");
        if (order == null) {
            return Arrays.asList(CheckType.INVENTORY, CheckType.OFFHAND, CheckType.ARMOR, CheckType.BACKPACK);
        } else {
            final String[] enumNames = order.split(",");
            final ArrayList<CheckType> checkOrder = new ArrayList<>();
            for (final String s : enumNames) {
                try {
                    final CheckType checkType = CheckType.valueOf(s.toUpperCase(Locale.ROOT));
                    checkOrder.add(checkType);
                } catch (IllegalArgumentException e) {
                    throw new InstructionParseException("There is no such check type: " + s, e);
                }
            }
            return checkOrder;
        }
    }

    protected void checkSelectedTypes(final Player player) {
        for (final CheckType type : checkOrder) {
            switch (type) {
                case INVENTORY:
                    checkInventory(player);
                    break;
                case ARMOR:
                    checkArmor(player);
                    break;
                case OFFHAND:
                    checkOffhand(player);
                    break;
                case BACKPACK:
                    checkBackpack(PlayerConverter.getID(player));
                    break;
            }
        }
    }

    protected void notifyPlayer(final String playerID, final String itemName, final int amount) {
        if (notify) {
            try {
                Config.sendNotify(instruction.getPackage().getName(), playerID, "items_taken",
                        new String[]{itemName, String.valueOf(amount)}, "items_taken,info");
            } catch (final QuestRuntimeException exception) {
                LogUtils.getLogger().log(Level.WARNING, "The notify system was unable to play a sound for the 'items_taken' category in '" + getFullId() + "'. Error was: '" + exception.getMessage() + "'");
                LogUtils.logThrowable(exception);
            }
        }
    }

    protected void checkInventory(final Player player) {
        final ItemStack[] inventory = player.getInventory().getStorageContents();
        final ItemStack[] newInv = takeDesiredAmount(player, inventory);
        player.getInventory().setStorageContents(newInv);
    }

    protected void checkArmor(final Player player) {
        final ItemStack[] armorSlots = player.getInventory().getArmorContents();
        final ItemStack[] newArmor = takeDesiredAmount(player, armorSlots);
        player.getInventory().setArmorContents(newArmor);
    }

    protected void checkOffhand(final Player player) {
        final ItemStack offhand = player.getInventory().getItemInOffHand();
        final ItemStack[] newOffhand = takeDesiredAmount(player, offhand);
        player.getInventory().setItemInOffHand(newOffhand[0]);
    }

    protected void checkBackpack(final String playerID) {
        final List<ItemStack> backpack = BetonQuest.getInstance().getPlayerData(playerID).getBackpack();
        final List<ItemStack> newBackpack = removeDesiredAmount(PlayerConverter.getPlayer(playerID), backpack);
        BetonQuest.getInstance().getPlayerData(playerID).setBackpack(newBackpack);
    }

    protected List<ItemStack> removeDesiredAmount(final Player player, final List<ItemStack> items) {
        final ItemStack[] itemArray = items.toArray(new ItemStack[0]);
        final ItemStack[] remainingItems = takeDesiredAmount(player, itemArray);
        return Arrays.stream(remainingItems)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    protected abstract ItemStack[] takeDesiredAmount(final Player player, final ItemStack... items);

    protected enum CheckType {
        INVENTORY,
        ARMOR,
        OFFHAND,
        BACKPACK
    }
}
