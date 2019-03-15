package nu.nerd.oddjob;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.permission.Permission;
import nu.nerd.oddjob.commands.ExecutorBase;
import nu.nerd.oddjob.commands.OddJobExecutor;
import nu.nerd.oddjob.commands.RunAsExecutor;
import nu.nerd.oddjob.commands.TaskExecutor;

// ----------------------------------------------------------------------------
/**
 * Main plugin class.
 */
public class OddJob extends JavaPlugin implements Listener {
    /**
     * This plugin as singleton.
     */
    public static OddJob PLUGIN;

    /**
     * Configuration as singleton.
     */
    public static Configuration CONFIG = new Configuration();

    // ------------------------------------------------------------------------
    /**
     * Return the Vault permission API.
     * 
     * @return the Vault permission API.
     */
    public Permission getPermissionAPI() {
        return _permissionAPI;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the {@link TaskType} with the specified unique ID.
     * 
     * @param id the unique ID of the task type.
     * @return the {@link TaskType} with the specified unique ID.
     */
    public TaskType getTaskType(String id) {
        return _taskTypes.get(id);
    }

    // ------------------------------------------------------------------------
    /**
     * Get a collection of all know task types.
     * 
     * @return the task types.
     */
    public Collection<TaskType> getAllTaskTypes() {
        return _taskTypes.values();
    }

    // ------------------------------------------------------------------------
    /**
     * Return the object that schedules {@link Task}s.
     * 
     * @return the object that schedules {@link Task}s.
     */
    public TaskScheduler getTaskScheduler() {
        return _taskScheduler;
    }

    // ------------------------------------------------------------------------
    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {
        PLUGIN = this;
        saveDefaultConfig();
        CONFIG.reload();
        loadTaskTypes();
        loadTasks();

        _permissionAPI = Bukkit.getServicesManager().getRegistration(Permission.class).getProvider();

        addCommandExecutor(new OddJobExecutor());
        addCommandExecutor(new TaskExecutor());
        addCommandExecutor(new RunAsExecutor());

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, new TaskRunner(), CONFIG.TASK_PERIOD_TICKS);
    }

    // ------------------------------------------------------------------------
    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onDisable()
     */
    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        saveTasks();
    }

    // ------------------------------------------------------------------------
    /**
     * When a player joins, schedule any overdue tasks pertinent to them that
     * were delayed because they required the player to be online.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private void onPlayerJoin(PlayerJoinEvent event) {
        if (CONFIG.DEBUG_EVENTS) {
            getLogger().info(event.getPlayer().getName() + " logged in.");
        }
        getTaskScheduler().executeOverdueTasksFor(event.getPlayer());
    }

    // ------------------------------------------------------------------------
    /**
     * Load all {@link TaskType} definitions from the configuration file.
     */
    public void loadTaskTypes() {
        reloadConfig();
        _taskTypes.clear();
        ConfigurationSection tasks = getConfig().getConfigurationSection("tasks");
        for (String id : tasks.getKeys(false)) {
            ConfigurationSection taskSection = tasks.getConfigurationSection(id);
            TaskType type = new TaskType();
            type.load(taskSection);
            _taskTypes.put(type.getId(), type);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Load all task instances from {@code tasks.yml}.
     */
    public void loadTasks() {
        FileConfiguration tasksConfig = new YamlConfiguration();
        try {
            tasksConfig.load(getTasksFile());
            getTaskScheduler().load(tasksConfig, getLogger());
        } catch (IOException | InvalidConfigurationException ex) {
            getLogger().severe(ex.getClass().getName() + " loading player tasks: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Save all task instances to {@code tasks.yml}.
     */
    public void saveTasks() {
        FileConfiguration tasksConfig = new YamlConfiguration();
        try {
            getTaskScheduler().save(tasksConfig, getLogger());
            tasksConfig.save(getTasksFile());
        } catch (IOException ex) {
            getLogger().severe(ex.getClass().getName() + " saving player tasks: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Return the file used to store task instances.
     * 
     * @return the file used to store task instances.
     */
    private File getTasksFile() {
        return new File(getDataFolder(), "tasks.yml");
    }

    // ------------------------------------------------------------------------
    /**
     * Add the specified CommandExecutor and set it as its own TabCompleter.
     * 
     * @param executor the CommandExecutor.
     */
    private void addCommandExecutor(ExecutorBase executor) {
        PluginCommand command = getCommand(executor.getName());
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    // ------------------------------------------------------------------------
    /**
     * Bukkit scheduler task implementation that runs the {@link TaskScheduler}
     * task queue.
     */
    final class TaskRunner implements Runnable {
        // --------------------------------------------------------------------
        /**
         * @see java.lang.Runnable#run()
         */
        @Override
        public void run() {
            try {
                getTaskScheduler().runPendingTasks();
            } catch (Exception ex) {
                getLogger().warning(ex.getClass().getSimpleName() + " thrown running pending tasks: " +
                                    ex.getMessage());
            }
            Bukkit.getScheduler().runTaskLater(OddJob.this, this, CONFIG.TASK_PERIOD_TICKS);
        }
    };

    // ------------------------------------------------------------------------
    /**
     * Map from task type ID to TaskType instance.
     */
    private final HashMap<String, TaskType> _taskTypes = new HashMap<>();

    /**
     * Schedules execution of task instances.
     */
    public static TaskScheduler _taskScheduler = new TaskScheduler();

    /**
     * The Vault permission API.
     */
    private Permission _permissionAPI;
} // class OddJob