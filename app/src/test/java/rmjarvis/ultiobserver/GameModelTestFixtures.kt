package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

abstract class GameModelTestFixtures {
    protected val testTimeZone: ZoneId = ZoneId.of("America/New_York")

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

    protected fun timestampAfterStart(state: LiveGameState, minutes: Int): Long {
        return state.startEpoch + Duration.ofMinutes(minutes.toLong()).toMillis()
    }

    protected fun timestampAt(date: LocalDate, time: LocalTime): Long {
        return LocalDateTime.of(date, time)
            .atZone(testTimeZone)
            .toInstant()
            .toEpochMilli()
    }

    protected fun timestampAt(state: LiveGameState, time: LocalTime): Long {
        return LocalDateTime.of(state.startDate, time)
            .atZone(state.timeZone)
            .toInstant()
            .toEpochMilli()
    }

    protected fun recordGoalAt(
        state: LiveGameState,
        scoringTeam: TeamId,
        time: LocalTime,
    ): LiveGameState {
        return state.recordGoal(scoringTeam, timestampAt(state, time))
    }

    protected fun recordGoalFromCurrentStateAt(
        state: LiveGameState,
        scoringTeam: TeamId,
        time: LocalTime,
    ): LiveGameState {
        return state.recordGoalFromCurrentState(scoringTeam, timestampAt(state, time))
    }

    protected fun startHalftimeNowAt(state: LiveGameState, time: LocalTime): LiveGameState {
        return state.startHalftimeNow(timestampAt(state, time))
    }

    protected fun endGameNowAt(state: LiveGameState, time: LocalTime): LiveGameState {
        return state.endGameNow(timestampAt(state, time))
    }

    protected fun applyPendingCapAt(state: LiveGameState, time: LocalTime): LiveGameState {
        return state.applyPendingCap(timestampAt(state, time))
    }

    protected fun eventMessage(result: CardAssessmentResult): String? {
        return formatGameEventMessage(result.state, result.event)
    }

    protected fun eventMessage(result: TimeoutAssessmentResult): String? {
        return formatGameEventMessage(result.state, result.event)
    }

    protected fun eventMessage(result: PullInfractionAssessmentResult): String? {
        return formatGameEventMessage(result.state, result.event)
    }
}
