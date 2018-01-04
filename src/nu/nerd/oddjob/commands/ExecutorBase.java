package nu.nerd.oddjob.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

// ----------------------------------------------------------------------------
/**
 * Abstract base class for command executors.
 * 
 * This class implements tab completion based on a list of first level
 * subcommands. Subclasses can override
 * {@link #onTabComplete(CommandSender, Command, String, String[])} to implement
 * tab completion of subsequent command arguments.
 */
public abstract class ExecutorBase implements CommandExecutor, TabCompleter {
    // ------------------------------------------------------------------------
    /**
     * Constructor.
     * 
     * @param name the name of this command, without the /.
     * @param subcommands subcommands that are the first argument of this
     *        command.
     */
    protected ExecutorBase(String name, String... subcommands) {
        _name = name;
        _subcommands = Arrays.asList(subcommands);
    }

    // ------------------------------------------------------------------------
    /**
     * Return the name of the executed command.
     * 
     * @return the name of the executed command.
     */
    public String getName() {
        return _name;
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the command sender is in the game.
     * 
     * If the sender is not in game, tell them that they must be.
     * 
     * @param sender the command sender.
     * @return true if the sender is in game.
     */
    public boolean inGame(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("You must be in game to use this command.");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------------
    /**
     * @see org.bukkit.command.TabCompleter#onTabComplete(org.bukkit.command.CommandSender,
     *      org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        ArrayList<String> completions = new ArrayList<String>();
        if (command.getName().equalsIgnoreCase(_name)) {
            if (args.length == 0) {
                completions.addAll(_subcommands);
            } else if (args.length == 1) {
                completions.addAll(_subcommands.stream().filter(sub -> sub.startsWith(args[0].toLowerCase())).collect(Collectors.toList()));
            }
        }
        return completions;
    }

    // ------------------------------------------------------------------------
    /**
     * The name of this command, without the /.
     */
    protected String _name;

    /**
     * Subcommands of this command.
     */
    protected List<String> _subcommands;
} // class ExecutorBase