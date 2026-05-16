package rmjarvis.ultiobserver

import kotlin.math.max

// Title text for prompts that need a dialog title in the current Android app.
fun GamePrompt.formatTitle(): String {
    return when (this) {
        is GamePrompt.ApplyCap -> "Apply ${this.label()}?"
        is GamePrompt.LivePointMisconduct -> "Misconduct Penalty"
        is GamePrompt.HalftimeStarted -> "Halftime"
        is GamePrompt.GameOver -> "Game Over"
    }
}

// Main text shown to the observer for this prompt.
fun GamePrompt.formatMessage(): String {
    return when (this) {
        is GamePrompt.ApplyCap -> this.formatMessage()
        is GamePrompt.LivePointMisconduct -> this.formatMessage()
        is GamePrompt.HalftimeStarted -> "Announce halftime."
        is GamePrompt.GameOver -> this.formatMessage()
    }
}

// Full live-point misconduct message after the observer chooses offense or defense.
fun GamePrompt.LivePointMisconduct.resolutionMessage(againstOffense: Boolean): String {
    val baseMessage = this.event.formatMessage()
    return "$baseMessage\n\n${misconductResolution(againstOffense)}"
}

private fun GamePrompt.ApplyCap.label(): String {
    return capType.label.lowercase()
}

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

private fun GamePrompt.LivePointMisconduct.formatMessage(): String {
    val baseMessage = event.formatMessage()
    return "$baseMessage\n\nWas this against the offense or defense?"
}

private fun GamePrompt.ApplyCap.capClockTime(): String {
    return formatClockTime(localTimeFromEpoch(state.capEpoch(capType), state.timeZone))
}

private fun GamePrompt.GameOver.formatMessage(): String {
    val orderedTeams = state.winnerFirstTeams()
    return buildString {
        appendLine("${orderedTeams[0].name} ${orderedTeams[0].score}")
        append("${orderedTeams[1].name} ${orderedTeams[1].score}")
    }
}

private fun misconductResolution(againstOffense: Boolean): String {
    return if (againstOffense) {
        "Misconduct penalty against offense.\nReverse brick. Defense may instead leave the disc where it stopped.\n\n" +
            "Offense has 30 seconds to set. Then defense has 20 seconds to check the disc in."
    } else {
        "Misconduct penalty against defense.\nBrick nearest attacking end zone. Offense may instead leave it or center it.\n\n" +
            "Offense has 30 seconds to set. Then defense has 20 seconds to check the disc in."
    }
}
