package nu.nerd.oddjob;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;

//-----------------------------------------------------------------------------
/**
 * Represents a "type" of task that can be instantiated by {@code /task run}.
 */
public class TaskType {
    // ------------------------------------------------------------------------
    /**
     * Load this task from the configuration.
     * 
     * @param section the configuration section that describes this task type.
     */
    public void load(ConfigurationSection section) {
        _id = section.getName();
        _permission = section.getString("permission");
        _online = section.getBoolean("online");
        _broadcasts = section.getStringList("broadcasts");
        _broadcastPermission = section.getString("broadcast-permission");
        _messages = section.getStringList("messages");
        _consoleCommands = section.getStringList("console-commands");
        _playerCommands = section.getStringList("player-commands");

        _forceOnline = !getMessages().isEmpty() || !getPlayerCommands().isEmpty();
        if (!_forceOnline) {
            // Check for /run-as or /runas with a player that is not console.
            Pattern runAs = Pattern.compile("^/?run-?as .*$", Pattern.CASE_INSENSITIVE);
            Pattern runAsConsole = Pattern.compile("^/?run-?as console .*$", Pattern.CASE_INSENSITIVE);
            _forceOnline = _consoleCommands.stream().anyMatch(cmd -> (runAs.matcher(cmd).matches() &&
                                                                      !runAsConsole.matcher(cmd).matches()));
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Execute the specified task instance now.
     * 
     * @param task the task instance.
     */
    public void execute(Task task) {
        long now = System.currentTimeMillis();
        HashMap<String, String> replacements = new HashMap<String, String>();
        replacements.put("id", task.getId());
        replacements.put("type", task.getTaskTypeId());
        replacements.put("player", (task.getPlayerName() != null) ? task.getPlayerName() : "-");
        replacements.put("uuid", (task.getOfflinePlayer() != null) ? task.getOfflinePlayer().getUniqueId().toString() : "-");
        replacements.put("seconds", Long.toString(task.getTime() / 1000));
        replacements.put("ms", Long.toString(task.getTime()));
        replacements.put("now-seconds", Long.toString(now / 1000));
        replacements.put("now-ms", Long.toString(now));

        for (String broadcast : getBroadcasts()) {
            if (getBroadcastPermission() != null) {
                Bukkit.broadcast(prepareMessage(broadcast, replacements),
                                 getBroadcastPermission());
            } else {
                Bukkit.broadcastMessage(prepareMessage(broadcast, replacements));
            }
        }

        // Delay login messages for visibility.
        Bukkit.getScheduler().runTaskLater(OddJob.PLUGIN, () -> {
            Player player = task.getPlayer();
            if (player != null) {
                for (String message : getMessages()) {
                    String prepared = prepareMessage(message, replacements);
                    player.sendMessage(prepared);
                    if (OddJob.CONFIG.DEBUG_COMMANDS) {
                        OddJob.PLUGIN.getLogger().info("Tell " + player.getName() + ": " + prepared);
                    }
                }
            }
        }, 20);

        for (String command : getConsoleCommands()) {
            String replacedCommand = null;
            try {
                replacedCommand = prepareCommand(command, replacements);
                if (OddJob.CONFIG.DEBUG_COMMANDS) {
                    OddJob.PLUGIN.getLogger().info("Task " + task.getId() + " executing: " + replacedCommand);
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replacedCommand);
            } catch (Exception ex) {
                OddJob.PLUGIN.getLogger().severe(ex.getClass().getSimpleName() + " executing: " + replacedCommand);
            }
        }

        Player player = task.getPlayer();
        if (player != null) {
            for (String command : getPlayerCommands()) {
                String replacedCommand = null;
                try {
                    replacedCommand = prepareCommand(command, replacements);
                    if (OddJob.CONFIG.DEBUG_COMMANDS) {
                        OddJob.PLUGIN.getLogger().info("Task " + task.getId() + " executing for " +
                                                       player.getName() + ": " + replacedCommand);
                    }
                    Bukkit.dispatchCommand(player, replacedCommand);
                } catch (Exception ex) {
                    OddJob.PLUGIN.getLogger().severe(ex.getClass().getSimpleName() + " executing for " +
                                                     player.getName() + ": " + replacedCommand);
                }
            }
        }
    } // execute

    // ------------------------------------------------------------------------
    /**
     * Describe this task type to the sender.
     * 
     * Invalid variable substitutions are shown in red, whereas valid variables
     * are highlighted in yellow.
     * 
     * @param sender the command sender asking for a description of htis task
     *        type.
     */
    public void describe(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Task type: " + ChatColor.YELLOW + getId());
        sender.sendMessage(ChatColor.GOLD + "Permission: " + ChatColor.YELLOW + getPermission());
        sender.sendMessage(ChatColor.GOLD + "Marked online: " + ChatColor.YELLOW + _online);
        sender.sendMessage(ChatColor.GOLD + "Forced online: " + ChatColor.YELLOW + _forceOnline);
        String broadcastPermissionClause = getBroadcastPermission() != null ? "players with permission " + ChatColor.YELLOW +
                                                                              getBroadcastPermission() + ChatColor.GOLD + ":"
                                                                            : "all players:";
        if (getBroadcasts().size() != 0) {
            sender.sendMessage(ChatColor.GOLD + "Broadcast to " + broadcastPermissionClause);
            for (String broadcast : getBroadcasts()) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', replaceDescription(broadcast)));
            }
        }

        if (getMessages().size() != 0) {
            sender.sendMessage(ChatColor.GOLD + "Messages:");
            for (String message : getMessages()) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', replaceDescription(message)));
            }
        }

        if (getConsoleCommands().size() != 0) {
            sender.sendMessage(ChatColor.GOLD + "Console commands:");
            for (String command : getConsoleCommands()) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', replaceDescription(command)));
            }
        }

