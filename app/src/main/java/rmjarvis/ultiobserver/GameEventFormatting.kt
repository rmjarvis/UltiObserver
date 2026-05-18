package rmjarvis.ultiobserver

/// Format UI-facing text for a model event in the current Android app.
fun GameEvent.formatMessage(): String {
    // This when block does runtime resolution to call the correct subtype's extension function.
    return when (this) {
        is GameEvent.TimeoutCharged -> this.formatMessage()
        is GameEvent.TimeoutUnavailable -> this.formatMessage()
        is GameEvent.TeamOutOfTimeouts -> this.formatMessage()
        is GameEvent.TeamCardsChanged -> this.formatMessage()
        is GameEvent.TechnicalFoulsChanged -> this.formatMessage()
        is GameEvent.PullInfractionRecorded -> this.formatMessage()
        is GameEvent.TimeViolationRecorded -> this.formatMessage()
    }
}

/// Format the title for an event-driven popup.
fun GameEvent.formatPopupTitle(): String {
    // This when block does runtime resolution to call the correct subtype's extension function.
    return when (this) {
        is GameEvent.TimeoutCharged -> this.formatPopupTitle()
        is GameEvent.TimeoutUnavailable -> this.formatPopupTitle()
        is GameEvent.TeamOutOfTimeouts -> this.formatPopupTitle()
        is GameEvent.TeamCardsChanged -> if (teamCardTotal >= 3) "Misconduct Penalty" else "Misconduct"
        is GameEvent.TechnicalFoulsChanged -> if (technicalFoulTotal >= 3) "Misconduct Penalty" else "Misconduct"
        is GameEvent.PullInfractionRecorded -> this.formatPopupTitle()
        is GameEvent.TimeViolationRecorded -> "Time Violation"
    }
}

/// Report whether this event needs an offense/defense choice before showing the penalty cue.
fun GameEvent.needsMisconductChoice(): Boolean {
    return when (this) {
        is GameEvent.TeamCardsChanged -> teamCardTotal >= 3 && state.phase == LivePhase.LIVE_POINT
        is GameEvent.TechnicalFoulsChanged -> technicalFoulTotal >= 3 && state.phase == LivePhase.LIVE_POINT
        else -> false
    }
}

/// Format a team-card event message, including player-card and misconduct cue details.
private fun GameEvent.TeamCardsChanged.formatMessage(): String {
    val totalMessage = "${state.teamName(team)} has $teamCardTotal ${pluralize(teamCardTotal, "card")}."
    val baseMessage = if (playerCardType == null) {
        totalMessage
    } else {
        val jerseyNumber = playerCardJerseyNumber as String
        (playerCardEventLines(playerCardType, jerseyNumber) + totalMessage).joinToString("\n")
    }
    return baseMessage.withMisconductCue(
        state = state,
        team = team,
        thresholdCount = teamCardTotal,
    )
}

/**
 * Build the player-specific message lines for a yellow, red, or second-yellow event.
 *
 * @param playerCardType The player-card event type to describe.
 * @param jerseyNumber The player number, or the unknown-player sentinel.
 */
private fun GameEvent.TeamCardsChanged.playerCardEventLines(
    playerCardType: PlayerCardEventType,
    jerseyNumber: String,
): List<String> {
    return buildList {
        val hasTournamentSuspension = state.playerHasTournamentSuspension(team, jerseyNumber)
        when (playerCardType) {
            PlayerCardEventType.YELLOW -> add("Yellow card on ${playerReference(jerseyNumber)}.")
            PlayerCardEventType.RED -> {
                add("Red card on ${playerReference(jerseyNumber)}.")
                if (!hasTournamentSuspension) {
                    add("${playerSentenceSubject(jerseyNumber)} receives a game suspension.")
                }
            }
            PlayerCardEventType.SECOND_YELLOW -> {
                add("Second yellow on ${playerReference(jerseyNumber)}.")
                if (!hasTournamentSuspension) {
                    add("${playerSentenceSubject(jerseyNumber)} receives a game suspension.")
                }
            }
        }
        if (playerCardType != PlayerCardEventType.YELLOW &&
            state.gameSuspensionStartedInSecondHalf() &&
            !hasTournamentSuspension
        ) {
            add("${playerSentenceSubject(jerseyNumber)} must also sit out the first half of the next game, if there is one.")
        }
        if (hasTournamentSuspension) {
            add("${playerSentenceSubject(jerseyNumber)} is suspended for the rest of the tournament.")
        }
    }
}

/**
 * Format a player reference for use in the middle of a sentence.
 *
 * @param jerseyNumber The player number, or the unknown-player sentinel.
 */
private fun playerReference(jerseyNumber: String): String {
    return if (jerseyNumber == UNKNOWN_PLAYER_NUMBER) "player N/A" else "player $jerseyNumber"
}

/**
 * Format a player reference for use as the subject of a sentence.
 *
 * @param jerseyNumber The player number, or the unknown-player sentinel.
 */
private fun playerSentenceSubject(jerseyNumber: String): String {
    return if (jerseyNumber == UNKNOWN_PLAYER_NUMBER) "The player" else "Player $jerseyNumber"
}

/// Report whether a game suspension started in the second half or later.
private fun LiveGameState.gameSuspensionStartedInSecondHalf(): Boolean {
    return halftimeTaken
}

/**
 * Report whether the player's prior and in-game cards reach tournament suspension thresholds.
 *
 * @param team The player's team.
 * @param jerseyNumber The player number, or the unknown-player sentinel.
 */
private fun LiveGameState.playerHasTournamentSuspension(team: TeamId, jerseyNumber: String): Boolean {
    val priorYellows = priorCards
        .filter { it.team == team && it.jerseyNumber == jerseyNumber }
        .sumOf { it.priorYellows }
    val priorReds = priorCards
        .filter { it.team == team && it.jerseyNumber == jerseyNumber }
        .sumOf { it.priorReds }
    val inGameRecord = playerCards(team).firstOrNull { it.jerseyNumber == jerseyNumber }
    val totalYellows = priorYellows + (inGameRecord?.yellows ?: 0)
    val totalReds = priorReds + (inGameRecord?.reds ?: 0)
    return totalYellows + 2 * totalReds >= 3
}

/// Format a technical-foul event message, including misconduct cue details when needed.
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

/// Format a time-violation event message with warning, timeout, or no-timeout consequences.
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

/**
 * Append a between-points misconduct cue when a threshold event has an immediate no-pull consequence.
 *
 * @param state The live state after the threshold event.
 * @param team The team that reached the threshold.
 * @param thresholdCount The team-card or technical-foul count after the event.
 */
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

/**
 * Format the between-points misconduct consequence for the penalized team.
 *
 * @param team The team that reached the misconduct threshold.
 */
private fun LiveGameState.betweenPointsMisconductCue(team: TeamId): String {
    val receivingTeam = pullingTeam.flip()
    return if (team == receivingTeam) {
        "Penalty against receiving team. No pull. Disc at negative brick in defending end zone."
    } else {
        "Penalty against pulling team. No pull. Receiving team starts at attacking brick."
    }
}

/**
 * Return a singular or plural noun for a count.
 *
 * @param count The count controlling pluralization.
 * @param singular The singular noun form.
 */
internal fun pluralize(count: Int, singular: String): String {
    return if (count == 1) singular else "${singular}s"
}
