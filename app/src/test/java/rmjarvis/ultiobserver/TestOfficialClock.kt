package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Tests for official tournament clock synchronization on the phone-time model axis. */
class TestOfficialClock : GameDomainTestFixtures() {
    /** Verify minute synchronization, manual adjustment, and user-facing formatting. */
    @Test
    fun clockAdjustments() {
        val phoneTime = 11_557_210L

        // Nearest-minute synchronization rounds the visible clock to its closest boundary.
        val nearestOffset = syncOfficialClockOffsetToNearestMinute(phoneTime, 0L)
        assertEquals(11_580_000L, phoneTime + nearestOffset)
        assertEquals(
            11_520_000L,
            11_549_999L + syncOfficialClockOffsetToNearestMinute(11_549_999L, 0L),
        )
        assertEquals(
            11_580_000L,
            11_550_000L + syncOfficialClockOffsetToNearestMinute(11_550_000L, 0L),
        )

        // Synchronizing again uses the already adjusted visible time rather than raw phone time.
        val alreadyAdjustedOffset = 77_210L
        assertEquals(
            60_000L,
            10_000L + syncOfficialClockOffsetToNearestMinute(10_000L, alreadyAdjustedOffset),
        )

        // Minute nudges preserve seconds and tenths.
        assertEquals(
            phoneTime + nearestOffset - 60_000L,
            phoneTime + adjustOfficialClockOffsetMinutes(nearestOffset, -1),
        )
        assertEquals(
            phoneTime + nearestOffset + 60_000L,
            phoneTime + adjustOfficialClockOffsetMinutes(nearestOffset, 1),
        )

        // The synchronization screen shows tenths and describes both adjusted directions.
        assertEquals("3:12:37.2 AM", formatOfficialClockTime(phoneTime, ZoneOffset.UTC))
        assertEquals("Using phone time", describeOfficialClockOffset(0L))
        assertEquals(
            "Official clock is 44.8 seconds behind phone time",
            describeOfficialClockOffset(-44_800L),
        )
        assertEquals(
            "Official clock is 1 minute, 15.2 seconds ahead of phone time",
            describeOfficialClockOffset(75_200L),
        )
        assertEquals(
            "Official clock is 2 minutes ahead of phone time",
            describeOfficialClockOffset(120_000L),
        )
    }

    /** Verify scheduled epochs use phone time while absolute display uses official time. */
    @Test
    fun gameClockMapping() {
        // Setup receives phone time and applies its offset before choosing the next half hour.
        val timeZone = ZoneId.systemDefault()
        val phoneNow = epochTimestamp(
            LocalDate.of(2026, 8, 5),
            LocalTime.of(10, 29, 30),
            timeZone,
        )
        val adjustedSetup = newSetupGameState(
            now = phoneNow,
            officialClockOffsetMillis = 60_000L,
        )
        assertEquals(LocalTime.of(11, 0), adjustedSetup.startTime)

        val phoneClockState = standardLiveGameState()
        val adjustedState = phoneClockState.copy(officialClockOffsetMillis = 60_000L)

        // A clock one minute ahead maps start and cap times one minute earlier on the phone clock,
        // while both states still display the same official start and cap times.
        assertEquals(
            phoneClockState.startEpoch - 60_000L,
            adjustedState.startEpoch,
        )
        assertEquals(
            phoneClockState.capEpoch(CapType.HALF) - 60_000L,
            adjustedState.capEpoch(CapType.HALF),
        )
        assertEquals(
            formatClockTime(adjustedState.startTime),
            adjustedState.formatOfficialGameTime(adjustedState.startEpoch),
        )
        assertEquals(
            phoneClockState.formatOfficialGameTime(phoneClockState.capEpoch(CapType.HALF)),
            adjustedState.formatOfficialGameTime(adjustedState.capEpoch(CapType.HALF)),
        )

        // The opening countdown is built directly from the phone-time start epoch.
        assertEquals(
            phoneClockState.countdown!!.targetEpoch - 60_000L,
            adjustedState.copy(phase = GamePhase.SETUP, countdown = null)
                .startGame(OrientationPreference.PORTRAIT)
                .countdown!!.targetEpoch,
        )
    }

    /** Verify action timers stay on phone time while event insertion captures official time. */
    @Test
    fun phoneCountdownAndOfficialEventLog() {
        // Two parallel states with the same goal at the same time, but one of them
        // has an offset of 120 seconds for the official clock.
        var phoneTimeState = standardLiveGameState()
        var offsetState = standardLiveGameState().copy(officialClockOffsetMillis = 120_000L)

        // Recording a goal starts the same phone-time countdown regardless of clock offset
        val phoneTime = 100_000L
        phoneTimeState = phoneTimeState.recordGoal(TeamId.TEAM_ONE, phoneTime)
        offsetState = offsetState.recordGoal(TeamId.TEAM_ONE, phoneTime)
        assertEquals(phoneTimeState.countdown, offsetState.countdown)

        // The event log gets the official time in each case, which differs.
        assertEquals(
            "7:03",
            offsetState.formatOfficialGameTime(phoneTime, EVENT_LOG_TIME_FORMATTER),
        )
        assertEquals(
            "7:03",
            offsetState.eventLog.last().timeText,
        )
        assertEquals(
            "7:01",
            phoneTimeState.eventLog.last().timeText,
        )

        // A later clock change leaves the already-recorded official time text untouched.
        val resynchronized = offsetState.withOfficialClockOffset(-60_000L)
        assertEquals(
            "7:03",
            resynchronized.eventLog.last().timeText,
        )
    }

