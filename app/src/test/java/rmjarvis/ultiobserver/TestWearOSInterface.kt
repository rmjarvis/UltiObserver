package rmjarvis.ultiobserver

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotPullDirection
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshotCodec

/// Tests for the phone-side Wear OS interface.
class TestWearOSInterface : GameDomainTestFixtures() {
    /**
     * Verify the phone produces a self-contained, authoritative state representation from which
     * the watch can render the companion experience.
     */
    @Test
    fun watchStateSnapshot() {
        val now = timestampAt(
            standardLiveGameState(),
            LocalTime.of(11, 0),
        )

        // The ordinary Off setting explicitly tells an installed watch that companion use is
        // disabled, without exposing game state.
        val disabled = buildWearStateSnapshot(
            game = standardLiveGameState(),
            settings = Settings(),
            now = now,
        )
        assertEquals(WearSnapshotStatus.DISABLED, disabled.status)
        assertNull(disabled.activeGame)

        // Wear OS mode distinguishes an idle phone from a disabled connection.
        val companionSettings = Settings().copy(
            timingAlerts = TimingAlertPreferences(
                watchConnectionMode = WatchConnectionMode.WEAR_OS,
            ),
        )
        val noGame = buildWearStateSnapshot(
            game = null,
            settings = companionSettings,
            now = now,
        )
        assertEquals(WearSnapshotStatus.NO_ACTIVE_GAME, noGame.status)
        assertNull(noGame.activeGame)

        // A default game carries the default team presentation and action labels while omitting
        // optional state that does not apply.
        val default = buildWearStateSnapshot(
            game = standardLiveGameState(),
            settings = companionSettings,
            now = now,
        )
        assertEquals(WearSnapshotStatus.ACTIVE_GAME, default.status)
        assertNotNull(default.activeGame)
        val defaultSnapshot = default.activeGame!!
        assertEquals(0L, defaultSnapshot.officialClockOffsetMillis)
        assertEquals("America/New_York", defaultSnapshot.officialTimeZoneId)
        assertNull(defaultSnapshot.capLabel)
        assertNull(defaultSnapshot.capTargetEpochMillis)
        assertNotNull(defaultSnapshot.countdown)
        assertNull(defaultSnapshot.ratio)
        assertNull(defaultSnapshot.undoDescription)
        assertEquals(WearSnapshotPullDirection.LEFT_TO_RIGHT, defaultSnapshot.pullDirection)
        assertEquals("Viscous Coupling", defaultSnapshot.teamOne.name)
        assertEquals(0, defaultSnapshot.teamOne.score)
        assertEquals(TeamColorChoice.WHITE.accentArgb, defaultSnapshot.teamOne.backgroundArgb)
        assertEquals(TeamColorChoice.WHITE.contentArgb, defaultSnapshot.teamOne.contentArgb)
        assertEquals("Time viol.", defaultSnapshot.teamOne.actions.timeViolationLabel)
        assertEquals("Offsides", defaultSnapshot.teamOne.actions.pullViolationLabel)
        assertEquals("Card", defaultSnapshot.teamOne.actions.cardLabel)
        assertEquals("Tech", defaultSnapshot.teamOne.actions.technicalFoulLabel)
        assertEquals("Timeout (2)", defaultSnapshot.teamOne.actions.timeoutLabel)
        assertTrue(defaultSnapshot.teamOne.actions.goalEnabled)
        assertTrue(defaultSnapshot.teamOne.actions.timeViolationEnabled)
        assertTrue(defaultSnapshot.teamOne.actions.pullViolationEnabled)
        assertTrue(defaultSnapshot.teamOne.actions.cardEnabled)
        assertTrue(defaultSnapshot.teamOne.actions.technicalFoulEnabled)
        assertTrue(defaultSnapshot.teamOne.actions.timeoutEnabled)

        // A live point without an interruption countdown omits countdown state entirely.
        val livePointSnapshot = buildWearStateSnapshot(
            game = standardLiveGameState().continueLivePoint(),
            settings = companionSettings,
            now = now,
        ).activeGame!!
        assertNull(livePointSnapshot.countdown)

        // A separate halftime game makes all context-dependent team actions unavailable.
        val halftimeBaseGame = standardLiveGameState()
        val halftimeGame = halftimeBaseGame.copy(
            division = GameDivision.OPEN,
            phase = GamePhase.HALFTIME,
            countdown = buildHalftimeCountdown(
                halftimeMinutes = halftimeBaseGame.rules.halftimeMinutes,
                sequenceStart = now,
            ),
            halftimeTaken = true,
        )
        val halftimeSnapshot = buildWearStateSnapshot(
            game = halftimeGame,
            settings = companionSettings,
            now = now,
        ).activeGame!!
        assertFalse(halftimeSnapshot.teamOne.actions.timeViolationEnabled)
        assertFalse(halftimeSnapshot.teamOne.actions.pullViolationEnabled)
        assertFalse(halftimeSnapshot.teamOne.actions.timeoutEnabled)
        assertFalse(halftimeSnapshot.teamTwo.actions.timeViolationEnabled)
        assertFalse(halftimeSnapshot.teamTwo.actions.pullViolationEnabled)
        assertFalse(halftimeSnapshot.teamTwo.actions.timeoutEnabled)

        // An active mixed game carries exact phone colors, phone-style action labels, timing
        // deadlines, future cues, ratio settings, pull direction, and Undo availability.
        val baseGame = standardLiveGameState(
            pullingTeam = TeamId.TEAM_TWO,
        )
        val activeGame = baseGame.copy(
            division = GameDivision.MIXED,
            rules = baseGame.rules.copy(
                gameTo = 15,
                useHardCap = true,
                nominalHardCapMinutes = 180,
                genderRatioRule = GenderRatioRule.ABBA,
            ),
            officialClockOffsetMillis = 42_000L,
            teamOne = baseGame.teamOne.copy(
                name = "Animal",
                color = TeamColorChoice.BLUE,
                score = 8,
                timeViolations = 1,
                falseStarts = 2,
                technicalFouls = 1,
            ),
            teamTwo = baseGame.teamTwo.copy(
                name = "Machine",
                color = TeamColorChoice.CUSTOM,
                customColorArgb = 0xFF123456,
                score = 7,
            ),
            undoEntry = UndoEntry("Undo goal by Animal", baseGame),
        )
        val settings = companionSettings.copy(
            fourMenThreeWomenBadgeColorArgb = TeamColorChoice.BLACK.accentArgb,
            fourWomenThreeMenBadgeColorArgb = TeamColorChoice.PINK.accentArgb,
        )
        val active = buildWearStateSnapshot(
            game = activeGame,
            settings = settings,
            now = now,
        )
        assertNotNull(active.activeGame)
        val gameSnapshot = active.activeGame!!
        val expectedCountdown = activeGame.activeCountdown(now)!!
        val expectedCap = activeGame.computeNextCapStatus(now)
        assertEquals(WearSnapshotStatus.ACTIVE_GAME, active.status)
        assertEquals(42_000L, gameSnapshot.officialClockOffsetMillis)
        assertEquals("America/New_York", gameSnapshot.officialTimeZoneId)
        assertEquals("Hard cap in", gameSnapshot.capLabel)
        assertEquals(expectedCap?.targetEpoch, gameSnapshot.capTargetEpochMillis)
        assertEquals("Animal", gameSnapshot.teamOne.name)
        assertEquals(8, gameSnapshot.teamOne.score)
        assertEquals(TeamColorChoice.BLUE.accentArgb, gameSnapshot.teamOne.backgroundArgb)
        assertEquals("Time viol. (1)", gameSnapshot.teamOne.actions.timeViolationLabel)
        assertEquals("False start (2)", gameSnapshot.teamOne.actions.pullViolationLabel)
        assertEquals("Tech (1)", gameSnapshot.teamOne.actions.technicalFoulLabel)
        assertEquals(0xFF123456, gameSnapshot.teamTwo.backgroundArgb)
        assertEquals(WearSnapshotPullDirection.RIGHT_TO_LEFT, gameSnapshot.pullDirection)
        assertEquals("Undo goal by Animal", gameSnapshot.undoDescription)
        assertNotNull(gameSnapshot.ratio)
        assertNotNull(gameSnapshot.countdown)
        assertEquals(expectedCountdown.targetEpoch, gameSnapshot.countdown!!.targetEpochMillis)
        assertNull(gameSnapshot.countdown!!.pausedAtEpochMillis)
        assertFalse(gameSnapshot.countdown!!.cues.isEmpty())
        assertEquals(
            expectedCountdown.upcomingTimingCues(now).map { cue -> cue.targetEpoch },
            gameSnapshot.countdown!!.cues.map { cue -> cue.targetEpochMillis },
        )

        // A paused countdown carries its pause epoch so the watch freezes the same target-derived
        // duration and suppresses upcoming cues without receiving a derived remaining duration.
        val pausedGame = activeGame.copy(
            countdown = expectedCountdown.pause(now),
        )
        val pausedSnapshot = buildWearStateSnapshot(
            game = pausedGame,
            settings = settings,
            now = now + 5_000L,
        ).activeGame!!.countdown!!
        assertEquals(expectedCountdown.targetEpoch, pausedSnapshot.targetEpochMillis)
        assertEquals(now, pausedSnapshot.pausedAtEpochMillis)
        assertTrue(pausedSnapshot.cues.isEmpty())

        // The shared codec preserves the complete protocol value across the Data Layer payload.
        assertEquals(active, WearStateSnapshotCodec.decode(WearStateSnapshotCodec.encode(active)))
    }
}
