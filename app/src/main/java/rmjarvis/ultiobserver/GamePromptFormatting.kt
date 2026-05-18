package rmjarvis.ultiobserver

import kotlin.math.max

/// Format title text for prompts that need a dialog title in the current Android app.
fun GamePrompt.formatTitle(): String {
    return when (this) {
        is GamePrompt.ApplyCap -> "Apply ${this.label()}?"
        is GamePrompt.LivePointMisconduct -> this.formatTitle()
        is GamePrompt.HalftimeStarted -> "Halftime"
        is GamePrompt.GameOver -> "Game Over"
    }
}

/// Format the main text shown to the observer for a prompt.
fun GamePrompt.formatMessage(): String {
    return when (this) {
        is GamePrompt.ApplyCap -> this.formatMessage()
        is GamePrompt.LivePointMisconduct -> this.formatMessage()
        is GamePrompt.HalftimeStarted -> "Announce halftime."
        is GamePrompt.GameOver -> this.formatMessage()
    }
}

/// Return the lower-case cap label used in an apply-cap prompt title.
private fun GamePrompt.ApplyCap.label(): String {
    return capType.label.lowercase()
}

/// Format the prompt body for an offered cap.
private fun GamePrompt.ApplyCap.formatMessage(): String {
    val wasAt = if (state.phase == LivePhase.HALFTIME) "is scheduled for" else "was at"
    val endWhen = if (state.phase == LivePhase.HALFTIME) "during halftime" else "now"
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

/// Format the game-over prompt body with the winner first.
private fun GamePrompt.GameOver.formatMessage(): String {
    val orderedTeams = state.winnerFirstTeams()
    return buildString {
        appendLine("${orderedTeams[0].name} ${orderedTeams[0].score}")
        append("${orderedTeams[1].name} ${orderedTeams[1].score}")
    }
}
