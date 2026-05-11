package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Absolute Long timestamps in the game model are Unix epoch milliseconds.

enum class TeamId {
    TEAM_ONE,
    TEAM_TWO,
}

enum class FieldEnd {
    NEAR,
    FAR,
}

enum class LivePhase {
    PRE_GAME,
    BETWEEN_POINTS,
    LIVE_POINT,
    HALFTIME,
    GAME_OVER,
}

enum class TeamColorChoice(
    val label: String,
    val accentArgb: Long,    // The background color matching the nominal jersey color.
    val contentArgb: Long,   // A text color with good contrast to the accent color.
) {
    WHITE("White", 0xFFF5F2E8, 0xFF1F1A17),
    BLACK("Black", 0xFF232220, 0xFFF6F2E8),
    RED("Red", 0xFFC23B2A, 0xFFFFF8F5),
    BLUE("Blue", 0xFF2A5CAA, 0xFFF7FAFF),
    GREEN("Green", 0xFF2E7D32, 0xFFF4FFF4),
    YELLOW("Yellow", 0xFFE7A51E, 0xFF2E2400),
    PINK("Pink", 0xFFFF4FA3, 0xFF2F1022),
    GRAY("Gray", 0xFF708090, 0xFFF7F8FA),
}

data class TeamSetup(
    val name: String = "",
    val color: TeamColorChoice = TeamColorChoice.WHITE,
)

data class PlayerCardRecord(
    val team: TeamId,
    val jerseyNumber: String,
    val priorYellows: Int,    // Cards issued in previous games of the current tournament.
    val priorReds: Int,
)

data class InGamePlayerCardRecord(
    val jerseyNumber: String,
    val yellows: Int = 0,
    val directReds: Int = 0,
)

// How to indicate cards for players when you don't know the player number.
const val UNKNOWN_PLAYER_NUMBER = "N/A"

data class GameRules(
    val gameTo: Int = 15,
    val halftimeMinutes: Int = 7,
    val useHalfCap: Boolean = true,
    val halfCapMinutes: Int = 45,
    val useSoftCap: Boolean = true,
    val softCapMinutes: Int = 90,
    val useHardCap: Boolean = true,
    val hardCapMinutes: Int = 100,
    val timeoutsPerHalf: Int = 2,
    val hasFloaterTimeout: Boolean = false,
)

data class GameSetupState(
    val startDate: LocalDate,
    val startTime: LocalTime,
    val timeZone: ZoneId,
    val rules: GameRules = GameRules(),
    val teamOne: TeamSetup = TeamSetup(name = "", color = TeamColorChoice.WHITE),
    val teamTwo: TeamSetup = TeamSetup(name = "", color = TeamColorChoice.BLUE),
    val priorCards: List<PlayerCardRecord> = emptyList(),
    val pullingTeam: TeamId = TeamId.TEAM_ONE,
    val pullingFromEnd: FieldEnd = FieldEnd.FAR,
)

data class TeamLiveState(
    val name: String,
    val color: TeamColorChoice,
    val score: Int = 0,
    val timeoutsUsedThisHalf: Int = 0,
    val firstHalfTimeoutsUsed: Int = 0,
    val offsides: Int = 0,
    val falseStarts: Int = 0,
    val technicalFouls: Int = 0,
    val blueCards: Int = 0,
)

fun TeamLiveState.withAddedTimeout(): TeamLiveState {
    return copy(timeoutsUsedThisHalf = timeoutsUsedThisHalf + 1)
}

data class CountdownState(
    val kind: CountdownKind,
    val label: String,
    val durationSeconds: Int,       // Original countdown length.
    val targetEpoch: Long,          // Clock time when the countdown reaches zero.
    val betweenPointsTarget: BetweenPointsCountdownTarget? = null,
) {
    fun swapOD(): CountdownState {
        if (kind != CountdownKind.BETWEEN_POINTS) {
            return this
        }
        val currentTarget = betweenPointsTarget
            ?: error("Between-points countdown is missing its target side.")
        val newTarget = currentTarget.flip()
        val deltaSeconds = newTarget.baseDurationSeconds - currentTarget.baseDurationSeconds
        return copy(
            label = newTarget.label,
            durationSeconds = durationSeconds + deltaSeconds,
            targetEpoch = targetEpoch + deltaSeconds * 1000L,
            betweenPointsTarget = newTarget,
        )
    }

}

enum class CountdownKind {
    BETWEEN_POINTS,
    TIME_OUT,
    HALFTIME,
}

enum class BetweenPointsCountdownTarget(
    val label: String,
    val baseDurationSeconds: Int,
) {
    OFFENSE_READY("Signal in", 60),
    PULL("Pull in", 80);

    fun flip(): BetweenPointsCountdownTarget {
        return if (this == OFFENSE_READY) PULL else OFFENSE_READY
    }
}

data class LiveGameState(
    val startDate: LocalDate,
    val startTime: LocalTime,
    val timeZone: ZoneId,
    val startEpoch: Long,
    val endTime: LocalTime? = null,
    val rules: GameRules,
    val teamOne: TeamLiveState,
    val teamTwo: TeamLiveState,
    val priorCards: List<PlayerCardRecord>,
    val teamOnePlayerCards: List<InGamePlayerCardRecord> = emptyList(),
    val teamTwoPlayerCards: List<InGamePlayerCardRecord> = emptyList(),
    val nearAttackingTeam: TeamId,
    val pullingTeam: TeamId,
    val pullingFromEnd: FieldEnd,
    val openingPullingTeam: TeamId,
    val openingPullingFromEnd: FieldEnd,
    val phase: LivePhase = LivePhase.PRE_GAME,
    val countdown: CountdownState? = null,
    val pullSequenceOffsidesRecorded: Boolean = false,
    val pullSequenceFalseStartRecorded: Boolean = false,
    val halftimeTaken: Boolean = false,
    val halftimeTargetScore: Int? = null,
    val winningScore: Int? = null,
    val halfCapApplied: Boolean = false,
    val softCapApplied: Boolean = false,
    val hardCapApplied: Boolean = false,
    val pendingCapOffer: CapType? = null,  // Set when asking whether to apply the next cap
    val undoEntry: UndoEntry? = null,
    val lastEvent: String = "Pregame setup complete.",
)

data class CapStatus(
    val label: String,
    val remaining: Duration,
)

data class UndoEntry(
    val label: String,
    val previous: LiveGameState,
)

data class CardAssessmentResult(
    val state: LiveGameState,
    val message: String,
    val needsLivePointMisconductChoice: Boolean,  // Set when a live-point card/TF needs O/D choice.
)

data class TimeoutAssessmentResult(
    val state: LiveGameState,
    val message: String? = null,
)

enum class RedCardMode {
    DIRECT_RED,
    SECOND_YELLOW,
}

enum class CapType {
    HALF,
    SOFT,
    HARD,
}

// Make the first live game state after setting the initial setup parameters.
fun createLiveGameState(setup: GameSetupState): LiveGameState {
    val nearAttackingTeam = if (setup.pullingFromEnd == FieldEnd.FAR) {
        setup.pullingTeam
    } else {
        setup.pullingTeam.flip()
    }
    val startEpoch = epochTimestamp(setup.startDate, setup.startTime, setup.timeZone)
    val initialCountdown = buildBetweenPointsCountdown(
        pullingFromEnd = setup.pullingFromEnd,
        sequenceStart = startEpoch,
    )

    return LiveGameState(
        startDate = setup.startDate,
        startTime = setup.startTime,
        timeZone = setup.timeZone,
        startEpoch = startEpoch,
        rules = setup.rules,
        teamOne = TeamLiveState(
            name = setup.teamOne.name.ifBlank { "Team 1" },
            color = setup.teamOne.color,
        ),
        teamTwo = TeamLiveState(
            name = setup.teamTwo.name.ifBlank { "Team 2" },
            color = setup.teamTwo.color,
        ),
        priorCards = setup.priorCards,
        nearAttackingTeam = nearAttackingTeam,
        pullingTeam = setup.pullingTeam,
        pullingFromEnd = setup.pullingFromEnd,
        openingPullingTeam = setup.pullingTeam,
        openingPullingFromEnd = setup.pullingFromEnd,
        phase = LivePhase.BETWEEN_POINTS,
        countdown = initialCountdown,
    )
}

