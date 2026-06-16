package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/// Tests for compact current-game persistence patching and restoration.
class TestCompactGameStatePersistence : GameDomainTestFixtures() {
    /// Verify game-state patches restore changed, unchanged, nullable, and list-replacement fields.
    @Test
    fun gameStatePatchRestoresAllFieldKinds() {
        val later = compactPatchLaterState()
        val previous = compactPatchPreviousState()

        val patch = GameStatePatch.fromLaterAndPrevious(later, previous)
        val prefixPatch = ListPatch.fromLaterAndPrevious(listOf(1, 2), listOf(1))!!
        val replacementPatch = ListPatch.fromLaterAndPrevious(listOf(1, 2), listOf(9))!!
        val longerReplacementPatch = ListPatch.fromLaterAndPrevious(listOf(1), listOf(1, 2))!!

        assertPatchProperties(patch, later, previous)
        assertEquals(previous, patch.applyTo(later))
        assertEquals(later, GameStatePatch().applyTo(later))
        assertEquals(later.teamOne, TeamLiveStatePatch().applyTo(later.teamOne))
        assertNull(ListPatch.fromLaterAndPrevious(listOf(1, 2), listOf(1, 2)))
        assertEquals(1, prefixPatch.previousSize)
        assertNull(prefixPatch.replacement)
        assertEquals(listOf(1), prefixPatch.applyTo(listOf(1, 2)))
        assertNull(replacementPatch.previousSize)
        assertEquals(listOf(9), replacementPatch.replacement)
        assertEquals(listOf(9), replacementPatch.applyTo(listOf(1, 2)))
        assertEquals(listOf(1, 2), longerReplacementPatch.replacement)
        assertEquals(listOf(1, 2), longerReplacementPatch.applyTo(listOf(1)))
    }

    /// Verify compact persisted states restore null and populated undo/redo chains.
    @Test
    fun persistedGameStateRestoresUndoAndRedoChains() {
        val later = compactPatchLaterState()
        val previous = compactPatchPreviousState()
        val undoBacked = later.copy(undoEntry = UndoEntry("Undo Compact patch", previous))
        val undone = undoBacked.undoLastAction()
        val snapshot = CurrentGameSnapshot(
            setupState = standardGameSetup(startTime = LocalTime.of(8, 0)),
            liveState = undone,
            setupMode = SetupMode.EDIT_CURRENT_GAME,
            hasSetupDraft = false,
        )

        val persisted = PersistedCurrentGameSnapshot.fromCurrentGameSnapshot(snapshot)
        val persistedLiveState = persisted.liveState!!
        val persistedUndoEntry = persistedLiveState.undoEntry
        val restored = persisted.toCurrentGameSnapshot()

        assertEquals(snapshot.versionName, persisted.versionName)
        assertEquals(snapshot.versionCode, persisted.versionCode)
        assertEquals(snapshot.setupState, persisted.setupState)
        assertEquals(snapshot.setupMode, persisted.setupMode)
        assertEquals(snapshot.hasSetupDraft, persisted.hasSetupDraft)
        assertEquals(undone.copy(undoEntry = null, redoEntry = null), persistedLiveState.state)
        assertNull(persistedUndoEntry)
        assertEquals(undoBacked.copy(undoEntry = null, redoEntry = null), persistedLiveState.redoEntry!!.state)
        assertEquals("Undo Compact patch", persistedLiveState.redoEntry!!.undoEntry!!.label)
        assertEquals(
            GameStatePatch.fromLaterAndPrevious(undoBacked.copy(undoEntry = null), previous),
            persistedLiveState.redoEntry!!.undoEntry!!.patchToPrevious,
        )
        assertNull(persistedLiveState.redoEntry!!.undoEntry!!.previousUndoEntry)
        assertEquals(snapshot, restored)
        assertEquals(undoBacked, restored.liveState!!.redoLastAction())
        assertEquals(
            CurrentGameSnapshot(),
            PersistedCurrentGameSnapshot(
                versionName = APP_STATE_VERSION_NAME,
                versionCode = APP_STATE_VERSION_CODE,
                setupState = newGameSetupState(),
                liveState = null,
                setupMode = SetupMode.NEW_GAME,
                hasSetupDraft = false,
            ).toCurrentGameSnapshot(),
        )
        assertEquals(later, PersistedGameState(state = later, undoEntry = null, redoEntry = null).restore())
    }

