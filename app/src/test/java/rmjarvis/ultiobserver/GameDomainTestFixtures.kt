package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals

/// Shared deterministic setup and assertion helpers for domain-model-focused unit tests.
abstract class GameDomainTestFixtures {
    protected val testTimeZone: ZoneId = ZoneId.of("America/New_York")

    /**
     * Build a representative setup state for model tests.
     *
     * This lets model tests start from a well-defined setup without repeating irrelevant constructor details.
     *
     * @param startDate The game start date to use in deterministic timestamp helpers.
     * @param startTime The game start time; required so tests choose the relevant clock scenario explicitly.
     * @param timeZone The game time zone, defaulting to a stable test zone.
     * @param rules The game rules to install in the setup.
     * @param pullingTeam The team pulling the opening pull.
     * @param pullingFromEnd The field end the opening pull starts from.
     */
    protected fun standardGameSetup(
        startDate: LocalDate = LocalDate.of(2026, 1, 1),
        startTime: LocalTime,
        timeZone: ZoneId = testTimeZone,
        rules: GameRules = GameRules(
            gameTo = 5,
            useHalfCap = false,
            useSoftCap = false,
            useHardCap = false,
        ),
        pullingTeam: TeamId = TeamId.TEAM_ONE,
        pullingFromEnd: FieldEnd = FieldEnd.FAR,
    ): GameState {
        return newSetupGameState(
            now = epochTimestamp(startDate, startTime, ZoneId.systemDefault()),
        ).copy(
            startDate = startDate,
            startTime = startTime,
            timeZone = timeZone,
            rules = rules,
            teamOne = TeamState("Viscous Coupling", TeamColorChoice.WHITE),
            teamTwo = TeamState("Animal", TeamColorChoice.RED),
            pullingTeam = pullingTeam,
            pullingFromEnd = pullingFromEnd,
            openingPullingTeam = pullingTeam,
            openingPullingFromEnd = pullingFromEnd,
        )
    }

    /**
     * Build a representative live game state for model tests.
     *
     * This lets model tests start from a well-defined game state without replaying setup boilerplate in every test.
     *
     * @param startTime The game start time used to derive the state's start epoch.
     * @param rules The game rules to install in the state.
     * @param startDate The game start date used to derive the state's start epoch.
     * @param timeZone The game time zone, defaulting to a stable test zone.
     * @param pullingTeam The team pulling the opening pull.
     * @param pullingFromEnd The field end the opening pull starts from.
     */
    protected fun standardLiveGameState(
        startTime: LocalTime = LocalTime.of(11, 0),
        rules: GameRules = GameRules(
            gameTo = 5,
            useHalfCap = false,
            useSoftCap = false,
            useHardCap = false,
        ),
        startDate: LocalDate = LocalDate.of(2026, 1, 1),
        timeZone: ZoneId = testTimeZone,
        pullingTeam: TeamId = TeamId.TEAM_ONE,
        pullingFromEnd: FieldEnd = FieldEnd.FAR,
    ): GameState {
        return standardGameSetup(
            startDate = startDate,
            startTime = startTime,
            timeZone = timeZone,
            rules = rules,
            pullingTeam = pullingTeam,
            pullingFromEnd = pullingFromEnd,
        ).startGame(OrientationPreference.PORTRAIT)
    }

    /// Build an initial live-game state from a setup-phase game state.
    protected fun createLiveGameState(setup: GameState): GameState {
        return setup.startGame(OrientationPreference.PORTRAIT)
    }

    /**
     * Apply edited setup fields to an existing live game.
     *
     * @param existing The live state currently being edited.
     * @param setup The setup-edited game state returned by the update-game form.
     * @param now The epoch millis for rebuilding affected countdowns.
     */
    protected fun applySetupToLiveGame(
        existing: GameState,
        setup: GameState,
        now: Long,
    ): GameState {
        return applySetupEditToActiveGame(existing, setup, now)
    }

    /**
     * Return an epoch timestamp a fixed number of minutes after a live state's start.
     *
     * @param state The state whose start epoch anchors the timestamp.
     * @param minutes The number of minutes after game start.
     */
    protected fun timestampAfterStart(state: GameState, minutes: Int): Long {
        return state.startEpoch + Duration.ofMinutes(minutes.toLong()).toMillis()
    }

    /**
     * Return an epoch timestamp for a live state's date and supplied local time.
     *
     * @param state The state whose date and time zone anchor the timestamp.
     * @param time The local time to convert.
     */
    protected fun timestampAt(state: GameState, time: LocalTime): Long {
        return epochTimestamp(state.startDate, time, state.timeZone)
    }

    /**
     * Record a goal at a local clock time.
     *
     * @param state The live state before the goal.
     * @param scoringTeam The team scoring the point.
     * @param time The local time assigned to the goal.
     */
    protected fun recordGoalAt(
        state: GameState,
        scoringTeam: TeamId,
        time: LocalTime,
    ): GameState {
        return state.recordGoal(scoringTeam, timestampAt(state, time))
    }