// Initiate the countdown for edge cases where we don't automatically start it directly
// on an event.
fun LiveGameState.startPullSequence(
    now: Long,
): LiveGameState {
    val countdown = buildBetweenPointsCountdown(
        pullingFromEnd = this.pullingFromEnd,
        sequenceStart = now,
    )
    return this.copy(
        phase = LivePhase.BETWEEN_POINTS,
        countdown = countdown,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        lastEvent = "Pull sequence started.",
    )
}

// Update state for someone scoring a goal.
fun LiveGameState.recordGoal(
    scoringTeam: TeamId,
    now: Long,
): LiveGameState {
    if (this.phase == LivePhase.GAME_OVER) {
        return this
    }

    val updatedTeamOne = if (scoringTeam == TeamId.TEAM_ONE) {
        this.teamOne.copy(score = this.teamOne.score + 1)
    } else {
        this.teamOne
    }
    val updatedTeamTwo = if (scoringTeam == TeamId.TEAM_TWO) {
        this.teamTwo.copy(score = this.teamTwo.score + 1)
    } else {
        this.teamTwo
    }
    val nextNearAttackingTeam = this.nearAttackingTeam.flip()
    val nextPullingTeam = scoringTeam
    val nextPullingFromEnd = if (scoringTeam == this.nearAttackingTeam) {
        FieldEnd.NEAR
    } else {
        FieldEnd.FAR
    }
    // Do this check every time, because if the user changes the rules.gameTo, we naturally
    // update to the updated rules.  However, if the soft cap has been applied, that takes
    // precedence.
    val gameWinningScore = this.winningScore ?: this.rules.gameTo
    val gameOver = max(updatedTeamOne.score, updatedTeamTwo.score) >= gameWinningScore

    if (gameOver) {
        val afterGoalState = this.copy(
            teamOne = updatedTeamOne,
            teamTwo = updatedTeamTwo,
            pullingTeam = nextPullingTeam,
            nearAttackingTeam = nextNearAttackingTeam,
            pullingFromEnd = nextPullingFromEnd,
            phase = LivePhase.BETWEEN_POINTS,
            countdown = buildBetweenPointsCountdown(
                pullingFromEnd = nextPullingFromEnd,
                sequenceStart = now,
            ),
            pullSequenceOffsidesRecorded = false,
            pullSequenceFalseStartRecorded = false,
            winningScore = this.winningScore,
            pendingCapOffer = null,
            lastEvent = "${teamName(this, scoringTeam)} scored.",
        ).withUndo(this, "Undo Goal by ${teamName(this, scoringTeam)}")
        return afterGoalState.copy(
            endTime = localTimeFromEpoch(now, this.timeZone),
            phase = LivePhase.GAME_OVER,
            countdown = null,
            winningScore = gameWinningScore,
            lastEvent = "Game over.",
        ).withUndo(afterGoalState, "Undo End Game")
    }

    // Caps are checked before halftime so hard cap takes precedence over soft, and soft over half.
    val pendingCapOffer = when {
        hardCapReached(this, now) -> CapType.HARD
        softCapReached(this, now) -> CapType.SOFT
        halfCapReached(
            state = this,
            teamOneScore = updatedTeamOne.score,
            teamTwoScore = updatedTeamTwo.score,
            now = now,
        ) -> CapType.HALF
        else -> null
    }

    val halftimeScore = this.halftimeTargetScore ?: halftimeScore(this.rules)
    val halftimeReached = !this.halftimeTaken &&
        max(updatedTeamOne.score, updatedTeamTwo.score) >= halftimeScore

    // A point-end cap offer is still only pending; the observer can apply or defer it.
    // Start halftime and surface that offer from the halftime state.
    if (halftimeReached) {
        return startHalftime(
            state = this,
            teamOne = updatedTeamOne,
            teamTwo = updatedTeamTwo,
            existingCapOffer = pendingCapOffer,
            now = now,
            undoPrevious = this,
            undoLabel = "Undo Goal by ${teamName(this, scoringTeam)}",
        )
    }

    // Regular point -- not half, and not game over.
    val countdown = buildBetweenPointsCountdown(
        pullingFromEnd = nextPullingFromEnd,
        sequenceStart = now,
    )

    return this.copy(
        teamOne = updatedTeamOne,
        teamTwo = updatedTeamTwo,
        pullingTeam = nextPullingTeam,
        nearAttackingTeam = nextNearAttackingTeam,
        pullingFromEnd = nextPullingFromEnd,
        phase = LivePhase.BETWEEN_POINTS,
        countdown = countdown,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        halftimeTaken = this.halftimeTaken,
        halftimeTargetScore = this.halftimeTargetScore,
        winningScore = this.winningScore,
        halfCapApplied = this.halfCapApplied,
        softCapApplied = this.softCapApplied,
        hardCapApplied = this.hardCapApplied,
        pendingCapOffer = pendingCapOffer,
        lastEvent = "${teamName(this, scoringTeam)} scored.",
    ).withUndo(this, "Undo Goal by ${teamName(this, scoringTeam)}")
}

// Manually start half time
fun LiveGameState.startHalftimeNow(
    now: Long,
): LiveGameState {
    if (this.halftimeTaken || this.phase != LivePhase.BETWEEN_POINTS) {
        return this
    }
    return startHalftime(
        state = this,
        teamOne = this.teamOne,
        teamTwo = this.teamTwo,
        existingCapOffer = this.pendingCapOffer,
        now = now,
        undoPrevious = this,
        undoLabel = "Undo Start Halftime",
    )
}

private fun startHalftime(
    state: LiveGameState,
    teamOne: TeamLiveState,
    teamTwo: TeamLiveState,
    existingCapOffer: CapType?,
    now: Long,
    undoPrevious: LiveGameState,
    undoLabel: String,
): LiveGameState {
    val secondHalfPullingTeam = state.openingPullingTeam.flip()
    val secondHalfPullingFromEnd = state.openingPullingFromEnd
    val secondHalfNearAttackingTeam = if (secondHalfPullingFromEnd == FieldEnd.FAR) {
        secondHalfPullingTeam
    } else {
        secondHalfPullingTeam.flip()
    }
    val halftimeCountdown = buildHalftimeCountdown(
        halftimeMinutes = state.rules.halftimeMinutes,
        sequenceStart = now,
    )
    val halftimeEnd = now + state.rules.halftimeMinutes * 60_000L
    val hardCapTime = state.startEpoch + state.rules.hardCapMinutes * 60_000L
    val softCapTime = state.startEpoch + state.rules.softCapMinutes * 60_000L
    // Preserve an already-pending soft/hard cap. Otherwise, catch caps that became
    // due just before a manual halftime start or that are scheduled during halftime.
    val pendingCapOffer = existingCapOffer.takeIf { it == CapType.SOFT || it == CapType.HARD }
        ?: when {
            hardCapRelevant(state) && hardCapTime < halftimeEnd -> CapType.HARD
            softCapRelevant(state) && softCapTime < halftimeEnd -> CapType.SOFT
            else -> null
        }

    return state.copy(
        teamOne = teamOne.copy(
            firstHalfTimeoutsUsed = state.teamOne.timeoutsUsedThisHalf,
            timeoutsUsedThisHalf = 0,
        ),
        teamTwo = teamTwo.copy(
            firstHalfTimeoutsUsed = state.teamTwo.timeoutsUsedThisHalf,
            timeoutsUsedThisHalf = 0,
        ),
        pullingTeam = secondHalfPullingTeam,
        pullingFromEnd = secondHalfPullingFromEnd,
        nearAttackingTeam = secondHalfNearAttackingTeam,
        phase = LivePhase.HALFTIME,
        countdown = halftimeCountdown,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        halftimeTaken = true,
        pendingCapOffer = pendingCapOffer,
        lastEvent = "Halftime.",
    ).withUndo(undoPrevious, undoLabel)
}