    /// Verify each game-state patch property matches the corresponding previous-state value.
    private fun assertPatchProperties(patch: GameStatePatch, later: GameState, previous: GameState) {
        assertEquals(previous.startDate, patch.startDate)
        assertEquals(previous.startTime, patch.startTime)
        assertEquals(previous.timeZone, patch.timeZone)
        assertEquals(previous.startEpoch, patch.startEpoch)
        assertEquals(previous.endEpoch, patch.endEpoch!!.value)
        assertEquals(previous.tournamentName, patch.tournamentName)
        assertEquals(previous.division, patch.division!!.value)
        assertEquals(previous.gameContext, patch.gameContext)
        assertEquals(previous.nearEndName, patch.nearEndName)
        assertEquals(previous.farEndName, patch.farEndName)
        assertEquals(previous.rules, patch.rules)
        assertTeamPatchProperties(patch.teamOne!!, previous.teamOne)
        assertTeamPatchProperties(patch.teamTwo!!, previous.teamTwo)
        assertEquals(previous.teamOnePlayers, patch.teamOnePlayers!!.applyTo(later.teamOnePlayers))
        assertEquals(previous.teamTwoPlayers, patch.teamTwoPlayers!!.applyTo(later.teamTwoPlayers))
        assertEquals(previous.teamOnePlayerCards, patch.teamOnePlayerCards!!.replacement)
        assertEquals(previous.teamTwoPlayerCards, patch.teamTwoPlayerCards!!.replacement)
        assertEquals(previous.eventLog, patch.eventLog!!.replacement)
        assertEquals(previous.nearAttackingTeam, patch.nearAttackingTeam)
        assertEquals(previous.pullingTeam, patch.pullingTeam)
        assertEquals(previous.pullingFromEnd, patch.pullingFromEnd)
        assertEquals(previous.pullPromptTarget, patch.pullPromptTarget)
        assertEquals(previous.openingPullingTeam, patch.openingPullingTeam)
        assertEquals(previous.openingPullingFromEnd, patch.openingPullingFromEnd)
        assertEquals(previous.phase, patch.phase)
        assertEquals(previous.countdown, patch.countdown!!.value)
        assertEquals(previous.pullCountdownExpired, patch.pullCountdownExpired)
        assertEquals(previous.pullSequenceOffsidesRecorded, patch.pullSequenceOffsidesRecorded)
        assertEquals(previous.pullSequenceFalseStartRecorded, patch.pullSequenceFalseStartRecorded)
        assertEquals(previous.pullSkippedForCurrentPoint, patch.pullSkippedForCurrentPoint)
        assertEquals(previous.pendingMisconductCountdown, patch.pendingMisconductCountdown)
        assertEquals(previous.halftimeTaken, patch.halftimeTaken)
        assertEquals(previous.halftimeTargetScore, patch.halftimeTargetScore!!.value)
        assertEquals(previous.winningScore, patch.winningScore!!.value)
        assertEquals(previous.halfCapApplied, patch.halfCapApplied)
        assertEquals(previous.softCapApplied, patch.softCapApplied)
        assertEquals(previous.hardCapApplied, patch.hardCapApplied)
        assertEquals(previous.pendingCapOffer, patch.pendingCapOffer!!.value)
        assertEquals(previous.lastEvent, patch.lastEvent)
    }

    /// Verify each team patch property matches the corresponding previous-team value.
    private fun assertTeamPatchProperties(patch: TeamLiveStatePatch, previous: TeamLiveState) {
        assertEquals(previous.name, patch.name)
        assertEquals(previous.color, patch.color)
        assertEquals(previous.customColorArgb, patch.customColorArgb!!.value)
        assertEquals(previous.coaches, patch.coaches)
        assertEquals(previous.fieldCaptains, patch.fieldCaptains)
        assertEquals(previous.spiritCaptains, patch.spiritCaptains)
        assertEquals(previous.score, patch.score)
        assertEquals(previous.timeoutsUsedThisHalf, patch.timeoutsUsedThisHalf)
        assertEquals(previous.firstHalfTimeoutsUsed, patch.firstHalfTimeoutsUsed)
        assertEquals(previous.offsides, patch.offsides)
        assertEquals(previous.falseStarts, patch.falseStarts)
        assertEquals(previous.timeViolations, patch.timeViolations)
        assertEquals(previous.technicalFouls, patch.technicalFouls)
        assertEquals(previous.blueCards, patch.blueCards)
    }

