package rmjarvis.ultiobserver

// UI-facing text for this model event in the current Android app.
fun GameEvent.formatMessage(): String {
    return when (this) {
        is GameEvent.TimeoutUnavailable -> "Timeouts are not available now."
        is GameEvent.TeamOutOfTimeouts -> "${this.state.teamName(this.team)} is out of timeouts."
        is GameEvent.TeamCardsChanged -> this.formatMessage()
        is GameEvent.TechnicalFoulsChanged -> this.formatMessage()
        is GameEvent.PullInfractionRecorded -> this.formatMessage()
        is GameEvent.TimeViolationRecorded -> this.formatMessage()
    }
}

// Title for event-driven popups.
fun GameEvent.formatPopupTitle(): String {
    return when (this) {
        is GameEvent.TimeoutUnavailable -> "Invalid Timeout"
        is GameEvent.TeamOutOfTimeouts -> "Invalid Timeout"
        is GameEvent.TeamCardsChanged -> if (teamCardTotal >= 3) "Misconduct Penalty" else "Misconduct"
        is GameEvent.TechnicalFoulsChanged -> if (technicalFoulTotal >= 3) "Misconduct Penalty" else "Misconduct"
        is GameEvent.PullInfractionRecorded -> "Pull Infraction"
        is GameEvent.TimeViolationRecorded -> "Time Violation"
    }
}

// Does this event need the observer to choose offense/defense before showing the penalty cue?
fun GameEvent.needsMisconductChoice(): Boolean {
    return when (this) {
        is GameEvent.TeamCardsChanged -> teamCardTotal >= 3 && state.phase == LivePhase.LIVE_POINT
        is GameEvent.TechnicalFoulsChanged -> technicalFoulTotal >= 3 && state.phase == LivePhase.LIVE_POINT
        else -> false
    }
}

private fun GameEvent.TeamCardsChanged.formatMessage(): String {
    val totalMessage = "${state.teamName(team)} has $teamCardTotal ${pluralize(teamCardTotal, "card")}."
    val baseMessage = when (playerCardType) {
        null -> totalMessage
        PlayerCardEventType.YELLOW -> "Yellow card on player ${playerCardJerseyNumber as String}.\n$totalMessage"
        PlayerCardEventType.RED -> "${ejectionMessage(playerCardJerseyNumber as String)}\n$totalMessage"
        PlayerCardEventType.SECOND_YELLOW -> {
            "Second yellow acts as a red card. ${ejectionMessage(playerCardJerseyNumber as String)}\n" +
                totalMessage
        }
    }
    return baseMessage.withMisconductCue(
        state = state,
        team = team,
        thresholdCount = teamCardTotal,
    )
}

private fun ejectionMessage(jerseyNumber: String): String {
    return if (jerseyNumber == UNKNOWN_PLAYER_NUMBER) {
        "The player is ejected."
    } else {
        "Player $jerseyNumber is ejected."
    }
}

private fun GameEvent.TechnicalFoulsChanged.formatMessage(): String {
    val baseMessage =
        "${state.teamName(team)} has $technicalFoulTotal technical " +
            "${pluralize(technicalFoulTotal, "foul")}."
    return baseMessage.withMisconductCue(
        state = state,
        team = team,
        thresholdCount = technicalFoulTotal,
    )
}

private fun GameEvent.PullInfractionRecorded.formatMessage(): String {
    return when (infraction) {
        PullInfractionType.OFFSIDES -> if (totalPullViolations <= 1) {
            "Start at brick mark"
        } else {
            "Start at midfield"
        }
        PullInfractionType.FALSE_START -> "Defense gets to set up."
    }
}

private fun GameEvent.TimeViolationRecorded.formatMessage(): String {
    return when (outcome) {
        TimeViolationOutcome.WARNING -> {
            if (team == state.pullingTeam) {
                "${state.teamName(team)} now has 30 seconds to pull."
            } else {
                "${state.teamName(team)} now has 30 seconds to signal readiness."
            }
        }
        TimeViolationOutcome.TIMEOUT -> "Timeout charged to ${state.teamName(team)}. Reset pull timing."
        TimeViolationOutcome.NO_TIMEOUT -> {
            if (team == state.pullingTeam.flip()) {
                "No timeouts remaining. No pull. Receiving team starts at midpoint of defending end zone."
            } else {
                "No timeouts remaining. No pull. Receiving team starts at midfield."
            }
        }
    }
}

private fun String.withMisconductCue(
    state: LiveGameState,
    team: TeamId,
    thresholdCount: Int,
): String {
    return if (thresholdCount < 3 || state.phase == LivePhase.LIVE_POINT) {
        this
    } else {
        "$this\n\n${state.betweenPointsMisconductCue(team)}"
    }
}

private fun LiveGameState.betweenPointsMisconductCue(team: TeamId): String {
    val receivingTeam = pullingTeam.flip()
    return if (team == receivingTeam) {
        "Penalty against receiving team. No pull. Disc at negative brick in defending end zone."
    } else {
        "Penalty against pulling team. No pull. Receiving team starts at attacking brick."
    }
}

private fun pluralize(count: Int, singular: String): String {
    return if (count == 1) singular else "${singular}s"
}
