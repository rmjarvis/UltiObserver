package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests for JSON serialization of current-game snapshots and undo/redo patch chains.
 */
class TestSerialization : GameDomainTestFixtures() {
    /**
     * Verify game-state patches restore changed fields, unchanged fields, nullable values,
     * prefix-list patches, and full list replacements.
     */
    @Test
    fun gameStatePatches() {
        // A full game-state patch should store previous values for each changed field.
        val later = patchLaterState()
        val previous = patchPreviousState()
        val patch = GameStatePatch.fromLaterAndPrevious(later, previous)
        assertPatchProperties(patch, previous)
        assertEquals(previous, patch.applyTo(later))

        // Empty patches should be no-ops for game and team state.
        assertEquals(later, GameStatePatch().applyTo(later))
        assertEquals(later.teamOne, TeamStatePatch().applyTo(later.teamOne))

        // Identical lists do not need a patch.
        assertNull(ListPatch.fromLaterAndPrevious(listOf(1, 2), listOf(1, 2)))

        // A previous list that is a prefix of the later list can be restored by size.
        val prefixPatch = ListPatch.fromLaterAndPrevious(listOf(1, 2), listOf(1))!!
        assertEquals(1, prefixPatch.previousSize)
        assertNull(prefixPatch.replacement)
        assertEquals(listOf(1), prefixPatch.applyTo(listOf(1, 2)))

        // A non-prefix previous list must be restored by replacement.
        val replacementPatch = ListPatch.fromLaterAndPrevious(listOf(1, 2), listOf(9))!!
        assertNull(replacementPatch.previousSize)
        assertEquals(listOf(9), replacementPatch.replacement)
        assertEquals(listOf(9), replacementPatch.applyTo(listOf(1, 2)))

        // Longer previous lists are also restored by replacement.
        val longerReplacementPatch = ListPatch.fromLaterAndPrevious(listOf(1), listOf(1, 2))!!
        assertEquals(listOf(1, 2), longerReplacementPatch.replacement)
        assertEquals(listOf(1, 2), longerReplacementPatch.applyTo(listOf(1)))
    }

    /**
     * Verify serialized game states restore setup metadata, live state, null undo entries,
     * and populated redo chains.
     */
    @Test
    fun serializedUndoRedoChains() {
        // Build a game state whose live state has been undone once.
        val later = patchLaterState()
        val previous = patchPreviousState()
        val undoBacked = later.copy(undoEntry = UndoEntry("Undo Test patch", previous))
        val undone = undoBacked.undoLastAction()
        val serializedLiveState = undone.toSerializedGameState()
        val serializedUndoEntry = serializedLiveState.undoEntry

        // The current game state stores its nested undo and redo state as patch chains.
        assertEquals(undone.copy(undoEntry = null, redoEntry = null), serializedLiveState.state)
        assertNull(serializedUndoEntry)
        assertEquals(
            undoBacked.copy(undoEntry = null, redoEntry = null),
            serializedLiveState.redoEntry!!.state,
        )
        assertEquals("Undo Test patch", serializedLiveState.redoEntry!!.undoEntry!!.label)
        assertEquals(
            GameStatePatch.fromLaterAndPrevious(undoBacked.copy(undoEntry = null), previous),
            serializedLiveState.redoEntry!!.undoEntry!!.patchToPrevious,
        )
        assertNull(serializedLiveState.redoEntry!!.undoEntry!!.previousUndoEntry)

        // Restoring the serialized state should rebuild the original undo/redo chain.
        val restored = serializedLiveState.restore()
        assertEquals(undone, restored)
        assertEquals(undoBacked, restored.redoLastAction())

        // Standalone serialized live states should restore directly.
        assertEquals(
            later,
            SerializedGameState(state = later, undoEntry = null, redoEntry = null).restore(),
        )
    }

