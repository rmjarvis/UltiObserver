package rmjarvis.ultiobserver

/**
 * Build the compact text shared from a completed game summary.
 *
 * @receiver The completed game state to summarize.
 */
internal fun LiveGameState.gameSummaryShareText(): String {
    val orderedTeams = winnerFirstTeams()
    val startLinePrefix = tournamentName.takeIf { it.isNotBlank() }?.let { "${it.trim()} - " } ?: ""
    val misconductLines = misconductShareLines()

    return buildList {
        add("UltiObserver Game Summary")
        add("$startLinePrefix${formatStartDate(startDate)}, ${formatClockTime(startTime)}")
        add(orderedTeams.joinToString(", ") { team -> "${team.name} ${team.score}" })
        if (misconductLines.isEmpty()) {
            add("No Misconduct Assessments")
        } else {
            add("Misconduct:")
            addAll(misconductLines)
        }
    }.joinToString("\n")
}

/// Return compact per-team misconduct lines for the share summary.
private fun LiveGameState.misconductShareLines(): List<String> {
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
private fun LiveGameState.misconductShareLine(teamId: TeamId): String? {
    val team = teamFor(teamId)
    val playerParts = playerCards(teamId)
        .filter { it.yellows > 0 || it.reds > 0 }
        .map { it.shareText() }
    val teamParts = buildList {
        if (team.blueCards > 0) {
            add("${team.blueCards} Blue")
        }
        if (team.technicalFouls > 0) {
            add("${team.technicalFouls} TF")
        }
    }
    if (playerParts.isEmpty() && teamParts.isEmpty()) {
        return null
    }
    val suffix = if (playerParts.isEmpty()) {
        teamParts.joinToString(", ")
    } else if (teamParts.isEmpty()) {
        playerParts.joinToString(", ")
    } else {
        "${playerParts.joinToString(", ")} + ${teamParts.joinToString(", ")}"
    }
    return "  ${team.name} $suffix"
}

/// Return compact share text for one player's issued yellow/red cards.
private fun InGamePlayerCardRecord.shareText(): String {
    val cardText = listOfNotNull(
        when (yellows) {
            1 -> "Y"
            2 -> "2Y"
            else -> null
        },
        "R".takeIf { reds == 1 },
    ).joinToString("+")
    return "${displayPlayerNumber(jerseyNumber)} ($cardText)"
}
