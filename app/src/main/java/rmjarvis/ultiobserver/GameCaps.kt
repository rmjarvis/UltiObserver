package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * Reposition the selected cap so it is due at the current time.
 * This is the manual cap action from Other, rather than the normal scheduled cap prompt.
 *
 * @param capType The cap whose scheduled offset should be enabled and aligned to now.
 * @param now The epoch millis that should become the cap's scheduled instant.
 */
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
/**
 * Apply the cap currently being offered to the observer.
 * This is run when the app has asked whether to apply the next pending cap and the
 * observer agrees to apply it.
 *
 * @param now The epoch millis used as the end time if hard cap immediately ends the game.
 */
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
/**
 * Clear the current cap offer when the observer chooses not to apply it yet.
 * This is run when the app has asked whether to apply the next pending cap and the
 * observer decides not to apply it yet.
 */
fun LiveGameState.deferPendingCap(): LiveGameState {
    return this.copy(
        pendingCapOffer = null,
        lastEvent = "Cap offer deferred.",
    )
}
/**
 * Compute the next cap that still matters for live status display.
 *
 * @param now The current epoch millis used to turn scheduled cap times into remaining durations.
 */
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
/**
 * Return a one-shot timing cue when a relevant cap reaches its scheduled wall-clock time.
 *
 * @param now The current epoch millis, checked against cap instants with a short delivery window.
 */
internal fun LiveGameState.dueCapTimingCue(now: Long): TimingCueDisplay? {
    return relevantCapTypes()
        .map { capType -> capType to capEpoch(capType) }
        .sortedBy { (_, capTime) -> capTime }
        .firstNotNullOfOrNull { (capType, capTime) ->
            val elapsedSinceCap = now - capTime
            if (elapsedSinceCap in 0L..1_100L) {
                TimingCueDisplay(
                    id = capType.timingCueId(),
                    message = capType.label,
                    remaining = Duration.ZERO,
                    countdownTime = Duration.ZERO,
                    targetEpoch = capTime,
                )
            } else {
                null
            }
        }
}

/**
 * Return the upcoming cap timing cue that should be displayed before the scheduled cap instant.
 *
 * @param now The current epoch millis used to select the next future cap and compute time remaining.
 */
internal fun LiveGameState.nextCapTimingCue(now: Long): TimingCueDisplay? {
    return relevantCapTypes()
        .map { capType -> capType to capEpoch(capType) }
        .sortedBy { (_, capTime) -> capTime }
        .firstNotNullOfOrNull { (capType, capTime) ->
            if (capTime >= now) {
                TimingCueDisplay(
                    id = capType.timingCueId(),
                    message = capType.label,
                    remaining = Duration.ofMillis(capTime - now),
                    countdownTime = Duration.ZERO,
                    targetEpoch = capTime,
                )
            } else {
                null
            }
        }
}

/**
 * Format a local clock time for user-facing display.
 * For example, `3:30 PM`.
 *
 * @param time The local time value to show.
 */
fun formatClockTime(time: LocalTime): String {
    return time.format(DateTimeFormatter.ofPattern("h:mm a"))
}
/// List cap types that still affect the current game.
private fun LiveGameState.relevantCapTypes(): List<CapType> {
    return listOfNotNull(
        CapType.HALF.takeIf { halfCapRelevant(teamOne.score, teamTwo.score) },
        CapType.SOFT.takeIf { softCapRelevant() },
        CapType.HARD.takeIf { hardCapRelevant() },
    )
}
/**
 * Report whether half cap can still affect the halftime target.
 *
 * @param teamOneScore The score to evaluate for team one, often the post-goal score being considered.
 * @param teamTwoScore The score to evaluate for team two, often the post-goal score being considered.
 */
internal fun LiveGameState.halfCapRelevant(teamOneScore: Int, teamTwoScore: Int): Boolean {
    return rules.useHalfCap &&
        !halftimeTaken &&
        !halfCapApplied &&
        halfCapCanChangeHalftime(rules, teamOneScore, teamTwoScore)
}
/// Report whether soft cap is enabled and has not already been applied.
internal fun LiveGameState.softCapRelevant(): Boolean {
    return rules.useSoftCap && !softCapApplied
}
/// Report whether hard cap is enabled and has not already been applied.
internal fun LiveGameState.hardCapRelevant(): Boolean {
    return rules.useHardCap && !hardCapApplied
}
/**
 * Report whether half cap is both relevant and due at the supplied time.
 *
 * @param teamOneScore The score to evaluate for team one.
 * @param teamTwoScore The score to evaluate for team two.
 * @param now The epoch millis to compare with the scheduled half-cap time.
 */
internal fun LiveGameState.halfCapReached(
    teamOneScore: Int,
    teamTwoScore: Int,
    now: Long,
): Boolean {
    return halfCapRelevant(teamOneScore, teamTwoScore) &&
        now >= capEpoch(CapType.HALF)
}
/**
 * Report whether soft cap is both relevant and due at the supplied time.
 *
 * @param now The epoch millis to compare with the scheduled soft-cap time.
 */
internal fun LiveGameState.softCapReached(now: Long): Boolean {
    return softCapRelevant() &&
        now >= capEpoch(CapType.SOFT)
}
/**
 * Report whether hard cap is both relevant and due at the supplied time.
 *
 * @param now The epoch millis to compare with the scheduled hard-cap time.
 */
internal fun LiveGameState.hardCapReached(now: Long): Boolean {
    return hardCapRelevant() &&
        now >= capEpoch(CapType.HARD)
}
/**
 * Calculate the normal halftime target as the next count above half the game target.
 * For example, a game to 15 has a normal halftime target of 8.
 *
 * @param rules The active game rules that provide the game-to target.
 */
internal fun halftimeScore(rules: GameRules): Int {
    return (rules.gameTo / 2) + 1
}
/**
 * Report whether half cap can still change the normal halftime outcome.
 * Half cap stops mattering once any next point would necessarily leave the game at normal halftime.
 *
 * @param rules The active rules that define the normal halftime score.
 * @param teamOneScore The score to evaluate for team one.
 * @param teamTwoScore The score to evaluate for team two.
 */
private fun halfCapCanChangeHalftime(rules: GameRules, teamOneScore: Int, teamTwoScore: Int): Boolean {
    val normalHalftimeScore = halftimeScore(rules)
    return max(teamOneScore, teamTwoScore) < normalHalftimeScore - 1 &&
        min(teamOneScore, teamTwoScore) < normalHalftimeScore - 2
}
/**
 * Compute the scheduled epoch millis for a cap from the game start and rule offset.
 *
 * @param capType The cap whose configured offset should be used.
 */
internal fun LiveGameState.capEpoch(capType: CapType): Long {
    return startEpoch + capType.offsetMinutes(rules) * 60_000L
}
