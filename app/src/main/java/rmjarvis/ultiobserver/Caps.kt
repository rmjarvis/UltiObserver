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

/// Return whether any enabled cap has an audible or haptic timing alert.
internal fun GameRules.hasEnabledCapTimingAlerts(timingAlertPreferences: TimingAlertPreferences): Boolean {
    return listOfNotNull(
        CapType.HALF.takeIf { capEnabled(CapType.HALF) },
        CapType.SOFT.takeIf { capEnabled(CapType.SOFT) },
        CapType.HARD.takeIf { capEnabled(CapType.HARD) },
    ).any { capType ->
        timingAlertPreferences.alertModeFor(capType.timingCueId()) != TimingAlertMode.NONE
    }
}

/**
 * Apply the selected cap immediately.
 * This is the manual cap action from More actions, rather than the normal scheduled cap prompt.
 *
 * @param capType The cap whose rule should be enabled and applied.
 * @param now The epoch millis used as the end time if hard cap immediately ends the game.
 */
fun GameState.makeCapNow(
    capType: CapType,
    now: Long,
): GameState {
    val capEnabledState = this.copy(
        rules = capType.rulesWithCapEnabled(rules),
        pendingCapOffer = null,
    )
    return capEnabledState.applyCap(
        capType = capType,
        now = now,
        undoPrevious = this,
        undoLabel = "Undo Apply ${capType.label.lowercase()} now",
    )
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
    val pendingCap = pendingCapOffer!!
    return applyCap(
        capType = pendingCap,
        now = now,
        undoPrevious = this,
        undoLabel = "Undo Apply ${pendingCap.label.lowercase()}",
    )
}

/**
 * Apply one cap to the current game state.
 *
 * @param capType The cap to apply.
 * @param now The epoch millis used as the end time if hard cap immediately ends the game.
 * @param undoPrevious The state that undo should restore.
 * @param undoLabel The undo label for the action that applied the cap.
 */
