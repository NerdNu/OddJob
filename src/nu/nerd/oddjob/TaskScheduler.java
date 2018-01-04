package nu.nerd.oddjob;

import java.util.HashMap;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.google.common.collect.TreeMultimap;

// ----------------------------------------------------------------------------
/**
 * An object that schedules execution of {@link Task} instances.
 */
public class TaskScheduler {
    // ------------------------------------------------------------------------
    /**
     * Run all {@link Task} instances that have fallen due since the last time
     * this method was called.
     * 
     * Any tasks that cannot run because they refer to a player that is not
     * currently online (that needs to be) will be moved to the overdue queue
     * for that player.
     */
    public void runPendingTasks() {
        for (;;) {
            Long earliestTime = getEarliestTime();
            if (earliestTime == null || System.currentTimeMillis() < earliestTime) {
                return;
            }

            for (Task task : getTasksAtTime(earliestTime)) {
                if (OddJob.CONFIG.DEBUG_TASKS) {
                    OddJob.PLUGIN.getLogger().info("Task " + task.getId() + " is due.");
                }
                taskIsDue(task);
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Execute tasks that were delayed waiting for their target player to join
     * the server.
     * 
     * @param player the player whose overdue tasks are run.
     */
    public void executeOverdueTasksFor(Player player) {
        TreeSet<Task> overdue = _overdueTasks.remove(player.getUniqueId());
        if (overdue != null) {
            if (OddJob.CONFIG.DEBUG_TASKS) {
                OddJob.PLUGIN.getLogger().info(player.getName() + " has overdue tasks.");
            }
            for (Task task : overdue) {
                _tasksById.remove(task.getId());
                if (task.isPermissionSatisfied()) {
                    if (OddJob.CONFIG.DEBUG_TASKS) {
                        OddJob.PLUGIN.getLogger().info("Permission check satisfied for task " + task.getId() + ".");
                    }
                    task.execute();
                } else {
                    if (OddJob.CONFIG.DEBUG_TASKS) {
                        OddJob.PLUGIN.getLogger().info("Permission check failed for task " + task.getId() + ".");
                    }
                }
            }
        } else {
            if (OddJob.CONFIG.DEBUG_TASKS) {
                OddJob.PLUGIN.getLogger().info(player.getName() + " has no overdue tasks.");
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Add a new {@link Task} to the time scheduled execution queue, or execute
     * it immediately if it is due now (or some time in the past).
     * 
     * If a task is due to be executed now, but requires a player that is not
     * currently online, the task will be added to that player's overdue queue.
     * 
     * @param task the task to be scheduled for execution.
     */
    public void scheduleTask(Task task) {
        cancelTask(task.getId());
        if (System.currentTimeMillis() >= task.getTime()) {
            taskIsDue(task);
        } else {
            addPendingTask(task);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Cancel the task with the specified ID.
     * 
     * @param id the unique ID.
     * @return true if a task with the specified ID was found, otherwise false.
     */
    public boolean cancelTask(String id) {
        Task task = removePendingTask(id);
        boolean found = (task != null);
        if (found) {
            removeOverdueTask(task);
        }
        return found;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the task instance with the specified ID.
     * 
     * @param id the unique ID.
     * @return the task instance with the specified ID.
     */
    public Task getTask(String id) {
        return _tasksById.get(id);
    }

    // ------------------------------------------------------------------------
    /**
     * Return the absolute execution time of the earliest pending task, or null
     * if there are no tasks in the queue.
     * 
     * @return the earliest task's time, or null if there are no tasks in the
     *         queue.
     */
    public Long getEarliestTime() {
        Iterator<Long> it = _pendingTasks.keys().iterator();
        if (it.hasNext()) {
            return it.next();
        }
        return null;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the set of tasks scheduled to execut at the specified time.
     * 
     * @param time the time expressed in milliseconds from Epoch (1970-01-01).
     *        The value is boxed as it will be passed the result of
     *        {@link TaskScheduler#getEarliestTime()}.
     * @return the set of tasks scheduled to execut at the specified time.
     */
    public NavigableSet<Task> getTasksAtTime(Long time) {
        return _pendingTasks.get(time);
    }

    // ------------------------------------------------------------------------
    /**
     * Load all tasks from a configuration file.
     * 
     * @param parentSection the parent section under which tasks are serialised
     *        as one child section each.
     * @param logger a logger for reporting errors.
     */
    public void load(ConfigurationSection parentSection, Logger logger) {
        _tasksById.clear();
        _pendingTasks.clear();
        _overdueTasks.clear();

        for (String id : parentSection.getKeys(false)) {
            Task task = new Task();
            if (task.load(parentSection.getConfigurationSection(id), logger)) {
                scheduleTask(task);
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Save all tasks to a configuration file.
     * 
     * @param parentSection the parent section under which tasks are serialised
     *        as one child section each.
     * @param logger a logger for reporting errors.
     */
    public void save(ConfigurationSection parentSection, Logger logger) {
        for (Task task : _tasksById.values()) {
            task.save(parentSection);
        }
    }

    // --------------------------------------------------------------------------
    /**
     * Given a task from the pending task queue that is now due to execute,
     * execute it now if possible, or add it to its target player's overdue
     * queue for execution when that player logs in.
     * 
     * If the target player of the task is online (or there is no target) the
     * task can execute now, but if the target player does not have the task's
     * required permission the task will do nothing and simply be removed from
     * the pending task queue.
     * 
     * @param task the task.
     */
    protected void taskIsDue(Task task) {
        removePendingTask(task.getId());
        if (task.isOnlineSatisfied()) {
            if (task.isPermissionSatisfied()) {
                if (OddJob.CONFIG.DEBUG_TASKS) {
                    OddJob.PLUGIN.getLogger().info("Permission check satisfied for task " + task.getId() + ".");
                }
                task.execute();
            } else {
                if (OddJob.CONFIG.DEBUG_TASKS) {
                    OddJob.PLUGIN.getLogger().info("Permission check failed for task " + task.getId() + ".");
                }
            }
        } else {
            if (OddJob.CONFIG.DEBUG_TASKS) {
                OddJob.PLUGIN.getLogger().info("Add task " + task.getId() + " as overdue.");
            }
            addOverdueTask(task);
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Add a task to the time-ordered queue of tasks whose time is not yet due.
     * 
     * @param task the task.
     */
    protected void addPendingTask(Task task) {
        _pendingTasks.put(task.getTime(), task);
        _tasksById.put(task.getId(), task);
    }

    // ------------------------------------------------------------------------
    /**
     * Remove a task from the time-ordered queue of tasks whose time is not yet
     * due.
     * 
     * @param id the unique ID of the task.
     * @return the removed task, or null if there was no match on the ID.
     */
    protected Task removePendingTask(String id) {
        Task task = _tasksById.remove(id);
        if (task != null) {
            _pendingTasks.remove(task.getTime(), task);
        }
        return task;
    }

    // ------------------------------------------------------------------------
    /**
     * Add the specified task to the set of overdue tasks for its target player
     * (the task must have a non-null target).
     * 
     * The tasks will be executed when the player logs in.
     * 
     * @param task the task.
     */
    protected void addOverdueTask(Task task) {
        OfflinePlayer player = task.getOfflinePlayer();
        TreeSet<Task> tasks = _overdueTasks.get(player.getUniqueId());
        if (tasks == null) {
            tasks = new TreeSet<Task>();
            _overdueTasks.put(player.getUniqueId(), tasks);
        }
        tasks.add(task);
        _tasksById.put(task.getId(), task);
    }

    // ------------------------------------------------------------------------
    /**
     * Remove a task from the set of overdue tasks of its target player.
     * 
     * @param task the task.
     */
    protected void removeOverdueTask(Task task) {
        OfflinePlayer player = task.getOfflinePlayer();
        if (player != null) {
            TreeSet<Task> tasks = _overdueTasks.get(player.getUniqueId());
            if (tasks != null) {
                tasks.remove(task);
                if (tasks.isEmpty()) {
                    _overdueTasks.remove(player.getUniqueId());
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Map from task ID to pending task.
     * 
     * This map has an entry for each task regardless of whether it is pending
     * or overdue.
     */
    private final HashMap<String, Task> _tasksById = new HashMap();

    /**
     * Pending tasks in ascending order by time stamp.
     */
    private final TreeMultimap<Long, Task> _pendingTasks = TreeMultimap.create();

    /**
     * Map from player UUID to a set of overdue tasks that cannot execute until
     * the player logs in.
     * 
     * The key cannot be an OfflinePlayer because when we try to look up by a
     * player instance at login, the lookup may fail. This is because
     * OfflinePlayer.getPlayer() can cache an out-of-date Player instance from a
     * previous login that cannot do Player.sendMessage(), for instance.
     * 
     * Tasks in the set are in ascending order by time.
     */
    private final HashMap<UUID, TreeSet<Task>> _overdueTasks = new HashMap();

} // class TaskScheduler