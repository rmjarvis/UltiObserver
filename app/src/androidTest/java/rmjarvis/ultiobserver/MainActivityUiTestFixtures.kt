package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeRight
import java.time.LocalTime
import org.junit.Rule

abstract class MainActivityUiTestFixtures {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    protected fun openNewGameSetup() {
        composeRule.onNodeWithText("Start New Game").performClick()
        waitForText("UltiObserver Setup")
    }

    protected fun startLiveGame() {
        openNewGameSetup()
        startGameFromSetup()
    }

    protected fun startGameFromSetup() {
        composeRule.onNodeWithText("Start Game").performClick()
        assertLiveScreen()
    }

    protected fun replaceSetupTeamName(fieldLabel: String, teamName: String) {
        composeRule.onNodeWithTag("setup-$fieldLabel-name").performScrollTo().performTextReplacement(teamName)
    }

    protected fun setIntegerSetupValue(
        buttonText: String,
        dialogTitle: String,
        fieldLabel: String,
        value: String,
    ) {
        openGameRulesSetupEditor()
        composeRule.onNodeWithText(buttonText).performScrollTo().performClick()
        waitForText(dialogTitle)
        composeRule.onNodeWithText(fieldLabel).performTextReplacement(value)
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        waitForText("Start Game")
    }

    protected fun setStartTime(startTime: LocalTime) {
        val hour = startTime.hour % 12
        val hourText = (if (hour == 0) 12 else hour).toString()
        val minuteText = startTime.minute.toString().padStart(2, '0')
        val period = if (startTime.hour >= 12) "PM" else "AM"

        openStartTimeSetupEditor()
        composeRule.onNodeWithTag("setup-start-time-field").performClick()
        waitForText("Set Start Time")
        composeRule.onNode(
            hasSetTextAction() and hasContentDescription("hour", substring = true, ignoreCase = true),
            useUnmergedTree = true,
        ).performTextReplacement(hourText)
        composeRule.onNode(
            hasSetTextAction() and hasContentDescription("minute", substring = true, ignoreCase = true),
            useUnmergedTree = true,
        ).performTextReplacement(minuteText)
        composeRule.onNodeWithText(period).performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Date")
        closeSetupEditor()
        waitForText("Start Game")
    }

    protected fun setStartTimeToRecentPast() {
        setStartTime(LocalTime.MIDNIGHT)
    }

    protected fun setStartTimeToFutureMinute() {
        setStartTime(LocalTime.now().plusMinutes(1))
    }

    protected fun setCapRuleValue(
        rowLabel: String,
        dialogTitle: String,
        value: String,
        enableFromNone: Boolean = false,
    ) {
        openGameRulesSetupEditor()
        composeRule.onNodeWithText(rowLabel).performScrollTo().performClick()
        waitForText(dialogTitle)
        if (enableFromNone) {
            composeRule.onNodeWithTag("setup-$dialogTitle-none").performClick()
        }
        composeRule.onNodeWithText("Minutes").performTextReplacement(value)
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        waitForText("Start Game")
    }

    protected fun setCapRuleToNone(rowLabel: String, dialogTitle: String) {
        openGameRulesSetupEditor()
        composeRule.onNodeWithText(rowLabel).performScrollTo().performClick()
        waitForText(dialogTitle)
        composeRule.onNodeWithTag("setup-$dialogTitle-none").performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        waitForText("Start Game")
    }

    protected fun setTimeoutRules(timeoutsPerHalf: String, hasFloater: Boolean) {
        openGameRulesSetupEditor()
        composeRule.onNodeWithText("Timeouts").performScrollTo().performClick()
        waitForText("Timeout Rules")
        composeRule.onNodeWithText("Timeouts per half").performTextReplacement(timeoutsPerHalf)
        if (hasFloater) {
            composeRule.onNodeWithTag("setup-timeouts-floater").performClick()
        }
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        waitForText("Start Game")
    }

    protected fun addPriorCardHolder(teamName: String, jersey: String, yellows: Int, reds: Int) {
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add Card Holder").performScrollTo().performClick()
        waitForText("Add player cards")
        composeRule.onNodeWithTag("setup-prior-card-team-${TeamId.TEAM_TWO.name}").performClick()
        composeRule.onNodeWithTag("setup-prior-card-jersey").performTextReplacement(jersey)
        repeat((yellows - 1).coerceAtLeast(0)) {
            composeRule.onAllNodesWithText("+1")[0].performClick()
        }
        repeat(reds.coerceAtLeast(0)) {
            composeRule.onAllNodesWithText("+1")[1].performClick()
        }
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("$teamName #$jersey").performScrollTo().assertIsDisplayed()
        closeSetupEditor()
        waitForText("Start Game")
    }

