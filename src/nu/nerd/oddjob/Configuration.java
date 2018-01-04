package nu.nerd.oddjob;

import java.util.logging.Logger;

import org.bukkit.configuration.file.FileConfiguration;

// ----------------------------------------------------------------------------
/**
 * Reads and exposes the plugin configuration.
 */
public class Configuration {
    /**
     * If true, log the configuration on reload.
     */
    public boolean DEBUG_CONFIG;

    /**
     * If true, log actions performed in event handlers.
     */
    public boolean DEBUG_EVENTS;

    /**
     * If true, log commands executed by tasks.
     */
    public boolean DEBUG_COMMANDS;

    /**
     * If true, log task scheduling decisions.
     */
    public boolean DEBUG_TASKS;

    /**
     * The number of ticks between checks of the task queue.
     */
    public int TASK_PERIOD_TICKS;

    // ------------------------------------------------------------------------
    /**
     * Load the plugin configuration.
     */
    public void reload() {
        OddJob.PLUGIN.reloadConfig();
        FileConfiguration config = OddJob.PLUGIN.getConfig();
        Logger logger = OddJob.PLUGIN.getLogger();

        DEBUG_CONFIG = config.getBoolean("debug.config");
        DEBUG_EVENTS = config.getBoolean("debug.events");
        DEBUG_COMMANDS = config.getBoolean("debug.commands");
        DEBUG_TASKS = config.getBoolean("debug.tasks");
        TASK_PERIOD_TICKS = Math.max(1, config.getInt("task-period-ticks"));

        if (DEBUG_CONFIG) {
            logger.info("Configuration:");
            logger.info("DEBUG_EVENTS: " + DEBUG_EVENTS);
            logger.info("DEBUG_COMMANDS: " + DEBUG_COMMANDS);
            logger.info("DEBUG_TASKS: " + DEBUG_TASKS);
            logger.info("TASK_PERIOD_TICKS: " + TASK_PERIOD_TICKS);
        }
    } // reload
} // class Configuration