package pl.betoncraft.betonquest.config;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import pl.betoncraft.betonquest.BetonQuest;
import pl.betoncraft.betonquest.config.ConfigAccessor.AccessorType;
import pl.betoncraft.betonquest.database.PlayerData;
import pl.betoncraft.betonquest.exceptions.InstructionParseException;
import pl.betoncraft.betonquest.exceptions.QuestRuntimeException;
import pl.betoncraft.betonquest.notify.Notify;
import pl.betoncraft.betonquest.utils.LogUtils;
import pl.betoncraft.betonquest.utils.PlayerConverter;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

/**
 * Handles the configuration of the plugin
 */
@SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.GodClass", "PMD.TooManyMethods", "PMD.UseObjectForClearerAPI",
        "PMD.CommentRequired", "PMD.AvoidLiteralsInIfCondition"})
public class Config {

    private static final List<String> UTIL_DIR_NAMES = Arrays.asList("logs", "backups", "conversations");
    private static final Map<String, ConfigPackage> PACKAGES = new HashMap<>();
    private static final Map<String, QuestCanceler> CANCELERS = new HashMap<>();
    private static final List<String> LANGUAGES = new ArrayList<>();
    private static BetonQuest plugin;
    private static Config instance;
    private static ConfigAccessor messages;
    private static ConfigAccessor internal;
    private static String lang;
    private static String defaultPackage = "default";
    private final File root;

    public Config() {
        this(true);
    }

    /**
     * Creates new instance of the Config handler
     *
     * @param verboose controls if this object should log it's actions to the file
     */
    @SuppressWarnings({"PMD.AssignmentToNonFinalStatic"})
    @SuppressFBWarnings({"ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"})
    public Config(final boolean verboose) {

        PACKAGES.clear();
        CANCELERS.clear();
        LANGUAGES.clear();

        instance = this;
        plugin = BetonQuest.getInstance();
        root = plugin.getDataFolder();
        lang = plugin.getConfig().getString("language");

        final boolean isInitialCreation = Optional.ofNullable(root.listFiles(File::isFile)).map(files -> files.length == 0).orElse(true);

        // save default config
        plugin.saveDefaultConfig();
        // need to be sure everything is saved
        plugin.reloadConfig();
        plugin.saveConfig();

        // load messages
        messages = new ConfigAccessor(new File(root, "messages.yml"), "messages.yml", AccessorType.OTHER);
        messages.saveDefaultConfig();
        internal = new ConfigAccessor(null, "internal-messages.yml", AccessorType.OTHER);
        for (final String key : messages.getConfig().getKeys(false)) {
            if (!"global".equals(key)) {
                if (verboose) {
                    LogUtils.getLogger().log(Level.FINE, "Loaded " + key + " language");
                }
                LANGUAGES.add(key);
            }
        }

        defaultPackage = plugin.getConfig().getString("default_package", defaultPackage);

        // save example package
        if (isInitialCreation) {
            createDefaultPackage(defaultPackage);
        }

        // load packages
        for (final File file : plugin.getDataFolder().listFiles()) {
            searchForPackages(file);
        }

        // load quest cancelers
        for (final ConfigPackage pack : PACKAGES.values()) {
            final ConfigurationSection section = pack.getMain().getConfig().getConfigurationSection("cancel");
            if (section == null) {
                continue;
            }
            for (final String key : section.getKeys(false)) {
                final String name = pack.getName() + "." + key;
                try {
                    CANCELERS.put(name, new QuestCanceler(name));
                } catch (final InstructionParseException e) {
                    LogUtils.getLogger().log(Level.WARNING, "Could not load '" + name + "' quest canceler: " + e.getMessage());
                    LogUtils.logThrowable(e);
                }
            }
        }
    }

    /**
     * Creates package with the given name and populates it with default quest
     *
     * @param packName name of the new package
     * @return true if the package was created, false if it already existed
     */
    public static boolean createDefaultPackage(final String packName) {
        return createPackage(packName, true);
    }

