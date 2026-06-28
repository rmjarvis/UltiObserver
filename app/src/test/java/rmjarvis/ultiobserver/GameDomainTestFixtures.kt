package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
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
    ): GameSetupState {
        return GameSetupState(
            startDate = startDate,
            startTime = startTime,
            timeZone = timeZone,
            rules = rules,
            teamOne = TeamIdentity("Viscous Coupling", TeamColorChoice.WHITE),
            teamTwo = TeamIdentity("Animal", TeamColorChoice.RED),
            pullingTeam = pullingTeam,
            pullingFromEnd = pullingFromEnd,
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
        return createLiveGameState(
            standardGameSetup(
                startDate = startDate,
                startTime = startTime,
                timeZone = timeZone,
                rules = rules,
                pullingTeam = pullingTeam,
                pullingFromEnd = pullingFromEnd,
            )
        )
    }

    /**
     * Build a live-team state from identity fields plus optional live counters.
     *
     * This keeps tests concise while production code constructs live teams from `TeamIdentity`.
     *
     * @param name Team display name for the test state.
     * @param color Team jersey color for the test state.
     * @param customColorArgb Opaque ARGB value for a custom jersey color, when relevant.
     * @param coaches Free-form coach details for the test state.
     * @param fieldCaptains Free-form field-captain details for the test state.
     * @param spiritCaptains Free-form spirit-captain details for the test state.
     * @param score Team score.
     * @param timeoutsUsedThisHalf Timeouts used in the current half.
     * @param firstHalfTimeoutsUsed Timeouts used in the first half.
     * @param offsides Offsides count.
     * @param falseStarts False-start count.
     * @param majorityPullViolations Majority-pull violation count.
     * @param timeViolations Time-violation count.
     * @param technicalFouls Technical-foul count.
     * @param blueCards Blue-card count.
     */
    protected fun testTeamLiveState(
        name: String,
        color: TeamColorChoice,
        customColorArgb: Long? = null,
        coaches: String = "",
        fieldCaptains: String = "",
        spiritCaptains: String = "",
        score: Int = 0,
        timeoutsUsedThisHalf: Int = 0,
        firstHalfTimeoutsUsed: Int = 0,
        offsides: Int = 0,
        falseStarts: Int = 0,
        majorityPullViolations: Int = 0,
        timeViolations: Int = 0,
        technicalFouls: Int = 0,
        blueCards: Int = 0,
    ): TeamLiveState {
        return TeamLiveState(
            identity = TeamIdentity(
                name = name,
                color = color,
                customColorArgb = customColorArgb,
                coaches = coaches,
                fieldCaptains = fieldCaptains,
                spiritCaptains = spiritCaptains,
            ),
            score = score,
            timeoutsUsedThisHalf = timeoutsUsedThisHalf,
            firstHalfTimeoutsUsed = firstHalfTimeoutsUsed,
            offsides = offsides,
            falseStarts = falseStarts,
            majorityPullViolations = majorityPullViolations,
            timeViolations = timeViolations,
            technicalFouls = technicalFouls,
            blueCards = blueCards,
        )
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
     * Return an epoch timestamp for a date and time in the test time zone.
     *
     * @param date The local date to convert.
     * @param time The local time to convert.
     */
    protected fun timestampAt(date: LocalDate, time: LocalTime): Long {
        return LocalDateTime.of(date, time)
            .atZone(testTimeZone)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Return an epoch timestamp for a live state's date and supplied local time.
     *
     * @param state The state whose date and time zone anchor the timestamp.
     * @param time The local time to convert.
     */
    protected fun timestampAt(state: GameState, time: LocalTime): Long {
        return LocalDateTime.of(state.startDate, time)
            .atZone(state.timeZone)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Return the expected archive context for a game saved when starting another game.
     *
     * @param state The state whose time zone formats the saved-at time.
     * @param now Epoch millis when the game was saved aside.
     */
    protected fun savedWhenNewGameStartedContext(state: GameState, now: Long): String {
        val savedTime = localTimeFromEpoch(now, state.timeZone)
        return "Saved at ${formatClockTime(savedTime)}, when a new game was started"
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
     * Start a point at a dummy timestamp when exact event-log timing is not under test.
     *
     * @receiver The state whose point should be started.
     */
    protected fun GameState.beginLivePoint(): GameState {
        return beginLivePoint(0L)
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
        return assessTechnicalFoul(team, 0L)
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
        return adjustTimeouts(teamOneTimeoutsUsed, teamTwoTimeoutsUsed, 0L)
    }

    /**
     * Apply a pull-violation correction at a dummy timestamp when timing is not under test.
     *
     * @receiver The state being corrected.
     */
    protected fun GameState.adjustPullViolations(
        teamOneOffsides: Int,
        teamOneFalseStarts: Int,
        teamTwoOffsides: Int,
        teamTwoFalseStarts: Int,
    ): GameState {
        return adjustPullViolations(
            teamOneOffsides,
            teamOneFalseStarts,
            0,
            teamTwoOffsides,
            teamTwoFalseStarts,
            0,
            0L,
        )
    }

    /**
     * Apply a pull-violation correction with an explicit timestamp and no majority-pull deltas.
     *
     * @receiver The state being corrected.
     */
    protected fun GameState.adjustPullViolations(
        teamOneOffsides: Int,
        teamOneFalseStarts: Int,
        teamTwoOffsides: Int,
        teamTwoFalseStarts: Int,
        now: Long,
    ): GameState {
        return adjustPullViolations(
            teamOneOffsides,
            teamOneFalseStarts,
            0,
            teamTwoOffsides,
            teamTwoFalseStarts,
            0,
            now,
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
    protected fun startHalftimeNowAt(state: GameState, time: LocalTime): GameState {
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
        return event.formatMessage()
    }

    /// Return the formatted popup message for a timeout assessment.
    protected fun TimeoutAssessmentResult.message(): String {
        return event.formatMessage()
    }

    /// Return the formatted popup message for a pull-violation assessment.
    protected fun PullViolationAssessmentResult.message(): String? {
        return event?.formatMessage()
    }

    /// Return the formatted popup message for a time-violation assessment.
    protected fun TimeViolationAssessmentResult.message(): String? {
        return event?.formatMessage()
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