// Manually end the game now.
fun LiveGameState.endGameNow(
    now: Long,
): LiveGameState {
    if (this.phase == LivePhase.GAME_OVER) {
        return this
    }
    return this.copy(
        endTime = localTimeFromEpoch(now, this.timeZone),
        phase = LivePhase.GAME_OVER,
        countdown = null,
        pendingCapOffer = null,
        lastEvent = "Game over.",
    ).withUndo(this, "Undo End Game")
}

// Start a point.  I.e. indicate that the pull happened.
fun LiveGameState.beginLivePoint(): LiveGameState {
    return this.copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        lastEvent = "Point is live.",
    ).withUndo(this, "Undo Start Point")
}

// If we record a goal before starting the point, start it, and then record the goal.
fun LiveGameState.recordGoalFromCurrentState(
    scoringTeam: TeamId,
    now: Long,
): LiveGameState {
    val livePointState = if (this.phase == LivePhase.BETWEEN_POINTS) {
        this.beginLivePoint()
    } else {
        this
    }
    return livePointState.recordGoal(scoringTeam, now)
}

// Resume a live point after a timeout or similar interruption.
fun LiveGameState.continueLivePoint(): LiveGameState {
    return this.copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        lastEvent = "Point continued.",
    )
}

// Advance automatic clock-driven transitions that do not require an observer button press.
fun LiveGameState.advanceGameClock(now: Long): LiveGameState {
    val countdown = this.countdown ?: return this
    if (now < countdown.targetEpoch) {
        return this
    }
    return when {
        this.phase == LivePhase.BETWEEN_POINTS && countdown.kind == CountdownKind.BETWEEN_POINTS -> {
            automaticLivePointState(this)
        }
        this.phase == LivePhase.LIVE_POINT && countdown.kind == CountdownKind.TIME_OUT -> {
            automaticContinueLivePointState(this)
        }
        this.phase == LivePhase.HALFTIME && countdown.kind == CountdownKind.HALFTIME -> {
            val betweenPointsState = this.copy(
                phase = LivePhase.BETWEEN_POINTS,
                countdown = buildBetweenPointsCountdown(
                    pullingFromEnd = this.pullingFromEnd,
                    sequenceStart = countdown.targetEpoch,
                ),
            )
            betweenPointsState.advanceGameClock(now)
        }
        else -> error("Countdown ${countdown.kind} is not valid while game phase is ${this.phase}.")
    }
}

// Adjust countdown timer (use negative number to subtract time)
fun LiveGameState.addTimeToCountdown(seconds: Int): LiveGameState {
    val countdown = this.countdown ?: return this
    val sign = if (seconds < 0) "-" else ""
    val absoluteSeconds = abs(seconds)
    return this.copy(
        countdown = countdown.copy(targetEpoch = countdown.targetEpoch + seconds * 1000L),
        lastEvent = "Adjusted timer by $sign${absoluteSeconds / 60}:${(absoluteSeconds % 60).toString().padStart(2, '0')}.",
    )
}

// Manually adjust the score
fun LiveGameState.adjustScore(teamOneScore: Int, teamTwoScore: Int): LiveGameState {
    return this.copy(
        teamOne = this.teamOne.copy(score = teamOneScore.coerceAtLeast(0)),
        teamTwo = this.teamTwo.copy(score = teamTwoScore.coerceAtLeast(0)),
        lastEvent = "Score adjusted.",
    ).withUndo(this, "Undo Score Adjustment")
}

// Manually adjust the number of timeouts
fun LiveGameState.adjustTimeouts(
    teamOneTimeoutsUsed: Int,
    teamTwoTimeoutsUsed: Int,
): LiveGameState {
    return this.copy(
        teamOne = this.teamOne.copy(
            timeoutsUsedThisHalf = teamOneTimeoutsUsed,
        ),
        teamTwo = this.teamTwo.copy(
            timeoutsUsedThisHalf = teamTwoTimeoutsUsed,
        ),
        lastEvent = "Timeouts adjusted.",
    ).withUndo(this, "Undo Timeout Adjustment")
}

// Manually adjust the cards and technical fouls that have been assigned
fun LiveGameState.adjustCardsAndTf(
    teamOneBlues: Int,
    teamOneTechnicalFouls: Int,
    teamTwoBlues: Int,
    teamTwoTechnicalFouls: Int,
    teamOnePlayerCards: List<InGamePlayerCardRecord>,
    teamTwoPlayerCards: List<InGamePlayerCardRecord>,
): LiveGameState {
    requirePlayerCardRecordsValid(teamOnePlayerCards)
    requirePlayerCardRecordsValid(teamTwoPlayerCards)
    val adjustedTeamOneBlues = teamOneBlues.coerceAtLeast(0)
    val adjustedTeamOneTechnicalFouls = teamOneTechnicalFouls.coerceAtLeast(0)
    val adjustedTeamTwoBlues = teamTwoBlues.coerceAtLeast(0)
    val adjustedTeamTwoTechnicalFouls = teamTwoTechnicalFouls.coerceAtLeast(0)

    return this.copy(
        teamOne = this.teamOne.copy(
            blueCards = adjustedTeamOneBlues,
            technicalFouls = adjustedTeamOneTechnicalFouls,
        ),
        teamTwo = this.teamTwo.copy(
            blueCards = adjustedTeamTwoBlues,
            technicalFouls = adjustedTeamTwoTechnicalFouls,
        ),
        teamOnePlayerCards = teamOnePlayerCards,
        teamTwoPlayerCards = teamTwoPlayerCards,
        lastEvent = "Cards and technical fouls adjusted.",
    ).withUndo(this, "Undo Cards / TF Adjustment")
}

// Make failures obvious if a caller bypasses the normal player-card adjustment flow.
private fun requirePlayerCardRecordsValid(records: List<InGamePlayerCardRecord>) {
    require(records.all { it.yellows >= 0 && it.directReds >= 0 }) {
        "Player card records cannot have negative card counts."
    }
    require(records.all(::playerCardRecordHasLegalCounts)) {
        "Player card records must be no cards, one yellow, second yellow, direct red, or one yellow plus direct red."
    }
    require(records.distinctBy { it.jerseyNumber }.size == records.size) {
        "Player card records cannot contain duplicate player entries."
    }
}

private fun playerCardRecordHasLegalCounts(record: InGamePlayerCardRecord): Boolean {
    return record.yellows <= 2 &&
        record.directReds <= 1 &&
        (record.yellows < 2 || record.directReds == 0)
}

// Check whether assigning another card would keep the player's card record legal.
fun canAddPlayerCardAssignment(
    records: List<InGamePlayerCardRecord>,
    jerseyNumber: String,
    cardType: CardType,
): Boolean {
    val existingRecord = records.firstOrNull { it.jerseyNumber == jerseyNumber }
        ?: InGamePlayerCardRecord(jerseyNumber = jerseyNumber)
    val updatedRecord = when (cardType) {
        CardType.YELLOW -> existingRecord.copy(yellows = existingRecord.yellows + 1)
        CardType.RED -> existingRecord.copy(directReds = existingRecord.directReds + 1)
    }
    return playerCardRecordHasLegalCounts(updatedRecord)
}