    /// Verify compact JSON omits unchanged patch fields and null event-log fields.
    @Test
    fun currentGameEncodingOmitsNullAndDefaultPatchFields() {
        val later = compactPatchLaterState()
        val previous = compactPatchPreviousState()
        val snapshot = CurrentGameSnapshot(
            setupState = standardGameSetup(startTime = LocalTime.of(8, 0)),
            liveState = later.copy(undoEntry = UndoEntry("Undo Compact patch", previous)),
            setupMode = SetupMode.EDIT_CURRENT_GAME,
            hasSetupDraft = false,
        )

        val json = encodeCurrentGameSnapshot(snapshot)

        assertFalse(json.contains("\"team\": null"))
        assertFalse(json.contains("\"player\": null"))
        assertFalse(json.contains("\"timeViolationOutcome\": null"))
        assertFalse(json.contains("\"teamOneScore\": null"))
        assertFalse(json.contains("\"teamTwoScore\": null"))
        assertFalse(json.contains("\"delta\": null"))
        assertFalse(json.contains("\"tournamentName\": null"))
        assertFalse(json.contains("\"replacement\": null"))
        assertFalse(json.contains("\"previousSize\": null"))
        assertFalse(json.contains("\"gameContext\": null"))
    }

    /// Verify invalid list patch representations fail loudly.
    @Test
    fun listPatchRequiresExactlyOneRepresentation() {
        assertThrows(IllegalArgumentException::class.java) {
            ListPatch<Int>()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ListPatch(previousSize = 1, replacement = listOf(1))
        }
    }

    /// Build a later state with non-null nullable fields and non-prefix list values.
    private fun compactPatchLaterState(): GameState {
        return standardLiveGameState(
            startDate = LocalDate.of(2026, 2, 3),
            startTime = LocalTime.of(9, 30),
            timeZone = ZoneId.of("America/Chicago"),
            rules = GameRules(gameTo = 17, halfCapMinutes = 50, softCapMinutes = 80, hardCapMinutes = 95),
            pullingTeam = TeamId.TEAM_TWO,
            pullingFromEnd = FieldEnd.NEAR,
        ).copy(
            endEpoch = 2_000L,
            tournamentName = "Later Tournament",
            division = GameDivision.WOMENS,
            gameContext = "Later semifinal",
            nearEndName = "Later near",
            farEndName = "Later far",
            teamOne = TeamLiveState(
                name = "Later One",
                color = TeamColorChoice.CUSTOM,
                customColorArgb = 0xFF102030L,
                coaches = "Later one coach",
                fieldCaptains = "Later one field captain",
                spiritCaptains = "Later one spirit captain",
                score = 7,
                timeoutsUsedThisHalf = 2,
                firstHalfTimeoutsUsed = 1,
                offsides = 3,
                falseStarts = 4,
                timeViolations = 1,
                technicalFouls = 5,
                blueCards = 6,
            ),
            teamTwo = TeamLiveState(
                name = "Later Two",
                color = TeamColorChoice.PINK,
                coaches = "Later two coach",
                fieldCaptains = "Later two field captain",
                spiritCaptains = "Later two spirit captain",
                score = 8,
                timeoutsUsedThisHalf = 1,
                firstHalfTimeoutsUsed = 2,
                offsides = 4,
                falseStarts = 3,
                timeViolations = 1,
                technicalFouls = 6,
                blueCards = 5,
            ),
            teamTwoPlayers = listOf(priorPlayerRecord("22", priorYellows = 1)),
            teamOnePlayerCards = listOf(InGamePlayerCardRecord("11", yellows = 1)),
            teamTwoPlayerCards = listOf(InGamePlayerCardRecord("12", reds = 1)),
            eventLog = listOf(
                EventLogEntry(timestampEpoch = 2_000L, type = EventLogType.FIRST_PULL),
                EventLogEntry(
                    timestampEpoch = 3_000L,
                    type = EventLogType.SCORE_ADJUSTED,
                    teamOneScore = 7,
                    teamTwoScore = 8,
                ),
            ),
            nearAttackingTeam = TeamId.TEAM_TWO,
            pullPromptTarget = PullPromptTarget.BOTH,
            openingPullingTeam = TeamId.TEAM_TWO,
            openingPullingFromEnd = FieldEnd.NEAR,
            phase = GamePhase.GAME_OVER,
            countdown = CountdownState(
                kind = CountdownKind.BETWEEN_POINTS,
                label = "Pull in",
                durationSeconds = 80,
                targetEpoch = 4_000L,
                betweenPointsTarget = BetweenPointsCountdownTarget.PULL,
            ),
            pullCountdownExpired = true,
            pullSequenceOffsidesRecorded = true,
            pullSequenceFalseStartRecorded = true,
            pullSkippedForCurrentPoint = true,
            pendingMisconductCountdown = true,
            halftimeTaken = true,
            halftimeTargetScore = 9,
            winningScore = 18,
            halfCapApplied = true,
            softCapApplied = true,
            hardCapApplied = true,
            pendingCapOffer = CapType.HARD,
            lastEvent = "Later event",
        )
    }

