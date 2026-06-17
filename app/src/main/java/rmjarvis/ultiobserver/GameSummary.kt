package rmjarvis.ultiobserver

/**
 * Text content for the top card on a completed-game summary.
 *
 * @param title Heading for the summary card.
 * @param startLine Formatted scheduled start date and time.
 * @param endLine Formatted game end time.
 * @param scoreLines Winner-first team score lines.
 */
internal data class GameOverSummaryText(
    val title: String,
    val startLine: String,
    val endLine: String,
    val scoreLines: List<String>,
)

/**
 * Text content for one team's completed-game summary section.
 *
 * @param teamName Name of the team being summarized.
 * @param issuedCardLines Player yellow/red card lines, or a no-cards line.
 * @param blueCardsLine Team blue-card count line.
 * @param technicalFoulsLine Team technical-foul count line.
 */
internal data class GameOverTeamSummaryText(
    val teamName: String,
    val issuedCardLines: List<String>,
    val blueCardsLine: String,
    val technicalFoulsLine: String,
)

/**
 * Build text content for the top card on a completed-game summary.
 *
 * @receiver The completed game state to summarize.
 */
internal fun GameState.gameOverSummaryText(): GameOverSummaryText {
    val endTime = localTimeFromEpoch(endEpoch!!, timeZone)
    return GameOverSummaryText(
        title = "Game summary",
        startLine = "Start ${formatStartDate(startDate)} ${formatClockTime(startTime)}",
        endLine = "End time ${formatClockTime(endTime)}",
        scoreLines = winnerFirstTeams().map { team -> "${team.name} ${team.score}" },
    )
}

/**
 * Build text content for one team's completed-game summary section.
 *
 * @param teamId The team whose section should be summarized.
 */
internal fun GameState.gameOverTeamSummaryText(teamId: TeamId): GameOverTeamSummaryText {
    val team = teamFor(teamId)
    val issuedCardLines = playerCards(teamId)
        .flatMap { record -> record.summaryIssuedCardLines() }
        .takeIf { it.isNotEmpty() }
        ?: listOf("No yellow or red cards issued.")

    return GameOverTeamSummaryText(
        teamName = team.name,
        issuedCardLines = issuedCardLines,
        blueCardsLine = "Blue cards ${team.blueCards}",
        technicalFoulsLine = "Technical fouls ${team.technicalFouls}",
    )
}

/**
 * Build the compact text shared from a completed game summary.
 *
 * @receiver The completed game state to summarize.
 */
internal fun GameState.gameSummaryShareText(): String {
    val orderedTeams = winnerFirstTeams()
    val startLinePrefix = tournamentName.takeIf { it.isNotBlank() }?.let { "${it.trim()} - " } ?: ""
    val misconductLines = misconductShareLines()

    return buildList {
        add("UltiObserver Game Summary")
        add("$startLinePrefix${formatStartDate(startDate)}, ${formatClockTime(startTime)}")
        add(orderedTeams.joinToString(", ") { team -> "${team.name} ${team.score}" })
        if (misconductLines.isEmpty()) {
            add("No misconduct assessments")
        } else {
            add("Misconduct:")
            addAll(misconductLines)
        }
    }.joinToString("\n")
}

/// Return compact per-team misconduct lines for the share summary.
private fun GameState.misconductShareLines(): List<String> {
    return listOfNotNull(
        misconductShareLine(TeamId.TEAM_ONE),
        misconductShareLine(TeamId.TEAM_TWO),
    )
}

/**
 * Return one compact misconduct line for a team, or null when the team has no in-game misconduct.
 *
 * @param teamId The team whose misconduct should be summarized.
 */
private fun GameState.misconductShareLine(teamId: TeamId): String? {
    val team = teamFor(teamId)
    val playerParts = playerCards(teamId)
        .flatMap { it.shareLines() }
    val teamParts = buildList {
        if (team.blueCards > 0) {
            add("${team.blueCards} Blue")
        }
        if (team.technicalFouls > 0) {
            add(countedNounPhrase(team.technicalFouls, "Tech"))
        }
    }
    if (playerParts.isEmpty() && teamParts.isEmpty()) {
        return null
    }
    return buildList {
        add("  ${team.name}:")
        playerParts.forEach { detail ->
            add("    $detail")
        }
        if (teamParts.isNotEmpty()) {
            add("    ${teamParts.joinToString(", ")}")
        }
    }.joinToString("\n")
}

/// Return share-text lines for this player's in-game yellow/red card events.
private fun PlayerRecord.shareLines(): List<String> {
    return cards.map { card ->
        "${playerIdentity(compact = false)} ${card.shareLabel()}${card.shareReasonSuffix()}"
    }
}

/// Return game-over summary text lines for this player's in-game yellow/red card events.
private fun PlayerRecord.summaryIssuedCardLines(): List<String> {
    return cards.map { card ->
        "${playerIdentity(compact = false)}: ${card.summaryLabel()}${card.summaryReasonSuffix()}"
    }
}

/// Return the short share-text label for one player card.
private fun InGamePlayerCardEvent.shareLabel(): String {
    return cardType.label
}

/// Return the game-summary label for one player card.
private fun InGamePlayerCardEvent.summaryLabel(): String {
    return "${cardType.label} card"
}

/// Return the share-text reason suffix for one player card.
private fun InGamePlayerCardEvent.shareReasonSuffix(): String {
    return reason.text().takeIf { it.isNotEmpty() }?.let { " -- $it" }.orEmpty()
}

/// Return the game-summary reason suffix for one player card.
private fun InGamePlayerCardEvent.summaryReasonSuffix(): String {
    return reason.text().takeIf { it.isNotEmpty() }?.let { " -- $it" }.orEmpty()
}
