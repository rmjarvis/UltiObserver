package rmjarvis.ultiobserver

// UI-facing text for this model event in the current Android app.
fun GameEvent.formatMessage(): String {
    return when (this) {
        is GameEvent.TimeoutUnavailable -> "Timeouts are not available now."
        is GameEvent.TeamOutOfTimeouts -> "${this.state.teamName(this.team)} is out of timeouts."
        is GameEvent.TeamCardsChanged -> this.formatMessage()
        is GameEvent.TechnicalFoulsChanged -> this.formatMessage()
        is GameEvent.PullInfractionRecorded -> this.formatMessage()
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
    val baseMessage = if (secondYellowJerseyNumber == null) {
        "${state.teamName(team)} has $teamCardTotal ${pluralize(teamCardTotal, "card")}."
    } else {
        val ejectionMessage = if (secondYellowJerseyNumber == UNKNOWN_PLAYER_NUMBER) {
            "The player is ejected."
        } else {
            "Player $secondYellowJerseyNumber is ejected."
        }
        "Second yellow acts as a red card. $ejectionMessage\n" +
            "${state.teamName(team)} has $teamCardTotal ${pluralize(teamCardTotal, "card")}."
    }
    return baseMessage.withMisconductCue(
        state = state,
        team = team,
        thresholdCount = teamCardTotal,
    )
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