// Turn requested yellow/red totals into the player-card add/remove steps needed to reconcile them.
fun LiveGameState.buildPlayerCardAdjustmentSteps(
    teamOneYellows: Int,
    teamOneReds: Int,
    teamTwoYellows: Int,
    teamTwoReds: Int,
): List<PlayerCardAdjustmentStep> {
    val stateTeamOneYellows = this.teamYellowCards(TeamId.TEAM_ONE)
    val stateTeamOneReds = this.teamRedCards(TeamId.TEAM_ONE)
    val stateTeamTwoYellows = this.teamYellowCards(TeamId.TEAM_TWO)
    val stateTeamTwoReds = this.teamRedCards(TeamId.TEAM_TWO)

    return buildList {
        fun addSteps(team: TeamId, cardType: CardType, desiredCount: Int, currentCount: Int) {
            repeat(maxOf(0, desiredCount - currentCount)) {
                add(PlayerCardAdjustmentStep(team, cardType, PlayerCardAdjustmentMode.ADD))
            }
            repeat(maxOf(0, currentCount - desiredCount)) {
                add(PlayerCardAdjustmentStep(team, cardType, PlayerCardAdjustmentMode.REMOVE))
            }
        }

        addSteps(TeamId.TEAM_ONE, CardType.YELLOW, teamOneYellows, stateTeamOneYellows)
        addSteps(TeamId.TEAM_ONE, CardType.RED, teamOneReds, stateTeamOneReds)
        addSteps(TeamId.TEAM_TWO, CardType.YELLOW, teamTwoYellows, stateTeamTwoYellows)
        addSteps(TeamId.TEAM_TWO, CardType.RED, teamTwoReds, stateTeamTwoReds)
    }
}

// Return the players who currently have a card of the given type available to remove.
fun playerCardRemovalCandidates(
    records: List<InGamePlayerCardRecord>,
    cardType: CardType,
): List<PlayerCardRemovalCandidate> {
    return records.mapNotNull { record ->
        val count = playerCardCount(record, cardType)
        if (count > 0) {
            PlayerCardRemovalCandidate(record.jerseyNumber, count)
        } else {
            null
        }
    }
}

private fun playerCardCount(record: InGamePlayerCardRecord, cardType: CardType): Int {
    return when (cardType) {
        CardType.YELLOW -> record.yellows
        CardType.RED -> record.directReds
    }
}

// Assign a card to a specific player
fun addPlayerCardAssignment(
    records: List<InGamePlayerCardRecord>,
    jerseyNumber: String,
    cardType: CardType,
): List<InGamePlayerCardRecord> {
    return updatePlayerCardRecord(records, jerseyNumber) { record ->
        when (cardType) {
            CardType.YELLOW -> record.copy(yellows = record.yellows + 1)
            CardType.RED -> record.copy(directReds = record.directReds + 1)
        }
    }
}

// Remove a card from a specific player
fun removePlayerCardAssignment(
    records: List<InGamePlayerCardRecord>,
    jerseyNumber: String,
    cardType: CardType,
): List<InGamePlayerCardRecord> {
    val existingIndex = records.indexOfFirst { it.jerseyNumber == jerseyNumber }
    if (existingIndex < 0) {
        return records
    }
    return records.mapIndexedNotNull { index, record ->
        if (index != existingIndex) {
            record
        } else {
            val updated = when (cardType) {
                CardType.YELLOW -> record.copy(yellows = max(0, record.yellows - 1))
                CardType.RED -> record.copy(directReds = max(0, record.directReds - 1))
            }
            if (updated.yellows == 0 && updated.directReds == 0) null else updated
        }
    }
}

// Manually change the number of offside or false starts each team has.
fun LiveGameState.adjustPullInfractions(
    teamOneOffsides: Int,
    teamOneFalseStarts: Int,
    teamTwoOffsides: Int,
    teamTwoFalseStarts: Int,
): LiveGameState {
    return this.copy(
        teamOne = this.teamOne.copy(
            offsides = teamOneOffsides.coerceAtLeast(0),
            falseStarts = teamOneFalseStarts.coerceAtLeast(0),
        ),
        teamTwo = this.teamTwo.copy(
            offsides = teamTwoOffsides.coerceAtLeast(0),
            falseStarts = teamTwoFalseStarts.coerceAtLeast(0),
        ),
        lastEvent = "Pull infractions adjusted.",
    ).withUndo(this, "Undo Pull Infraction Adjustment")
}

// Swap which team is on which end of the field.
fun LiveGameState.swapFieldEnds(): LiveGameState {
    val newPullingFromEnd = this.pullingFromEnd.flip()
    return this.copy(
        nearAttackingTeam = this.nearAttackingTeam.flip(),
        pullingFromEnd = newPullingFromEnd,
        countdown = this.countdown?.swapOD(),
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        lastEvent = "Field ends swapped.",
    ).withUndo(this, "Undo Swap Ends of Field")
}

// Swap which team is pulling.
fun LiveGameState.swapPullingTeam(): LiveGameState {
    val newPullingTeam = this.pullingTeam.flip()
    val newPullingFromEnd = this.pullingFromEnd.flip()
    return this.copy(
        pullingTeam = newPullingTeam,
        pullingFromEnd = newPullingFromEnd,
        countdown = this.countdown?.swapOD(),
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        lastEvent = "Pulling team swapped.",
    ).withUndo(this, "Undo Swap Pulling Team")
}

// Manually apply one of the caps
fun LiveGameState.makeCapNow(
    capType: CapType,
    now: Long,
): LiveGameState {
    val offsetMinutes = when (capType) {
        CapType.HALF -> this.rules.halfCapMinutes
        CapType.SOFT -> this.rules.softCapMinutes
        CapType.HARD -> this.rules.hardCapMinutes
    }
    val offset = offsetMinutes * 60_000L
    val adjustedStart = localDateTimeFromEpoch(now - offset, this.timeZone)
    val capName = capDisplayName(capType)
    return this.copy(
        rules = when (capType) {
            CapType.HALF -> this.rules.copy(useHalfCap = true)
            CapType.SOFT -> this.rules.copy(useSoftCap = true)
            CapType.HARD -> this.rules.copy(useHardCap = true)
        },
        startDate = adjustedStart.toLocalDate(),
        startTime = adjustedStart.toLocalTime(),
        startEpoch = now - offset,
        lastEvent = "$capName cap set to now.",
    ).withUndo(this, "Undo $capName Cap Now")
}

// Apply the next cap due to its time being reached.
// This is run when we have asked the user whether to apply the next pending cap,
// and they agree to apply it.
fun LiveGameState.applyPendingCap(
    now: Long,
): LiveGameState {
    val pendingCap = this.pendingCapOffer!!
    val currentHigherScore = max(this.teamOne.score, this.teamTwo.score)
    return when (pendingCap) {
        CapType.HALF -> this.copy(
            halftimeTargetScore = currentHigherScore + 1,
            halfCapApplied = true,
            pendingCapOffer = null,
            lastEvent = "Half cap applied.",
        ).withUndo(this, "Undo Apply Half Cap")

        CapType.SOFT -> this.copy(
            winningScore = currentHigherScore + 1,
            softCapApplied = true,
            pendingCapOffer = null,
            lastEvent = "Soft cap applied.",
        ).withUndo(this, "Undo Apply Soft Cap")

        CapType.HARD -> {
            if (this.teamOne.score != this.teamTwo.score) {
                this.copy(
                    endTime = localTimeFromEpoch(now, this.timeZone),
                    phase = LivePhase.GAME_OVER,
                    countdown = null,
                    hardCapApplied = true,
                    pendingCapOffer = null,
                    lastEvent = "Game over.",
                ).withUndo(this, "Undo Apply Hard Cap")
            } else {
                this.copy(
                    winningScore = currentHigherScore + 1,
                    hardCapApplied = true,
                    pendingCapOffer = null,
                    lastEvent = "Hard cap applied.",
                ).withUndo(this, "Undo Apply Hard Cap")
            }
        }
    }
}

