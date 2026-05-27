# DevTrack

DevTrack is a personal work-tracking context for developers. It exists to describe the user's own units of work, tracked time, and reporting language without collapsing them into Jira's model.

## Language

**Task**:
The internal unit of work in DevTrack. A Task may stand alone or reference `0..n` Jira Tickets, and multiple Tasks may relate to the same Jira Ticket. A Task does not carry a canonical planned day; worked days belong to `Work Session` and the finished moment belongs to `Task Completion`.
_Avoid_: Ticket, issue, work item

**Jira Ticket**:
An external identifier from Jira that DevTrack detects and attaches to Tasks. It is used for grouping and reporting, but it is not the primary work object inside DevTrack.
_Avoid_: Task, DevTrack task

**Task Status**:
The lifecycle state of a Task. It describes the progress or outcome of the work itself, not the Task's placement in a screen or bucket. The canonical statuses are `Todo`, `Doing`, `Done`, and `Archived`.
_Avoid_: Level, lane, bucket

**Todo**:
The Task exists but no Work Session has happened for it yet.
_Avoid_: Backlog, planned

**Doing**:
The Task has already had work started on it and is not yet terminal. A Task moves from `Todo` to `Doing` on its first Work Session and stays there until it becomes `Done` or `Archived`. It stays separate from whether a Work Session is currently active or paused right now.
_Avoid_: In Progress session state, paused task

**Done**:
The terminal state meaning the Task reached its intended outcome. A Done Task may still have Work Sessions on earlier days and a distinct later `Task Completion` moment.
_Avoid_: Archived, closed by removal

**Archived**:
The terminal state meaning the Task left the active working flow without claiming the intended outcome was reached. Archived work may still keep its past Work Sessions for reporting. If the Task has an Active Work Session at the moment it is archived, DevTrack closes that session automatically. Archiving a Parent Task also archives its still-open Sub-tasks.
_Avoid_: Done, completed

**Work Session**:
A dated period of work on exactly one Task. Work Sessions answer "when did I work on this?" and are the source of truth for time reporting.
_Avoid_: Task date, log line

**Active Work Session**:
The one Work Session currently running in the application. DevTrack allows at most one Active Work Session at a time, and switching Tasks ends the current session before starting the next one.
_Avoid_: Parallel session, active task status

**Manual Work Session**:
A Work Session entered after the fact to recover or correct forgotten time tracking. A Manual Work Session may be attached to an existing Task, and DevTrack should also support creating the Task from the same retroactive entry when needed. Adding forgotten time to a terminal Task does not reopen it automatically. On a `Todo` Task, the first Manual Work Session moves the Task to `Doing`.
_Avoid_: Reopen by correction, planned work

**Task Completion**:
The moment a Task is marked `Done`. It is distinct from Work Sessions because finishing a Task and working on it can happen on different days. A Task may later be reopened, and reopening clears the current Task Completion. If the Task has an Active Work Session at that moment, DevTrack closes the session automatically.
_Avoid_: Last worked day, generic update time

**Open Task**:
A Task that is still part of the user's active working flow. In DevTrack, Open Tasks are the Tasks in `Todo` or `Doing`.
_Avoid_: Backlog item, planned task

**Sub-task**:
A Task attached to a parent Task rather than existing at the top level. Completing all Sub-tasks does not automatically complete the parent Task.
_Avoid_: Auto-complete trigger

**Parent Task**:
A top-level Task that owns one or more Sub-tasks. Its displayed total time includes both its own Work Sessions and the Work Sessions of its Sub-tasks. If a Sub-task receives its first Work Session, the Parent Task becomes `Doing` as well. When reopened from `Archived`, its open status is derived from aggregate work history across the parent and its Sub-tasks.
_Avoid_: Container only, non-task wrapper

**Open Sub-task**:
A Sub-task that is still in `Todo` or `Doing`.
_Avoid_: Implicitly completed child

**Sub-task Depth**:
The maximum nesting allowed for Sub-tasks. In DevTrack, Sub-task Depth is limited to one level.
_Avoid_: Arbitrary nesting, task tree

**Worklist**:
The primary screen showing the user's Open Tasks. It replaces the idea of a day-specific task list. `Done` and `Archived` Tasks are hidden there by default and reached through filters or search.
_Avoid_: Today, backlog

## Flagged Ambiguities