    /**
     * Record a goal from the current phase at a local clock time.
     *
     * @param state The live state before the goal.
     * @param scoringTeam The team scoring the point.
     * @param time The local time assigned to the goal.
     */
    protected fun recordGoalFromCurrentStateAt(
        state: GameState,
        scoringTeam: TeamId,
        time: LocalTime,
    ): GameState {
        return state.recordGoalFromCurrentState(scoringTeam, timestampAt(state, time))
    }

    /**
     * Start a point at the scheduled game timestamp when exact event-log timing is not under test.
     *
     * @receiver The state whose point should be started.
     */
    protected fun GameState.beginLivePoint(): GameState {
        return beginLivePoint(startEpoch)
    }

    /**
     * Record a yellow-card action at a dummy timestamp when timing is not under test.
     *
     * @receiver The state receiving the card action.
     * @param team The team receiving the card.
     * @param jerseyNumber The player receiving the card.
     */
    protected fun GameState.assessYellowCard(team: TeamId, jerseyNumber: String): CardAssessmentResult {
        return assessYellowCard(team, jerseyNumber, 0L)
    }

    /**
     * Record a first-yellow action at a dummy timestamp when timing is not under test.
     *
     * @receiver The state receiving the card action.
     * @param team The team receiving the card.
     * @param jerseyNumber The player receiving the card.
     */
    protected fun GameState.assessFirstYellowCard(team: TeamId, jerseyNumber: String): CardAssessmentResult {
        return assessFirstYellowCard(team, jerseyNumber, 0L)
    }

    /**
     * Record a second-yellow action at a dummy timestamp when timing is not under test.
     *
     * @receiver The state receiving the card action.
     * @param team The team receiving the card.
     * @param jerseyNumber The player receiving the card.
     */
    protected fun GameState.assessSecondYellowCard(team: TeamId, jerseyNumber: String): CardAssessmentResult {
        return assessSecondYellowCard(team, jerseyNumber, 0L)
    }

    /**
     * Record a red-card action at a dummy timestamp when timing is not under test.
     *
     * @receiver The state receiving the card action.
     * @param team The team receiving the card.
     * @param jerseyNumber The player receiving the card.
     */
    protected fun GameState.assessRedCard(team: TeamId, jerseyNumber: String): CardAssessmentResult {
        return assessRedCard(team, jerseyNumber, 0L)
    }

    /**
     * Record a blue-card action at a dummy timestamp when timing is not under test.
     *
     * @receiver The state receiving the card action.
     * @param team The team receiving the card.
     */
    protected fun GameState.assessBlueCard(team: TeamId): CardAssessmentResult {
        return assessBlueCard(team, 0L)
    }

    /**
     * Record a technical foul at a dummy timestamp when timing is not under test.
     *
     * @receiver The state receiving the technical-foul action.
     * @param team The team receiving the technical foul.
     */
    protected fun GameState.assessTechnicalFoul(team: TeamId): CardAssessmentResult {
        return assessTechnicalFoul(team, 0L, RuleGuidanceMode.FULL)
    }

    /**
     * Build a player record from card-count summaries for tests.
     *
     * @param jerseyNumber The player's jersey number, or blank for a name-only identity.
     * @param yellows The number of yellow-card events to create.
     * @param reds The number of red-card events to create.
     * @param playerName The player's name, or blank when unknown.
     */
    protected fun playerRecordWithCards(
        jerseyNumber: String,
        yellows: Int = 0,
        reds: Int = 0,
        playerName: String = "",
    ): PlayerRecord {
        require(yellows >= 0 && reds >= 0) {
            "Player records cannot have negative card counts."
        }
        return PlayerRecord(
            jerseyNumber = jerseyNumber,
            playerName = playerName,
            cards = buildList {
                repeat(yellows) { add(InGamePlayerCardEvent(CardType.YELLOW, index = size)) }
                repeat(reds) { add(InGamePlayerCardEvent(CardType.RED, index = size)) }
            },
        )
    }

    /**
     * Build a player record with previous-game card counts for tests.
     *
     * @param jerseyNumber The player's jersey number, or blank for a name-only identity.
     * @param priorYellows Yellow cards from previous games.
     * @param priorReds Red cards from previous games.
     * @param playerName The player's name, or blank when unknown.
     */
    protected fun priorPlayerRecord(
        jerseyNumber: String,
        priorYellows: Int = 0,
        priorReds: Int = 0,
        playerName: String = "",
    ): PlayerRecord {
        return PlayerRecord(
            jerseyNumber = jerseyNumber,
            playerName = playerName,
            priorYellows = priorYellows,
            priorReds = priorReds,
        )
    }

    /**
     * Record a pull violation at a dummy timestamp when timing is not under test.
     *
     * @receiver The state receiving the pull-violation action.
     * @param team The team committing the pull violation.
     */
    protected fun GameState.assessPullViolation(team: TeamId): PullViolationAssessmentResult {
        return assessPullViolation(team, 0L, pullViolationTypeFor(team))
    }