    /**
     * Verify each game-state patch property matches the corresponding previous-state value.
     *
     * @param patch The patch created from later and previous game states.
     * @param previous The previous game state that should be recoverable from the patch.
     */
    private fun assertPatchProperties(patch: GameStatePatch, previous: GameState) {
        assertEquals(previous.startDate, patch.startDate)
        assertEquals(previous.startTime, patch.startTime)
        assertEquals(previous.timeZone, patch.timeZone)
        assertEquals(previous.endEpoch, patch.endEpoch!!.value)
        assertEquals(previous.tournamentName, patch.tournamentName)
        assertEquals(previous.division, patch.division!!.value)
        assertEquals(previous.level, patch.level)
        assertEquals(previous.gameContext, patch.gameContext)
        assertEquals(previous.observerNames, patch.observerNames!!.replacement)
        assertEquals(previous.fieldName, patch.fieldName)
        assertEquals(previous.nearEndName, patch.nearEndName)
        assertEquals(previous.farEndName, patch.farEndName)
        assertEquals(previous.rules, patch.rules)
        assertTeamPatchProperties(patch.teamOne!!, previous.teamOne)
        assertTeamPatchProperties(patch.teamTwo!!, previous.teamTwo)
        assertEquals(previous.teamOnePlayers, patch.teamOnePlayers!!.replacement)
        assertEquals(previous.teamTwoPlayers, patch.teamTwoPlayers!!.replacement)
        assertEquals(previous.eventLog, patch.eventLog!!.replacement)
        assertEquals(previous.pullingTeam, patch.pullingTeam)
        assertEquals(previous.pullingFromEnd, patch.pullingFromEnd)
        assertEquals(previous.topDisplayedEnd, patch.topDisplayedEnd)
        assertEquals(previous.pullPromptTarget, patch.pullPromptTarget)
        assertEquals(previous.initialGenderRatio, patch.initialGenderRatio)
        assertEquals(previous.firstHalfGenZone, patch.firstHalfGenZone)
        assertEquals(previous.openingPullingTeam, patch.openingPullingTeam)
        assertEquals(previous.openingPullingFromEnd, patch.openingPullingFromEnd)
        assertEquals(previous.phase, patch.phase)
        assertEquals(previous.countdown, patch.countdown!!.value)
        assertEquals(previous.pullSequenceOffsidesRecorded, patch.pullSequenceOffsidesRecorded)
        assertEquals(previous.pullSequenceFalseStartRecorded, patch.pullSequenceFalseStartRecorded)
        assertEquals(previous.pullSkippedForCurrentPoint, patch.pullSkippedForCurrentPoint)
        assertEquals(previous.pendingMisconductCountdown, patch.pendingMisconductCountdown)
        assertEquals(previous.halftimeTaken, patch.halftimeTaken)
        assertEquals(previous.halftimeTargetScore, patch.halftimeTargetScore!!.value)
        assertEquals(previous.halftimeHighScore, patch.halftimeHighScore!!.value)
        assertEquals(previous.pendingWaterBreakOffer, patch.pendingWaterBreakOffer)
        assertEquals(previous.winningScore, patch.winningScore!!.value)
        assertEquals(previous.halfCapApplied, patch.halfCapApplied)
        assertEquals(previous.softCapApplied, patch.softCapApplied)
        assertEquals(previous.hardCapApplied, patch.hardCapApplied)
        assertEquals(previous.pendingCapOffer, patch.pendingCapOffer!!.value)
        assertEquals(previous.lastEvent, patch.lastEvent)
    }

    /**
     * Verify each team patch property matches the corresponding previous-team value.
     *
     * @param patch The patch created from later and previous team states.
     * @param previous The previous team state that should be recoverable from the patch.
     */
    private fun assertTeamPatchProperties(patch: TeamStatePatch, previous: TeamState) {
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
        assertEquals(previous.majorityPullViolations, patch.majorityPullViolations)
        assertEquals(previous.timeViolations, patch.timeViolations)
        assertEquals(previous.technicalFouls, patch.technicalFouls)
        assertEquals(previous.blueCards, patch.blueCards)
    }

    /**
     * Verify JSON serialization omits null event-log fields and default patch fields.
     */
    @Test
    fun currentGameSerialization() {
        // Serialization omits null event-log fields and unchanged patch fields.
        val later = patchLaterState()
        val previous = patchPreviousState()
        val json = encodeCurrentGame(
            later.copy(undoEntry = UndoEntry("Undo Test patch", previous))
        )
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
        assertFalse(json.contains("\"observerNames\": null"))
        assertFalse(json.contains("\"fieldName\": null"))
    }

    /**
     * Verify invalid list patch representations fail loudly.
     */
    @Test
    fun listPatchValidation() {
        // A list patch must encode either a prefix size or a full replacement.
        assertThrows(IllegalArgumentException::class.java) {
            ListPatch<Int>()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ListPatch(previousSize = 1, replacement = listOf(1))
        }
    }

