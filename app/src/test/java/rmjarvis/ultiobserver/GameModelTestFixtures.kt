package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals

/// Shared deterministic setup and assertion helpers for GameModel-focused unit tests.
abstract class GameModelTestFixtures {
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
            teamOne = TeamSetup("Viscous Coupling", TeamColorChoice.WHITE),
            teamTwo = TeamSetup("Animal", TeamColorChoice.RED),
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
    ): LiveGameState {
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
     * Return an epoch timestamp a fixed number of minutes after a live state's start.
     *
     * @param state The state whose start epoch anchors the timestamp.
     * @param minutes The number of minutes after game start.
     */
    protected fun timestampAfterStart(state: LiveGameState, minutes: Int): Long {
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
    protected fun timestampAt(state: LiveGameState, time: LocalTime): Long {
        return LocalDateTime.of(state.startDate, time)
            .atZone(state.timeZone)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Record a goal at a local clock time.
     *
     * @param state The live state before the goal.
     * @param scoringTeam The team scoring the point.
     * @param time The local time assigned to the goal.
     */
    protected fun recordGoalAt(
        state: LiveGameState,
        scoringTeam: TeamId,
        time: LocalTime,
    ): LiveGameState {
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
        state: LiveGameState,
        scoringTeam: TeamId,
        time: LocalTime,
    ): LiveGameState {
        return state.recordGoalFromCurrentState(scoringTeam, timestampAt(state, time))
    }

    /**
     * Manually start halftime at a local clock time.
     *
     * @param state The live state before halftime.
     * @param time The local time assigned to the manual halftime start.
     */
    protected fun startHalftimeNowAt(state: LiveGameState, time: LocalTime): LiveGameState {
        return state.startHalftimeNow(timestampAt(state, time))
    }

    /**
     * Manually end the game at a local clock time.
     *
     * @param state The live state before game end.
     * @param time The local time assigned to the manual game end.
     */
    protected fun endGameNowAt(state: LiveGameState, time: LocalTime): LiveGameState {
        return state.endGameNow(timestampAt(state, time))
    }

    /**
     * Apply a pending cap at a local clock time.
     *
     * @param state The live state with a pending cap offer.
     * @param time The local time assigned to applying the cap.
     */
    protected fun applyPendingCapAt(state: LiveGameState, time: LocalTime): LiveGameState {
        return state.applyPendingCap(timestampAt(state, time))
    }

    /// Return the formatted popup message for a card assessment.
    protected fun CardAssessmentResult.message(): String? {
        return event.formatMessage()
    }

    /// Return the formatted popup message for a timeout assessment.
    protected fun TimeoutAssessmentResult.message(): String? {
        return event?.formatMessage()
    }

    /// Return the formatted popup message for a pull-infraction assessment.
    protected fun PullInfractionAssessmentResult.message(): String? {
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
        expectedPrevious: LiveGameState,
        state: LiveGameState,
    ): LiveGameState {
        val undoneState = state.undoLastAction()
        assertEquals(expectedPrevious, undoneState.copy(redoEntry = null))
        assertEquals(state, undoneState.redoLastAction())
        return undoneState
    }

    /// Build an apply-cap prompt from a state with a pending cap offer.
    protected fun LiveGameState.capPrompt(): GamePrompt.ApplyCap {
        return GamePrompt.ApplyCap(this, pendingCapOffer!!)
    }

    /// Build a live-point misconduct prompt from a card assessment event.
    protected fun CardAssessmentResult.misconductPrompt(): GamePrompt.LivePointMisconduct {
        return GamePrompt.LivePointMisconduct(event)
    }
}
