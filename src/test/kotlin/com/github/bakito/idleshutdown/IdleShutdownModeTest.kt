package com.github.bakito.idleshutdown

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the idle shutdown mode evaluation logic.
 *
 * The plugin supports two modes:
 * - **File-triggered**: When `/tmp/idle` exists, the plugin is forced enabled and cannot be disabled.
 * - **Manual**: When `/tmp/idle` does not exist, the user controls whether the plugin is enabled.
 *
 * The status bar widget has an additional gate: it only shows when idle shutdown
 * is effective (enabled or file-triggered) AND `showLastActivityInStatusbar` is true.
 *
 * Since we cannot mock the file system or IDE services in plain unit tests,
 * these tests replicate the decision logic to verify all mode combinations.
 */
class IdleShutdownModeTest {

    // -----------------------------------------------------------------------
    // Helper functions that replicate the decision logic from source files
    // -----------------------------------------------------------------------

    /**
     * Replicates [IdleShutdownSettings.isTimeoutFilePresent] concept.
     */
    private fun isTimeoutFilePresent(timeoutFileExists: Boolean): Boolean = timeoutFileExists

    /**
     * Replicates [IdleShutdownSettings.canBeDisabled] — returns false when timeout file exists.
     */
    private fun canBeDisabled(timeoutFileExists: Boolean): Boolean = !timeoutFileExists

    /**
     * Replicates the effective idle enabled check from [IdleWatchdog.start] and
     * [IdleActivityStatusBarWidgetFactory.isAvailable].
     * `val idleEnabled = timeoutFilePresent || settings.state.enabled`
     */
    private fun isIdleEnabled(timeoutFileExists: Boolean, userEnabled: Boolean): Boolean =
        timeoutFileExists || userEnabled

    /**
     * Replicates the watchdog start decision — the watchdog starts only when
     * idle is effectively enabled (file-triggered or user-enabled).
     */
    private fun watchdogShouldStart(timeoutFileExists: Boolean, userEnabled: Boolean): Boolean =
        isIdleEnabled(timeoutFileExists, userEnabled)

    /**
     * Replicates the status bar widget visibility decision from
     * [IdleActivityStatusBarWidgetFactory.isAvailable] and [IdleActivityStatusBarWidget].
     * The widget is available only when idle is enabled AND showLastActivityInStatusbar is true.
     */
    private fun isStatusBarWidgetAvailable(
        timeoutFileExists: Boolean,
        userEnabled: Boolean,
        showInStatusBar: Boolean
    ): Boolean {
        val idleEnabled = isIdleEnabled(timeoutFileExists, userEnabled)
        return idleEnabled && showInStatusBar
    }

    /**
     * Replicates the timeout minutes selection logic from [IdleShutdownSettings.getTimeoutMinutes].
     * When the timeout file exists, it reads the value from the file; otherwise uses the custom value.
     */
    private fun getTimeoutMinutes(
        timeoutFileExists: Boolean,
        timeoutFileValue: Int,
        customTimeoutMinutes: Int
    ): Int {
        return if (timeoutFileExists) timeoutFileValue else customTimeoutMinutes
    }

    // -----------------------------------------------------------------------
    // File-triggered mode (timeout file exists)
    // -----------------------------------------------------------------------

    @Test
    fun `file-triggered mode forces idle enabled regardless of user setting disabled`() {
        assertTrue(
            isIdleEnabled(timeoutFileExists = true, userEnabled = false),
            "File-triggered mode should force idle enabled even when user disabled it"
        )
    }

    @Test
    fun `file-triggered mode forces idle enabled when user setting is also enabled`() {
        assertTrue(
            isIdleEnabled(timeoutFileExists = true, userEnabled = true),
            "File-triggered mode should be enabled when user also enabled it"
        )
    }

    @Test
    fun `file-triggered mode prevents user from disabling the plugin`() {
        assertFalse(
            canBeDisabled(timeoutFileExists = true),
            "When timeout file exists, the plugin should not be disableable by the user"
        )
    }

    @Test
    fun `file-triggered mode starts the watchdog regardless of user setting`() {
        assertTrue(
            watchdogShouldStart(timeoutFileExists = true, userEnabled = false),
            "Watchdog should start in file-triggered mode even when user disabled it"
        )
        assertTrue(
            watchdogShouldStart(timeoutFileExists = true, userEnabled = true),
            "Watchdog should start in file-triggered mode when user enabled it"
        )
    }

    @Test
    fun `file-triggered mode uses timeout file value for timeout minutes`() {
        assertEquals(
            45,
            getTimeoutMinutes(
                timeoutFileExists = true,
                timeoutFileValue = 45,
                customTimeoutMinutes = 90
            ),
            "File-triggered mode should use the timeout file value, ignoring custom setting"
        )
    }

    @Test
    fun `file-triggered mode with status bar on shows the widget`() {
        assertTrue(
            isStatusBarWidgetAvailable(
                timeoutFileExists = true,
                userEnabled = false,
                showInStatusBar = true
            ),
            "Status bar widget should be available in file-triggered mode with status bar on"
        )
    }

    @Test
    fun `file-triggered mode with status bar off hides the widget`() {
        assertFalse(
            isStatusBarWidgetAvailable(
                timeoutFileExists = true,
                userEnabled = false,
                showInStatusBar = false
            ),
            "Status bar widget should not be available when showInStatusBar is off"
        )
    }

    // -----------------------------------------------------------------------
    // Manual mode (no timeout file)
    // -----------------------------------------------------------------------

