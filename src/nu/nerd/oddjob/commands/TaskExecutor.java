package nu.nerd.oddjob.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.google.common.collect.ImmutableMap;

import net.md_5.bungee.api.ChatColor;
import nu.nerd.oddjob.OddJob;
import nu.nerd.oddjob.Task;
import nu.nerd.oddjob.TaskType;

// ----------------------------------------------------------------------------
/**
 * Handles the {@code /task} command.
 */
public class TaskExecutor extends ExecutorBase {
    // ------------------------------------------------------------------------
    /**
     * Constructor.
     */
    public TaskExecutor() {
        super("task", "help", "types", "describe", "run", "cancel");
    }

    // ------------------------------------------------------------------------
    /**
     * @see org.bukkit.command.CommandExecutor#onCommand(org.bukkit.command.CommandSender,
     *      org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
            return false;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("types")) {
            sender.sendMessage(ChatColor.GOLD + "Task types: " +
                               OddJob.PLUGIN.getAllTaskTypes().stream().map(t -> ChatColor.YELLOW + t.getId())
                               .collect(Collectors.joining(ChatColor.WHITE + ", ")));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("describe")) {
            String taskTypeArg = args[1];
            TaskType taskType = OddJob.PLUGIN.getTaskType(taskTypeArg);
            if (taskType == null) {
                sender.sendMessage(ChatColor.RED + "There is no task type named " + taskTypeArg + ".");
            } else {
                taskType.describe(sender);
            }
            return true;
        }

        if ((args.length == 4 || args.length == 5) && args[0].equalsIgnoreCase("run")) {
            long now = System.currentTimeMillis();
            HashMap<String, String> replacements = new HashMap<String, String>();
            replacements.put("now-seconds", Long.toString(now / 1000));
            replacements.put("now-ms", Long.toString(now));
            String taskIdArg = TaskType.replace(args[1], replacements);

            String taskTypeArg = args[2];
            String playerArg = args[3];
            String timeArg = (args.length == 5) ? args[4] : null;

            TaskType taskType = OddJob.PLUGIN.getTaskType(taskTypeArg);
            if (taskType == null) {
                sender.sendMessage(ChatColor.RED + "There is no task type named " + taskTypeArg + ".");
                return true;
            }

            OfflinePlayer player = null;
            if (playerArg.equals("null")) {
                player = null;
            } else {
                try {
                    player = Bukkit.getOfflinePlayer(UUID.fromString(playerArg));
                } catch (IllegalArgumentException ex) {
                    player = Bukkit.getOfflinePlayer(playerArg);
                }
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "There is no player matching " + playerArg + ".");
                    return true;
                }
            }

            long time;
            if (timeArg == null) {
                time = now;
            } else {
                Object parsedTime = parseTime(timeArg);
                if (parsedTime instanceof Long) {
                    time = (Long) parsedTime;
                } else {
                    sender.sendMessage(ChatColor.RED + (String) parsedTime);
                    return true;
                }
            }

            OddJob.PLUGIN.getTaskScheduler().scheduleTask(new Task(taskIdArg, taskTypeArg, player, time));
            if (sender instanceof Player) {
                // If the sender is in-game...
                double relativeSeconds = 0.001 * (time - now);
                String target = (player == null) ? " with no target"
                                                 : " on " + ChatColor.YELLOW + player.getName();
                sender.sendMessage(ChatColor.GOLD + "Scheduled " + ChatColor.YELLOW + taskIdArg +
                                   ChatColor.GOLD + " of type " + ChatColor.YELLOW + taskTypeArg +
                                   ChatColor.GOLD + target +
                                   ChatColor.GOLD + " in " + ChatColor.YELLOW + String.format("%.1f", relativeSeconds) +
                                   ChatColor.GOLD + " seconds.");
            }
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("cancel")) {
            String taskIdArg = args[1];
            if (OddJob.PLUGIN.getTaskScheduler().cancelTask(taskIdArg)) {
                sender.sendMessage(ChatColor.GOLD + "Task " + ChatColor.YELLOW + taskIdArg +
                                   ChatColor.GOLD + " was cancelled.");
            } else {
                sender.sendMessage(ChatColor.RED + "There is task with the ID \"" + taskIdArg + "\".");
            }
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Invalid arguments. Try /" + command.getName() + " help.");
        return true;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the task time, parsed from a command line argument.
     * 
     * @param timeArg the command line argument.
     * @return either the number of milliseconds since epoch, as a long, or a
     *         string error message.
     */
    private Object parseTime(String timeArg) {
        if (timeArg.startsWith("@")) {
            try {
                return 1000 * Long.valueOf(timeArg.substring(1));
            } catch (NumberFormatException ex) {
                return "Invalid absolute time: expecting an integer after '@'.";
            }
        } else if (timeArg.startsWith("+")) {
            String suffix = timeArg.substring(1).toLowerCase();
            Pattern expected = Pattern.compile("^(\\d+[hms])+$");
            if (!expected.matcher(suffix).matches()) {
                return "Invalid relative time: expecting '+' then a sequence of integers followed by 'h', 'm' or 's'.";
            }

            long seconds = 0;
            int num = 0;
            for (int i = 0; i < suffix.length(); ++i) {
                char c = suffix.charAt(i);
                int digit = c - '0';
                if (digit >= 0 && digit <= 9) {
                    num = 10 * num + digit;
                } else {
                    seconds += num * UNITS_TO_S.get(c);
                    num = 0;
                }
            }
            return 1000 * seconds + System.currentTimeMillis();
        } else {
            return "Time arguments must begin with '@' or '+'.";
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Map from single letter suffix in relative times to corresponding number
     * of seconds.
     */
    private final Map<Character, Integer> UNITS_TO_S = ImmutableMap.of('h', 3600, 'm', 60, 's', 1);
} // class TaskExecutor