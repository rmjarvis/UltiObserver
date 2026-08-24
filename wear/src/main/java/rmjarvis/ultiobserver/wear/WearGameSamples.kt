package rmjarvis.ultiobserver.wear

import androidx.compose.ui.graphics.Color

/** Representative Wear display snapshots used by previews and the pre-transport prototype app. */
internal object WearGameSamples {
    private val animal = WearTeamDisplay(
        name = "Animal",
        score = 8,
        backgroundColor = Color(0xFF2A5CAA),
        contentColor = Color(0xFFF7FAFF),
    )
    private val machine = WearTeamDisplay(
        name = "Machine",
        score = 7,
        backgroundColor = Color(0xFFE7A51E),
        contentColor = Color(0xFF2E2400),
    )

    val animalActions = WearTeamActionsDisplay(
        team = animal,
        timeViolationLabel = "Time viol.",
        pullViolationLabel = "Offsides",
        cardLabel = "Card",
        technicalFoulLabel = "Tech",
        timeoutLabel = "Timeout (2)",
    )

    val machineActions = WearTeamActionsDisplay(
        team = machine,
        timeViolationLabel = "Time viol.",
        pullViolationLabel = "False start",
        cardLabel = "Card",
        technicalFoulLabel = "Tech",
        timeoutLabel = "Timeout (2)",
    )

    val countdown = WearGameDisplay(
        officialTime = "12:47",
        capStatus = "Soft cap in 13:41",
        countdownLabel = "Pull in",
        countdownValue = "0:42",
        nextCue = "Next: 20 seconds to pull",
        teamOne = animal,
        teamTwo = machine,
        pullDirection = WearPullDirection.LEFT_TO_RIGHT,
        ratioBadge = WearRatioBadge(
            label = "M2",
            backgroundColor = Color(0xFF2A5CAA),
            contentColor = Color(0xFFF7FAFF),
        ),
        connected = true,
        undoDescription = "Undo goal by Animal",
    )

    val pointLive = countdown.copy(
        countdownLabel = "Point live",
        countdownValue = null,
        nextCue = null,
        ratioBadge = null,
        pullDirection = WearPullDirection.RIGHT_TO_LEFT,
    )

    val lostConnection = countdown.copy(connected = false)
}