    /// Build a previous state that differs in every patchable field from the later state.
    private fun compactPatchPreviousState(): GameState {
        return standardLiveGameState(
            startDate = LocalDate.of(2026, 2, 2),
            startTime = LocalTime.of(8, 15),
            timeZone = ZoneId.of("America/New_York"),
            rules = GameRules(gameTo = 15, halfCapMinutes = 45, softCapMinutes = 90, hardCapMinutes = 105),
            pullingTeam = TeamId.TEAM_ONE,
            pullingFromEnd = FieldEnd.FAR,
        ).copy(
            endEpoch = null,
            tournamentName = "Previous Tournament",
            division = null,
            gameContext = "Previous pool game",
            nearEndName = "Previous near",
            farEndName = "Previous far",
            teamOne = TeamLiveState(
                name = "Previous One",
                color = TeamColorChoice.WHITE,
                coaches = "Previous one coach",
                fieldCaptains = "Previous one field captain",
                spiritCaptains = "Previous one spirit captain",
                score = 1,
                timeoutsUsedThisHalf = 0,
                firstHalfTimeoutsUsed = 0,
                offsides = 0,
                falseStarts = 0,
                timeViolations = 0,
                technicalFouls = 0,
                blueCards = 0,
            ),
            teamTwo = TeamLiveState(
                name = "Previous Two",
                color = TeamColorChoice.CUSTOM,
                customColorArgb = 0xFF304050L,
                coaches = "Previous two coach",
                fieldCaptains = "Previous two field captain",
                spiritCaptains = "Previous two spirit captain",
                score = 2,
                timeoutsUsedThisHalf = 0,
                firstHalfTimeoutsUsed = 0,
                offsides = 0,
                falseStarts = 0,
                timeViolations = 0,
                technicalFouls = 0,
                blueCards = 0,
            ),
            teamOnePlayers = listOf(priorPlayerRecord("10", priorReds = 1)),
            teamOnePlayerCards = listOf(InGamePlayerCardRecord("10", yellows = 2)),
            teamTwoPlayerCards = listOf(InGamePlayerCardRecord("20", yellows = 1)),
            eventLog = listOf(EventLogEntry(timestampEpoch = 1_000L, type = EventLogType.FIRST_PULL)),
            nearAttackingTeam = TeamId.TEAM_ONE,
            pullPromptTarget = PullPromptTarget.NEITHER,
            openingPullingTeam = TeamId.TEAM_ONE,
            openingPullingFromEnd = FieldEnd.FAR,
            phase = GamePhase.BETWEEN_POINTS,
            countdown = null,
            pullCountdownExpired = false,
            pullSequenceOffsidesRecorded = false,
            pullSequenceFalseStartRecorded = false,
            pullSkippedForCurrentPoint = false,
            pendingMisconductCountdown = false,
            halftimeTaken = false,
            halftimeTargetScore = null,
            winningScore = null,
            halfCapApplied = false,
            softCapApplied = false,
            hardCapApplied = false,
            pendingCapOffer = null,
            lastEvent = "Previous event",
        )
    }
}