// Don't apply the next cap due to its time being reached.
// This is run when we have asked the user whether to apply the next pending cap,
// and they decide not to apply it yet.
fun LiveGameState.deferPendingCap(): LiveGameState {
    return this.copy(pendingCapOffer = null, lastEvent = "Cap offer deferred.")
}

// Undo the last action.
fun LiveGameState.undoLastAction(): LiveGameState {
    return this.undoEntry?.previous ?: this
}

// Adjust the game setup after the game has already started.
fun applySetupToLiveGame(
    existing: LiveGameState,
    setup: GameSetupState,
    now: Long,
): LiveGameState {
    val openingNearAttackingTeam = if (setup.pullingFromEnd == FieldEnd.FAR) {
        setup.pullingTeam
    } else {
        setup.pullingTeam.flip()
    }
    val shouldResyncPullState = existing.teamOne.score == 0 &&
        existing.teamTwo.score == 0 &&
        existing.phase != LivePhase.LIVE_POINT

    val base = existing.copy(
        startDate = setup.startDate,
        startTime = setup.startTime,
        timeZone = setup.timeZone,
        startEpoch = epochTimestamp(setup.startDate, setup.startTime, setup.timeZone),
        rules = setup.rules,
        teamOne = existing.teamOne.copy(
            name = setup.teamOne.name.ifBlank { "Team 1" },
            color = setup.teamOne.color,
        ),
        teamTwo = existing.teamTwo.copy(
            name = setup.teamTwo.name.ifBlank { "Team 2" },
            color = setup.teamTwo.color,
        ),
        priorCards = setup.priorCards,
        teamOnePlayerCards = existing.teamOnePlayerCards,
        teamTwoPlayerCards = existing.teamTwoPlayerCards,
        openingPullingTeam = setup.pullingTeam,
        openingPullingFromEnd = setup.pullingFromEnd,
    )

    val updatedState = if (shouldResyncPullState) {
        base.copy(
            nearAttackingTeam = openingNearAttackingTeam,
            pullingTeam = setup.pullingTeam,
            pullingFromEnd = setup.pullingFromEnd,
        ).startPullSequence(now)
    } else {
        base
    }
    return updatedState.withUndo(existing, "Undo Update Game Setup")
}

// Go to setup screen from live game
// This just extracts the information from the live state that the setup screen needs.
fun liveGameToSetupState(state: LiveGameState): GameSetupState {
    return GameSetupState(
        startDate = state.startDate,
        startTime = state.startTime,
        timeZone = state.timeZone,
        rules = state.rules,
        teamOne = TeamSetup(
            name = state.teamOne.name,
            color = state.teamOne.color,
        ),
        teamTwo = TeamSetup(
            name = state.teamTwo.name,
            color = state.teamTwo.color,
        ),
        priorCards = state.priorCards,
        pullingTeam = state.openingPullingTeam,
        pullingFromEnd = state.openingPullingFromEnd,
    )
}

// Someone called a timeout.
// This records another used timeout and starts or extends the appropriate countdown.
fun LiveGameState.assessTimeout(
    team: TeamId,
    now: Long,
): TimeoutAssessmentResult {
    val timeoutState = timeoutEligibleState(this, now)
        ?: return TimeoutAssessmentResult(this, "Timeouts are not available now.")
    if (timeoutState.timeoutsRemaining(team) <= 0) {
        return TimeoutAssessmentResult(this, "${teamName(this, team)} is out of timeouts.")
    }
    return TimeoutAssessmentResult(timeoutState.chargeTimeout(team, now))
}

fun LiveGameState.chargeTimeout(
    team: TeamId,
    now: Long,
): LiveGameState {
    val timeoutState = timeoutEligibleState(this, now) ?: return this
    if (timeoutState.timeoutsRemaining(team) <= 0) {
        return this
    }

    val updatedState = timeoutState.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            timeoutState.teamOne.withAddedTimeout()
        } else {
            timeoutState.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            timeoutState.teamTwo.withAddedTimeout()
        } else {
            timeoutState.teamTwo
        },
        lastEvent = "Timeout charged to ${teamName(timeoutState, team)}."
    )

    if (timeoutState.phase == LivePhase.BETWEEN_POINTS) {
        return applyBetweenPointsTimeout(updatedState)
            .withUndo(this, "Undo Timeout by ${teamName(timeoutState, team)}")
    }
    return applyLivePointTimeout(updatedState, now)
        .withUndo(this, "Undo Timeout by ${teamName(timeoutState, team)}")
}

// Offsides on the pulling team
fun LiveGameState.recordOffsides(): LiveGameState {
    if (this.pullSequenceOffsidesRecorded) {
        return this
    }
    val team = this.pullingTeam
    return this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            this.teamOne.copy(offsides = this.teamOne.offsides + 1)
        } else {
            this.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            this.teamTwo.copy(offsides = this.teamTwo.offsides + 1)
        } else {
            this.teamTwo
        },
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        pullSequenceOffsidesRecorded = true,
        lastEvent = "Offsides on ${teamName(this, team)}.",
    ).withUndo(this, "Undo Offsides on ${teamName(this, team)}")
}

// False start on the receiving team
fun LiveGameState.recordFalseStart(): LiveGameState {
    if (this.pullSequenceFalseStartRecorded) {
        return this
    }
    val team = this.pullingTeam.flip()
    return this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            this.teamOne.copy(falseStarts = this.teamOne.falseStarts + 1)
        } else {
            this.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            this.teamTwo.copy(falseStarts = this.teamTwo.falseStarts + 1)
        } else {
            this.teamTwo
        },
        pullSequenceFalseStartRecorded = true,
        lastEvent = "False start on ${teamName(this, team)}.",
    ).withUndo(this, "Undo False Start on ${teamName(this, team)}")
}

// Terse field-position cue shown after recording false start.
fun falseStartResolutionMessage(): String {
    return "Defense gets to set up."
}

// Prompt shown when live-point misconduct needs an offense/defense choice.
fun livePointMisconductPrompt(baseMessage: String): String {
    return "$baseMessage\n\nWas this against the offense or defense?"
}

// Full live-point misconduct message after the observer chooses offense or defense.
fun livePointMisconductResolutionMessage(baseMessage: String, againstOffense: Boolean): String {
    return "$baseMessage\n\n${livePointMisconductMessage(againstOffense)}"
}

// Short cap name for prompt titles/buttons.
fun capOfferLabel(capType: CapType): String {
    return when (capType) {
        CapType.HALF -> "half cap"
        CapType.SOFT -> "soft cap"
        CapType.HARD -> "hard cap"
    }
}

private fun capDisplayName(capType: CapType): String {
    return when (capType) {
        CapType.HALF -> "Half"
        CapType.SOFT -> "Soft"
        CapType.HARD -> "Hard"
    }
}

