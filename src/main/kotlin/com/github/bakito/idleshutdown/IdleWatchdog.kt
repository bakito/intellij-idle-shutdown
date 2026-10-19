package com.github.bakito.idleshutdown

import com.intellij.execution.ExecutionManager
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectManager
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.InputEvent
import java.awt.event.InputMethodEvent
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Tracks user input (mouse + keyboard). After the idle timeout it shows a warning
 * notification and, if there is still no input when the countdown ends, saves all
 * files and exits the IDE. Any input during the countdown cancels the shutdown.
 *
 * Optionally postpones the whole thing while a run/debug process is running.
 * Other background IDE work (indexing etc.) does not count as user activity.
 */
@Service
class IdleWatchdog : Disposable {

    private val log = logger<IdleWatchdog>()

    @Volatile private var lastActivityMs = System.currentTimeMillis()

    /** 0 = no shutdown pending; otherwise the time at which the IDE will exit. */
    @Volatile private var shutdownDeadlineMs = 0L

    // Only touched on the EDT.
    private var warning: Notification? = null

    private val activityListener = AWTEventListener { onUserActivity() }

    /**
     * Sees every input event before IntelliJ's key/mouse dispatchers consume it. Editor typing and
     * shortcuts are often consumed there and never reach the plain AWT listener.
     */
    private val ideEventDispatcher = object : IdeEventQueue.EventDispatcher {
        override fun dispatch(e: AWTEvent): Boolean {
            if (e is InputEvent || e is InputMethodEvent) onUserActivity()
            return false // never consume
        }
    }

