# DevTrack

A local-first desktop time tracking and reporting tool built for developers who juggle multiple Jira tickets in parallel.

DevTrack helps you answer questions like:
- Where did my time go today?
- Which Jira tickets did I work on this week?
- How much time was spent on meetings, reviews, or overhead?
- What goes into my monthly activity report (CRA)?

All data stays on your machine. No network connection required, no telemetry, no accounts.

## Features

- **Task management** with automatic Jira ticket detection (e.g. `PROJ-123 Fix login`)
- **Time tracking** with start/pause/resume/stop and automatic session switching
- **Pomodoro mode** with configurable work/break cycles and OS notifications
- **Command palette** (`Ctrl+K`) for power-user keyboard-driven workflows
- **Reports** -- daily, weekly, monthly CRA, and standup generation in Markdown
- **Timeline and calendar views** for visualizing work patterns
- **Templates** for recurring tasks (standups, reviews, etc.)
- **System tray** integration with color-coded status icon
- **Backup/restore** with ZIP-based export
- **i18n** -- French (default) and English, switchable at runtime
- **Dark/Light/System themes** using Material Design 3

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.1.10 |
| UI | Compose for Desktop (Multiplatform 1.7.3) |
| Design | Material Design 3 |
| Build | Gradle 8.12 |
| Database | SQLite + Exposed ORM |
| DI | Koin 4.0.2 |
| Testing | JUnit 5 + MockK |

## Prerequisites

- **JDK 21** (the build toolchain will download it automatically if you have Gradle's toolchain provisioning enabled, otherwise install it manually)

## Build

```bash
# Compile and run tests
./gradlew build

# Run tests only
./gradlew test
```

### Create native installers

```bash
# Linux
./gradlew packageDeb          # .deb package
./gradlew packageAppImage     # AppImage

# Windows
./gradlew packageMsi          # .msi installer
./gradlew packageExe          # .exe installer

# Distributable (no installer, just the app directory)
./gradlew createDistributable
```

Output is written to `build/compose/binaries/`.

## Run

```bash
./gradlew run
```

Or launch a packaged distribution directly after building it.

## Data locations

| Path | Content |
|---|---|
| `~/.devtrack/data/devtrack.db` | SQLite database |
| `~/.devtrack/logs/devtrack.log` | Application logs |
| `~/.devtrack/logs/audit.log` | Audit trail |
| `~/.devtrack/window.json` | Window size/position |

## Usage

See the [Usage Guide](docs/USAGE.md) for a walkthrough of all screens, keyboard shortcuts, command palette commands, and report types.

## Project Structure

```
src/main/kotlin/com/devtrack/
  app/           # Entry point, DI modules, window management
  domain/        # Models and services (task, session, pomodoro, reports)
  data/          # SQLite database, Exposed tables, repositories
  ui/            # Compose screens, components, theme, navigation, i18n
  infrastructure/# System tray, notifications, backup, export, logging

src/test/        # Unit and integration tests
docs/            # PRD, implementation plan, usage guide
```

## License

[MIT](LICENSE)