    /**
     * Record offsides at a dummy timestamp when timing is not under test.
     *
     * @receiver The state receiving the offsides action.
     */
    protected fun GameState.recordOffsides(): GameState {
        return recordOffsides(0L)
    }

    /**
     * Record a false start at a dummy timestamp when timing is not under test.
     *
     * @receiver The state receiving the false-start action.
     */
    protected fun GameState.recordFalseStart(): GameState {
        return recordFalseStart(0L)
    }

    /**
     * Apply a score correction at a dummy timestamp when timing is not under test.
     *
     * @receiver The state being corrected.
     * @param teamOneScore The corrected team-one score.
     * @param teamTwoScore The corrected team-two score.
     */
    protected fun GameState.adjustScore(teamOneScore: Int, teamTwoScore: Int): GameState {
        return adjustScore(teamOneScore, teamTwoScore, 0L)
    }

    /**
     * Apply a timeout correction at a dummy timestamp when timing is not under test.
     *
     * @receiver The state being corrected.
     * @param teamOneTimeoutsUsed The corrected team-one timeout count.
     * @param teamTwoTimeoutsUsed The corrected team-two timeout count.
     */
    protected fun GameState.adjustTimeouts(teamOneTimeoutsUsed: Int, teamTwoTimeoutsUsed: Int): GameState {
        return adjustTimeouts(
            teamOneTimeoutsUsed,
            teamTwoTimeoutsUsed,
            teamOne.firstHalfTimeoutsUsed,
            teamTwo.firstHalfTimeoutsUsed,
            0L,
        )
    }

    /**
     * Apply a card/technical-foul correction at a dummy timestamp when timing is not under test.
     *
     * @receiver The state being corrected.
     */
    protected fun GameState.adjustCardsAndTf(
        teamOneBlues: Int,
        teamOneTechnicalFouls: Int,
        teamTwoBlues: Int,
        teamTwoTechnicalFouls: Int,
        teamOnePlayers: List<PlayerRecord>,
        teamTwoPlayers: List<PlayerRecord>,
    ): GameState {
        return adjustCardsAndTf(
            teamOneBlues,
            teamOneTechnicalFouls,
            teamTwoBlues,
            teamTwoTechnicalFouls,
            teamOnePlayers,
            teamTwoPlayers,
            0L,
            "Undo Adjust blue card/tech counts",
        )
    }

    /**
     * Manually start halftime at a local clock time.
     *
     * @param state The live state before halftime.
     * @param time The local time assigned to the manual halftime start.
     */
    protected fun startHalftimeAt(state: GameState, time: LocalTime): GameState {
        return state.startHalftimeNow(timestampAt(state, time))
    }

    /**
     * Manually end the game at a local clock time.
     *
     * @param state The live state before game end.
     * @param time The local time assigned to the manual game end.
     */
    protected fun endGameNowAt(state: GameState, time: LocalTime): GameState {
        return state.endGameNow(timestampAt(state, time))
    }

    /**
     * Apply a pending cap at a local clock time.
     *
     * @param state The live state with a pending cap offer.
     * @param time The local time assigned to applying the cap.
     */
    protected fun applyPendingCapAt(state: GameState, time: LocalTime): GameState {
        return state.applyPendingCap(timestampAt(state, time))
    }

    /// Return the formatted popup message for a card assessment.
    protected fun CardAssessmentResult.message(): String? {
        return event.formatMessage().plainText
    }

    /// Return the formatted popup message for a timeout assessment.
    protected fun TimeoutAssessmentResult.message(): String {
        return event.formatMessage().plainText
    }

    /// Return the formatted popup message for a pull-violation assessment.
    protected fun PullViolationAssessmentResult.message(): String? {
        return event?.formatMessage()?.plainText
    }

    /// Return the formatted popup message for a time-violation assessment.
    protected fun TimeViolationAssessmentResult.message(): String? {
        return event?.formatMessage()?.plainText
    }

    /**
     * Assert that undo restores a specific previous state and redo restores the supplied state.
     *
     * @param expectedPrevious The state expected after undo, ignoring the redo entry attached by undo.
     * @param state The undo-backed state to exercise.
     */
    protected fun assertUndoRestores(
        expectedPrevious: GameState,
        state: GameState,
    ): GameState {
        val undoneState = state.undoLastAction()
        assertEquals(expectedPrevious, undoneState.copy(redoEntry = null))
        assertEquals(state, undoneState.redoLastAction())
        return undoneState
    }

    /// Build an apply-cap prompt from a state with a pending cap offer.
    protected fun GameState.capPrompt(): GamePrompt.ApplyCap {
        return GamePrompt.ApplyCap(this, pendingCapOffer!!)
    }

    /// Build a live-point misconduct prompt from a card assessment event.
    protected fun CardAssessmentResult.misconductPrompt(): GamePrompt.LivePointMisconduct {
        return GamePrompt.LivePointMisconduct(event)
    }
}
