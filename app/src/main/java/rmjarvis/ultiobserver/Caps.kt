package rmjarvis.ultiobserver

import java.time.Duration
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/**
 * Representation of the next relevant cap status shown on the live screen.
 *
 * @param label The user-facing cap label.
 * @param remaining The time remaining until that cap reaches its scheduled time.
 */
data class CapStatus(
    val label: String,
    val remaining: Duration,
)

/// Identity of the game cap rule being displayed, prompted, or applied.
@Serializable
enum class CapType {
    HALF,
    SOFT,
    HARD;

    val label: String
        get() = when (this) {
            HALF -> "Half cap"
            SOFT -> "Soft cap"
            HARD -> "Hard cap"
        }

    val titleLabel: String
        get() = label.replace(" cap", " Cap")

    /**
     * Return this cap's configured offset from game start.
     *
     * @param rules The rules that contain the cap offsets.
     */
    fun offsetMinutes(rules: GameRules): Int {
        return when (this) {
            HALF -> rules.halfCapMinutes
            SOFT -> rules.softCapMinutes
            HARD -> rules.hardCapMinutes
        }
    }

    /**
     * Return rules with this cap enabled while preserving the other rule values.
     *
     * @param rules The rule set to update.
     */
    fun rulesWithCapEnabled(rules: GameRules): GameRules {
        return when (this) {
            HALF -> rules.copy(useHalfCap = true)
            SOFT -> rules.copy(useSoftCap = true)
            HARD -> rules.copy(useHardCap = true)
        }
    }

    /// Return the timing cue id that announces this cap.
    fun timingCueId(): TimingCueId {
        return when (this) {
            HALF -> TimingCueId.HALF_CAP
            SOFT -> TimingCueId.SOFT_CAP
            HARD -> TimingCueId.HARD_CAP
        }
    }
}

/**
 * Reposition the selected cap so it is due at the current time.
 * This is the manual cap action from Other, rather than the normal scheduled cap prompt.
 *
 * @param capType The cap whose scheduled offset should be enabled and aligned to now.
 * @param now The epoch millis that should become the cap's scheduled instant.
 */
fun GameState.makeCapNow(
    capType: CapType,
    now: Long,
): GameState {
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
fun GameState.applyPendingCap(
    now: Long,
): GameState {
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
                    phase = GamePhase.GAME_OVER,
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
fun GameState.deferPendingCap(): GameState {
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
fun GameState.computeNextCapStatus(now: Long): CapStatus? {
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
internal fun GameState.dueCapTimingCue(now: Long): TimingCueDisplay? {
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
internal fun GameState.nextCapTimingCue(now: Long): TimingCueDisplay? {
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

/// List cap types that still affect the current game.
private fun GameState.relevantCapTypes(): List<CapType> {
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
internal fun GameState.halfCapRelevant(teamOneScore: Int, teamTwoScore: Int): Boolean {
    return rules.useHalfCap &&
        !halftimeTaken &&
        !halfCapApplied &&
        halfCapCanChangeHalftime(rules, teamOneScore, teamTwoScore)
}
/// Report whether soft cap is enabled and has not already been applied.
internal fun GameState.softCapRelevant(): Boolean {
    return rules.useSoftCap && !softCapApplied
}
/// Report whether hard cap is enabled and has not already been applied.
internal fun GameState.hardCapRelevant(): Boolean {
    return rules.useHardCap && !hardCapApplied
}
/**
 * Report whether half cap is both relevant and due at the supplied time.
 *
 * @param teamOneScore The score to evaluate for team one.
 * @param teamTwoScore The score to evaluate for team two.
 * @param now The epoch millis to compare with the scheduled half-cap time.
 */
internal fun GameState.halfCapReached(
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
internal fun GameState.softCapReached(now: Long): Boolean {
    return softCapRelevant() &&
        now >= capEpoch(CapType.SOFT)
}
/**
 * Report whether hard cap is both relevant and due at the supplied time.
 *
 * @param now The epoch millis to compare with the scheduled hard-cap time.
 */
internal fun GameState.hardCapReached(now: Long): Boolean {
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
internal fun GameState.capEpoch(capType: CapType): Long {
    return startEpoch + capType.offsetMinutes(rules) * 60_000L
}

/// Return the lower-case cap label used in an apply-cap prompt title.
private fun GamePrompt.ApplyCap.label(): String {
    return capType.label.lowercase()
}

/// Format the title for an apply-cap prompt.
internal fun GamePrompt.ApplyCap.formatTitle(): String = "Apply ${this.label()}?"

/// Format the prompt body for an offered cap.
internal fun GamePrompt.ApplyCap.formatMessage(): String {
    val wasAt = if (state.phase == GamePhase.HALFTIME) "is scheduled for" else "was at"
    val endWhen = if (state.phase == GamePhase.HALFTIME) "during halftime" else "now"
    return when (capType) {
        CapType.HALF -> {
            val target = max(state.teamOne.score, state.teamTwo.score) + 1
            "Half cap was at ${capClockTime()}. Halftime target would become $target. Apply now?"
        }
        CapType.SOFT -> {
            val target = max(state.teamOne.score, state.teamTwo.score) + 1
            "Soft cap $wasAt ${capClockTime()}. Winning score would become $target. Apply now?"
        }
        CapType.HARD -> {
            if (state.teamOne.score == state.teamTwo.score) {
                "Hard cap $wasAt ${capClockTime()}. Score is tied, so one more point would be played. Apply now?"
            } else {
                "Hard cap $wasAt ${capClockTime()}. Score is not tied, so the game would end $endWhen. Apply now?"
            }
        }
    }
}

/// Format the scheduled clock time for an offered cap.
private fun GamePrompt.ApplyCap.capClockTime(): String {
    return formatClockTime(localTimeFromEpoch(state.capEpoch(capType), state.timeZone))
}