    /**
     * Creates a package with the given name and optionally populates it with the default quest.
     *
     * @param packName  name of the new package
     * @param populate if the files should be populated with the example quest or left empty
     * @return true if the package was created, false if it already existed
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public static boolean createPackage(final String packName, final boolean populate) {
        final File def = new File(instance.root, packName.replace("-", File.separator));
        final File conversations = new File(def, "conversations");
        if (!def.exists()) {
            LogUtils.getLogger().log(Level.INFO, "Deploying " + packName + " package!");
            def.mkdirs();
            conversations.mkdir();
            if (populate) {
                saveResource(def, "default/main.yml", "main.yml");
                saveResource(def, "default/events.yml", "events.yml");
                saveResource(def, "default/conditions.yml", "conditions.yml");
                saveResource(def, "default/journal.yml", "journal.yml");
                saveResource(def, "default/items.yml", "items.yml");
                saveResource(def, "default/objectives.yml", "objectives.yml");
                saveResource(def, "default/custom.yml", "custom.yml");
                saveResource(conversations, "default/conversations/innkeeper.yml", "innkeeper.yml");
            } else {
                try {
                    new File(def, "main.yml").createNewFile();
                    new File(def, "events.yml").createNewFile();
                    new File(def, "conditions.yml").createNewFile();
                    new File(def, "journal.yml").createNewFile();
                    new File(def, "items.yml").createNewFile();
                    new File(def, "objectives.yml").createNewFile();
                    new File(def, "custom.yml").createNewFile();
                } catch (IOException e) {
                    LogUtils.getLogger().log(Level.WARNING, "Could not create file: " + e.getMessage());
                    LogUtils.logThrowable(e);
                }
            }
            plugin.saveConfig();
            return true;
        }
        return false;
    }

    /**
     * Saves the resource with the name in a root directory
     *
     * @param root     directory where the resource will be saved
     * @param resource resource name
     * @param name     file name
     */
    @SuppressFBWarnings({"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE"})
    private static void saveResource(final File root, final String resource, final String name) {
        if (!root.isDirectory()) {
            return;
        }
        final File file = new File(root, name);
        if (!file.exists()) {
            try {
                file.createNewFile();
                try (InputStream input = plugin.getResource(resource);
                     OutputStream output = Files.newOutputStream(file.toPath());) {
                    final byte[] buffer = new byte[1024];
                    int len = input.read(buffer);
                    while (len != -1) {
                        output.write(buffer, 0, len);
                        len = input.read(buffer);
                    }
                }
            } catch (final IOException e) {
                LogUtils.getLogger().log(Level.WARNING, "Could not save resource: " + e.getMessage());
                LogUtils.logThrowable(e);
            }
        }
    }

    /**
     * @return the current instance of the Config handler
     */
    public static Config getInstance() {
        return instance;
    }

    /**
     * Returns a map containing all quest cancelers from across all packages.
     *
     * @return the map with quest cancelers
     */
    public static Map<String, QuestCanceler> getCancelers() {
        return CANCELERS;
    }

    /**
     * Retrieves the message from the configuration in specified language and
     * replaces the variables
     *
     * @param lang      language in which the message should be retrieved
     * @param message   name of the message to retrieve
     * @param variables array of variables to replace
     * @return message in that language, or message in English, or null if it
     * does not exist
     */
    public static String getMessage(final String lang, final String message, final String... variables) {
        String result = messages.getConfig().getString(lang + "." + message);
        if (result == null) {
            result = messages.getConfig().getString(Config.getLanguage() + "." + message);
        }
        if (result == null) {
            result = messages.getConfig().getString("en." + message);
        }
        if (result == null) {
            result = internal.getConfig().getString(lang + "." + message);
        }
        if (result == null) {
            result = internal.getConfig().getString("en." + message);
        }
        if (result != null) {
            if (variables != null) {
                for (int i = 0; i < variables.length; i++) {
                    result = result.replace("{" + (i + 1) + "}", variables[i]);
                }
            }
            result = result.replace('&', '§');
        }
        return result;
    }

    /**
     * Retrieves the message from the configuration in specified language
     *
     * @param message name of the message to retrieve
     * @param lang    language in which the message should be retrieved
     * @return message in that language, or message in English, or null if it
     * does not exist
     */
    public static String getMessage(final String lang, final String message) {
        return getMessage(lang, message, null);
    }

    /**
     * @return the map of packages and their names
     */
    public static Map<String, ConfigPackage> getPackages() {
        return PACKAGES;
    }

    /**
     * Retrieves the string from across all configuration. The variables are not
     * replaced! To replace variables automatically just call getString() method
     * on ConfigPackage.
     *
     * @param address address of the string
     * @return the requested string
     */
    public static String getString(final String address) {
        if (address == null) {
            return null;
        }
        final String[] parts = address.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        final String main = parts[0];
        if ("config".equals(main)) {
            return plugin.getConfig().getString(address.substring(7));
        } else if ("messages".equals(main)) {
            return messages.getConfig().getString(address.substring(9));
        } else {
            final ConfigPackage pack = PACKAGES.get(main);
            if (pack == null) {
                return null;
            }
            return pack.getRawString(address.substring(main.length() + 1));
        }
    }

