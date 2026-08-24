package rmjarvis.ultiobserver.wear

import androidx.compose.runtime.Composable
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import androidx.wear.compose.ui.tooling.preview.WearPreviewLargeRound

@WearPreviewDevices
@Composable
private fun CountdownGamePreview() {
    WearGameScreen(
        display = WearGameSamples.countdown,
        onTeamOne = {},
        onTeamTwo = {},
        onUndo = {},
    )
}

@WearPreviewLargeRound
@Composable
private fun PointLivePreview() {
    WearGameScreen(
        display = WearGameSamples.pointLive,
        onTeamOne = {},
        onTeamTwo = {},
        onUndo = {},
    )
}

@WearPreviewLargeRound
@Composable
private fun LostConnectionPreview() {
    WearGameScreen(
        display = WearGameSamples.lostConnection,
        onTeamOne = {},
        onTeamTwo = {},
        onUndo = {},
    )
}

@WearPreviewFontScales
@Composable
private fun CountdownFontScalePreview() {
    WearGameScreen(
        display = WearGameSamples.countdown,
        onTeamOne = {},
        onTeamTwo = {},
        onUndo = {},
    )
}

@WearPreviewDevices
@Composable
private fun TeamActionsPreview() {
    WearTeamActionsScreen(
        display = WearGameSamples.animalActions,
        onGoal = {},
        onTimeViolation = {},
        onPullViolation = {},
        onCard = {},
        onTechnicalFoul = {},
        onTimeout = {},
        onCancel = {},
    )
}