    /**
     * Build a later state with non-null nullable fields and non-prefix list values.
     */
    private fun patchLaterState(): GameState {
        return standardLiveGameState(
            startDate = LocalDate.of(2026, 2, 3),
            startTime = LocalTime.of(9, 30),
            timeZone = ZoneId.of("America/Chicago"),
            rules = GameRules(
                gameTo = 17,
                halfCapMinutes = 50,
                nominalSoftCapMinutes = 80,
                nominalHardCapMinutes = 95,
                switchGenZoneAtHalftime = false,
            ),
            pullingTeam = TeamId.TEAM_TWO,
            pullingFromEnd = FieldEnd.NEAR,
        ).copy(
            endEpoch = 2_000L,
            tournamentName = "Later Tournament",
            division = GameDivision.WOMENS,
            level = "Club",
            gameContext = "Later semifinal",
            observerNames = listOf("Later observer", "Later partner"),
            fieldName = "Later field",
            nearEndName = "Later near",
            farEndName = "Later far",
            teamOne = TeamState(
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
                majorityPullViolations = 2,
                timeViolations = 1,
                technicalFouls = 5,
                blueCards = 6,
            ),
            teamTwo = TeamState(
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
                majorityPullViolations = 1,
                timeViolations = 1,
                technicalFouls = 6,
                blueCards = 5,
            ),
            teamOnePlayers = listOf(playerRecordWithCards("11", yellows = 1)),
            teamTwoPlayers = listOf(
                priorPlayerRecord("22", priorYellows = 1),
                playerRecordWithCards("12", reds = 1),
            ),
            eventLog = listOf(
                EventLogEntry(timestampEpoch = 2_000L, type = EventLogType.FIRST_PULL),
                EventLogEntry(
                    timestampEpoch = 3_000L,
                    type = EventLogType.SCORE_ADJUSTED,
                    teamOneScore = 7,
                    teamTwoScore = 8,
                ),
            ),
            topDisplayedEnd = FieldEnd.NEAR,
            pullPromptTarget = PullPromptTarget.BOTH,
            initialGenderRatio = GenderRatio.FOUR_WOMEN_THREE_MEN,
            firstHalfGenZone = FieldEnd.NEAR,
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
            pullSequenceOffsidesRecorded = true,
            pullSequenceFalseStartRecorded = true,
            pullSkippedForCurrentPoint = true,
            pendingMisconductCountdown = true,
            halftimeTaken = true,
            halftimeTargetScore = 9,
            halftimeHighScore = 8,
            pendingWaterBreakOffer = true,
            winningScore = 18,
            halfCapApplied = true,
            softCapApplied = true,
            hardCapApplied = true,
            pendingCapOffer = CapType.HARD,
            lastEvent = "Later event",
        )
    }

    /**
     * Build a previous state that differs in every patchable field from the later state.
     */
    private fun patchPreviousState(): GameState {
        return standardLiveGameState(
            startDate = LocalDate.of(2026, 2, 2),
            startTime = LocalTime.of(8, 15),
            timeZone = ZoneId.of("America/New_York"),
            rules = GameRules(
                gameTo = 15,
                halfCapMinutes = 45,
                nominalSoftCapMinutes = 90,
                nominalHardCapMinutes = 105,
                switchGenZoneAtHalftime = true,
            ),
            pullingTeam = TeamId.TEAM_ONE,
            pullingFromEnd = FieldEnd.FAR,
        ).copy(
            endEpoch = null,
            tournamentName = "Previous Tournament",
            division = null,
            level = "Masters",
            gameContext = "Previous pool game",
            observerNames = listOf("Previous observer"),
            fieldName = "Previous field",
            nearEndName = "Previous near",
            farEndName = "Previous far",
            teamOne = TeamState(
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
                majorityPullViolations = 1,
                timeViolations = 0,
                technicalFouls = 0,
                blueCards = 0,
            ),
            teamTwo = TeamState(
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
                majorityPullViolations = 2,
                timeViolations = 0,
                technicalFouls = 0,
                blueCards = 0,
            ),
            teamOnePlayers = listOf(
                priorPlayerRecord("10", priorReds = 1),
                playerRecordWithCards("13", yellows = 2),
            ),
            teamTwoPlayers = listOf(playerRecordWithCards("20", yellows = 1)),
            eventLog = listOf(
                EventLogEntry(timestampEpoch = 1_000L, type = EventLogType.FIRST_PULL),
            ),
            topDisplayedEnd = FieldEnd.FAR,
            pullPromptTarget = PullPromptTarget.NEITHER,
            initialGenderRatio = GenderRatio.FOUR_MEN_THREE_WOMEN,
            firstHalfGenZone = FieldEnd.FAR,
            openingPullingTeam = TeamId.TEAM_ONE,
            openingPullingFromEnd = FieldEnd.FAR,
            phase = GamePhase.BETWEEN_POINTS,
            countdown = null,
            pullSequenceOffsidesRecorded = false,
            pullSequenceFalseStartRecorded = false,
            pullSkippedForCurrentPoint = false,
            pendingMisconductCountdown = false,
            halftimeTaken = false,
            halftimeTargetScore = null,
            halftimeHighScore = null,
            pendingWaterBreakOffer = false,
            winningScore = null,
            halfCapApplied = false,
            softCapApplied = false,
            hardCapApplied = false,
            pendingCapOffer = null,
            lastEvent = "Previous event",
        )
    }
}
