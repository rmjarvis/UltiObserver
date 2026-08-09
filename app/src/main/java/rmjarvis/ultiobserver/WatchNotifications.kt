package rmjarvis.ultiobserver

private const val WATCH_TEAM_NAME_MAX_CHARS = 10

/** Standard notification title and optional body ready for Android rendering. */
internal data class WatchNotificationContent(
    val title: String,
    val body: String?,
)

/** Build the compact watch score line for the current game. */
internal fun GameState.watchNotificationScoreLine(settings: Settings): String {
    var scoreLine = when {
        teamOne.score > teamTwo.score -> {
            "${teamOne.score}-${teamTwo.score} " +
                teamOne.name.watchNotificationName()
        }
        teamTwo.score > teamOne.score -> {
            "${teamTwo.score}-${teamOne.score} " +
                teamTwo.name.watchNotificationName()
        }
        else -> "${teamOne.score}-${teamTwo.score} tied"
    }
    if (division == GameDivision.MIXED && rules.genderRatioRule == GenderRatioRule.ABBA) {
        scoreLine = scoreLine + " • ${currentGenderRatioBadgeText(settings.showAbbaRatioAsSequence)}"
    }
    return scoreLine
}

/** Combine the score with the next scheduled cue, when one exists. */
internal fun watchNotificationContent(
    scoreLine: String,
    nextCueText: String?,
): WatchNotificationContent {
    if (nextCueText != null) {
        return WatchNotificationContent(
            title = "Next cue: $nextCueText",
            body = scoreLine,
        )
    } else {
        return WatchNotificationContent(
            title = scoreLine,
            body = null,
        )
    }
}

/** Limit a team name by characters and append one compact ellipsis when needed. */
private fun String.watchNotificationName(): String {
    val charCount = codePointCount(0, length)
    if (charCount <= WATCH_TEAM_NAME_MAX_CHARS) {
        return this
    } else {
        val endIndex = offsetByCodePoints(0, WATCH_TEAM_NAME_MAX_CHARS)
        return substring(0, endIndex) + "…"
    }
}
