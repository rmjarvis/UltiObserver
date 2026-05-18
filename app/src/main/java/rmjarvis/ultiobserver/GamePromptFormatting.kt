package rmjarvis.ultiobserver

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

/// Format the game-over prompt body with the winner first.
private fun GamePrompt.GameOver.formatMessage(): String {
    val orderedTeams = state.winnerFirstTeams()
    return buildString {
        appendLine("${orderedTeams[0].name} ${orderedTeams[0].score}")
        append("${orderedTeams[1].name} ${orderedTeams[1].score}")
    }
}
