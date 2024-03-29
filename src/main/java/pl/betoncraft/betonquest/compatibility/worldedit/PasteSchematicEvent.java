package pl.betoncraft.betonquest.compatibility.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.session.ClipboardHolder;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import pl.betoncraft.betonquest.Instruction;
import pl.betoncraft.betonquest.api.QuestEvent;
import pl.betoncraft.betonquest.exceptions.InstructionParseException;
import pl.betoncraft.betonquest.exceptions.QuestRuntimeException;
import pl.betoncraft.betonquest.utils.LogUtils;
import pl.betoncraft.betonquest.utils.location.CompoundLocation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * Pastes a schematic at a given location.
 */
@SuppressWarnings("PMD.CommentRequired")
public class PasteSchematicEvent extends QuestEvent {

    private final CompoundLocation loc;
    private final boolean noAir;
    private File file;

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public PasteSchematicEvent(final Instruction instruction) throws InstructionParseException {
        super(instruction, true);
        loc = instruction.getLocation();
        final WorldEditPlugin worldEdit = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
        final File folder = new File(worldEdit.getDataFolder(), "schematics");
        if (!folder.exists() || !folder.isDirectory()) {
            throw new InstructionParseException("Schematic folder does not exist");
        }
        final String schemName = instruction.next();
        file = new File(folder, schemName);
        if (!file.exists()) {
            file = new File(folder, schemName + ".schematic");
            if (!file.exists()) {
                throw new InstructionParseException("Schematic " + schemName + " does not exist (" + folder.toPath().resolve(schemName + ".schematic") + ")");
            }
        }
        noAir = instruction.hasArgument("noair");
    }

    @Override
    protected Void execute(final String playerID) throws QuestRuntimeException {
        try {
            final ClipboardFormat format = ClipboardFormats.findByFile(file);
            if (format == null) {
                throw new IOException("Unknown Schematic Format");
            }

            final Clipboard clipboard;
            try (ClipboardReader reader = format.getReader(Files.newInputStream(file.toPath()))) {
                clipboard = reader.read();
            }

            final Location location = loc.getLocation(playerID);
            try (EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(BukkitAdapter.adapt(location.getWorld()), -1)) {
                final Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BukkitAdapter.asBlockVector(location))
                        .ignoreAirBlocks(noAir)
                        .build();

                Operations.complete(operation);
            }
        } catch (IOException | WorldEditException e) {
            LogUtils.getLogger().log(Level.WARNING, "Error while pasting a schematic: " + e.getMessage());
            LogUtils.logThrowable(e);
        }
        return null;
    }

}