    protected fun startLiveGameWithDueCap(rowLabel: String, dialogTitle: String) {
        composeRule.onNodeWithText("Start New Game").performClick()
        waitForText("UltiObserver Setup")
        setStartTimeToRecentPast()
        setIntegerSetupValue("Game to", "Game To", "Points", "5")
        when (rowLabel) {
            "Half cap" -> {
                setCapRuleToNone("Soft cap", "Soft Cap")
                setCapRuleToNone("Hard cap", "Hard Cap")
            }
            "Soft cap" -> {
                setCapRuleToNone("Half cap", "Half Cap")
                setCapRuleToNone("Hard cap", "Hard Cap")
            }
            "Hard cap" -> {
                setCapRuleToNone("Half cap", "Half Cap")
                setCapRuleToNone("Soft cap", "Soft Cap")
            }
        }
        setCapRuleValue(rowLabel, dialogTitle, "0")
        startGameFromSetup()
    }

    protected fun startLiveGameWithCapDuringHalftime(rowLabel: String, dialogTitle: String) {
        composeRule.onNodeWithText("Start New Game").performClick()
        waitForText("UltiObserver Setup")
        setStartTimeToFutureMinute()
        setIntegerSetupValue("Game to", "Game To", "Points", "5")
        setIntegerSetupValue("Halftime", "Halftime", "Minutes", "7")
        setCapRuleValue(rowLabel, dialogTitle, "2")
        startGameFromSetup()
    }

    protected fun returnHomeFromGame() {
        pressAppBack()
        waitForText("Start New Game")
    }

    protected fun recordGoal(team: TeamId, undoLabel: String) {
        composeRule.onNodeWithTag(teamActionTag(team, "goal")).performClick()
        waitForText(undoLabel)
    }

    protected fun startPointAndUnlock() {
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")
        unlockLiveScreen()
    }

