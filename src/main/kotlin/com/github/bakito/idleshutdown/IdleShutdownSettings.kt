package com.github.bakito.idleshutdown

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import java.io.File

@Service
@State(name = "IdleShutdownSettings", storages = [Storage("idleShutdown.xml")])
class IdleShutdownSettings : PersistentStateComponent<IdleShutdownSettings.State> {

    companion object {
        const val IDLE_TIMEOUT_FILE = "/tmp/idle"

        private val TimeoutFile = File(IDLE_TIMEOUT_FILE)
        private val DefaultTimeoutMinutes: Int = run {
            try {
                TimeoutFile.readText().trim().toInt()
            } catch (e: Exception) {
                60
            }
        }

        fun getInstance(): IdleShutdownSettings = service()
    }

    class State {
        /** Whether the idle shutdown feature is enabled. */
        var enabled: Boolean = false
        /** Custom idle timeout in minutes (used when the timeout file does not exist). */
        var customTimeoutMinutes: Int = 60
        /** Seconds of visible warning before the IDE actually closes. 0 = close immediately. */
        var warningSeconds: Int = 60
        /** Don't close while any run/debug process is still running. */
        var skipWhenProcessRunning: Boolean = true
        /** Show the last activity timestamp in the IDE status bar. */
        var showLastActivityInStatusbar: Boolean = false
    }

    private var state = State()

    /** Returns true if the idle timeout file exists in the file system. */
    fun isTimeoutFilePresent(): Boolean = TimeoutFile.exists()

    /** Returns the idle timeout in minutes, read from /tmp/idle or the user's custom setting. */
    fun getTimeoutMinutes(): Int {
        if (TimeoutFile.exists()) {
            return try {
                TimeoutFile.readText().trim().toInt()
            } catch (e: Exception) {
                60
            }
        }
        return state.customTimeoutMinutes
    }

    /** Returns true if the plugin can be disabled by the user. */
    fun canBeDisabled(): Boolean = !TimeoutFile.exists()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
