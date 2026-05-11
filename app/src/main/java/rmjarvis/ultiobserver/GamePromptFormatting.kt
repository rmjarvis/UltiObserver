package rmjarvis.ultiobserver

import kotlin.math.max

// Title text for prompts that need a dialog title in the current Android app.
fun GamePrompt.formatTitle(): String? {
    return when (this) {
        is GamePrompt.ApplyCap -> "Apply ${capPromptLabel(this.capType)}?"
        is GamePrompt.LivePointMisconduct -> "Misconduct Penalty"
        is GamePrompt.HalftimeStarted -> null
        is GamePrompt.GameOver -> null
    }
}

// Main text shown to the observer for this prompt.
fun GamePrompt.formatMessage(): String {
    return when (this) {
        is GamePrompt.ApplyCap -> formatApplyCapPromptMessage(this.state, this.capType)
        is GamePrompt.LivePointMisconduct -> {
            val baseMessage = formatGameEventMessage(this.state, this.event)!!
            "$baseMessage\n\nWas this against the offense or defense?"
        }
        is GamePrompt.HalftimeStarted -> "Halftime"
        is GamePrompt.GameOver -> formatGameOverPromptMessage(this.state)
    }
}

// Full live-point misconduct message after the observer chooses offense or defense.
fun GamePrompt.LivePointMisconduct.formatResolutionMessage(againstOffense: Boolean): String {
    val baseMessage = formatGameEventMessage(this.state, this.event)!!
    return "$baseMessage\n\n${livePointMisconductMessage(againstOffense)}"
}

private fun capPromptLabel(capType: CapType): String {
    return when (capType) {
        CapType.HALF -> "half cap"
        CapType.SOFT -> "soft cap"
        CapType.HARD -> "hard cap"
    }
}

private fun formatApplyCapPromptMessage(state: LiveGameState, capType: CapType): String {
    val wasAt = if (state.phase == LivePhase.HALFTIME) "is scheduled for" else "was at"
    val endWhen = if (state.phase == LivePhase.HALFTIME) "during halftime" else "now"
    return when (capType) {
        CapType.HALF -> {
            val target = max(state.teamOne.score, state.teamTwo.score) + 1
            "Half cap was at ${formatCapClockTime(state, CapType.HALF)}. Halftime target would become $target. Apply now?"
        }
        CapType.SOFT -> {
            val target = max(state.teamOne.score, state.teamTwo.score) + 1
            "Soft cap $wasAt ${formatCapClockTime(state, CapType.SOFT)}. Winning score would become $target. Apply now?"
        }
        CapType.HARD -> {
            if (state.teamOne.score == state.teamTwo.score) {
                "Hard cap $wasAt ${formatCapClockTime(state, CapType.HARD)}. Score is tied, so one more point would be played. Apply now?"
            } else {
                "Hard cap $wasAt ${formatCapClockTime(state, CapType.HARD)}. Score is not tied, so the game would end $endWhen. Apply now?"
            }
        }
    }
}

private fun formatCapClockTime(state: LiveGameState, capType: CapType): String {
    return formatClockTime(localTimeFromEpoch(capEpoch(state, capType), state.timeZone))
}

private fun formatGameOverPromptMessage(state: LiveGameState): String {
    val orderedTeams = winnerFirstTeams(state)
    return buildString {
        appendLine("Game is over")
        appendLine("${orderedTeams[0].name} ${orderedTeams[0].score}")
        append("${orderedTeams[1].name} ${orderedTeams[1].score}")
    }
}

private fun livePointMisconductMessage(againstOffense: Boolean): String {
    return if (againstOffense) {
        "Misconduct penalty against offense.\nReverse brick. Offense 30 sec to set, defense 20 sec to check in. Defense may instead leave the disc where it stopped."
    } else {
        "Misconduct penalty against defense.\nBrick nearest attacking end zone. Offense 30 sec to set, defense 20 sec to check in. Offense may instead leave it or center it."
    }
}
