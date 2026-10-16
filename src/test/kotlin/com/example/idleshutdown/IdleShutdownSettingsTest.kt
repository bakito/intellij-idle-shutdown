package com.example.idleshutdown

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdleShutdownSettingsTest {

    @Test
    fun `new state has correct defaults`() {
        val state = IdleShutdownSettings.State()
        assertFalse(state.enabled)
        assertEquals(60, state.customTimeoutMinutes)
        assertEquals(60, state.warningSeconds)
        assertTrue(state.skipWhenProcessRunning)
        assertFalse(state.showLastActivityInStatusbar)
    }

    @Test
    fun `loadState replaces state`() {
        val settings = IdleShutdownSettings()
        val newState = IdleShutdownSettings.State().apply {
            enabled = true
            customTimeoutMinutes = 120
            warningSeconds = 30
            skipWhenProcessRunning = false
            showLastActivityInStatusbar = true
        }
        settings.loadState(newState)
        val state = settings.getState()
        assertTrue(state.enabled)
        assertEquals(120, state.customTimeoutMinutes)
        assertEquals(30, state.warningSeconds)
        assertFalse(state.skipWhenProcessRunning)
        assertTrue(state.showLastActivityInStatusbar)
    }

    @Test
    fun `getState returns same instance after load`() {
        val settings = IdleShutdownSettings()
        val state = IdleShutdownSettings.State()
        settings.loadState(state)
        assertEquals(state, settings.getState())
    }

    @Test
    fun `timeout file creation and detection works`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "idleshutdown-test-${System.nanoTime()}")
        tempDir.mkdirs()
        try {
            val testFile = File(tempDir, "test-idle")
            assertFalse(testFile.exists())
            testFile.writeText("30")
            assertTrue(testFile.exists())
            assertEquals(30, testFile.readText().trim().toInt())
            testFile.delete()
            assertFalse(testFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `getTimeoutMinutes returns positive value`() {
        val settings = IdleShutdownSettings()
        val state = IdleShutdownSettings.State().apply {
            customTimeoutMinutes = 90
        }
        settings.loadState(state)
        // When /tmp/idle does not exist, it returns customTimeoutMinutes
        // When it exists, it returns the file value
        val timeout = settings.getTimeoutMinutes()
        assertTrue(timeout > 0, "Timeout should be positive")
    }
}