private fun GameState.applyCap(
    capType: CapType,
    now: Long,
    undoPrevious: GameState,
    undoLabel: String,
): GameState {
    val currentHigherScore = max(teamOne.score, teamTwo.score)
    return when (capType) {
        CapType.HALF -> this.copy(
            halftimeTargetScore = currentHigherScore + 1,
            halfCapApplied = true,
            pendingCapOffer = null,
        ).withUndo(undoPrevious, undoLabel)

        CapType.SOFT -> this.copy(
            winningScore = currentHigherScore + 1,
            softCapApplied = true,
            pendingCapOffer = null,
            pendingWaterBreakOffer = pendingWaterBreakOffer || softCapWaterBreakReached(),
        ).withUndo(undoPrevious, undoLabel)

        CapType.HARD -> {
            if (this.teamOne.score != this.teamTwo.score) {
                this.copy(
                    endEpoch = now,
                    phase = GamePhase.GAME_OVER,
                    countdown = null,
                    hardCapApplied = true,
                    pendingCapOffer = null,
                ).withUndo(undoPrevious, undoLabel)
            } else {
                val softCapTriggersWaterBreak =
                    softCapReached(teamOne.score, teamTwo.score, now) &&
                        softCapWaterBreakReached()
                this.copy(
                    winningScore = currentHigherScore + 1,
                    hardCapApplied = true,
                    pendingCapOffer = null,
                    pendingWaterBreakOffer =
                        pendingWaterBreakOffer || softCapTriggersWaterBreak,
                ).withUndo(undoPrevious, undoLabel)
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
        this.halfCapRelevant() to
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
 * Return the cap status message to show during a live point.
 *
 * A relevant passed cap reports that it will apply at point end. Before its scheduled time, the
 * latest cap made irrelevant by the current score reports that it no longer matters.
 *
 * @param now The current epoch millis used to compare against scheduled cap times.
 */
internal fun GameState.capStatusMessage(now: Long): String? {
    if (phase != GamePhase.LIVE_POINT) {
        return null
    }
    val passedCap = passedCapForPointEnd(now)
    if (passedCap != null) {
        return "${passedCap.label} passed. It will apply at the end of this point."
    }
    val irrelevantCap = CapType.entries
        .filter { capType -> capNoLongerRelevant(capType) && now < capEpoch(capType) }
        .maxByOrNull { capType -> capEpoch(capType) }
        ?: return null
    return "${irrelevantCap.label} is no longer relevant."
}

/// Report whether an enabled, unapplied cap can no longer affect the game.
private fun GameState.capNoLongerRelevant(capType: CapType): Boolean {
    return when (capType) {
        CapType.HALF -> rules.capEnabled(capType) &&
            !halftimeTaken &&
            !halfCapApplied &&
            !halfCapRelevant()
        CapType.SOFT -> rules.capEnabled(capType) &&
            !softCapApplied &&
            !softCapRelevant()
        CapType.HARD -> rules.capEnabled(capType) &&
            !hardCapApplied &&
            !hardCapRelevant()
    }
}

/**
 * Return the passed cap that should be offered at the end of the current live point.
 *
 * This mirrors point-end cap priority: hard cap takes precedence over soft cap, and soft cap
 * takes precedence over half cap.
 *
 * @param now The current epoch millis used to compare against scheduled cap times.
 */
private fun GameState.passedCapForPointEnd(now: Long): CapType? {
    return when {
        hardCapRelevant() &&
            now >= capEpoch(CapType.HARD) -> CapType.HARD
        softCapRelevant() &&
            now >= capEpoch(CapType.SOFT) -> CapType.SOFT
        halfCapRelevant() && now >= capEpoch(CapType.HALF) -> CapType.HALF
        else -> null
    }
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
    return upcomingCapTimingCues(now).firstOrNull()
}

/**
 * Return upcoming cap timing cues in scheduled order.
 *
 * @param now The current epoch millis used to exclude past cap times and compute time remaining.
 */
internal fun GameState.upcomingCapTimingCues(now: Long): List<TimingCueDisplay> {
    return relevantCapTypes()
        .map { capType -> capType to capEpoch(capType) }
        .sortedBy { (_, capTime) -> capTime }
        .mapNotNull { (capType, capTime) ->
            if (capTime > now) {
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
        CapType.HALF.takeIf { halfCapRelevant() },
        CapType.SOFT.takeIf { softCapRelevant() },
        CapType.HARD.takeIf { hardCapRelevant() },
    )
}
/**
 * Report whether half cap can still affect the halftime target.
 *
 */
internal fun GameState.halfCapRelevant(): Boolean {
    return rules.capEnabled(CapType.HALF) &&
        !halftimeTaken &&
        !halfCapApplied &&
        halfCapCanChangeHalftime(rules, teamOne.score, teamTwo.score)
}
/** Report whether soft cap can still lower the winning score after the current point. */
internal fun GameState.softCapRelevant(): Boolean {
    return rules.capEnabled(CapType.SOFT) &&
        !softCapApplied &&
        softCapCanChangeWinningScore(teamOne.score, teamTwo.score)
}
/** Report whether hard cap can still change play after the current point. */
internal fun GameState.hardCapRelevant(): Boolean {
    return rules.capEnabled(CapType.HARD) &&
        !hardCapApplied &&
        hardCapCanChangeGameOutcome(teamOne.score, teamTwo.score)
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
    return rules.capEnabled(CapType.HALF) &&
        !halftimeTaken &&
        !halfCapApplied &&
        halfCapCanChangeHalftimeNow(rules, teamOneScore, teamTwoScore) &&
        now >= capEpoch(CapType.HALF)
}
/**
 * Report whether applying soft cap to the supplied completed-point score would matter and is due.
 *
 * @param now The epoch millis to compare with the scheduled soft-cap time.
 */
internal fun GameState.softCapReached(
    teamOneScore: Int,
    teamTwoScore: Int,
    now: Long,
): Boolean {
    return rules.capEnabled(CapType.SOFT) &&
        !softCapApplied &&
        softCapCanChangeWinningScoreNow(teamOneScore, teamTwoScore) &&
        now >= capEpoch(CapType.SOFT)
}
/**
 * Report whether applying hard cap to the supplied completed-point score would matter and is due.
 *
 * @param now The epoch millis to compare with the scheduled hard-cap time.
 */
internal fun GameState.hardCapReached(
    teamOneScore: Int,
    teamTwoScore: Int,
    now: Long,
): Boolean {
    return rules.capEnabled(CapType.HARD) &&
        !isUniversePoint(teamOneScore, teamTwoScore) &&
        now >= capEpoch(CapType.HARD)
}

/// Report whether soft cap could lower the current winning score after the next point.
private fun GameState.softCapCanChangeWinningScore(
    teamOneScore: Int,
    teamTwoScore: Int,
): Boolean {
    val currentWinningScore = winningScore ?: rules.gameTo
    return max(teamOneScore, teamTwoScore) != currentWinningScore - 1 &&
        !(teamOneScore == teamTwoScore && teamOneScore == currentWinningScore - 2)
}

/// Report whether applying soft cap to this completed-point score would lower the winning score.
private fun GameState.softCapCanChangeWinningScoreNow(
    teamOneScore: Int,
    teamTwoScore: Int,
): Boolean {
    return max(teamOneScore, teamTwoScore) < rules.gameTo - 1
}

/// Report whether hard cap could change the game outcome after the next point.
private fun GameState.hardCapCanChangeGameOutcome(
    teamOneScore: Int,
    teamTwoScore: Int,
): Boolean {
    val currentWinningScore = winningScore ?: rules.gameTo
    return max(teamOneScore, teamTwoScore) != currentWinningScore - 1 ||
        min(teamOneScore, teamTwoScore) < currentWinningScore - 2
}

/// Report whether this score is already universe point under the current winning score.
private fun GameState.isUniversePoint(teamOneScore: Int, teamTwoScore: Int): Boolean {
    return teamOneScore == teamTwoScore &&
        teamOneScore == (winningScore ?: rules.gameTo) - 1
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

/// Report whether applying half cap to this completed-point score would lower halftime.
private fun halfCapCanChangeHalftimeNow(
    rules: GameRules,
    teamOneScore: Int,
    teamTwoScore: Int,
): Boolean {
    return max(teamOneScore, teamTwoScore) < halftimeScore(rules) - 1
}
/**
 * Compute the scheduled epoch millis for a cap from the game start and rule offset.
 *
 * @param capType The cap whose configured offset should be used.
 */
internal fun GameState.capEpoch(capType: CapType): Long {
    return startEpoch + rules.capMinutes(capType) * 60_000L
}

/// Format the declarative title for a due-cap prompt.
internal fun GamePrompt.ApplyCap.formatTitle(): String = capType.label

/// Format the prompt body for an offered cap.
internal fun GamePrompt.ApplyCap.formatMessage(): RuleGuidanceMessage {
    val capTime = capClockTime()
    val scheduledDuringHalftime = isScheduledDuringHalftime()
    val timingSentence = if (scheduledDuringHalftime) {
        "${capType.label} is scheduled for $capTime, which is during halftime, " +
            "so we can apply it now."
    } else {
        "${capType.label} was at $capTime, so it applies now."
    }
    val text = when (capType) {
        CapType.HALF -> {
            val target = max(state.teamOne.score, state.teamTwo.score) + 1
            "$timingSentence The new halftime target is $target."
        }
        CapType.SOFT -> {
            val target = max(state.teamOne.score, state.teamTwo.score) + 1
            "$timingSentence The new winning score is $target."
        }
        CapType.HARD -> {
            if (state.teamOne.score == state.teamTwo.score) {
                "$timingSentence Score is tied, so play one more point."
            } else {
                "$timingSentence Score is not tied, so the game is over."
            }
        }
    }
    return RuleGuidanceMessage(listOf(RuleGuidanceLine(text)))
}

/// Report whether this cap falls after halftime began but before its countdown ends.
private fun GamePrompt.ApplyCap.isScheduledDuringHalftime(): Boolean {
    if (state.phase != GamePhase.HALFTIME) {
        return false
    }
    val halftimeStart = state.countdown!!.targetEpoch -
        state.rules.halftimeMinutes * 60_000L
    return state.capEpoch(capType) > halftimeStart
}

/// Format the scheduled clock time for an offered cap.
private fun GamePrompt.ApplyCap.capClockTime(): String {
    return state.formatOfficialGameTime(state.capEpoch(capType))
}