// Full text for the apply-cap confirmation dialog.
fun LiveGameState.capOfferExplanation(): String {
    val wasAt = if (this.phase == LivePhase.HALFTIME) "is scheduled for" else "was at"
    val endWhen = if (this.phase == LivePhase.HALFTIME) "during halftime" else "now"
    return when (this.pendingCapOffer!!) {
        CapType.HALF -> {
            val target = max(this.teamOne.score, this.teamTwo.score) + 1
            "Half cap was at ${formatCapClockTime(this, CapType.HALF)}. Halftime target would become $target. Apply now?"
        }
        CapType.SOFT -> {
            val target = max(this.teamOne.score, this.teamTwo.score) + 1
            "Soft cap $wasAt ${formatCapClockTime(this, CapType.SOFT)}. Winning score would become $target. Apply now?"
        }
        CapType.HARD -> {
            if (this.teamOne.score == this.teamTwo.score) {
                "Hard cap $wasAt ${formatCapClockTime(this, CapType.HARD)}. Score is tied, so one more point would be played. Apply now?"
            } else {
                "Hard cap $wasAt ${formatCapClockTime(this, CapType.HARD)}. Score is not tied, so the game would end $endWhen. Apply now?"
            }
        }
    }
}

// Terse field-position cue shown after recording offsides.
fun LiveGameState.offsidesResolutionMessage(teamId: TeamId): String {
    val team = if (teamId == TeamId.TEAM_ONE) this.teamOne else this.teamTwo
    val pullViolations = team.offsides + team.falseStarts
    return if (pullViolations <= 1) {
        "Start at brick mark"
    } else {
        "Start at midfield"
    }
}

// Assess a blue card and check whether it triggers misconduct handling.
fun LiveGameState.assessBlueCard(team: TeamId): CardAssessmentResult {
    val updatedState = this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            this.teamOne.copy(blueCards = this.teamOne.blueCards + 1)
        } else {
            this.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            this.teamTwo.copy(blueCards = this.teamTwo.blueCards + 1)
        } else {
            this.teamTwo
        },
        lastEvent = "Blue card assessed to ${teamName(this, team)}.",
    ).withUndo(this, "Undo Blue Card on ${teamName(this, team)}")
    val cardTotal = updatedState.teamCardTotal(team)
    return CardAssessmentResult(
        state = updatedState,
        message = buildCardMessage(
            baseMessage = "${teamName(updatedState, team)} has $cardTotal ${pluralize(cardTotal, "card")}.",
            state = updatedState,
            team = team,
            thresholdCount = cardTotal,
        ),
        needsLivePointMisconductChoice = cardTotal >= 3 && updatedState.phase == LivePhase.LIVE_POINT,
    )
}

// Assess a technical foul and check whether it triggers misconduct handling.
fun LiveGameState.assessTechnicalFoul(team: TeamId): CardAssessmentResult {
    val updatedState = this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            this.teamOne.copy(technicalFouls = this.teamOne.technicalFouls + 1)
        } else {
            this.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            this.teamTwo.copy(technicalFouls = this.teamTwo.technicalFouls + 1)
        } else {
            this.teamTwo
        },
        lastEvent = "Technical foul on ${teamName(this, team)}.",
    ).withUndo(this, "Undo Technical Foul on ${teamName(this, team)}")
    val technicalFouls = if (team == TeamId.TEAM_ONE) {
        updatedState.teamOne.technicalFouls
    } else {
        updatedState.teamTwo.technicalFouls
    }
    return CardAssessmentResult(
        state = updatedState,
        message = buildCardMessage(
            baseMessage = "${teamName(updatedState, team)} has $technicalFouls technical ${pluralize(technicalFouls, "foul")}.",
            state = updatedState,
            team = team,
            thresholdCount = technicalFouls,
        ),
        needsLivePointMisconductChoice = technicalFouls >= 3 && updatedState.phase == LivePhase.LIVE_POINT,
    )
}

// Assess a yellow card and check whether it triggers misconduct handling.
// It could be one of two things depending on whether it's the first or second yellow.
fun LiveGameState.assessYellowCard(team: TeamId, jerseyNumber: String): CardAssessmentResult {
    val currentRecord = this.playerCardFor(team, jerseyNumber)
    return if (currentRecord?.yellows ?: 0 >= 1) {
        this.assessRedCard(team, jerseyNumber, RedCardMode.SECOND_YELLOW)
    } else {
        this.assessStandaloneYellowCard(team, jerseyNumber)
    }
}

// Figure out the consequence for a standalone yellow.
fun LiveGameState.assessStandaloneYellowCard(team: TeamId, jerseyNumber: String): CardAssessmentResult {
    val updatedState = addInGameYellowCard(this, team, jerseyNumber)
        .withUndo(this, "Undo Yellow Card on ${teamName(this, team)} #$jerseyNumber")
    val cardTotal = updatedState.teamCardTotal(team)
    return CardAssessmentResult(
        state = updatedState,
        message = buildCardMessage(
            baseMessage = "${teamName(updatedState, team)} has $cardTotal ${pluralize(cardTotal, "card")}.",
            state = updatedState,
            team = team,
            thresholdCount = cardTotal,
        ),
        needsLivePointMisconductChoice = cardTotal >= 3 && updatedState.phase == LivePhase.LIVE_POINT,
    )
}

// Assess a red card and check whether it triggers misconduct handling.
fun LiveGameState.assessRedCard(
    team: TeamId,
    jerseyNumber: String,
    mode: RedCardMode,
): CardAssessmentResult {
    val updatedState = when (mode) {
        RedCardMode.DIRECT_RED -> addInGameDirectRed(this, team, jerseyNumber)
            .withUndo(this, "Undo Direct Red on ${teamName(this, team)} #$jerseyNumber")
        RedCardMode.SECOND_YELLOW -> addInGameSecondYellow(this, team, jerseyNumber)
            .withUndo(this, "Undo Second Yellow on ${teamName(this, team)} #$jerseyNumber")
    }
    val cardTotal = updatedState.teamCardTotal(team)
    val baseMessage = when (mode) {
        RedCardMode.DIRECT_RED -> "${teamName(updatedState, team)} has $cardTotal ${pluralize(cardTotal, "card")}."
        RedCardMode.SECOND_YELLOW -> {
            val ejectionMessage = if (jerseyNumber == UNKNOWN_PLAYER_NUMBER) {
                "The player is ejected."
            } else {
                "Player $jerseyNumber is ejected."
            }
            "Second yellow acts as a red card. $ejectionMessage\n${teamName(updatedState, team)} has $cardTotal ${pluralize(cardTotal, "card")}."
        }
    }
    return CardAssessmentResult(
        state = updatedState,
        message = buildCardMessage(
            baseMessage = baseMessage,
            state = updatedState,
            team = team,
            thresholdCount = cardTotal,
        ),
        needsLivePointMisconductChoice = cardTotal >= 3 && updatedState.phase == LivePhase.LIVE_POINT,
    )
}

enum class CardType(val label: String) {
    YELLOW("Yellow"),
    RED("Red"),
}

enum class PlayerCardAdjustmentMode {
    ADD,
    REMOVE,
}

data class PlayerCardAdjustmentStep(
    val team: TeamId,
    val cardType: CardType,
    val mode: PlayerCardAdjustmentMode,
)

data class PlayerCardRemovalCandidate(
    val jerseyNumber: String,
    val cardCount: Int,
)

// Figure out what the next relevant cap is in a live game.
fun LiveGameState.computeNextCapStatus(now: Long): CapStatus? {
    // `to` in Kotlin makes pairs. So `first to second` makes a pair (first, second).
    // Here we make pairs with second being another pair:
    // (isCapRelevant, (capName, capTime))
    val caps = listOf(
        halfCapRelevant(this, this.teamOne.score, this.teamTwo.score) to
            ("Half cap" to capEpoch(this, CapType.HALF)),
        softCapRelevant(this) to
            ("Soft cap" to capEpoch(this, CapType.SOFT)),
        hardCapRelevant(this) to
            ("Hard cap" to capEpoch(this, CapType.HARD)),
    )
        // Keep only caps whose relevance flag is true.
        .filter { it.first }
        // Keep just the (label, time) pair.
        .map { it.second }

    return caps
        // Convert each capTime into the time left from now until the cap.
        .map { (label, capTime) -> label to Duration.ofMillis(capTime - now) }
        // Find the first one whose duration is not negative.
        .firstOrNull { (_, remaining) -> !remaining.isNegative }
        // If any are found, make a CapStatus from this cap's time remaining.
        ?.let { (label, remaining) -> CapStatus(label, remaining) }
}