    /**
     * Sets the string at specified address
     *
     * @param address address of the variable
     * @param value   value that needs to be set
     * @return true if it was set, false otherwise
     */
    @SuppressWarnings("PMD.LinguisticNaming")
    public static boolean setString(final String address, final String value) {
        if (address == null) {
            return false;
        }
        final String[] parts = address.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        final String main = parts[0];
        if ("config".equals(main)) {
            plugin.getConfig().set(address.substring(7), value);
            plugin.saveConfig();
            return true;
        } else if ("messages".equals(main)) {
            messages.getConfig().set(address.substring(9), value);
            messages.saveConfig();
            return true;
        } else {
            final ConfigPackage pack = PACKAGES.get(main);
            if (pack == null) {
                return false;
            }
            return pack.setString(address.substring(main.length() + 1), value);
        }
    }

    /**
     * @return messages configuration
     */
    public static ConfigAccessor getMessages() {
        return messages;
    }

    /**
     * @return the default language
     */
    public static String getLanguage() {
        return lang;
    }

    /**
     * Returns the ID of a conversation assigned to specified NPC, across all
     * packages. If there are multiple assignments for the same value, the first
     * one will be returned.
     *
     * @param value the name of the NPC (as defined in <i>main.yml</i>)
     * @return the ID of the conversation assigned to this NPC or null if there
     * isn't one
     */
    public static String getNpc(final String value) {
        // load npc assignments from all packages
        for (final Map.Entry<String, ConfigPackage> entry : PACKAGES.entrySet()) {
            final ConfigPackage pack = entry.getValue();
            final ConfigurationSection assignments = pack.getMain().getConfig().getConfigurationSection("npcs");
            if (assignments != null) {
                for (final String assignment : assignments.getKeys(false)) {
                    if (assignment.equalsIgnoreCase(value)) {
                        return entry.getKey() + "." + assignments.getString(assignment);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Sends a message to player in his chosen language or default or English
     * (if previous not found).
     *
     * @param playerID    ID of the player
     * @param messageName ID of the message
     */
    public static void sendMessage(final String packName, final String playerID, final String messageName) {
        sendMessage(packName, playerID, messageName, null, null, null, null);
    }

    /**
     * Sends a message to player in his chosen language or default or English
     * (if previous not found). It will replace all {x} sequences with the
     * variables.
     *
     * @param playerID    ID of the player
     * @param messageName ID of the message
     * @param variables   array of variables which will be inserted into the string
     */
    public static void sendMessage(final String packName, final String playerID, final String messageName, final String... variables) {
        sendMessage(packName, playerID, messageName, variables, null, null, null);
    }

    /**
     * Sends a message to player in his chosen language or default or English
     * (if previous not found). It will replace all {x} sequences with the
     * variables and play the sound.
     *
     * @param playerID    ID of the player
     * @param messageName ID of the message
     * @param variables   array of variables which will be inserted into the string
     * @param soundName   name of the sound to play to the player
     */
    public static void sendMessage(final String packName, final String playerID, final String messageName, final String[] variables, final String soundName) {
        sendMessage(packName, playerID, messageName, variables, soundName, null, null);
    }

    /**
     * Sends a message to player in his chosen language or default or English
     * (if previous not found). It will replace all {x} sequences with the
     * variables and play the sound. It will also add a prefix to the message.
     *
     * @param playerID        ID of the player
     * @param messageName     ID of the message
     * @param variables       array of variables which will be inserted into the message
     * @param soundName       name of the sound to play to the player
     * @param prefixName      ID of the prefix
     * @param prefixVariables array of variables which will be inserted into the prefix
     */
    public static void sendMessage(final String packName, final String playerID, final String messageName, final String[] variables, final String soundName,
                                   final String prefixName, final String... prefixVariables) {
        final String message = parseMessage(packName, playerID, messageName, variables, prefixName, prefixVariables);
        if (message == null || message.length() == 0) {
            return;
        }

        final Player player = PlayerConverter.getPlayer(playerID);
        player.sendMessage(message);
        if (soundName != null) {
            playSound(playerID, soundName);
        }
    }

    public static void sendNotify(final String packName, final String playerID, final String messageName, final String category) throws QuestRuntimeException {
        sendNotify(packName, playerID, messageName, null, category);
    }

    public static void sendNotify(final String packName, final Player player, final String messageName, final String category) throws QuestRuntimeException {
        sendNotify(packName, player, messageName, null, category);
    }

    public static void sendNotify(final String packName, final String playerID, final String messageName, final String[] variables, final String category) throws QuestRuntimeException {
        sendNotify(packName, playerID, messageName, variables, category, null);
    }

    public static void sendNotify(final String packName, final Player player, final String messageName, final String[] variables, final String category) throws QuestRuntimeException {
        sendNotify(packName, player, messageName, variables, category, null);
    }

    public static void sendNotify(final String packName, final String playerID, final String messageName, final String[] variables, final String category, final Map<String, String> data) throws QuestRuntimeException {
        sendNotify(packName, PlayerConverter.getPlayer(playerID), messageName, variables, category, data);
    }

    /**
     * Sends a notification to player in his chosen language or default or English
     * (if previous not found). It will replace all {x} sequences with the
     * variables and play the sound. It will also add a prefix to the message.
     *
     * @param player      player
     * @param messageName ID of the message
     * @param variables   array of variables which will be inserted into the message
     * @param category    notification category
     * @param data        custom notifyIO data
     */
    public static void sendNotify(final String packName, final Player player, final String messageName, final String[] variables, final String category, final Map<String, String> data) throws QuestRuntimeException {
        final String message = parseMessage(packName, player, messageName, variables);
        if (message == null || message.length() == 0) {
            return;
        }

        Notify.get(category, data).sendNotify(message, player);
    }

    public static String parseMessage(final String packName, final String playerID, final String messageName, final String... variables) {
        return parseMessage(packName, playerID, messageName, variables, null, null);
    }

    public static String parseMessage(final String packName, final Player player, final String messageName, final String... variables) {
        return parseMessage(packName, player, messageName, variables, null, null);
    }

    public static String parseMessage(final String packName, final String playerID, final String messageName, final String[] variables, final String prefixName,
                                      final String... prefixVariables) {
        return parseMessage(packName, PlayerConverter.getPlayer(playerID), messageName, variables, prefixName, prefixVariables);
    }

    /**
     * Retrieve's a message in the language of the player, replacing variables
     *
     * @param player          player
     * @param messageName     name of the message to retrieve
     * @param variables       Variables to replace in message
     * @param prefixName      ID of the prefix
     * @param prefixVariables array of variables which will be inserted into the prefix
     */
    public static String parseMessage(final String packName, final Player player, final String messageName, final String[] variables, final String prefixName,
                                      final String... prefixVariables) {
        final PlayerData playerData = BetonQuest.getInstance().getPlayerData(PlayerConverter.getID(player));
        if (playerData == null) {
            return null;
        }
        final String language = playerData.getLanguage();
        String message = getMessage(language, messageName, variables);
        if (message == null || message.length() == 0) {
            return null;
        }
        if (prefixName != null) {
            final String prefix = getMessage(language, prefixName, prefixVariables);
            if (prefix.length() > 0) {
                message = prefix + message;
            }
        }
        if (packName != null) {
            for (final String variable : BetonQuest.resolveVariables(message)) {
                final String replacement = BetonQuest.getInstance().getVariableValue(packName, variable, PlayerConverter.getID(player));
                message = message.replace(variable, replacement);
            }
        }
        return message;
    }

    /**
     * Plays a sound specified in the plugins config to the player
     *
     * @param playerID  the uuid of the player
     * @param soundName the name of the sound to play to the player
     */
    public static void playSound(final String playerID, final String soundName) {
        final Player player = PlayerConverter.getPlayer(playerID);
        if (player == null) {
            return;
        }
        final String rawSound = BetonQuest.getInstance().getConfig().getString("sounds." + soundName);
        if (!"false".equalsIgnoreCase(rawSound)) {
            try {
                player.playSound(player.getLocation(), Sound.valueOf(rawSound), 1F, 1F);
            } catch (final IllegalArgumentException e) {
                LogUtils.getLogger().log(Level.WARNING, "Unknown sound type: " + rawSound);
                LogUtils.logThrowable(e);
            }
        }
    }

    /**
     * @return the languages defined for this plugin
     */
    public static List<String> getLanguages() {
        return LANGUAGES;
    }

    /**
     * @return the default package, as specified in the config or null if default package was deleted
     */
    public static ConfigPackage getDefaultPackage() {
        return getPackages().get(defaultPackage);
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private void searchForPackages(final File file) {
        if (file.isDirectory() && !UTIL_DIR_NAMES.contains(file.getName())) {
            final File[] content = file.listFiles();
            for (final File subFile : content) {
                if ("main.yml".equals(subFile.getName())) {
                    // this is a package, add it and stop searching
                    final String packPath = BetonQuest.getInstance().getDataFolder()
                            .toURI().relativize(file.toURI())
                            .toString().replace('/', ' ').trim().replace(' ', '-');
                    final ConfigPackage pack = new ConfigPackage(file, packPath);
                    if (pack.isEnabled()) {
                        PACKAGES.put(packPath, pack);
                    }
                    return;
                }
            }
            for (final File subFile : content) {
                searchForPackages(subFile);
            }
        }
    }
}
