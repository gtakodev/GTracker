# Usage Guide

This guide covers how to use DevTrack day-to-day.

## Application Layout

The window is divided into four areas:

- **Top bar** -- Logo on the left, active timer widget in the center (task name, elapsed time, pause/resume/stop controls), and a theme toggle on the right.
- **Sidebar** (collapsible) -- Navigation to all screens: Today, Backlog, Timeline, Calendar, Reports, Templates, Settings.
- **Main content area** -- The active screen.
- **Status bar** -- Shows the currently active session and total tracked time for the day.

Closing the window with the X button minimizes DevTrack to the **system tray**. To fully quit, right-click the tray icon and select **Quit**.

---

## Screens

### Today (`Ctrl+1`)

Your daily workspace. Tasks are organized into three sections: **Active**, **To Do**, and **Done**.

- Use the quick-create field at the top to add a task. Type something like `PROJ-42 Fix auth bug #bugfix` and DevTrack will auto-detect the Jira ticket (`PROJ-42`) and category (`BUGFIX`).
- Click the **play button** on a task card to start tracking time. Only one task can be active at a time -- starting a new one automatically stops the previous session.
- Drag and drop tasks to reorder them.

### Backlog (`Ctrl+2`)

All unplanned tasks. Provides:

- Filtering by category and status
- Sorting options
- Multi-select for batch operations (plan, delete, categorize)

### Timeline (`Ctrl+3`)

A chronological bar chart of the day's work sessions, color-coded by category. Useful for spotting gaps and understanding how the day was structured.

### Calendar (`Ctrl+4`)

A monthly or weekly grid showing work density as a heatmap. Click on a day to see its details.

### Reports (`Ctrl+5`)

Generate reports in Markdown format. Four types are available:

| Type | Content |
|---|---|
| **Daily** | Table of Jira tickets and non-ticket tasks with tracked time |
| **Weekly** | Same structure, spanning Monday through Friday |
| **Monthly CRA** | Cross-tabulation grid -- Jira tickets as rows, days as columns, values in half-days |
| **Standup** | Auto-generated "Yesterday / Today / Blockers" format |

Each report can be previewed and copied to the clipboard.

### Templates

Manage recurring task templates (e.g. "Daily standup", "Code review", "Weekly report"). Three defaults are seeded on first launch. Create a task from a template with a single click or via the command palette.

### Settings (`Ctrl+,`)

| Section | Options |
|---|---|
| **Appearance** | Light / Dark / System theme; French / English language |
| **Timer** | Inactivity threshold (5-120 minutes, default 30) |
| **Pomodoro** | Work duration (15-60 min), short break (1-15 min), long break (5-30 min), sessions before long break (1-8) |
| **Reports** | Hours per day (default 8.0), half-day threshold (default 4.0) |
| **Data** | Export and import backups; view database and log file paths |

---

## Command Palette

Open with **`Ctrl+K`** (command mode) or **`Ctrl+N`** (create mode).

Type freely to search tasks by title or Jira ticket. Prefix with `/` for slash commands:

| Command | Description |
|---|---|
| `/start <task>` | Start tracking a task |
| `/pause` | Pause the active session |
| `/resume` | Resume the paused session |
| `/done` | Mark the active task as done and stop tracking |
| `/switch <task>` | Stop current session and start a different task |
| `/plan <task> today\|tomorrow\|<date>` | Schedule a task |
| `/report today\|week\|month` | Generate a report |
| `/pomodoro <task>` | Start a Pomodoro session on a task |
| `/template <name>` | Create a task from a template |

### Smart parsing

When creating a task, DevTrack parses the input:

- `PROJ-123 Fix login` -- detects Jira ticket `PROJ-123`
- `PROJ-123 Fix login #bugfix` -- also sets the category to `BUGFIX`
- Jira tickets are matched with the pattern `[A-Z]{2,10}-\d+`

---

## Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+K` | Open command palette |
| `Ctrl+N` | Open command palette in create mode |
| `Ctrl+1` | Go to Today |
| `Ctrl+2` | Go to Backlog |
| `Ctrl+3` | Go to Timeline |
| `Ctrl+4` | Go to Calendar |
| `Ctrl+5` | Go to Reports |
| `Ctrl+,` | Go to Settings |
| `Escape` | Close modal / dialog |

---

## Time Tracking Workflow

1. **Create a task** -- use the quick-create field on the Today screen or the command palette.
2. **Start tracking** -- click the play button on a task card, or use `/start <task>`.
3. **Monitor** -- the timer runs in the top bar, the status bar, and the system tray tooltip.
4. **Pause / Resume** -- via the top bar controls, system tray context menu, or `/pause` and `/resume`.
5. **Stop** -- click stop, or start a different task (auto-switch), or use `/done`.
6. **Review** -- check the Timeline screen for a visual summary, or generate a report.

---

## Pomodoro Mode

1. Start a Pomodoro session via the command palette (`/pomodoro <task>`) or the task card menu.
2. A countdown timer replaces the normal elapsed timer in the top bar.
3. When a work interval ends, an OS notification fires and a break begins automatically.
4. After a configurable number of work sessions, a long break is triggered.
5. Customize durations in **Settings > Pomodoro**.

---

## System Tray

The tray icon color indicates the current state:

| Color | Meaning |
|---|---|
| Green | A task is actively being tracked |
| Yellow | The active session is paused |
| Gray | No active session |

Right-click the tray icon for a context menu with pause, resume, stop, and quit options.

---

## Inactivity Detection

If no mouse or keyboard activity is detected for longer than the configured threshold (default: 30 minutes), DevTrack will prompt you to:

- **Continue** -- keep the session running (the inactive time counts).
- **Auto-pause** -- pause the session retroactively at the point inactivity was detected.
- **Stop** -- end the session entirely.

---

## Orphan Session Recovery

If DevTrack detects sessions that were never properly closed (e.g. after a crash or forced shutdown), a dialog appears on startup asking how to resolve each one: close it at the last known event time, discard it, or set a custom end time.

---

## Backup and Restore

- **Export**: Settings > Data > Export. Creates a `.devtrack-backup` file (ZIP containing the database and metadata).
- **Import**: Settings > Data > Import. Select a `.devtrack-backup` file to restore from. This replaces the current database.