// Format the time into a nice string like "3:30 PM"
fun formatClockTime(time: LocalTime): String {
    return time.format(DateTimeFormatter.ofPattern("h:mm a"))
}

// Format a duration into a nice format like "0:32"
fun formatDuration(duration: Duration): String {
    val totalSeconds = max(0L, duration.seconds)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun halfCapRelevant(state: LiveGameState, teamOneScore: Int, teamTwoScore: Int): Boolean {
    return state.rules.useHalfCap &&
        !state.halftimeTaken &&
        !state.halfCapApplied &&
        halfCapCanChangeHalftime(state.rules, teamOneScore, teamTwoScore)
}

private fun softCapRelevant(state: LiveGameState): Boolean {
    return state.rules.useSoftCap && !state.softCapApplied
}

private fun hardCapRelevant(state: LiveGameState): Boolean {
    return state.rules.useHardCap && !state.hardCapApplied
}

private fun halfCapReached(
    state: LiveGameState,
    teamOneScore: Int,
    teamTwoScore: Int,
    now: Long,
): Boolean {
    return halfCapRelevant(state, teamOneScore, teamTwoScore) &&
        now >= capEpoch(state, CapType.HALF)
}

private fun softCapReached(state: LiveGameState, now: Long): Boolean {
    return softCapRelevant(state) &&
        now >= capEpoch(state, CapType.SOFT)
}

private fun hardCapReached(state: LiveGameState, now: Long): Boolean {
    return hardCapRelevant(state) &&
        now >= capEpoch(state, CapType.HARD)
}

// Build a countdown after a goal is scored.
// This is different depending on whether the observer is on the side of the pulling
// or receiving team.  (Observer is assumed on the near end.)
private fun buildBetweenPointsCountdown(
    pullingFromEnd: FieldEnd,
    sequenceStart: Long,
): CountdownState {
    val target = betweenPointsCountdownTargetFor(pullingFromEnd)
    return CountdownState(
        kind = CountdownKind.BETWEEN_POINTS,
        label = target.label,
        durationSeconds = target.baseDurationSeconds,
        targetEpoch = sequenceStart + target.baseDurationSeconds * 1000L,
        betweenPointsTarget = target,
    )
}

private fun betweenPointsCountdownTargetFor(pullingFromEnd: FieldEnd): BetweenPointsCountdownTarget {
    return if (pullingFromEnd == FieldEnd.NEAR) {
        BetweenPointsCountdownTarget.PULL
    } else {
        BetweenPointsCountdownTarget.OFFENSE_READY
    }
}

// Visible between-points countdown text for the currently responsible side of the field.
fun betweenPointsDisplay(
    pullingFromEnd: FieldEnd,
    sequenceStart: Long,
    now: Long,
): Pair<String, Duration> {
    val countdown = buildBetweenPointsCountdown(pullingFromEnd, sequenceStart)
    return countdown.label to Duration.ofMillis((countdown.targetEpoch - now).coerceAtLeast(0L))
}

// Build a countdown for half time.
private fun buildHalftimeCountdown(
    halftimeMinutes: Int,
    sequenceStart: Long,
): CountdownState {
    val durationSeconds = halftimeMinutes * 60
    return CountdownState(
        kind = CountdownKind.HALFTIME,
        label = "Halftime",
        durationSeconds = durationSeconds,
        targetEpoch = sequenceStart + durationSeconds * 1000L,
    )
}

// Calculate how many time outs are allowed in the current half according to the rules.
fun LiveGameState.timeoutsAllowedThisHalf(team: TeamId): Int {
    val firstHalfAllowance = this.rules.timeoutsPerHalf + if (this.rules.hasFloaterTimeout) 1 else 0
    if (!this.halftimeTaken) {
        return firstHalfAllowance
    }

    val firstHalfTimeoutsUsed = teamState(this, team).firstHalfTimeoutsUsed
    val floaterCarries = this.rules.hasFloaterTimeout && firstHalfTimeoutsUsed < firstHalfAllowance
    return this.rules.timeoutsPerHalf + if (floaterCarries) 1 else 0
}

// Calculate how many time outs are still available in the current half.
fun LiveGameState.timeoutsRemaining(team: TeamId): Int {
    val usedThisHalf = teamState(this, team).timeoutsUsedThisHalf
    return (this.timeoutsAllowedThisHalf(team) - usedThisHalf).coerceAtLeast(0)
}

// Return the state in which a timeout may be charged, if the rules allow one now.
private fun timeoutEligibleState(state: LiveGameState, now: Long): LiveGameState? {
    val advancedState = state.advanceGameClock(now)
    return when (advancedState.phase) {
        LivePhase.BETWEEN_POINTS, LivePhase.LIVE_POINT -> advancedState
        LivePhase.HALFTIME -> null
        else -> null
    }
}

// Apply a timeout between points.  (Basically just adds 70 sec to the timer.)
private fun applyBetweenPointsTimeout(
    state: LiveGameState,
): LiveGameState {
    val countdown = state.countdown!!
    return state.copy(
        countdown = countdown.copy(
            durationSeconds = countdown.durationSeconds + 70,
            targetEpoch = countdown.targetEpoch + 70_000L,
        )
    )
}

// Apply a timeout by the thrower during a live point.
private fun applyLivePointTimeout(
    state: LiveGameState,
    now: Long,
): LiveGameState {
    return state.copy(
        countdown = CountdownState(
            kind = CountdownKind.TIME_OUT,
            label = "Offense set in",
            durationSeconds = 70,
            targetEpoch = now + 70_000L,
        ),
    )
}

// Add a yellow card to a specific player
private fun addInGameYellowCard(state: LiveGameState, team: TeamId, jerseyNumber: String): LiveGameState {
    return state.withPlayerCards(
        team = team,
        records = updatePlayerCardRecord(
            records = state.playerCardsFor(team),
            jerseyNumber = jerseyNumber,
        ) { record ->
            record.copy(yellows = record.yellows + 1)
        },
        lastEvent = "Yellow card for ${teamName(state, team)} #$jerseyNumber.",
    )
}

// Add a second yellow card to a specific player
private fun addInGameSecondYellow(state: LiveGameState, team: TeamId, jerseyNumber: String): LiveGameState {
    return state.withPlayerCards(
        team = team,
        records = updatePlayerCardRecord(
            records = state.playerCardsFor(team),
            jerseyNumber = jerseyNumber,
        ) { record ->
            record.copy(yellows = record.yellows + 1)
        },
        lastEvent = "Second yellow for ${teamName(state, team)} #$jerseyNumber.",
    )
}

// Add a direct red card to a specific player
private fun addInGameDirectRed(state: LiveGameState, team: TeamId, jerseyNumber: String): LiveGameState {
    return state.withPlayerCards(
        team = team,
        records = updatePlayerCardRecord(
            records = state.playerCardsFor(team),
            jerseyNumber = jerseyNumber,
        ) { record ->
            record.copy(directReds = record.directReds + 1)
        },
        lastEvent = "Direct red for ${teamName(state, team)} #$jerseyNumber.",
    )
}

// Handle the details of updating a player's card status in the list of carded players.
private fun updatePlayerCardRecord(
    records: List<InGamePlayerCardRecord>,
    jerseyNumber: String,
    transform: (InGamePlayerCardRecord) -> InGamePlayerCardRecord,
): List<InGamePlayerCardRecord> {
    val existingIndex = records.indexOfFirst { it.jerseyNumber == jerseyNumber }
    val updatedRecords = if (existingIndex >= 0) {
        records.mapIndexed { index, record ->
            if (index == existingIndex) transform(record) else record
        }
    } else {
        records + transform(InGamePlayerCardRecord(jerseyNumber = jerseyNumber))
    }
    requirePlayerCardRecordsValid(updatedRecords)
    return updatedRecords
}

// Build the message for what happens when a team gets a card.
private fun buildCardMessage(
    baseMessage: String,
    state: LiveGameState,
    team: TeamId,
    thresholdCount: Int,
): String {
    return if (thresholdCount < 3) {
        baseMessage
    } else if (state.phase == LivePhase.LIVE_POINT) {
        baseMessage
    } else {
        "$baseMessage\n\n${betweenPointsMisconductMessage(state, team)}"
    }
}

// Build the portion of the message for misconduct penalty between points.
private fun betweenPointsMisconductMessage(state: LiveGameState, team: TeamId): String {
    val receivingTeam = state.pullingTeam.flip()
    return if (team == receivingTeam) {
        "Penalty against receiving team. No pull. Disc at negative brick in defending end zone."
    } else {
        "Penalty against pulling team. No pull. Receiving team starts at attacking brick."
    }
}

// Build the portion of the message for misconduct penalty during points.
fun livePointMisconductMessage(againstOffense: Boolean): String {
    return if (againstOffense) {
        "Misconduct penalty against offense.\nReverse brick. Offense 30 sec to set, defense 20 sec to check in. Defense may instead leave the disc where it stopped."
    } else {
        "Misconduct penalty against defense.\nBrick nearest attacking end zone. Offense 30 sec to set, defense 20 sec to check in. Offense may instead leave it or center it."
    }
}

// Check if a player already has a yellow card yet.
fun LiveGameState.playerHasYellowThisGame(team: TeamId, jerseyNumber: String): Boolean {
    return (this.playerCardFor(team, jerseyNumber)?.yellows ?: 0) > 0
}

// Return the player-card records for one team.
fun LiveGameState.playerCards(team: TeamId): List<InGamePlayerCardRecord> {
    return this.playerCardsFor(team)
}

// Count in-game yellow cards from the player-card records.
fun LiveGameState.teamYellowCards(team: TeamId): Int {
    return this.playerCardsFor(team).sumOf { it.yellows }
}

// Count in-game direct red cards from the player-card records.
fun LiveGameState.teamRedCards(team: TeamId): Int {
    return this.playerCardsFor(team).sumOf { it.directReds }
}

// Count the total number of cards a team has.
fun LiveGameState.teamCardTotal(team: TeamId): Int {
    val currentTeam = if (team == TeamId.TEAM_ONE) this.teamOne else this.teamTwo
    return this.teamYellowCards(team) + currentTeam.blueCards + (2 * this.teamRedCards(team))
}

// Helper function to pluralize nicely.
private fun pluralize(count: Int, singular: String): String {
    return if (count == 1) singular else "${singular}s"
}

// Get the team name for a given id
private fun teamName(state: LiveGameState, team: TeamId): String {
    return if (team == TeamId.TEAM_ONE) state.teamOne.name else state.teamTwo.name
}

// Move to live-point state while preserving the previous user-action undo entry.
private fun automaticLivePointState(state: LiveGameState): LiveGameState {
    return state.copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        lastEvent = "Point is live.",
    )
}

