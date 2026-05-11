package rmjarvis.ultiobserver

// Convert model events into UI-facing text for the current Android app.
fun formatGameEventMessage(state: LiveGameState, event: GameEvent?): String? {
    if (event == null) {
        return null
    }
    return when (event) {
        GameEvent.TimeoutUnavailable -> "Timeouts are not available now."
        is GameEvent.TeamOutOfTimeouts -> "${teamName(state, event.team)} is out of timeouts."
        is GameEvent.TeamCardsChanged -> formatTeamCardsChanged(state, event)
        is GameEvent.TechnicalFoulsChanged -> formatTechnicalFoulsChanged(state, event)
        is GameEvent.PullInfractionRecorded -> formatPullInfractionRecorded(event)
    }
}

// Does this event need the observer to choose offense/defense before showing the penalty cue?
fun GameEvent.needsLivePointMisconductChoice(state: LiveGameState): Boolean {
    return when (this) {
        is GameEvent.TeamCardsChanged -> teamCardTotal >= 3 && state.phase == LivePhase.LIVE_POINT
        is GameEvent.TechnicalFoulsChanged -> technicalFoulTotal >= 3 && state.phase == LivePhase.LIVE_POINT
        else -> false
    }
}

// Prompt shown when live-point misconduct needs an offense/defense choice.
fun livePointMisconductPrompt(state: LiveGameState, event: GameEvent): String {
    val baseMessage = formatGameEventMessage(state, event)!!
    return "$baseMessage\n\nWas this against the offense or defense?"
}

// Full live-point misconduct message after the observer chooses offense or defense.
fun livePointMisconductResolutionMessage(
    state: LiveGameState,
    event: GameEvent,
    againstOffense: Boolean,
): String {
    val baseMessage = formatGameEventMessage(state, event)!!
    return "$baseMessage\n\n${livePointMisconductMessage(againstOffense)}"
}

private fun formatTeamCardsChanged(state: LiveGameState, event: GameEvent.TeamCardsChanged): String {
    val baseMessage = if (event.secondYellowJerseyNumber == null) {
        "${teamName(state, event.team)} has ${event.teamCardTotal} ${pluralizeEvent(event.teamCardTotal, "card")}."
    } else {
        val ejectionMessage = if (event.secondYellowJerseyNumber == UNKNOWN_PLAYER_NUMBER) {
            "The player is ejected."
        } else {
            "Player ${event.secondYellowJerseyNumber} is ejected."
        }
        "Second yellow acts as a red card. $ejectionMessage\n" +
            "${teamName(state, event.team)} has ${event.teamCardTotal} ${pluralizeEvent(event.teamCardTotal, "card")}."
    }
    return appendMisconductCueIfNeeded(
        baseMessage = baseMessage,
        state = state,
        team = event.team,
        thresholdCount = event.teamCardTotal,
    )
}

private fun formatTechnicalFoulsChanged(state: LiveGameState, event: GameEvent.TechnicalFoulsChanged): String {
    val baseMessage =
        "${teamName(state, event.team)} has ${event.technicalFoulTotal} technical " +
            "${pluralizeEvent(event.technicalFoulTotal, "foul")}."
    return appendMisconductCueIfNeeded(
        baseMessage = baseMessage,
        state = state,
        team = event.team,
        thresholdCount = event.technicalFoulTotal,
    )
}

private fun formatPullInfractionRecorded(event: GameEvent.PullInfractionRecorded): String {
    return when (event.infraction) {
        PullInfractionType.OFFSIDES -> if (event.totalPullViolations <= 1) {
            "Start at brick mark"
        } else {
            "Start at midfield"
        }
        PullInfractionType.FALSE_START -> "Defense gets to set up."
    }
}

private fun appendMisconductCueIfNeeded(
    baseMessage: String,
    state: LiveGameState,
    team: TeamId,
    thresholdCount: Int,
): String {
    return if (thresholdCount < 3 || state.phase == LivePhase.LIVE_POINT) {
        baseMessage
    } else {
        "$baseMessage\n\n${betweenPointsMisconductMessage(state, team)}"
    }
}

private fun betweenPointsMisconductMessage(state: LiveGameState, team: TeamId): String {
    val receivingTeam = state.pullingTeam.flip()
    return if (team == receivingTeam) {
        "Penalty against receiving team. No pull. Disc at negative brick in defending end zone."
    } else {
        "Penalty against pulling team. No pull. Receiving team starts at attacking brick."
    }
}

private fun pluralizeEvent(count: Int, singular: String): String {
    return if (count == 1) singular else "${singular}s"
}
