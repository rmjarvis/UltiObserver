package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestLiveGameFlowUi : MainActivityUiTestFixtures() {
    // Test a representative complete game from setup through halftime to final score.
    // Keep this as a user-visible UI story that checks flow, not detailed model accounting.
    @Test
    fun normalGamePath() {
        val viscousCoupling = "Viscous Coupling"
        val animal = "Animal"

        // Set up a short non-default game so the UI story covers setup editing,
        // halftime, and game over without a long repetitive scoring sequence.
        openNewGameSetup()
        replaceSetupTeamName("Team 1", viscousCoupling)
        replaceSetupTeamName("Team 2", animal)
        setIntegerSetupValue("Game to", "Game To", "Points", "5")
        setIntegerSetupValue("Halftime", "Halftime", "Minutes", "1")
        setCapRuleToNone("Half cap", "Half Cap")
        setCapRuleToNone("Soft cap", "Soft Cap")
        setCapRuleToNone("Hard cap", "Hard Cap")
        composeRule.onNodeWithText("Near end").performScrollTo().performClick()
        startGameFromSetup()
        composeRule.onNodeWithText(viscousCoupling).assertIsDisplayed()
        composeRule.onNodeWithText(animal).assertIsDisplayed()

        // The opening pull starts the first live point; a short swipe should fail before a full unlock.
        startPointWithFailedSwipeThenUnlock()

        // The top-right Lock action should relock the same live layout.
        composeRule.onNodeWithTag("live-top-lock").performClick()
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).assertIsNotEnabled()
        unlockLiveScreen()

        // The center field Lock action should also relock the screen during a live point.
        composeRule.onNodeWithTag("live-center-lock").performClick()
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "timeout")).assertIsNotEnabled()
        unlockLiveScreen()

        // Animal calls a live-point timeout, then play resumes from the timeout countdown.
        recordTimeout(TeamId.TEAM_TWO, "Undo Timeout by $animal")
        continuePointAndUnlock()

        // Viscous Coupling gets two early card points, then a third card that needs a misconduct choice.
        recordYellowCard(TeamId.TEAM_ONE, "17", "Yellow card on player 17.\n$viscousCoupling has 1 card.")
        recordBlueCard(TeamId.TEAM_ONE, "$viscousCoupling has 2 cards.")
        recordYellowCard(
            team = TeamId.TEAM_ONE,
            playerNumber = "8",
            expectedMessage = "$viscousCoupling has 3 cards.",
            misconductChoice = "Offense",
            expectedMisconductMessage = "Reverse brick",
        )
        recordYellowCard(
            team = TeamId.TEAM_ONE,
            playerNumber = "9",
            expectedMessage = "$viscousCoupling has 4 cards.",
            misconductChoice = "Defense",
            expectedMisconductMessage = "Brick nearest attacking end zone",
        )

        // Viscous Coupling scores the first point, then Animal false-starts and that entry is undone.
        recordGoal(TeamId.TEAM_ONE, "Undo Goal by $viscousCoupling")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-infraction")).performClick()
        waitForText("Defense gets to set up.")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Undo False Start on $animal").performClick()

        // Viscous Coupling then records an offsides; the duplicate offsides button is disabled.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).performClick()
        waitForText("Start at brick mark")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).assertIsNotEnabled()

        // Animal picks up two yellows and two technical fouls during the live point.
        recordYellowCard(TeamId.TEAM_TWO, "23", "Yellow card on player 23.\n$animal has 1 card.")
        recordYellowCard(TeamId.TEAM_TWO, "8", "Yellow card on player 8.\n$animal has 2 cards.")
        recordTechnicalFoul(TeamId.TEAM_TWO, "$animal has 1 technical foul.")
        recordTechnicalFoul(TeamId.TEAM_TWO, "$animal has 2 technical fouls.")

        // Viscous Coupling calls a live-point timeout before Animal finishes the point.
        recordTimeout(TeamId.TEAM_ONE, "Undo Timeout by $viscousCoupling")
        continuePointAndUnlock()
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")

        // Animal uses its final first-half timeout, then gets the out-of-timeouts cue.
        recordTimeout(TeamId.TEAM_TWO, "Undo Timeout by $animal")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "timeout")).performClick()
        waitForText("$animal is out of timeouts.")
        composeRule.onNodeWithText("OK").performClick()

        // Animal reaches the technical-foul threshold between points, so the UI shows the yardage cue.
        recordTechnicalFoul(
            team = TeamId.TEAM_TWO,
            expectedMessage = "Receiving team starts at attacking brick.",
            substring = true,
        )

        // Viscous Coupling scores the next two points, checking that halftime interrupts the flow.
        recordGoal(TeamId.TEAM_ONE, "Undo Goal by $viscousCoupling")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Halftime")
        composeRule.onNodeWithText("OK").performClick()

        // Advance the visible halftime countdown using the same correction control an observer has.
        composeRule.onAllNodesWithText("+5").onFirst().performClick()
        repeat(14) {
            composeRule.onAllNodesWithText("-5").onFirst().performClick()
        }
        waitForText("Start Point")

        // After halftime, Animal scores and uses one second-half timeout before the next pull.
        startPointAndUnlock()
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")
        recordTimeout(TeamId.TEAM_TWO, "Undo Timeout by $animal")

        // Animal ties the game, Viscous Coupling goes ahead, and Animal wins on universe.
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")
        recordGoal(TeamId.TEAM_ONE, "Undo Goal by $viscousCoupling")
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "goal")).performClick()
        waitForText("Game is over", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Game Summary").assertIsDisplayed()
        composeRule.onNodeWithText("$viscousCoupling 4").assertIsDisplayed()
        composeRule.onNodeWithText("$animal 5").assertIsDisplayed()
        composeRule.onNodeWithText(viscousCoupling).assertIsDisplayed()
        composeRule.onNodeWithText(animal).assertIsDisplayed()
        composeRule.onNodeWithText("Undo End Game").performClick()
        assertLiveScreen()

        // Manually ending from the restored final state should return to the same summary.
        openOtherSheet()
        composeRule.onNodeWithText("End Game").performClick()
        waitForText("Game is over", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Game Summary").assertIsDisplayed()
        composeRule.onNodeWithText("$viscousCoupling 4").assertIsDisplayed()
        composeRule.onNodeWithText("$animal 5").assertIsDisplayed()

        // The finished game should reopen from home, archive, and then reopen from Previous Games.
        pressAppBack()
        waitForText("Completed Game")
        composeRule.onNodeWithText("Archive Completed Game").performClick()
        waitForText("Previous Games")
        composeRule.onNodeWithText("$viscousCoupling 4 - 5 $animal").performClick()
        composeRule.onNodeWithText("Game Summary").assertIsDisplayed()
        composeRule.onNodeWithText("$animal 5").assertIsDisplayed()
    }

    // Test the primary live screen actions that should be available directly from the phone.
    // Keep the assertions at the visible undo/message level; GameModel owns detailed state checks.
    @Test
    fun livePrimaryActionsAndUndoPath() {
        startLiveGame()

        // Each side can record only one pull infraction of its type for the current pull sequence.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-infraction")).performClick()
        waitForText("Defense gets to set up.")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-infraction")).assertIsNotEnabled()

        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).performClick()
        waitForText("Start at brick mark")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).assertIsNotEnabled()

        // A between-points goal implicitly starts the point and exposes a useful undo.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Undo Goal by Team 1")
        composeRule.onNodeWithText("Undo Goal by Team 1").performClick()
        waitForText("Lock")
        waitForText("Redo")
        composeRule.onNodeWithText("Redo").performClick()
        waitForText("Undo Goal by Team 1")

        // Timeout should remain wired after the undo path.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "timeout")).performClick()
        waitForText("Undo Timeout by Team 1")
    }

    @Test
    fun fieldDiagramDoesNotMoveWhenCountdownClears() {
        startLiveGame()

        val fieldTopBeforeStartPoint = composeRule.onNodeWithTag("live-field-diagram")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")
        val fieldTopAfterStartPoint = composeRule.onNodeWithTag("live-field-diagram")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertEquals(fieldTopBeforeStartPoint, fieldTopAfterStartPoint, 0.5f)
    }
}
