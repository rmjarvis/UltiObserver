package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/// Tests for deterministic clock and duration formatting helpers.
class TestGameClock {
    /**
     * Test deterministic clock helpers that are public model surface.
     * These tests pin display behavior without relying on the wall clock.
     */
    @Test
    fun clockAndDurationDisplays() {
        // Setup-time defaults round to the next half hour using a caller-supplied clock.
        assertEquals(LocalTime.of(9, 0), nextHalfHourFrom(LocalTime.of(9, 0)))
        assertEquals(LocalTime.of(9, 30), nextHalfHourFrom(LocalTime.of(9, 0, 1)))
        assertEquals(LocalTime.of(9, 30), nextHalfHourFrom(LocalTime.of(9, 1)))
        assertEquals(LocalTime.of(9, 30), nextHalfHourFrom(LocalTime.of(9, 29)))
        assertEquals(LocalTime.of(10, 0), nextHalfHourFrom(LocalTime.of(9, 30)))
        assertEquals(LocalTime.MIDNIGHT, nextHalfHourFrom(LocalTime.of(23, 45)))

        // Clock formatting covers midnight, noon, morning, and afternoon values.
        assertEquals("12:00 AM", formatClockTime(LocalTime.MIDNIGHT))
        assertEquals("12:00 PM", formatClockTime(LocalTime.NOON))
        assertEquals("9:05 AM", formatClockTime(LocalTime.of(9, 5)))
        assertEquals("3:30 PM", formatClockTime(LocalTime.of(15, 30)))

        // Duration formatting clamps negative durations to zero and formats minute/second boundaries.
        assertEquals("0:00", formatDuration(Duration.ofSeconds(-3)))
        assertEquals("0:00", formatDuration(Duration.ZERO))
        assertEquals("0:59", formatDuration(Duration.ofSeconds(59)))
        assertEquals("1:00", formatDuration(Duration.ofSeconds(60)))
        assertEquals("1:01", formatDuration(Duration.ofSeconds(61)))
        assertEquals("61:01", formatDuration(Duration.ofSeconds(3661)))
    }
}
