package nu.nerd.oddjob.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import net.md_5.bungee.api.ChatColor;
import nu.nerd.oddjob.OddJob;

// ----------------------------------------------------------------------------
/**
 * Handles the {@code /oddjob} command.
 */
public class OddJobExecutor extends ExecutorBase {
    // ------------------------------------------------------------------------
    public OddJobExecutor() {
        super("oddjob", "help", "reload", "save-tasks", "load-tasks");
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

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            OddJob.CONFIG.reload();
            OddJob.PLUGIN.loadTaskTypes();
            sender.sendMessage(ChatColor.GOLD + OddJob.PLUGIN.getName() + " configuration reloaded.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("save-tasks")) {
            OddJob.PLUGIN.saveTasks();
            sender.sendMessage(ChatColor.GOLD + "Tasks saved.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("load-tasks")) {
            OddJob.PLUGIN.loadTasks();
            sender.sendMessage(ChatColor.GOLD + "Tasks loaded.");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Invalid arguments. Try /" + command.getName() + " help.");
        return true;
    }
} // class OddJobExecutor