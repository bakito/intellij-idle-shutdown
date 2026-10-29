# Idle Shutdown

<img src="src/main/resources/META-INF/pluginIcon.svg" width="128" alt="Plugin Icon" />

An IntelliJ IDEA plugin that automatically saves all files and closes the IDE after a configurable period of inactivity.

## Features

- **Automatic shutdown** — Closes the IDE when no keyboard or mouse activity is detected for a set amount of time.
- **Warning notification** — Shows a warning before closing, giving you a chance to cancel by moving the mouse or pressing a key.
- **Process-aware** — Optionally skips shutdown while run/debug processes are active.
- **Status bar** — Optionally displays the last activity timestamp and a countdown until shutdown in the IDE status bar; the countdown turns red during the warning phase.
- **Safe** — Saves all open files before exiting.
- **Configurable** — Enable/disable the feature and adjust the idle timeout directly from the IDE settings, or set the timeout via a file for centralized control.
- **Cloud Workstation keepalive** — When running inside a Google Cloud Workstation, automatically prevents the workstation from shutting down due to idle timeout by reporting activity to the local gateway.

## Requirements

- IntelliJ IDEA Community **2025.2** or later (build 252+).
- JDK **21** or later to build the plugin.

## Development with devbox

The recommended way to develop this plugin is using [devbox](https://jetify.com/docs/devbox), which provides a reproducible development environment with all required dependencies.

### Installing devbox

```bash
nix shell nixpkgs#devbox
devbox install
```

Or follow the full installation guide at <https://jetify.com/docs/devbox/installing>.

### Using devbox

Enter the devbox shell to activate the environment:

```bash
devbox shell
```

This provides Java 25 and Gradle, with the project's `.gradle` directory isolated under the repository root.

Run any of the predefined commands:

```bash
devbox run buildPlugin   # Build the plugin archive
devbox run test          # Run unit tests
devbox run run-ide       # Run the plugin in a sandbox IDE for testing
devbox run verify        # Validate the plugin structure
devbox run clean         # Clean build artifacts
```

## Building

Build the plugin with Gradle:

```bash
./gradlew buildPlugin
```

The built plugin archive is output to `build/distributions/idle-shutdown-<version>.zip`.

Other useful commands:

```bash
./gradlew runIde        # Run the plugin in a sandbox IDE for testing
./gradlew verifyPlugin  # Validate the plugin structure
./gradlew clean         # Clean build artifacts
```

## Installing

1. Build the plugin using `./gradlew buildPlugin`.
2. In IntelliJ IDEA, open **Settings | Plugins** (`Ctrl+Alt+S`).
3. Click the gear icon ⚙️ → **Install Plugin from Disk…**.
4. Select the `idle-shutdown-<version>.zip` file from the `build/distributions/` directory.
5. Restart the IDE when prompted.

## Configuration

Open **Settings | Tools | Idle Shutdown** to configure the plugin.

| Setting                                             | Default | Description                                                                                                    |
|-----------------------------------------------------|---------|----------------------------------------------------------------------------------------------------------------|
| **Enable idle shutdown**                            | `Off`   | Turn the idle shutdown feature on or off. Forced on when `~/.config/bakito/intellij-idle-shutdown.xml` exists. |
| **Idle timeout (minutes)**                          | `60`    | Minutes of inactivity before the shutdown sequence starts. Overridden by the config file if present.           |
| **Warning countdown (seconds)**                     | `60`    | Seconds the warning is visible before the IDE closes. Set to `0` to close immediately.                         |
| **Don't close while a run/debug process is active** | `On`    | Postpone shutdown while tests, builds, or debug sessions are running.                                          |
| **Show last activity in status bar**                | `Off`   | Display the last keyboard/mouse activity timestamp in the IDE status bar.                                      |

### Idle Timeout via Config File

The idle timeout can be set centrally by creating the file `~/.config/bakito/intellij-idle-shutdown.xml`.
When this file exists, the timeout is read from it and cannot be changed from the IDE settings UI. The feature is also forced enabled. If the file is missing or contains an invalid value, the plugin uses the value from the IDE settings (default: **60 minutes**).

#### Example Config File

```xml
<?xml version="1.0" encoding="UTF-8"?>
<idleShutdownAfterMinutes>60</idleShutdownAfterMinutes>
```

To create the config file:

```bash
mkdir -p ~/.config/bakito
cat > ~/.config/bakito/intellij-idle-shutdown.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<idleShutdownAfterMinutes>60</idleShutdownAfterMinutes>
EOF
```

### How it works

- The plugin monitors keyboard and mouse events at the system level.
- After the idle timeout is reached, a warning notification appears.
- Any keyboard or mouse input during the warning period cancels the shutdown.
- If the warning countdown expires, all modified files are saved and the IDE exits.
- If "Don't close while a run/debug process is active" is enabled, shutdown is postponed while tests, builds, or debug sessions are running.

## Cloud Workstation Keepalive

When the plugin detects that it is running inside a [Google Cloud Workstation](https://cloud.google.com/workstations), it automatically activates a keepalive mechanism to prevent the workstation from shutting down due to idle timeout.

### How it works

- On IDE startup the plugin checks for the `GOOGLE_CLOUD_WORKSTATIONS` environment variable. If present, the keepalive is enabled.
- The plugin monitors keyboard and mouse activity, similar to the idle shutdown tracker.
- When the IDE has been idle for more than **30 seconds**, the plugin sends an HTTP `GET` request to the local Cloud Workstation gateway (`http://localhost:981/_workstation/reportactivity`) to signal that the session is still in use.
- The keepalive check runs every **15 seconds**. Network failures are silently retried on the next tick.
- No configuration is required — the feature activates automatically and runs in the background.

### When it runs

The keepalive only activates when the `GOOGLE_CLOUD_WORKSTATIONS` environment variable is set. On local machines or other cloud environments it is completely inactive and has no overhead.

## Idle Shutdown vs. Cloud Workstation Keepalive

The plugin includes two independent features that address idle behavior from opposite directions:

| Aspect | Idle Shutdown | Cloud Workstation Keepalive |
|---|---|---|
| **Purpose** | Closes the IDE after a period of inactivity to free resources. | Keeps the Cloud Workstation alive by reporting activity to the gateway. |
| **Trigger** | Configurable idle timeout (default: 60 minutes). | Fixed 30-second idle threshold. |
| **Action** | Shows a warning, then saves files and exits the IDE. | Sends HTTP pings to `localhost:981` to prevent workstation auto-shutdown. |
| **Configuration** | User-configurable via IDE settings or config file. | No configuration; activates automatically when `GOOGLE_CLOUD_WORKSTATIONS` is set. |
| **Environment** | Runs everywhere. | Only runs inside Google Cloud Workstations. |
| **User control** | Can be enabled/disabled in settings. | Always active in supported environments; cannot be disabled. |

### How they complement each other

The two features are **complementary, not mutually exclusive**. They serve different purposes and operate independently:

- **Idle Shutdown** closes the IDE when you walk away, freeing local or cloud compute resources.
- **Cloud Workstation Keepalive** ensures the underlying workstation stays running while the IDE is open, even during brief pauses.

In practice, when running on a Cloud Workstation, the keepalive prevents the workstation from shutting down during short idle periods (e.g., reading documentation, thinking, or stepping away briefly). Once the idle shutdown timeout is reached (e.g., after 60 minutes), the IDE exits — which also stops the keepalive, allowing the workstation to eventually shut down on its own schedule.

Together they provide a balanced approach: the workstation stays alive while you're actively working (with short breaks), but the IDE still closes when you're truly done for the day.

### Implementation comparison with Google Cloud Code

The keepalive implementation in this plugin was inspired by Google's Cloud Code IntelliJ plugin, but takes a different approach to activity tracking. Here is how they compare:

| Aspect | Google Cloud Code | This Plugin |
|---|---|---|
| **Activity tracking** | Own `lastActivityTime` field with dedicated `IdeEventQueue.addPostprocessor()` listener | Reuses `IdleWatchdog.getLastActivityTime()` — no duplicate state |
| **Event listener lifecycle** | Registers a postprocessor on startup; never removes it | No listener registered — borrows IdleWatchdog's existing listeners |
| **Listener leak risk** | Postprocessor fires on every AWT event for the IDE's lifetime | None — no additional listener |
| **Activity sources** | AWT events + `ComponentEvent` (resize/move/show) | AWT + `IdeEventQueue` + editor caret, selection, and mouse events (broader coverage) |
| **Scheduler** | `java.util.Timer` (single-threaded; one uncaught exception kills it) | `ScheduledExecutorService` (robust, standard modern Java) |
| **HTTP client** | `com.google.api.client.http.HttpRequestFactory` with retry handler | Reused `java.net.http.HttpClient` singleton, no extra dependency |
| **Code footprint** | Self-contained but duplicates activity tracking logic | ~97 lines, delegates activity to existing service |
| **Portability** | Independent — works in any plugin | Depends on `IdleWatchdog` being available |

#### Key advantages of this approach

1. **No resource leaks.** Google Cloud Code's `addPostprocessor()` registers an event dispatcher that fires on every AWT event for the entire IDE session and is never removed. This plugin has zero additional listeners — it simply polls `IdleWatchdog`'s existing timestamp.
2. **Broader activity detection.** `IdleWatchdog` listens to AWT events, `IdeEventQueue` dispatchers, editor caret/selection/mouse events, and action listeners. Google Cloud Code only listens to AWT + `ComponentEvent`. The keepalive benefits from all of IdleWatchdog's sources without adding any of its own.
3. **Less code, fewer moving parts.** By reusing `IdleWatchdog`, an entire activity tracking subsystem (listener registration, timestamp field, event filtering logic) is eliminated. Fewer lines of code means fewer bugs.
4. **Modern Java patterns.** `ScheduledExecutorService` over `java.util.Timer`, and a reused `HttpClient` singleton over per-request client creation.

The only trade-off is a dependency on `IdleWatchdog`, which is acceptable since both components live in the same plugin and serve complementary purposes. The coupling ensures both features share a single source of truth for user activity.

### Troubleshooting

#### SSL error during `buildSearchableOptions`

When building the plugin, you may encounter an SSL certificate error in the `buildSearchableOptions` Gradle task:

> javax.net.ssl.SSLHandshakeException: ... / X509TrustException: ...

**Cause:** The `buildSearchableOptions` task launches the IDE in headless mode to generate metadata for searchable settings. On some corporate networks or CI environments with strict SSL/proxy configurations, the headless IDE instance fails to validate SSL certificates when downloading resources.

**Fix:** If the error is non-blocking (the build still succeeds), it can be safely ignored. To eliminate the error, you can disable the task in `build.gradle.kts`:

```kotlin
intellijPlatform {
    buildSearchableOptions = false
}
```

Disabling this task means the plugin's settings won't appear in the IDE Settings search dialog, but all other functionality remains unaffected. Alternatively, ensure your build environment has access to the required SSL certificates or is properly configured for your network's proxy.
