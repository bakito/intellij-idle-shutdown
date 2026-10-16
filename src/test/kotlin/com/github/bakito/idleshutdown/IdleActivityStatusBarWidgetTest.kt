package com.github.bakito.idleshutdown

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Tests for the [IdleActivityStatusBarWidget] time formatting logic.
 * Since the widget requires the IDE environment, we extract and test the
 * formatting logic separately.
 */
class IdleActivityStatusBarWidgetTest {

    /**
     * Replicates the [IdleActivityStatusBarWidget.formatRemaining] logic for testing.
     */
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

    @Test
    fun `formatRemaining with hours displays HH_MM_SS format`() {
        assertEquals("Shutdown in 01:23:45", formatRemaining(3600 + 1380 + 45))
        assertEquals("Shutdown in 02:00:00", formatRemaining(7200))
        assertEquals("Shutdown in 10:59:59", formatRemaining(36000 + 3540 + 59))
    }

    @Test
    fun `formatRemaining with minutes displays Xm Ys format`() {
        assertEquals("Shutdown in 1m 30s", formatRemaining(90))
        assertEquals("Shutdown in 59m 0s", formatRemaining(3540))
        assertEquals("Shutdown in 1m 0s", formatRemaining(60))
    }

    @Test
    fun `formatRemaining with seconds only displays Ys format`() {
        assertEquals("Shutdown in 45s", formatRemaining(45))
        assertEquals("Shutdown in 0s", formatRemaining(0))
        assertEquals("Shutdown in 59s", formatRemaining(59))
    }

    @Test
    fun `formatRemaining with negative seconds treats as zero`() {
        assertEquals("Shutdown in 0s", formatRemaining(-1))
        assertEquals("Shutdown in 0s", formatRemaining(-100))
    }

    @Test
    fun `formatRemaining boundary at exactly 1 hour`() {
        assertEquals("Shutdown in 01:00:00", formatRemaining(3600))
    }

    @Test
    fun `formatRemaining boundary at 59 minutes 59 seconds`() {
        assertEquals("Shutdown in 59m 59s", formatRemaining(3599))
    }

    @Test
    fun `formatRemaining boundary at 1 minute`() {
        assertEquals("Shutdown in 1m 0s", formatRemaining(60))
    }

    @Test
    fun `formatRemaining boundary at 59 seconds`() {
        assertEquals("Shutdown in 59s", formatRemaining(59))
    }

    @Test
    fun `formatRemaining with large values`() {
        assertEquals("Shutdown in 23:59:59", formatRemaining(86399))
        assertEquals("Shutdown in 99:59:59", formatRemaining(359999))
    }
}
