package rmjarvis.ultiobserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rmjarvis.ultiobserver.wearprotocol.WearCountdownSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearCueSnapshot

class TestVibrationScheduler {
    /** Test vibration cues as part of normal countdowns */
    @Test
    fun countdownScheduling() {
        val clock = VibrationClock()
        val pattern = VibrationPattern(100L, 3, 120L)
        val countdown = WearCountdownSnapshot("Pull", 20_000L, null, listOf(
            WearCueSnapshot("Silent cue", 12_000L),
            WearCueSnapshot("Vibrate", 15_000L, pattern),
        ))

        // A later vibrating cue keeps the processor awake across an earlier silent cue.
        clock.scheduler.update(countdown, phoneNow = 10_000L)
        assertTrue(clock.awake)
        clock.advance(2_000L)
        assertTrue(clock.played.isEmpty())
        clock.advance(3_000L)
        assertEquals(listOf(pattern), clock.played)

        // Repeated snapshots do not replay a cue, and the wake lock covers the complete pattern.
        clock.scheduler.update(countdown, phoneNow = 15_000L)
        clock.advance(539L)
        assertTrue(clock.awake)
        clock.advance(1L)
        assertFalse(clock.awake)
        assertEquals(listOf(pattern), clock.played)

        // Halftime and pregame use the same rule, even with a vibration several minutes away.
        val halftime = countdown.copy(label = "Halftime", targetEpochMillis = 610_000L,
            cues = listOf(WearCueSnapshot("End halftime", 610_000L, pattern)))
        clock.scheduler.update(halftime, phoneNow = 15_540L)
        assertTrue(clock.awake)
        clock.advance(100_000L)
        assertEquals(listOf(pattern), clock.played)
        clock.scheduler.stop()
        assertFalse(clock.awake)
        clock.advance(600_000L)
        assertEquals(listOf(pattern), clock.played)
    }

    /** Test updates to the scheduled vibrations when there are changes to the countdown. */
    @Test
    fun countdownChanges() {
        val clock = VibrationClock()
        val pattern = VibrationPattern(100L, 3, 120L)
        val original = WearCountdownSnapshot("Pull", 20_000L, null,
            listOf(WearCueSnapshot("Vibrate", 15_000L, pattern)))

        // Changing a deadline replaces the old scheduled vibration.
        clock.scheduler.update(original, phoneNow = 10_000L)
        val adjusted = original.copy(targetEpochMillis = 25_000L,
            cues = listOf(WearCueSnapshot("Vibrate", 20_000L, pattern)))
        clock.scheduler.update(adjusted, phoneNow = 10_000L)
        clock.advance(5_000L)
        assertTrue(clock.played.isEmpty())

        // Pausing releases the processor; resuming schedules the updated cues.
        clock.scheduler.update(adjusted.copy(pausedAtEpochMillis = 15_000L), phoneNow = 15_000L)
        assertFalse(clock.awake)
        clock.advance(5_000L)
        assertTrue(clock.played.isEmpty())
        clock.scheduler.update(adjusted, phoneNow = 15_000L)
        assertTrue(clock.awake)

        // Turning vibration off cancels the pending request without waiting for its deadline.
        clock.scheduler.update(adjusted.copy(cues = adjusted.cues.map { it.copy(vibration = null) }),
            phoneNow = 15_000L)
        assertFalse(clock.awake)
        clock.advance(5_000L)
        assertTrue(clock.played.isEmpty())

        // A just-arrived cue can play immediately, but losing the countdown cannot cut it short.
        clock.scheduler.update(adjusted, phoneNow = 20_100L)
        assertEquals(listOf(pattern), clock.played)
        clock.scheduler.update(null, phoneNow = 20_100L)
        assertTrue(clock.awake)
        clock.advance(540L)
        assertFalse(clock.awake)

        // Reconnection never replays an already delivered cue or an old missed cue.
        clock.scheduler.update(adjusted, phoneNow = 20_640L)
        clock.scheduler.update(original, phoneNow = 30_000L)
        assertFalse(clock.awake)
        assertEquals(listOf(pattern), clock.played)
    }
    /** A newly due cue waits for the complete vibration sequence already in progress. */
    @Test
    fun overlappingCues() {
        // The first cue starts immediately and the second becomes due before its sequence ends.
        val clock = VibrationClock()
        val first = VibrationPattern(100L, 3, 120L)
        val second = VibrationPattern(200L, 1, gapMillis = 0L)
        val countdown = WearCountdownSnapshot("Pull", 20_000L, null, listOf(
            WearCueSnapshot("First", 10_000L, first),
            WearCueSnapshot("Second", 10_100L, second),
        ))
        clock.scheduler.update(countdown, phoneNow = 10_000L)
        clock.advance(100L)
        clock.scheduler.update(countdown, phoneNow = 10_100L)
        assertEquals(listOf(first), clock.played)
        assertTrue(clock.awake)

        // The second cue starts after all three pulses finish, with no gap in the wake lock.
        clock.advance(439L)
        assertEquals(listOf(first), clock.played)
        clock.advance(1L)
        assertEquals(listOf(first, second), clock.played)
        assertTrue(clock.awake)
        clock.advance(200L)
        assertFalse(clock.awake)
    }
}

/** Run scheduled callbacks deterministically without sleeping or using Android clocks. */
private class VibrationClock {
    var now = 0L
    var awake = false
    val played = mutableListOf<VibrationPattern>()
    private data class Timer(val at: Long, val action: () -> Unit)
    private val timers = mutableListOf<Timer>()
    val scheduler = VibrationScheduler(
        clock = { now },
        schedule = { delay, action ->
            val timer = Timer(now + delay, action)
            timers += timer
            val cancel: () -> Unit = { timers.remove(timer) }
            cancel
        },
        keepAwake = { awake = it },
        vibrate = { played += it },
    )

    fun advance(millis: Long) {
        val end = now + millis
        while (true) {
            val next = timers.minByOrNull { it.at } ?: break
            if (next.at > end) break
            now = next.at
            timers.remove(next)
            next.action()
        }
        now = end
    }
}