        if (getPlayerCommands().size() != 0) {
            sender.sendMessage(ChatColor.GOLD + "Player commands:");
            for (String command : getPlayerCommands()) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', replaceDescription(command)));
            }
        }
    } // describe

    // ------------------------------------------------------------------------
    /**
     * Return the unique ID of this task type.
     * 
     * @return the unique ID of this task type.
     */
    public String getId() {
        return _id;
    }

    // ------------------------------------------------------------------------
    /**
     * Return permission required by the target player for the task to execute.
     * 
     * If there is a target player and they do not have the specified
     * permission, the task will do nothing when executed.
     * 
     * @return the permission, or null if the target player does not require a
     *         permission.
     */
    public String getPermission() {
        return _permission;
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the target player must be online for this task type to
     * run.
     * 
     * (Irrelevant if there is no target player.)
     * 
     * This method considers both the explicit {@code online:} setting in the
     * task type configuration and any online requirement implied by configured
     * player messages, player commands or {@code /runas} commands for a player
     * that is not console.
     * 
     * @return true if the target player must be online.
     */
    public boolean isOnline() {
        return _online || _forceOnline;
    }

    // ------------------------------------------------------------------------
    /**
     * Return a list of broadcasts to be sent when the task runs.
     * 
     * @return a list of broadcasts to be sent when the task runs.
     */
    public List<String> getBroadcasts() {
        return _broadcasts;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the permission required to receive broadcast messages from this
     * task, or null if all players should receive broadcasts.
     * 
     * @return the permission required to receive broadcast messages from this
     *         task, or null if all players should receive broadcasts.
     */
    public String getBroadcastPermission() {
        return _broadcastPermission;
    }

    // ------------------------------------------------------------------------
    /**
     * Return a list of messages to send to the target player.
     * 
     * @return a list of messages to send to the target player.
     */
    public List<String> getMessages() {
        return _messages;
    }

    // ------------------------------------------------------------------------
    /**
     * Return a list of commands to execute in the console.
     * 
     * @return a list of commands to execute in the console.
     */
    public List<String> getConsoleCommands() {
        return _consoleCommands;
    }

    // ------------------------------------------------------------------------
    /**
     * Return a list of commands to execute as the target player.
     * 
     * @return a list of commands to execute as the target player.
     */
    public List<String> getPlayerCommands() {
        return _playerCommands;
    }

    // ------------------------------------------------------------------------
    /**
     * Replace all variable references in the string s according to the
     * replacements array.
     * 
     * @param s a string that may contain variable references of the form
     *        %name%.
     * @param replacements an array of alternating variable names (sans "%") and
     *        their corresponding string values.
     * @return the string with all variable references replaced by their values.
     */
    public static String replace(String s, String... replacements) {
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("replacements should contain an even number of Strings");
        }
        for (int i = 0; i < replacements.length; i += 2) {
            s = s.replace('%' + replacements[i] + '%', replacements[i + 1]);
        }
        return s;
    }

    // ------------------------------------------------------------------------
    /**
     * Replace all variable references in the string s according to the
     * replacements map.
     * 
     * @param s a string that may contain variable references of the form
     *        %name%.
     * @param replacements a map from variable name (sans "%") to string value.
     * @return the string with all variable references replaced by their values.
     */
    public static String replace(String s, Map<String, String> replacements) {
        for (Entry<String, String> entry : replacements.entrySet()) {
            s = s.replace('%' + entry.getKey() + '%', entry.getValue());
        }
        return s;
    }

    // ------------------------------------------------------------------------
    /**
     * Highlight all known variables in a message or command string in lime
     * green if the variable has a defined replacement, or in red if the
     * variable name is invalid and will not be replaced when the task is run.
     * 
     * This method is used by {@link TaskType#describe(CommandSender)} to
     * describe task types.
     * 
     * @param s the command or message string.
     * @return the string with variables highlighted in lime or red.
     */
    public static String replaceDescription(String s) {
        String replaced = s.replaceAll("(%(?:\\w|-)+%)", "&c$1&f");
        String[] variables = { "id", "type", "player", "uuid", "seconds", "ms", "now-seconds", "now-ms" };
        String[] replacements = new String[variables.length * 2];
        for (int i = 0; i < variables.length; ++i) {
            replacements[2 * i] = variables[i];
            replacements[2 * i + 1] = "&a%" + variables[i] + '%';
        }
        return replace(replaced, replacements);
    }

    // ------------------------------------------------------------------------
    /**
     * Prepare a command for execution by replacing variables and dropping the
     * first leading / in the command (if present).
     * 
     * @param command the command.
     * @param replacements a map from variable name to string value.
     * @return the prepared command.
     */
    private String prepareCommand(String command, HashMap<String, String> replacements) {
        return replace(command.startsWith("/") ? command.substring(1) : command, replacements);
    }

    // ------------------------------------------------------------------------
    /**
     * Prepare a direct or broadcast message for transmission by replacing
     * variables and converting alternate colour codes.
     * 
     * @param message the message.
     * @param replacements a map from variable name to string value.
     * @return the prepared message.
     */
    private String prepareMessage(String message, HashMap<String, String> replacements) {
        return ChatColor.translateAlternateColorCodes('&', replace(message, replacements));
    }

    // ------------------------------------------------------------------------
    /**
     * The unique ID of this task type.
     */
    private String _id;

    /**
     * The permission required to run tasks of this type, or null if not
     * required.
     */
    private String _permission;

    /**
     * True if this task is declared in the configuration as requiring the
     * target player to be online.
     */
    private boolean _online;

    /**
     * True if this task type is calculated to require an online target player
     * because of configured player messages, player commands, or console
     * commands that /runas a non-console player.
     */
    private boolean _forceOnline;

    /**
     * A list of broadcast messages to be shown when tasks of this type execute.
     */
    private List<String> _broadcasts;

    /**
     * The permission required to receive the broadcast messages, or null if all
     * players should see them.
     */
    private String _broadcastPermission;

    /**
     * A list of messages to be sent to the target player.
     */
    private List<String> _messages;

    /**
     * A list of console commands to execute, in order, and before all player
     * commands.
     */
    private List<String> _consoleCommands;

    /**
     * A list of commands to execute as the target player.
     */
    private List<String> _playerCommands;

} // class TaskType