    /** Verify offset changes update only the current game's absolute timing. */
    @Test
    fun inProgressClockChange() {
        val viewModel = AppViewModel(NoOpAppStateStorage)
        val countdown = CountdownState(
            kind = CountdownKind.BETWEEN_POINTS,
            label = "Pull in",
            durationSeconds = 70,
            targetEpoch = 170_000L,
        )
        val currentGame = standardLiveGameState().copy(countdown = countdown)
        val originalStartEpoch = currentGame.startEpoch
        viewModel.updateCurrentGame(currentGame)

        // Changing the offset updates the stored mapping and absolute start epoch, while a running
        // action timer remains attached to its original phone-time target.
        viewModel.updateOfficialClockOffset(-44_800L)
        assertEquals(-44_800L, viewModel.settings.officialClockOffsetMillis)
        assertEquals(170_000L, viewModel.currentGame!!.countdown!!.targetEpoch)
        assertEquals(originalStartEpoch + 44_800L, viewModel.currentGame!!.startEpoch)

        // An active opening timer is the exception because its target is the official start time.
        viewModel.updateCurrentGame(
            standardLiveGameState().copy(
                countdown = countdown.copy(kind = CountdownKind.OPENING_PULL),
            )
        )
        viewModel.updateOfficialClockOffset(45_200L)
        assertEquals(124_800L, viewModel.currentGame!!.countdown!!.targetEpoch)

        // Saving the game and changing the clock leaves the archived snapshot untouched.
        viewModel.startNewGame(now = 200_000L)
        val archivedGame = viewModel.archivedGames.single()
        viewModel.updateOfficialClockOffset(15_200L)
        assertEquals(archivedGame, viewModel.archivedGames.single())

        // Returning the archived game to current applies the new offset.
        viewModel.openArchivedGame(index = 0, now = 200_000L)
        viewModel.makeArchivedGameCurrent()
        assertEquals(15_200L, viewModel.currentGame!!.officialClockOffsetMillis)
        assertEquals(archivedGame.startEpoch + 30_000L, viewModel.currentGame!!.startEpoch)
        assertEquals(
            archivedGame.countdown!!.targetEpoch + 30_000L,
            viewModel.currentGame!!.countdown!!.targetEpoch,
        )
    }

    /** Verify undo and redo retain the clock mapping active when they are invoked. */
    @Test
    fun undoRedoUseCurrentOffset() {
        // Moving from the opening state into live play puts the unadjusted opening state in undo
        // history; the offset is deliberately changed only after that action.
        val opening = standardLiveGameState()
        val openingTarget = opening.countdown!!.targetEpoch
        val live = opening.beginLivePoint(100_000L).withOfficialClockOffset(60_000L)

        // Undo restores the earlier opening state but reapplies the currently active offset,
        // including moving its opening countdown to the corresponding phone time.
        val undone = live.undoLastAction()
        assertEquals(60_000L, undone.officialClockOffsetMillis)
        assertEquals(openingTarget - 60_000L, undone.countdown!!.targetEpoch)

        // Redo likewise retains the current offset instead of restoring the historical one.
        val redone = undone.redoLastAction()
        assertEquals(60_000L, redone.officialClockOffsetMillis)
    }

    /** Verify restoring a genuinely completed archive adopts the current app clock mapping. */
    @Test
    fun restoredCompletedArchiveUsesCurrentOffset() {
        // End and archive a game through the normal completed-game path with one offset, then
        // change the app's offset while no game is current.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.updateCurrentGame(
            standardLiveGameState().endGameNow(100_000L)
                .withOfficialClockOffset(10_000L)
        )
        viewModel.archiveCompletedGame()
        viewModel.updateOfficialClockOffset(60_000L)

        // Restoring the completed archive keeps it completed but applies the app's current offset,
        // rather than the different offset stored in the archived snapshot.
        viewModel.openArchivedGame(index = 0, now = 200_000L)
        viewModel.makeArchivedGameCurrent()
        assertEquals(GamePhase.GAME_OVER, viewModel.currentGame!!.phase)
        assertEquals(60_000L, viewModel.currentGame!!.officialClockOffsetMillis)

        // Even though it is completed, it behaves like any other current game when the offset
        // changes again.
        viewModel.updateOfficialClockOffset(75_000L)
        assertEquals(75_000L, viewModel.currentGame!!.officialClockOffsetMillis)
    }
}
