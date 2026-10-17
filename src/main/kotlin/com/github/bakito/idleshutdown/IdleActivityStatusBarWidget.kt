package com.github.bakito.idleshutdown

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.JBColor
import javax.swing.JLabel
import javax.swing.event.MouseInputAdapter
import java.awt.Color
import java.awt.event.MouseEvent
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class IdleActivityStatusBarWidget(private val project: Project) :
    StatusBarWidget, CustomStatusBarWidget, Disposable {

    companion object {
        const val ID = "IdleActivityStatusBarWidget"
        // Theme-aware status bar colors
        private val THEME_DARK = Color.decode("#AFB1B3") as Color
        private val THEME_LIGHT = Color.decode("#6E6E6E") as Color
    }

    private val textLabel = JLabel("").apply { isFocusable = false }
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "IdleActivity-Widget").apply { isDaemon = true }
    }

    init {
        textLabel.toolTipText = "Time until idle shutdown timeout (double-click to open settings)"
        textLabel.addMouseMotionListener(object : MouseInputAdapter() {})
        textLabel.addMouseListener(object : MouseInputAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    ApplicationManager.getApplication().invokeLater {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, IdleShutdownConfigurable::class.java)
                    }
                }
            }
        })
        scheduler.scheduleAtFixedRate({
            val settings = service<IdleShutdownSettings>()
            val timeoutFilePresent = settings.isTimeoutFilePresent()
            val idleEnabled = timeoutFilePresent || settings.state.enabled
            val showInStatusBar = settings.state.showLastActivityInStatusbar

            ApplicationManager.getApplication().invokeLater {
                // Hide the entire widget component when idle is disabled or status bar display is off
                textLabel.isVisible = idleEnabled && showInStatusBar

                if (idleEnabled && showInStatusBar) {
                    val timeoutDeadline = service<IdleWatchdog>().getTimeoutDeadlineMs()
                    val remaining = (timeoutDeadline - System.currentTimeMillis()) / 1000
                    val text = formatRemaining(remaining)

                    textLabel.text = text
                    val themeColor = if (!JBColor.isBright()) THEME_DARK else THEME_LIGHT
                    textLabel.foreground = themeColor
                } else {
                    textLabel.text = ""
                    textLabel.foreground = null
                }
            }
        }, 0, 1, TimeUnit.SECONDS)
    }

    private fun formatRemaining(seconds: Long): String {
        val abs = seconds.coerceAtLeast(0)
        val hours = abs / 3600
        val minutes = (abs % 3600) / 60
        val secs = abs % 60
        return if (hours > 0) {
            val h = String.format("%02d", hours)
            val m = String.format("%02d", minutes)
            val s = String.format("%02d", secs)
            "Shutdown in ${h}:${m}:${s}"
        } else if (minutes > 0) {
            "Shutdown in ${minutes}m ${secs}s"
        } else {
            "Shutdown in ${secs}s"
        }
    }

    override fun ID() = ID

    override fun getComponent() = textLabel

    override fun install(statusBar: StatusBar) {
        // nothing to do
    }

    override fun dispose() {
        scheduler.shutdownNow()
    }
}
