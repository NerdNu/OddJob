OddJob
======
A Bukkit plugin that executes scheduled tasks.

The plugin is analogous to the UNIX `at` command. Tasks execute only a single
time, though in principle a task could run a command to re-schedule itself at
a later time.

If you're looking for a plugin that runs repeated tasks like the UNIX `cron`
command, there are options available on the 
[Bukkit](https://dev.bukkit.org/search?search=cron) and
[Spigot](https://www.spigotmc.org/search/60277780/?q=cron&t=resource_update&o=relevance) 
plugin lists.


Tasks
-----
Tasks (or *task instances*) are composed of:

 * A unique identifier, distinct from that of all other *tasks*.
 * A time at which the task is scheduled to execute.
 * An optional target player who, if specified, is affected by the task's
   actions.
 * A *task type* that specifies the actions performed by the task.


Task Types
----------
The *task type* defines the actions of all tasks that have that type. It is,
in effect, a simple script.

Task types are defined in the OddJob configuration `config.yml` as a 
configuration section whose name uniquely identifies the task type. Each task
type sections contains the following keys:

 * `permission` - If not `null`, the *task*'s target player must possess this permission or the task will do nothing when executed.
 * `online` - If `true`, execution of the task will be delayed after it's appointed time until the target player is online.
 * `broadcasts` - A list of broadcast messages to be shown when the task executes.
 * `broadcast-permission` - An optional permission that players must have to receive broadcasts; if unspecified, all players receive broadcasts from tasks of this type.
 * `messages` - A list of messages sent to the task's target player.
 * `console-commands` - A list of commands executed in the server console (with unlimited permissions).
 * `player-commands` - A list of commands executed as the task's target player.


Variable Substitution
---------------------
Messages, broadcasts undergo substitution of the following variables:

 * `%id%` - The unique ID of the currently executing *task*.
 * `%type%` - The unique ID of the *task type* of the task.
 * `%player%` - The name of the target player, or `-` if `null` (not specified).
 * `%uuid%` - The UUID of the target player, or `-` if `null` (not specified).
 * `%seconds%` - The task time specified as seconds since Epoch [1].
 * `%ms%` - The task time specified as milliseconds since Epoch.
 * `%now-seconds%` - The current time specified as seconds since Epoch. Note that this may be later than `%seconds` if the task was delayed waiting for the target player to log in.
 * `%now-ms%` - The current time specified as milliseconds since Epoch.
 
When a new task is defined, `%now-seconds%` and `%now-ms%` can be substituted
into the task's unique ID. The latter variable, in particular should result in
an ID that is always unique.  

Messages and broadcasts also undergo alternate colour code substitution, e.g.
'&e' for yellow.

*[1]: Epoch is January 1, 1970.*


Forced Online Task Types
------------------------
If a task type has messages, player commands, or console commands that use
`/run-as <player> <command>` to run a command as a specified player, then
the it needs the target player to be online for correct functioning. In this
situation, the task is marked *forced online* and behaves as if the task type
had `online` set to `true`.


Task Persistence
----------------
Online tasks can be delayed indefinitely waiting for their target player to
log in. Additionally, tasks may be scheduled many days into the future. For
these reasons, tasks are saved in `OddJob/tasks.yml` when the plugin shuts
down, and loaded from there when the plugin starts up.


Task Execution
--------------
Tasks that are of a type defined to be `online` or determined to be forced 
online have their execution delayed after their scheduled time until the target
player logs in. (If the target player is `null`, they won't be delayed.)

When a task is due to run, and the target player is online (if required to be 
so), tasks perform their actions in the following order:

 1. Check that the target player (if non-null) has the required permission (if 
   set).
 1. Send out all broadcast messages.
 1. Send all player messages to the target player.
 1. Run all console commands.
 1. Run all player commands as the target player.

In order to give administrators greater flexibility in the ordering of commands,
OddJob defines a `/run-as` command that can run other commands as a specified
player. The intent is to use this command among the list of console commands
in order to effectively interleave player commands with other commands that
affect the server as a whole, such as granting or revoking permissions.

Expansion of the actions of tasks to include control structures (complex 
conditionals, loops) is considered to be a bad idea; it would lead to overly
convoluted YAML syntax. If you need to make tasks with more complicated control
flows, it is recommended that you write a custom plugin command or a script
in CommandHelper or some similar language.


Task Creation, Redefinition, Rescheduling and Cancellation
----------------------------------------------------------
Tasks are created and cancelled with the `/task` command (described later).
If a task is created with the same ID as an existing task, the existing task
is replaced with the new definition. The new task *could* be completely 
different - a different time, task type and target player. However, if the new
task has the same task type and target, the effect is the same as if the
original task was simply rescheduled to a different time.


Motivation and Example
----------------------
Consider giving players a reward for voting that expires after 24 hours and
must be renewed by voting again. Let us imagine that the reward consists of
permission to use a command.

In OddJob's `config.yml` we define a task type `vote` to grant the reward
and task type `unvote` to revoke it:
```
tasks:
  vote:
    messages:
    - '&3Thanks for voting! Have a reward!'
    console-commands:
    - '/exec u:%player% a:addperm v:someplugin.somepermission w:world'

  unvote:
    permission: someplugin.somepermission
    messages:
    - '&3It has been more than 24 hours since you voted.'
    - '&3Your voter reward has expired.'
    console-commands:
    - '/exec u:%player% a:rmperm v:someplugin.somepermission w:world'
```

Both task types specify player messages, so even though neither is declared
`online: true`, they are implicitly so ("forced online").

When the plugin that handles incoming vote notifications receives notice that
the player has voted, it runs the following two commands:

 * `/task run vote-%player%   vote   %player%`
 * `/task run unvote-%player% unvote %player% +24h`
 
Note that it is the responsibility of the vote handling plugin (not OddJob)
to substitute the player name where `%player%` appears, and therefore, 
depending on the plugin, a different syntax may be used.

If player *totemo* casts a vote, then the first of these commands runs a task
of type `vote`, with a target *totemo* and the unique ID `vote-totemo`.
Since no time stamp is specified, the `vote` task type executes immediately,
granting *totemo* the `someplugin.somecommand` permission. Since the
`vote-totemo` task has now executed, the task is removed from the queue.

The second command schedules execution of a task of type `unvote`, with 
target *totemo* and ID `unvote-totemo` 24 hours in the future. When that
task executes, it will revoke *totemo*'s `someplugin.somecommand` 
permission. The permission check is redundant, but does no harm.

If *totemo* votes again before the reward has expired, the same commands will
be run again:
```
/task run vote-totemo   vote   totemo
/task run unvote-totemo unvote totemo +24h
```
The effect will be to redefine `unvote-totemo` with a new time that is 24 
hours further into the future. The original revocation of the voting reward
is effectively cancelled, ensuring that the reward is not taken away
prematurely.


Commands
--------


Configuration
-------------


Permissions
-----------