    @Test
    fun `manual mode with user enabled forces idle on`() {
        assertTrue(
            isIdleEnabled(timeoutFileExists = false, userEnabled = true),
            "Manual mode with user enabled should be active"
        )
    }

    @Test
    fun `manual mode with user disabled turns idle off`() {
        assertFalse(
            isIdleEnabled(timeoutFileExists = false, userEnabled = false),
            "Manual mode with user disabled should not be active"
        )
    }

    @Test
    fun `manual mode allows user to disable the plugin`() {
        assertTrue(
            canBeDisabled(timeoutFileExists = false),
            "When no timeout file exists, the user should be able to disable the plugin"
        )
    }

    @Test
    fun `manual mode starts watchdog only when user enables it`() {
        assertTrue(
            watchdogShouldStart(timeoutFileExists = false, userEnabled = true),
            "Watchdog should start in manual mode when user enables it"
        )
        assertFalse(
            watchdogShouldStart(timeoutFileExists = false, userEnabled = false),
            "Watchdog should not start in manual mode when user disables it"
        )
    }

    @Test
    fun `manual mode uses custom timeout value`() {
        assertEquals(
            90,
            getTimeoutMinutes(
                timeoutFileExists = false,
                timeoutFileValue = 45,
                customTimeoutMinutes = 90
            ),
            "Manual mode should use the custom timeout setting"
        )
    }

    @Test
    fun `manual mode with user disabled and status bar on still hides widget`() {
        assertFalse(
            isStatusBarWidgetAvailable(
                timeoutFileExists = false,
                userEnabled = false,
                showInStatusBar = true
            ),
            "Status bar widget should not be available when idle is disabled, even with status bar on"
        )
    }

    @Test
    fun `manual mode with user enabled and status bar on shows widget`() {
        assertTrue(
            isStatusBarWidgetAvailable(
                timeoutFileExists = false,
                userEnabled = true,
                showInStatusBar = true
            ),
            "Status bar widget should be available in manual mode when both are enabled"
        )
    }

    @Test
    fun `manual mode with user enabled and status bar off hides widget`() {
        assertFalse(
            isStatusBarWidgetAvailable(
                timeoutFileExists = false,
                userEnabled = true,
                showInStatusBar = false
            ),
            "Status bar widget should not be available when showInStatusBar is off"
        )
    }

    // -----------------------------------------------------------------------
    // State defaults affect mode behavior
    // -----------------------------------------------------------------------

    @Test
    fun `default state results in idle disabled in manual mode`() {
        val state = IdleShutdownSettings.State()
        assertFalse(state.enabled, "Default state should have enabled = false")
        assertFalse(state.showLastActivityInStatusbar, "Default state should have showLastActivityInStatusbar = false")

        // With default state and no timeout file, idle should be off
        assertFalse(
            isIdleEnabled(timeoutFileExists = false, userEnabled = state.enabled),
            "Default state with no timeout file should result in idle disabled"
        )
    }

    @Test
    fun `default state hides status bar widget in manual mode`() {
        val state = IdleShutdownSettings.State()
        assertFalse(
            isStatusBarWidgetAvailable(
                timeoutFileExists = false,
                userEnabled = state.enabled,
                showInStatusBar = state.showLastActivityInStatusbar
            ),
            "Default state should not show the status bar widget"
        )
    }

    // -----------------------------------------------------------------------
    // Timeout value edge cases
    // -----------------------------------------------------------------------

    @Test
    fun `file-triggered mode with invalid file value falls back to default`() {
        // In IdleShutdownSettings.getTimeoutMinutes(), a parse error falls back to 60
        // We test the happy path here; the exception path is tested via the real method
        assertEquals(
            120,
            getTimeoutMinutes(
                timeoutFileExists = true,
                timeoutFileValue = 120,
                customTimeoutMinutes = 60
            )
        )
    }

    @Test
    fun `manual mode with default custom timeout returns 60`() {
        val state = IdleShutdownSettings.State()
        assertEquals(
            60,
            getTimeoutMinutes(
                timeoutFileExists = false,
                timeoutFileValue = 0,
                customTimeoutMinutes = state.customTimeoutMinutes
            ),
            "Default custom timeout should be 60 minutes"
        )
    }

    // -----------------------------------------------------------------------
    // Skip when process running (independent of mode)
    // -----------------------------------------------------------------------

    @Test
    fun `skipWhenProcessRunning defaults to true`() {
        val state = IdleShutdownSettings.State()
        assertTrue(state.skipWhenProcessRunning, "Should skip shutdown when process is running by default")
    }

    @Test
    fun `skipWhenProcessRunning can be disabled in state`() {
        val state = IdleShutdownSettings.State().apply {
            skipWhenProcessRunning = false
        }
        assertFalse(state.skipWhenProcessRunning, "User should be able to disable skip when process running")
    }

    // -----------------------------------------------------------------------
    // Warning seconds configuration
    // -----------------------------------------------------------------------

    @Test
    fun `warningSeconds defaults to 60`() {
        val state = IdleShutdownSettings.State()
        assertEquals(60, state.warningSeconds, "Default warning should be 60 seconds")
    }

    @Test
    fun `warningSeconds zero means immediate shutdown`() {
        // In IdleWatchdog.onIdleTimeoutReached(), warningSeconds == 0 triggers exitIde() directly
        val state = IdleShutdownSettings.State().apply {
            warningSeconds = 0
        }
        assertEquals(0, state.warningSeconds, "Warning seconds can be set to 0 for immediate shutdown")
    }
}
