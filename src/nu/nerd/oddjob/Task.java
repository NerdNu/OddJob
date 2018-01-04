package nu.nerd.oddjob;

import java.util.Comparator;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

// ----------------------------------------------------------------------------
/**
 * Represents an instance of a task whose type is a {@link TaskType}.
 * 
 * Tasks refer to their {@link TaskType} indirectly through the type's ID, so
 * that they update correctly if task types are reloaded.
 */
public class Task implements Comparable<Task> {
    // ------------------------------------------------------------------------
    /**
     * Default constructor for use with
     * {@link Task#load(ConfigurationSection, Logger)}.
     */
    public Task() {
    }

    // ------------------------------------------------------------------------
    /**
     * Constructor.
     * 
     * @param id the unique ID of this task instance.
     * @param taskTypeId the unique ID of the task type.
     * @param player the offline player that is the target of the task (can be
     *        null).
     * @param time the time at which this task is due to execute, expressed as a
     *        number of milliseconds since Epoch.
     */
    public Task(String id, String taskTypeId, OfflinePlayer player, long time) {
        _id = id;
        _taskTypeId = taskTypeId;
        _offlinePlayer = player;
        _time = time;
    }

    // ------------------------------------------------------------------------
    /**
     * Execute this task.
     */
    public void execute() {
        TaskType taskType = getTaskType();
        if (taskType != null) {
            if (OddJob.CONFIG.DEBUG_TASKS) {
                OddJob.PLUGIN.getLogger().info("Executing task " + getId() + " of type " + getTaskTypeId() + ".");
            }
            taskType.execute(this);
        } else {
            Logger logger = OddJob.PLUGIN.getLogger();
            logger.warning("Task " + getId() + " did nothing because its task type (" +
                           getTaskTypeId() + ") is invalid.");
        }
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if this Task has no player target, requires no specific
     * permission, or if there is a target and he has the required permission.
     * 
     * @return true if permission requirements are satisfied.
     */
    public boolean isPermissionSatisfied() {
        // TODO: allow configurable default world?
        return getOfflinePlayer() == null ||
               getTaskType().getPermission() == null ||
               OddJob.PLUGIN.getPermissionAPI().playerHas("world", getOfflinePlayer(), getTaskType().getPermission());
    }

    // ------------------------------------------------------------------------
    /**
     * Return true if the task does not require the player to be online, or if
     * the player is online.
     * 
     * @return true if the online requirement is satisfied.
     */
    public boolean isOnlineSatisfied() {
        if (!getTaskType().isOnline()) {
            if (OddJob.CONFIG.DEBUG_TASKS) {
                OddJob.PLUGIN.getLogger().info("Player not required to be online.");
            }
            return true;
        }

        if (getOfflinePlayer() == null) {
            if (OddJob.CONFIG.DEBUG_TASKS) {
                OddJob.PLUGIN.getLogger().info("Online check satisfied: no player required.");
            }
            return true;
        }

        Player player = getPlayer();
        boolean result = (player != null);
        if (OddJob.CONFIG.DEBUG_TASKS) {
            if (result) {
                OddJob.PLUGIN.getLogger().info("Online check satisfied: " + getPlayerName() + " is online.");
            } else {
                OddJob.PLUGIN.getLogger().info("Online check failed: " + getPlayerName() + " is not online.");
            }
        }
        return result;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the unique ID of this task.
     * 
     * @return the unique ID of this task.
     */
    public String getId() {
        return _id;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the task type.
     * 
     * @return the task type.
     */
    public TaskType getTaskType() {
        return OddJob.PLUGIN.getTaskType(_taskTypeId);
    }

    // ------------------------------------------------------------------------
    /**
     * Return the unique ID of the task type.
     * 
     * @return the unique ID of the task type.
     */
    public String getTaskTypeId() {
        return _taskTypeId;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the OfflinePlayer that is the target of this task, or null if
     * there is no target.
     * 
     * @return the target offline player.
     */
    public OfflinePlayer getOfflinePlayer() {
        return _offlinePlayer;
    }

    // ------------------------------------------------------------------------
    /**
     * Return the player that is the target of the task, or null if offline, or
     * if there is no target.
     * 
     * Testing of running overdue tasks at login reveals that
     * getOfflinePlayer().getPlayer() returns a non-null Player instance that
     * does not deliver messages to the player. This is apparently because
     * getOfflinePlayer() caches a Player reference that becomes invalid when
     * the player logs out. (Then we use the invalid reference on the next
     * login.) So we get the Player by UUID; that works.
     * 
     * @return the target player if online, or null.
     */
    public Player getPlayer() {
        return (getOfflinePlayer() == null) ? null : Bukkit.getPlayer(getOfflinePlayer().getUniqueId());
    }

    // ------------------------------------------------------------------------
    /**
     * Return the name of the target player, or null if there is no target.
     * 
     * @return the target player name, or null.
     */
    public String getPlayerName() {
        return (getOfflinePlayer() == null) ? null : getOfflinePlayer().getName();
    }

    // ------------------------------------------------------------------------
    /**
     * Return the time the task is scheduled to run, expressed as milliseconds
     * since Epoch.
     * 
     * @return the task time.
     */
    public long getTime() {
        return _time;
    }

    // ------------------------------------------------------------------------
    /**
     * Save this task instance to a configuration section whose name is the task
     * ID.
     * 
     * @param parentSection the section containing the section created to store
     *        this task.
     */
    public void save(ConfigurationSection parentSection) {
        ConfigurationSection section = parentSection.createSection(getId());
        section.set("task-type", getTaskTypeId());
        if (getOfflinePlayer() != null) {
            section.set("player-name", getOfflinePlayer().getName());
            section.set("player-uuid", getOfflinePlayer().getUniqueId().toString());
        }
        section.set("time", getTime());
    }

    // ------------------------------------------------------------------------
    /**
     * Load this task from a section whose name is the task's ID.
     * 
     * @param section the task section.
     * @param logger a logger used for reporting errors.
     * @return true if the task loaded successfully; false on error.
     */
    public boolean load(ConfigurationSection section, Logger logger) {
        _id = section.getName();

        _taskTypeId = section.getString("task-type");
        if (getTaskType() == null) {
            logger.warning("Task " + section.getName() + " has an invalid type: " + _taskTypeId);
        }

        String playerUuidString = section.getString("player-uuid");
        if (playerUuidString == null) {
            _offlinePlayer = null;
        } else {
            try {
                _offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(playerUuidString));
            } catch (IllegalArgumentException ex) {
                logger.severe("could not load task " + section.getName() + " - invalid player UUID: " + playerUuidString);
                return false;
            }
        }

        _time = section.getLong("time");
        return true;
    }

    // --------------------------------------------------------------------------
    /**
     * @see java.lang.Object#hashCode()
     * 
     *      Generated by Eclipse.
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((_id == null) ? 0 : _id.hashCode());
        result = prime * result + ((_offlinePlayer == null) ? 0 : _offlinePlayer.getUniqueId().hashCode());
        result = prime * result + ((_taskTypeId == null) ? 0 : _taskTypeId.hashCode());
        result = prime * result + (int) (_time ^ (_time >>> 32));
        return result;
    }

    // --------------------------------------------------------------------------
    /**
     * @see java.lang.Object#equals(java.lang.Object)
     * 
     *      Generated by Eclipse.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Task)) {
            return false;
        }
        Task other = (Task) obj;
        if (_id == null) {
            if (other._id != null) {
                return false;
            }
        } else if (!_id.equals(other._id)) {
            return false;
        }
        if (_offlinePlayer == null) {
            if (other._offlinePlayer != null) {
                return false;
            }
        } else if (!_offlinePlayer.equals(other._offlinePlayer)) {
            return false;
        }
        if (_taskTypeId == null) {
            if (other._taskTypeId != null) {
                return false;
            }
        } else if (!_taskTypeId.equals(other._taskTypeId)) {
            return false;
        }
        if (_time != other._time) {
            return false;
        }
        return true;
    }

    // --------------------------------------------------------------------------
    /**
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(Task other) {
        return COMPARATOR.compare(this, other);
    }

    // ------------------------------------------------------------------------
    /**
     * Comparator used to implement {@link #compareTo(Task)}.
     * 
     * This comparator enforces time ordering of overdue tasks, even though the
     * task ID is unique and therefore sufficient to establish separate identity
     * in the overdue task set.
     * 
     * This would break if we allowed the time to be modified after creation.
     * Instead, we remove the task and create a new instance with the modified
     * time.
     */
    private final Comparator<Task> COMPARATOR = Comparator
    .comparing(Task::getTime)
    .thenComparing(Task::getId);
    // Redundant after the ID, but interesting:
    // .thenComparing(Task::getTaskTypeId)
    // .thenComparing(Task::getPlayerName,
    // Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * Unique ID of this task.
     */
    private String _id;

    /**
     * The task type
     */
    private String _taskTypeId;

    /**
     * The target player, or null.
     */
    private OfflinePlayer _offlinePlayer;

    /**
     * The task's scheduled time expressed as milliseconds since Epoch.
     */
    private long _time;
} // class Task