    /** Records user activity (input, opening/closing/switching files, ...) and cancels a pending shutdown. */
    fun onUserActivity() {
        lastActivityMs = System.currentTimeMillis()
        if (shutdownDeadlineMs != 0L) cancelPendingShutdown("activity")
    }

    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "IdleShutdown-Watchdog").apply { isDaemon = true }
    }

    private var started = false

    /** Returns the epoch timestamp (ms) of the last keyboard or mouse activity. */
    fun getLastActivityTime(): Long = lastActivityMs

    /** Returns the epoch timestamp (ms) when the IDE will exit, or 0 if no shutdown is pending. */
    fun getShutdownDeadlineMs(): Long = shutdownDeadlineMs

    /** Returns true when the warning countdown notification is active. */
    fun isWarningActive(): Boolean = shutdownDeadlineMs != 0L

    /** Returns the epoch timestamp (ms) when the default idle timeout will be reached from the last activity. */
    fun getTimeoutDeadlineMs(): Long {
        val timeoutMs = IdleShutdownSettings.getInstance().getTimeoutMinutes().coerceAtLeast(1) * 60_000L
        return lastActivityMs + timeoutMs
    }

    /** If the timeout file exists, the plugin is forced enabled; otherwise the user setting decides. */
    private fun isEnabled(): Boolean {
        val settings = IdleShutdownSettings.getInstance()
        return settings.isTimeoutFilePresent() || settings.state.enabled
    }

    /**
     * Starts activity tracking. Tracking always runs (it is cheap), so enabling the feature later in the
     * settings works without restarting the IDE and starts from the real last activity.
     */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        Toolkit.getDefaultToolkit().addAWTEventListener(
            activityListener,
            AWTEvent.MOUSE_EVENT_MASK or
                AWTEvent.MOUSE_MOTION_EVENT_MASK or
                AWTEvent.MOUSE_WHEEL_EVENT_MASK or
                AWTEvent.KEY_EVENT_MASK or
                AWTEvent.FOCUS_EVENT_MASK or
                AWTEvent.INPUT_METHOD_EVENT_MASK
        )
        IdeEventQueue.getInstance().addDispatcher(ideEventDispatcher, this)
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(AnActionListener.TOPIC, object : AnActionListener {
                override fun beforeEditorTyping(c: Char, dataContext: DataContext) = onUserActivity()
            })
        // Editor-level safety net: selecting/highlighting code, moving the caret, clicking or moving the mouse.
        EditorFactory.getInstance().eventMulticaster.apply {
            addSelectionListener(object : SelectionListener {
                override fun selectionChanged(e: SelectionEvent) = onUserActivity()
            }, this@IdleWatchdog)
            addCaretListener(object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) = onUserActivity()
            }, this@IdleWatchdog)
            addEditorMouseListener(object : EditorMouseListener {
                override fun mousePressed(event: EditorMouseEvent) = onUserActivity()
                override fun mouseReleased(event: EditorMouseEvent) = onUserActivity()
            }, this@IdleWatchdog)
            addEditorMouseMotionListener(object : EditorMouseMotionListener {
                override fun mouseMoved(e: EditorMouseEvent) = onUserActivity()
                override fun mouseDragged(e: EditorMouseEvent) = onUserActivity()
            }, this@IdleWatchdog)
        }
        executor.scheduleWithFixedDelay({ checkSafely() }, CHECK_INTERVAL_S, CHECK_INTERVAL_S, TimeUnit.SECONDS)
    }

    private fun checkSafely() {
        try {
            check()
        } catch (t: Throwable) {
            // A thrown exception would silently cancel the scheduled task.
            log.warn("Idle check failed", t)
        }
    }

    private fun check() {
        val now = System.currentTimeMillis()

        if (!isEnabled()) {
            if (shutdownDeadlineMs != 0L) cancelPendingShutdown("disabled")
            return
        }

        val deadline = shutdownDeadlineMs
        if (deadline != 0L) {
            if (now >= deadline) {
                shutdownDeadlineMs = 0L
                ApplicationManager.getApplication().invokeLater { exitIde() }
            }
            return
        }

        val settingsInstance = IdleShutdownSettings.getInstance()
        val timeoutMs = settingsInstance.getTimeoutMinutes().coerceAtLeast(1) * 60_000L
        if (now - lastActivityMs < timeoutMs) return

        // Decide on the EDT: it's the safest place to query execution state and show UI.
        ApplicationManager.getApplication().invokeLater { onIdleTimeoutReached() }
    }

    private fun onIdleTimeoutReached() {
        val settingsInstance = IdleShutdownSettings.getInstance()
        val settings = settingsInstance.state
        if (shutdownDeadlineMs != 0L || !isEnabled()) return

        // Re-check: the user may have moved the mouse since the background check.
        val timeoutMs = settingsInstance.getTimeoutMinutes().coerceAtLeast(1) * 60_000L
        if (System.currentTimeMillis() - lastActivityMs < timeoutMs) return

        if (settings.skipWhenProcessRunning && hasRunningProcess()) {
            log.info("Idle timeout reached but a run/debug process is active; postponing")
            return // re-evaluated on the next tick
        }

        val warningSeconds = settings.warningSeconds.coerceAtLeast(0)
        if (warningSeconds == 0) {
            exitIde()
            return
        }

        shutdownDeadlineMs = System.currentTimeMillis() + warningSeconds * 1000L
        val n = Notification(NOTIFICATION_GROUP_ID,
            "Idle Shutdown: no activity detected. The IDE will close in $warningSeconds seconds. " +
                "Move the mouse or press a key to cancel.",
            NotificationType.WARNING
        )
        warning = n
        Notifications.Bus.notify(n)
    }

    private fun hasRunningProcess(): Boolean = try {
        ProjectManager.getInstance().openProjects.any { project ->
            !project.isDisposed &&
                ExecutionManager.getInstance(project).getRunningProcesses().any { !it.isProcessTerminated }
        }
    } catch (t: Throwable) {
        // If detection fails, fall back to the plain idle behaviour rather than never closing.
        log.warn("Could not query running processes", t)
        false
    }

    private fun cancelPendingShutdown(reason: String) {
        shutdownDeadlineMs = 0L
        ApplicationManager.getApplication().invokeLater {
            warning?.expire()
            warning = null
        }
        log.info("Pending idle shutdown cancelled ($reason)")
    }

    private fun exitIde() {
        warning?.expire()
        warning = null
        lastActivityMs = System.currentTimeMillis() // don't re-trigger if exit gets vetoed
        FileDocumentManager.getInstance().saveAllDocuments()
        ApplicationManager.getApplication().exit()
    }

    override fun dispose() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(activityListener)
        executor.shutdownNow()
    }

    private companion object {
        const val CHECK_INTERVAL_S = 15L
        const val NOTIFICATION_GROUP_ID = "Idle Shutdown"
    }
}
