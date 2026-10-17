package com.github.bakito.idleshutdown

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import javax.xml.parsers.DocumentBuilderFactory
import java.io.File

@Service
@State(name = "IdleShutdownSettings", storages = [Storage("idleShutdown.xml")])
class IdleShutdownSettings : PersistentStateComponent<IdleShutdownSettings.State> {

    companion object {
        private val log = logger<IdleShutdownSettings>()

        const val IDLE_TIMEOUT_FILE = "~/.config/bakito/intellij-idle-shutdown.xml"

        private val TimeoutFile = File(System.getProperty("user.home"))
            .resolve(".config/bakito/intellij-idle-shutdown.xml")

        fun getInstance(): IdleShutdownSettings = service()

        @Volatile private var parseError: String? = null

        fun getParseError(): String? = parseError

        /**
         * Reads the idle timeout from the XML config file using a safe DOM parser.
         * Returns null if the file does not exist or cannot be parsed.
         */
        private fun readTimeoutFromConfig(): Int? {
            if (!TimeoutFile.exists()) {
                parseError = null
                return null
            }
            return try {
                val factory = DocumentBuilderFactory.newInstance().apply {
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                    setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                }
                val document = factory.newDocumentBuilder().parse(TimeoutFile)
                val elements = document.getElementsByTagName("idleShutdownAfterMinutes")
                if (elements.length == 0) {
                    parseError = "Config file exists but is missing the idleShutdownAfterMinutes element"
                    log.warn("Config file missing idleShutdownAfterMinutes element")
                    null
                } else {
                    val text = elements.item(0).textContent.trim()
                    val value = text.toIntOrNull()
                    if (value == null) {
                        parseError = "Config file value '$text' is not a valid integer"
                        log.warn("Config file value '$text' is not a valid integer")
                        null
                    } else {
                        parseError = null
                        value
                    }
                }
            } catch (e: Exception) {
                parseError = "Failed to parse config file: ${e.message}"
                log.warn("Failed to parse config file $IDLE_TIMEOUT_FILE", e)
                null
            }
        }
    }

    class State {
        /** Whether the idle shutdown feature is enabled. */
        var enabled: Boolean = false
        /** Custom idle timeout in minutes (used when the config file does not exist). */
        var customTimeoutMinutes: Int = 60
        /** Seconds of visible warning before the IDE actually closes. 0 = close immediately. */
        var warningSeconds: Int = 60
        /** Don't close while any run/debug process is still running. */
        var skipWhenProcessRunning: Boolean = true
        /** Show the last activity timestamp in the IDE status bar. */
        var showLastActivityInStatusbar: Boolean = false
    }

    private var state = State()

    /** Returns true if the idle config file exists in the file system. */
    fun isTimeoutFilePresent(): Boolean = TimeoutFile.exists()

    /** Returns the last parsing error message if the config file exists but could not be parsed. */
    fun getParseError(): String? = parseError

    /** Returns the idle timeout in minutes, read from the config file or the user's custom setting. */
    fun getTimeoutMinutes(): Int {
        return readTimeoutFromConfig() ?: state.customTimeoutMinutes
    }

    /** Returns true if the plugin can be disabled by the user. */
    fun canBeDisabled(): Boolean = !TimeoutFile.exists()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
