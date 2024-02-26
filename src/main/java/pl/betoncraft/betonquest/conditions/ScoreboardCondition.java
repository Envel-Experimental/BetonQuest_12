package pl.betoncraft.betonquest.conditions;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import pl.betoncraft.betonquest.Instruction;
import pl.betoncraft.betonquest.VariableNumber;
import pl.betoncraft.betonquest.api.Condition;
import pl.betoncraft.betonquest.exceptions.InstructionParseException;
import pl.betoncraft.betonquest.exceptions.QuestRuntimeException;
import pl.betoncraft.betonquest.utils.PlayerConverter;

/**
 * Checks the amount of points on scoreboards.
 */
@SuppressWarnings("PMD.CommentRequired")
public class ScoreboardCondition extends Condition {

    private final String objective;
    private final VariableNumber count;

    public ScoreboardCondition(final Instruction instruction) throws InstructionParseException {
        super(instruction, true);
        objective = instruction.next();
        count = instruction.getVarNum();
    }

    @Override
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    protected Boolean execute(final String playerID) throws QuestRuntimeException {
        final Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        final Objective obj = board.getObjective(objective);
        if (obj == null) {
            throw new QuestRuntimeException("Scoreboard objective " + objective + " does not exist!");
        }
        final Score score = obj.getScore(PlayerConverter.getName(playerID));
        return score.getScore() >= count.getInt(playerID);
    }

}