// Clear an in-point timeout countdown while preserving the timeout undo entry.
private fun automaticContinueLivePointState(state: LiveGameState): LiveGameState {
    return state.copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        lastEvent = "Point continued.",
    )
}

// Get the live state for one team.
private fun teamState(state: LiveGameState, team: TeamId): TeamLiveState {
    return if (team == TeamId.TEAM_ONE) state.teamOne else state.teamTwo
}

// Attach the previous game state in the undoEntry.
// Use this everywhere we want an action to be undo-able (essentially all user actions).
// Most returned states for a user-initiated action should have a .withUndo(state, label)
// at the end.
private fun LiveGameState.withUndo(previous: LiveGameState, label: String): LiveGameState {
    return copy(undoEntry = UndoEntry(label = label, previous = previous))
}

// Get the card record for a specific player.
private fun LiveGameState.playerCardsFor(team: TeamId): List<InGamePlayerCardRecord> {
    return if (team == TeamId.TEAM_ONE) teamOnePlayerCards else teamTwoPlayerCards
}

private fun LiveGameState.withPlayerCards(
    team: TeamId,
    records: List<InGamePlayerCardRecord>,
    lastEvent: String,
): LiveGameState {
    return when (team) {
        TeamId.TEAM_ONE -> copy(
            teamOnePlayerCards = records,
            lastEvent = lastEvent,
        )
        TeamId.TEAM_TWO -> copy(
            teamTwoPlayerCards = records,
            lastEvent = lastEvent,
        )
    }
}

private fun LiveGameState.playerCardFor(team: TeamId, jerseyNumber: String): InGamePlayerCardRecord? {
    return playerCardsFor(team).firstOrNull { it.jerseyNumber == jerseyNumber }
}

// Helper to flip TeamId between the two teams.
private fun TeamId.flip(): TeamId {
    return if (this == TeamId.TEAM_ONE) TeamId.TEAM_TWO else TeamId.TEAM_ONE
}

// Helper to flip FieldEnd between the two directions.
private fun FieldEnd.flip(): FieldEnd {
    return if (this == FieldEnd.NEAR) FieldEnd.FAR else FieldEnd.NEAR
}

// Calculate the halftime score as the next count over half the total.  (e.g. 15 -> 8)
private fun halftimeScore(rules: GameRules): Int {
    return (rules.gameTo / 2) + 1
}

// Half cap stops mattering once any next point would leave the target at normal halftime.
private fun halfCapCanChangeHalftime(rules: GameRules, teamOneScore: Int, teamTwoScore: Int): Boolean {
    val normalHalftimeScore = halftimeScore(rules)
    return max(teamOneScore, teamTwoScore) < normalHalftimeScore - 1 &&
        min(teamOneScore, teamTwoScore) < normalHalftimeScore - 2
}

private fun capEpoch(state: LiveGameState, capType: CapType): Long {
    val offsetMinutes = when (capType) {
        CapType.HALF -> state.rules.halfCapMinutes
        CapType.SOFT -> state.rules.softCapMinutes
        CapType.HARD -> state.rules.hardCapMinutes
    }
    return state.startEpoch + offsetMinutes * 60_000L
}

private fun formatCapClockTime(state: LiveGameState, capType: CapType): String {
    return formatClockTime(localTimeFromEpoch(capEpoch(state, capType), state.timeZone))
}

private fun epochTimestamp(date: LocalDate, time: LocalTime, timeZone: ZoneId): Long {
    return LocalDateTime.of(date, time)
        .atZone(timeZone)
        .toInstant()
        .toEpochMilli()
}

private fun localDateTimeFromEpoch(epoch: Long, timeZone: ZoneId): LocalDateTime {
    return LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(epoch),
        timeZone,
    )
}

private fun localTimeFromEpoch(epoch: Long, timeZone: ZoneId): LocalTime {
    return localDateTimeFromEpoch(epoch, timeZone).toLocalTime()
}

// The default start time for a game is the next even half hour after the reference time.
fun nextHalfHourFrom(referenceTime: LocalTime): LocalTime {
    val roundedMinute = when {
        referenceTime.minute == 0 && referenceTime.second == 0 -> 0
        referenceTime.minute < 30 -> 30
        else -> 0
    }
    val baseHour = if (roundedMinute == 0 && referenceTime.minute >= 30) {
        referenceTime.hour + 1
    } else {
        referenceTime.hour
    }
    return LocalTime.of(baseHour % 24, roundedMinute)
}
