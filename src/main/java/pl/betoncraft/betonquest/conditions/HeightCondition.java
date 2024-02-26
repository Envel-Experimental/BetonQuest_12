package pl.betoncraft.betonquest.conditions;

import pl.betoncraft.betonquest.Instruction;
import pl.betoncraft.betonquest.VariableNumber;
import pl.betoncraft.betonquest.api.Condition;
import pl.betoncraft.betonquest.exceptions.InstructionParseException;
import pl.betoncraft.betonquest.exceptions.QuestRuntimeException;
import pl.betoncraft.betonquest.utils.PlayerConverter;
import pl.betoncraft.betonquest.utils.location.CompoundLocation;

/**
 * Checks Y height player is at (must be below)
 */
@SuppressWarnings("PMD.CommentRequired")
public class HeightCondition extends Condition {

    private final VariableNumber height;

    public HeightCondition(final Instruction instruction) throws InstructionParseException {
        super(instruction, true);
        final String string = instruction.next();
        final String packName = instruction.getPackage().getName();
        if (string.matches("\\-?\\d+\\.?\\d*")) {
            try {
                height = new VariableNumber(packName, string);
            } catch (InstructionParseException e) {
                throw new InstructionParseException("Could not parse height", e);
            }
        } else {
            try {
                height = new VariableNumber(new CompoundLocation(packName, string).getLocation(null).getY());
            } catch (QuestRuntimeException e) {
                throw new InstructionParseException("Could not parse height", e);
            }
        }
    }

    @Override
    protected Boolean execute(final String playerID) throws QuestRuntimeException {
        return PlayerConverter.getPlayer(playerID).getLocation().getY() < height.getDouble(playerID);
    }

}
