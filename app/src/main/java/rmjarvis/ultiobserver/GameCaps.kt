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
    val offsetMinutes = capType.offsetMinutes(rules)
    val offset = offsetMinutes * 60_000L
    val adjustedStart = localDateTimeFromEpoch(now - offset, this.timeZone)
    return this.copy(
        rules = capType.rulesWithCapEnabled(rules),
        startDate = adjustedStart.toLocalDate(),
        startTime = adjustedStart.toLocalTime(),
        startEpoch = now - offset,
        lastEvent = "${capType.label} set to now.",
    ).withUndo(this, "Undo ${capType.titleLabel} Now")
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
                    endEpoch = now,
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
    return this.copy(
        pendingCapOffer = null,
        lastEvent = "Cap offer deferred.",
    )
}
// Figure out what the next relevant cap is in a live game.
fun LiveGameState.computeNextCapStatus(now: Long): CapStatus? {
    // `to` in Kotlin makes pairs. So `first to second` makes a pair (first, second).
    // Here we make pairs with second being another pair:
    // (isCapRelevant, (capName, capTime))
    val caps = listOf(
        this.halfCapRelevant(this.teamOne.score, this.teamTwo.score) to
            (CapType.HALF.label to this.capEpoch(CapType.HALF)),
        this.softCapRelevant() to
            (CapType.SOFT.label to this.capEpoch(CapType.SOFT)),
        this.hardCapRelevant() to
            (CapType.HARD.label to this.capEpoch(CapType.HARD)),
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
internal fun LiveGameState.halfCapRelevant(teamOneScore: Int, teamTwoScore: Int): Boolean {
    return rules.useHalfCap &&
        !halftimeTaken &&
        !halfCapApplied &&
        halfCapCanChangeHalftime(rules, teamOneScore, teamTwoScore)
}
internal fun LiveGameState.softCapRelevant(): Boolean {
    return rules.useSoftCap && !softCapApplied
}
internal fun LiveGameState.hardCapRelevant(): Boolean {
    return rules.useHardCap && !hardCapApplied
}
internal fun LiveGameState.halfCapReached(
    teamOneScore: Int,
    teamTwoScore: Int,
    now: Long,
): Boolean {
    return halfCapRelevant(teamOneScore, teamTwoScore) &&
        now >= capEpoch(CapType.HALF)
}
internal fun LiveGameState.softCapReached(now: Long): Boolean {
    return softCapRelevant() &&
        now >= capEpoch(CapType.SOFT)
}
internal fun LiveGameState.hardCapReached(now: Long): Boolean {
    return hardCapRelevant() &&
        now >= capEpoch(CapType.HARD)
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
internal fun LiveGameState.capEpoch(capType: CapType): Long {
    return startEpoch + capType.offsetMinutes(rules) * 60_000L
}