- The current code has a second axis, separate from `Task Status`, used to group Tasks in the UI. It calls that axis `TaskLevel`, but this is no longer treated as a canonical domain concept and should be removed from the product language.
- The current code stores a `plannedDate` on `Task`, but that is no longer canonical domain language. The day of work belongs to `Work Session`, and the day a Task is finished belongs to `Task Completion`.
- The current code uses `TODO`, `IN_PROGRESS`, and `PAUSED` as task statuses. That conflicts with the emerging domain language, where `Todo` and `Doing` are task lifecycle states and active/paused belongs to the current Work Session.
- The current product still centers its primary screen on "Today" and daily planned tasks. That conflicts with the emerging domain language, where the default visible flow is all `Open Task`s rather than tasks assigned to a specific day.
- The current product language still uses the screen name "Today". The canonical name is now `Worklist`.
- The current code already supports adding a Manual Work Session to an existing Task, but its picker is still shaped by the old `Today`/`Backlog` model and it does not yet support creating a new Task inline from that retroactive entry.

## Example Dialogue

Dev: "I worked on DPD-1423 this morning and again this afternoon, but those were two different Tasks in DevTrack."

Domain expert: "Right. `DPD-1423` is the Jira Ticket. The separate pieces of work you tracked are Tasks that both reference that ticket."

Dev: "And daily standup is still a Task even though it has no Jira Ticket."

Domain expert: "Exactly. A Task can exist without any Jira Ticket at all."

Dev: "I worked on DPD-1423 on Monday and Wednesday, but I only marked it done on Friday."

Domain expert: "Then the Monday and Wednesday dates belong to Work Sessions, and the Friday date is the Task Completion."

Dev: "And if I abandon an investigation that no longer matters, that is not done."

Domain expert: "Right. That Task is Archived, not Done."

Dev: "If I worked on a Task yesterday and I'm not timing it right now, is it still doing?"

Domain expert: "Yes. Once work has started, the Task is Doing until you mark it Done or Archived."

Dev: "What do I see by default when I open DevTrack?"

Domain expert: "Your Open Tasks: everything still in Todo or Doing."

Dev: "So the main screen is not really Today anymore."

Domain expert: "Correct. It is your Worklist: the set of Open Tasks you can act on now."

Dev: "Can I track two Tasks at once?"

Domain expert: "No. DevTrack allows only one Active Work Session at a time."

Dev: "Can I reopen a Task after marking it Done or Archived?"

Domain expert: "Yes. A terminal Task can return to the open flow."

Dev: "And if I reopen a done Task, is it still considered completed?"

Domain expert: "No. Reopening clears the current Task Completion until the Task is marked Done again."

Dev: "If all Sub-tasks are done, does the parent close automatically?"

Domain expert: "No. The parent Task still needs an explicit terminal decision."

Dev: "Is a Sub-task a different kind of object?"

Domain expert: "No. It is still a Task, just one that points to a Parent Task."

Dev: "Can a Sub-task have its own Sub-tasks?"

Domain expert: "No. DevTrack stops at one level of Sub-task Depth."

Dev: "If I log time on both the parent and its Sub-tasks, what total do I see on the parent?"

Domain expert: "The parent shows aggregated time: its own time plus the time of its Sub-tasks."

Dev: "If I start working on a Sub-task first, what happens to the parent?"

Domain expert: "The parent also becomes Doing, because work on the Parent Task has effectively started."

Dev: "Can I mark a Parent Task done while one of its Sub-tasks is still open?"

Domain expert: "No. DevTrack blocks that action until every Sub-task is terminal."

Dev: "And can I archive a Parent Task while one of its Sub-tasks is still open?"

Domain expert: "Yes. Archiving a Parent Task follows a different workflow and is still allowed."

Dev: "If I later reopen that Parent Task, do its auto-archived Sub-tasks reopen too?"

Domain expert: "No. Reopening the parent does not automatically reopen archived Sub-tasks."

Dev: "Can I reopen a Sub-task while its Parent Task is still archived?"

Domain expert: "Yes. Reopening the Sub-task also reopens the Parent Task."

Dev: "And when that Parent Task reopens, is it Todo or Doing?"

Domain expert: "It depends on aggregate history. If the parent or one of its Sub-tasks has already had work, it reopens as Doing; otherwise it reopens as Todo."

Dev: "What happens if I start another Task while one session is already active?"

Domain expert: "DevTrack switches automatically: it ends the current session and starts the new one."

Dev: "And if I mark the current Task done while its session is still running?"

Domain expert: "DevTrack closes the session automatically and records the Task Completion at that moment."

Dev: "And if I archive the current Task instead?"

Domain expert: "DevTrack also closes the session automatically, but that does not create a Task Completion."

Dev: "Do I still see Done and Archived Tasks in the Worklist by default?"

Domain expert: "No. The Worklist focuses on Open Tasks, and terminal Tasks are reached through filters or search."

Dev: "What if I forgot to start the timer?"

Domain expert: "You add a Manual Work Session afterward, on an existing Task or on a Task created from that same retroactive entry."

Dev: "If I add forgotten time to a Done or Archived Task, does it reopen?"

Domain expert: "No. Correcting tracked time does not silently change a terminal Task back into an open one."
