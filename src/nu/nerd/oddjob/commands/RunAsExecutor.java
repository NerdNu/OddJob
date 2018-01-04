package nu.nerd.oddjob.commands;

import java.util.Arrays;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;
import nu.nerd.oddjob.OddJob;

// ----------------------------------------------------------------------------
/**
 * Handles the {@code /run-as} command.
 * 
 * The Minecraft server cannot run commands on behalf of players unless they are
 * online. Therefore, tasks that use {@code /run-as} in their console command
 * list must be flagged as requiring the player to be online. Note that tasks
 * that have player commands are automatically flagged as requiring the player
 * to be online. The only real advantage of using {@code /run-as} in the console
 * commands is that you can control the order of player commands relative to
 * console commands that affect the player. Conversely, all console commands run
 * before all player commands.
 */
public class RunAsExecutor extends ExecutorBase {
    // ------------------------------------------------------------------------
    /**
     * Constructor.
     */
    public RunAsExecutor() {
        super("runas", "help");
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

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Invalid arguments. Try /" + command.getName() + " help.");
        } else {
            String playerArg = args[0];
            CommandSender runner;
            if (playerArg.equalsIgnoreCase("console")) {
                runner = Bukkit.getConsoleSender();
            } else {
                Player player = Bukkit.getPlayer(playerArg);
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "Error: " + playerArg + " is offline.");
                    return true;
                }
                runner = player;
            }

            String commandLine = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            if (commandLine.startsWith("/")) {
                commandLine = commandLine.substring(1);
            }

            try {
                Bukkit.dispatchCommand(runner, commandLine);
            } catch (Exception ex) {
                Logger logger = OddJob.PLUGIN.getLogger();
                logger.severe(ex.getClass().getSimpleName() + " executing for " +
                              runner.getName() + ": " + commandLine);
            }
        }
        return true;
    }
} // class RunAsExecutor