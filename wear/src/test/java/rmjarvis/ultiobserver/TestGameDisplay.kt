package rmjarvis.ultiobserver

import org.junit.Assert.*
import org.junit.Test
import rmjarvis.ultiobserver.wearprotocol.*

/** Display values derived from the phone snapshot and calibrated phone time. */
class TestGameDisplay {
    /** Show game identity, colors, direction, and the phone's current action availability. */
    @Test
    fun gameState() {
        val game = navigationSnapshot().activeGame!!
        val display = game.toGameDisplay(0L, true)
        assertEquals("12:00", display.officialTime)
        assertEquals("Animal", display.teamOne.name)
        assertEquals("Viscous Coupling", display.teamTwo.name)
        assertEquals(0, display.teamOne.score)
        assertEquals(PullDirection.LEFT_TO_RIGHT, display.pullDirection)
        assertTrue(display.connected)
        assertTrue(display.actionsAvailable)
        assertFalse(display.gameOver)
        assertNull(display.ratioBadge)
        assertNull(display.undoDescription)
        assertNull(display.countdownValue)
        assertEquals("", display.countdownLabel)

        // A mixed game's badge, reversed pull, and Undo come from the updated snapshot.
        val updated = game.copy(
            officialClockOffsetMillis = 60_000L,
            pullDirection = WearSnapshotPullDirection.RIGHT_TO_LEFT,
            ratio = WearRatioSnapshot("4W", 0xFF123456, 0xFFFFFFFF),
            undoDescription = "Undo Goal by Animal", actionsAvailable = false,
        ).toGameDisplay(0L, false)
        assertEquals("12:01", updated.officialTime)
        assertEquals(PullDirection.RIGHT_TO_LEFT, updated.pullDirection)
        assertEquals("4W", updated.ratioBadge!!.label)
        assertEquals("Undo Goal by Animal", updated.undoDescription)
        assertFalse(updated.connected)
        assertFalse(updated.actionsAvailable)
    }

    /** Advance caps, countdowns, and cues while respecting paused and completed games. */
    @Test
    fun timing() {
        val game = navigationSnapshot().activeGame!!.copy(
            upcomingCaps = listOf(WearCapSnapshot("Half cap", 30_000L), WearCapSnapshot("Hard cap", 120_000L)),
            countdown = WearCountdownSnapshot("Offense set in", 90_000L, null, listOf(
                WearCueSnapshot("Clear the field", 40_000L), WearCueSnapshot("Offense set", 80_000L),
            )),
            statusMessageTransitions = listOf(WearStatusMessageTransition(0L, "Cap passed")),
        )
        val running = game.toGameDisplay(60_000L, true)
        assertEquals("Hard cap in 1:00", running.capStatus)
        assertEquals("Offense set in", running.countdownLabel)
        assertEquals("0:30", running.countdownValue)
        assertEquals("Next: Offense set", running.nextCue)
        assertNull(running.statusMessage)

        // A paused timer retains its remaining time. After resuming past its target, it shows
        // zero rather than a negative duration, and expired cues/caps disappear.
        assertEquals("0:30", game.copy(countdown = game.countdown!!.copy(pausedAtEpochMillis = 60_000L))
            .toGameDisplay(75_000L, true).countdownValue)
        val expired = game.toGameDisplay(130_000L, true)
        assertEquals("0:00", expired.countdownValue)
        assertNull(expired.capStatus)
        assertNull(expired.nextCue)
        val finished = game.copy(gameOver = true).toGameDisplay(60_000L, true)
        assertNull(finished.countdownValue)
        assertNull(finished.nextCue)
    }

    /** Select the latest status text only when no countdown occupies that area. */
    @Test
    fun statusMessages() {
        val game = navigationSnapshot().activeGame!!.copy(statusMessageTransitions = listOf(
            WearStatusMessageTransition(10_000L, "Half cap passed"),
            WearStatusMessageTransition(20_000L, "Hard cap passed"),
            WearStatusMessageTransition(30_000L, null),
        ))
        assertNull(game.toGameDisplay(0L, true).statusMessage)
        assertEquals("Half cap passed", game.toGameDisplay(10_000L, true).statusMessage)
        assertEquals("Hard cap passed", game.toGameDisplay(25_000L, true).statusMessage)
        assertNull(game.toGameDisplay(30_000L, true).statusMessage)
    }

    /** Countdown actions occupy the same area as the phone action and suppress status text. */
    @Test
    fun countdownActions() {
        val game = navigationSnapshot().activeGame!!.copy(
            countdownAction = WearCountdownAction.START_MISCONDUCT,
            statusMessageTransitions = listOf(WearStatusMessageTransition(0L, "Half cap passed")),
        )
        val display = game.toGameDisplay(0L, true)
        assertEquals(WearCountdownAction.START_MISCONDUCT, display.countdownAction)
        assertNull(display.statusMessage)
        assertNull(display.countdownValue)

        // The replacement snapshot restores ordinary status text once the action is gone.
        val cleared = game.copy(countdownAction = null).toGameDisplay(0L, true)
        assertNull(cleared.countdownAction)
        assertEquals("Half cap passed", cleared.statusMessage)
    }

    /** Preserve individual action restrictions and disable all actions while a request is pending. */
    @Test
    fun teamActions() {
        val team = navigationSnapshot().activeGame!!.teamOne
        val enabled = team.toTeamActionsDisplay(true)
        assertEquals("Animal", enabled.team.name)
        assertEquals("Time viol.", enabled.timeViolationLabel)
        assertEquals("Offsides", enabled.pullViolationLabel)
        assertEquals("Card", enabled.cardLabel)
        assertEquals("Tech", enabled.technicalFoulLabel)
        assertEquals("Timeout (2)", enabled.timeoutLabel)
        assertTrue(enabled.goalEnabled && enabled.timeViolationEnabled && enabled.pullViolationEnabled &&
            enabled.cardEnabled && enabled.technicalFoulEnabled && enabled.timeoutEnabled)

        // The same disabled display results from a pending command or phone-owned restrictions.
        val disabled = team.toTeamActionsDisplay(false)
        assertFalse(disabled.goalEnabled || disabled.timeViolationEnabled || disabled.pullViolationEnabled ||
            disabled.cardEnabled || disabled.technicalFoulEnabled || disabled.timeoutEnabled)
        val restricted = team.copy(actions = team.actions.copy(
            goalEnabled = false, timeViolationEnabled = false, pullViolationEnabled = false,
            cardEnabled = false, technicalFoulEnabled = false, timeoutEnabled = false,
        ))
        assertEquals(disabled, restricted.toTeamActionsDisplay(true))
    }
}