    protected fun startPointWithFailedSwipeThenUnlock() {
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag("live-unlock-slider").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(40f, 0f))
            cancel()
        }
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag("live-unlock-slider").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        waitForText("Slide right to unlock")
        unlockLiveScreen()
    }

    protected fun continuePointAndUnlock() {
        composeRule.onNodeWithText("Continue Point").performClick()
        waitForText("Slide right to unlock")
        unlockLiveScreen()
    }

    protected fun clearArchivedGamesProgrammatically() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.deleteAllArchivedGames()
        }
        composeRule.waitForIdle()
    }

    protected fun unlockLiveScreen() {
        composeRule.onNodeWithTag("live-unlock-slider").performTouchInput {
            swipeRight()
        }
        waitForText("Lock")
    }

    protected fun confirmDeleteWithSlider(dialogTitle: String = "Delete Game?") {
        waitForText(dialogTitle)
        composeRule.onNodeWithTag("confirm-delete-slider").performTouchInput {
            swipeRight()
        }
    }

    protected fun recordTimeout(team: TeamId, undoLabel: String) {
        composeRule.onNodeWithTag(teamActionTag(team, "timeout")).performClick()
        waitForText(undoLabel)
    }

    protected fun applyScoreAdjustment() {
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Score").performClick()
        waitForText("Adjust Score")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Score Adjustment")
    }

    protected fun applyTimeoutAdjustment() {
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Timeouts").performClick()
        waitForText("Adjust Timeouts")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Timeout Adjustment")
    }

    protected fun applyPullInfractionAdjustment() {
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Pull Infractions").performClick()
        waitForText("Adjust Pull Infractions")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("+1")[2].performClick()
        composeRule.onAllNodesWithText("+1")[3].performClick()
        composeRule.onAllNodesWithText("-1")[1].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Pull Infraction Adjustment")
    }

    protected fun applyNoOpCardAdjustment() {
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Cards / TF").performClick()
        waitForText("Adjust Cards / TF")
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Cards / TF Adjustment")
    }

    protected fun recordYellowCard(
        team: TeamId,
        playerNumber: String,
        expectedMessage: String,
        misconductChoice: String? = null,
        expectedMisconductMessage: String? = null,
        substring: Boolean = false,
    ) {
        openCardsSheet()
        composeRule.onAllNodesWithText("Yellow")[teamCardButtonIndex(team)].performClick()
        waitForText("Yellow Card")
        if (playerNumber.isBlank()) {
            composeRule.onNodeWithText("N/A").performClick()
        } else {
            enterCardPlayerNumber(playerNumber)
            composeRule.onNodeWithText("Record").performClick()
        }

        if (misconductChoice == null) {
            waitForText(expectedMessage, substring = substring)
        } else {
            waitForText("Misconduct Penalty")
            composeRule.onNodeWithText(misconductChoice).performClick()
            waitForText(expectedMisconductMessage ?: expectedMessage, substring = true)
        }
        composeRule.onNodeWithText("OK").performClick()
    }

    protected fun recordRedCard(team: TeamId, playerNumber: String, expectedMessage: String) {
        openCardsSheet()
        composeRule.onAllNodesWithText("Red")[teamCardButtonIndex(team)].performClick()
        waitForText("Red Card")
        enterCardPlayerNumber(playerNumber)
        composeRule.onNodeWithText("Record").performClick()
        waitForText(expectedMessage, substring = true)
        composeRule.onNodeWithText("OK").performClick()
    }

    protected fun enterCardPlayerNumber(playerNumber: String) {
        composeRule.onNodeWithTag("card-player-number").performTextReplacement(playerNumber)
    }

    protected fun recordBlueCard(team: TeamId, expectedMessage: String) {
        openCardsSheet()
        composeRule.onAllNodesWithText("Blue")[teamCardButtonIndex(team)].performClick()
        waitForText(expectedMessage)
        composeRule.onNodeWithText("OK").performClick()
    }

    protected fun recordTechnicalFoul(
        team: TeamId,
        expectedMessage: String,
        substring: Boolean = false,
    ) {
        openCardsSheet()
        composeRule.onAllNodesWithText("Tech")[teamCardButtonIndex(team)].performClick()
        waitForText(expectedMessage, substring = substring)
        composeRule.onNodeWithText("OK").performClick()
    }

    protected fun teamCardButtonIndex(team: TeamId): Int {
        return if (team == TeamId.TEAM_ONE) 0 else 1
    }

    protected fun assertLiveScreen() {
        waitForText("Cards / TF")
        composeRule.onNodeWithText("Cards / TF").assertIsDisplayed()
        composeRule.onNodeWithText("Other").assertIsDisplayed()
    }

    protected fun openSetupDialog(buttonText: String, dialogTitle: String) {
        openGameRulesSetupEditor()
        composeRule.onNodeWithText(buttonText).performScrollTo().performClick()
        waitForText(dialogTitle)
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Game to")
        closeSetupEditor()
    }

    protected fun openStartTimeSetupEditor() {
        composeRule.onNodeWithTag("setup-edit-start-time").performScrollTo().performClick()
        waitForText("Date")
    }

    protected fun openStartingPullSetupEditor() {
        composeRule.onNodeWithTag("setup-edit-starting-pull").performScrollTo().performClick()
        waitForText("Pulling team")
    }

    protected fun openGameRulesSetupEditor() {
        composeRule.onNodeWithTag("setup-edit-game-rules").performScrollTo().performClick()
        waitForText("Game to")
    }

    protected fun openPriorCardsSetupEditor() {
        composeRule.onNodeWithTag("setup-edit-prior-cards").performScrollTo().performClick()
        waitForText("Add Card Holder")
    }

    protected fun closeSetupEditor() {
        composeRule.onNodeWithText("Done").performClick()
    }

    protected fun openCardsSheet() {
        composeRule.onNodeWithText("Cards / TF").performClick()
        waitForText("Cards / Technical Fouls")
    }

    protected fun openOtherSheet() {
        composeRule.onAllNodesWithText("Other").onFirst().performClick()
        waitForText("Update Game Setup")
    }

    protected fun openOtherDialogAndCancel(label: String) {
        composeRule.onNodeWithText(label).performClick()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Update Game Setup")
    }

    protected fun pressAppBack() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    protected fun waitForText(text: String, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun teamActionTag(team: TeamId, action: String): String {
        return "live-${team.name}-$action"
    }
}
