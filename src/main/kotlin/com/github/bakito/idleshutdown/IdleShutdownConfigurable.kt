package com.github.bakito.idleshutdown

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.*
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class IdleShutdownConfigurable : Configurable {

    private var enableBox: JCheckBox? = null
    private var timeoutSpinner: JSpinner? = null
    private var warningSpinner: JSpinner? = null
    private var skipBox: JCheckBox? = null
    private var statusbarBox: JCheckBox? = null

    override fun getDisplayName(): String = "Idle Shutdown"

    override fun createComponent(): JComponent {
        val settings = IdleShutdownSettings.getInstance()
        val timeoutFilePresent = settings.isTimeoutFilePresent()

        val enable = JCheckBox()
        enable.isEnabled = !timeoutFilePresent
        val timeout = JSpinner(SpinnerNumberModel(60, 1, 1440, 1))
        timeout.isEnabled = !timeoutFilePresent
        val warning = JSpinner(SpinnerNumberModel(60, 0, 3_600, 5))
        val skip = JCheckBox()
        val statusbar = JCheckBox()
        enableBox = enable
        timeoutSpinner = timeout
        warningSpinner = warning
        skipBox = skip
        statusbarBox = statusbar

        val lastActivityMs = service<IdleWatchdog>().getLastActivityTime()
        val lastActivityText = "Last activity: ${SimpleDateFormat("HH:mm:ss").format(Date(lastActivityMs))}"

        return panel {
            row {
                label(lastActivityText)
            }

            if (timeoutFilePresent) {
                row {
                    label("Idle timeout: ${settings.getTimeoutMinutes()} minutes (configured in ${IdleShutdownSettings.IDLE_TIMEOUT_FILE}, cannot be changed here).")
                }
                separator()
            }

            row("Enable idle shutdown") {
                cell(enable)
            }

            if (!timeoutFilePresent) {
                row("Idle timeout (minutes)") {
                    cell(timeout)
                }
            }

            row("Warning countdown (seconds, 0 = none)") {
                cell(warning)
            }

            row("Don't close while a run/debug process is active") {
                cell(skip)
            }

            row("Show last activity in status bar") {
                cell(statusbar)
            }

            separator()
            row {
                label("Any keyboard/mouse input during the warning cancels the shutdown.")
            }
            row {
                label("Modified files are saved before exit.")
            }
        }.also { reset() }
    }

    private fun current() = IdleShutdownSettings.getInstance().state

    override fun isModified(): Boolean {
        val s = current()
        return enableBox?.isSelected != s.enabled ||
            (timeoutSpinner?.value as? Int) != s.customTimeoutMinutes ||
            (warningSpinner?.value as? Int) != s.warningSeconds ||
            skipBox?.isSelected != s.skipWhenProcessRunning ||
            statusbarBox?.isSelected != s.showLastActivityInStatusbar
    }

    override fun apply() {
        val s = current()
        s.enabled = enableBox?.isSelected ?: s.enabled
        s.customTimeoutMinutes = (timeoutSpinner?.value as? Int) ?: s.customTimeoutMinutes
        s.warningSeconds = (warningSpinner?.value as? Int) ?: s.warningSeconds
        s.skipWhenProcessRunning = skipBox?.isSelected ?: s.skipWhenProcessRunning
        s.showLastActivityInStatusbar = statusbarBox?.isSelected ?: s.showLastActivityInStatusbar
    }

    override fun reset() {
        val s = current()
        val settings = IdleShutdownSettings.getInstance()
        val timeoutFilePresent = settings.isTimeoutFilePresent()

        // If the timeout file exists, force enable
        enableBox?.isSelected = s.enabled || timeoutFilePresent
        enableBox?.isEnabled = !timeoutFilePresent

        timeoutSpinner?.value = s.customTimeoutMinutes
        warningSpinner?.value = s.warningSeconds
        skipBox?.isSelected = s.skipWhenProcessRunning
        statusbarBox?.isSelected = s.showLastActivityInStatusbar
    }

    override fun disposeUIResources() {
        enableBox = null; timeoutSpinner = null; warningSpinner = null; skipBox = null; statusbarBox = null
    }
}
