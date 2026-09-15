package rmjarvis.ultiobserver

import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import rmjarvis.ultiobserver.wearprotocol.WearActiveGameSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotPullDirection
import rmjarvis.ultiobserver.wearprotocol.WearTeamSnapshot

/** Convert one protocol game into the existing display-only main-screen model. */
internal fun WearActiveGameSnapshot.toGameDisplay(
    currentPhoneEpochMillis: Long,
    connected: Boolean,
): GameDisplay {
    val nextCap = upcomingCaps.firstOrNull { cap ->
        cap.targetEpochMillis >= currentPhoneEpochMillis
    }
    val countdownRemainingMillis = countdown?.let { state ->
        state.targetEpochMillis - (state.pausedAtEpochMillis ?: currentPhoneEpochMillis)
    }
    val nextCue = countdown?.cues?.firstOrNull { cue ->
        cue.targetEpochMillis >= currentPhoneEpochMillis
    }
    val statusMessage = statusMessageTransitions
        .lastOrNull { transition -> transition.targetEpochMillis <= currentPhoneEpochMillis }
        ?.message
    return GameDisplay(
        officialTime = formatOfficialTime(
            epochMillis = currentPhoneEpochMillis + officialClockOffsetMillis,
            timeZoneId = officialTimeZoneId,
        ),
        capStatus = nextCap?.let { cap ->
            val remainingMillis = cap.targetEpochMillis - currentPhoneEpochMillis
            "${cap.label} in ${formatDurationMillis(remainingMillis)}"
        },
        countdownActions = countdownActions,
        timingControls = timingControls,
        countdownLabel = countdown?.label.orEmpty(),
        countdownValue = if (gameOver) {
            null
        } else {
            countdownRemainingMillis?.let(::formatDurationMillis)
        },
        nextCue = if (gameOver) null else if (countdown?.pausedAtEpochMillis != null) "Paused"
            else nextCue?.let { cue -> "Next: ${cue.message}" },
        statusMessage = statusMessage.takeIf { countdown == null && countdownActions.isEmpty() },
        teamOne = teamOne.toTeamDisplay(),
        teamTwo = teamTwo.toTeamDisplay(),
        pullDirection = when (pullDirection) {
            WearSnapshotPullDirection.LEFT_TO_RIGHT -> PullDirection.LEFT_TO_RIGHT
            WearSnapshotPullDirection.RIGHT_TO_LEFT -> PullDirection.RIGHT_TO_LEFT
        },
        ratioBadge = ratio?.let { badge ->
            RatioBadgeDisplay(
                label = badge.label,
                backgroundColor = Color(badge.backgroundArgb),
                contentColor = Color(badge.contentArgb),
            )
        },
        connected = connected,
        actionsAvailable = actionsAvailable,
        gameOver = gameOver,
        undoDescription = undoDescription,
    )
}

private fun WearTeamSnapshot.toTeamDisplay(): TeamDisplay {
    return TeamDisplay(
        name = name,
        score = score,
        fieldEndName = fieldEndName,
        backgroundColor = Color(backgroundArgb),
        contentColor = Color(contentArgb),
    )
}

internal fun WearTeamSnapshot.toTeamActionsDisplay(actionsAvailable: Boolean): TeamActionsDisplay {
    return TeamActionsDisplay(
        team = toTeamDisplay(),
        timeViolationLabel = actions.timeViolationLabel,
        pullViolationLabel = actions.pullViolationLabel,
        cardLabel = actions.cardLabel,
        technicalFoulLabel = actions.technicalFoulLabel,
        timeoutLabel = actions.timeoutLabel,
        goalEnabled = actionsAvailable && actions.goalEnabled,
        timeViolationEnabled = actionsAvailable && actions.timeViolationEnabled,
        pullViolationEnabled = actionsAvailable && actions.pullViolationEnabled,
        cardEnabled = actionsAvailable && actions.cardEnabled,
        technicalFoulEnabled = actionsAvailable && actions.technicalFoulEnabled,
        timeoutEnabled = actionsAvailable && actions.timeoutEnabled,
    )
}

private fun formatOfficialTime(epochMillis: Long, timeZoneId: String): String {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.of(timeZoneId))
        .format(WATCH_TIME_FORMATTER)
}

private fun formatDurationMillis(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1_000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private val WATCH_TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm")
