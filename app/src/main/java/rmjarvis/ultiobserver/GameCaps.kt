package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

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
internal fun halfCapRelevant(state: LiveGameState, teamOneScore: Int, teamTwoScore: Int): Boolean {
    return state.rules.useHalfCap &&
        !state.halftimeTaken &&
        !state.halfCapApplied &&
        halfCapCanChangeHalftime(state.rules, teamOneScore, teamTwoScore)
}
internal fun softCapRelevant(state: LiveGameState): Boolean {
    return state.rules.useSoftCap && !state.softCapApplied
}
internal fun hardCapRelevant(state: LiveGameState): Boolean {
    return state.rules.useHardCap && !state.hardCapApplied
}
internal fun halfCapReached(
    state: LiveGameState,
    teamOneScore: Int,
    teamTwoScore: Int,
    now: Long,
): Boolean {
    return halfCapRelevant(state, teamOneScore, teamTwoScore) &&
        now >= capEpoch(state, CapType.HALF)
}
internal fun softCapReached(state: LiveGameState, now: Long): Boolean {
    return softCapRelevant(state) &&
        now >= capEpoch(state, CapType.SOFT)
}
internal fun hardCapReached(state: LiveGameState, now: Long): Boolean {
    return hardCapRelevant(state) &&
        now >= capEpoch(state, CapType.HARD)
}
// Calculate the halftime score as the next count over half the total.  (e.g. 15 -> 8)
internal fun halftimeScore(rules: GameRules): Int {
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
