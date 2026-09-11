package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * User narratives that control the authoritative phone game from the paired watch.
 *
 * Note: This test is run in conjunction with the app test with the same name.
 *       The phone test indicates that it is ready for the watch test to start by writing
 *       to the PAIRED_TEST_READY_FILE.
 *       Once the Python test runner for the watch tests sees the signal, it starts the
 *       corresponding watch test.
 */
class TestWearPairedPhoneUi {
    @get:Rule
    val composeRule = createAndroidComposeRule<WatchActivity>()

    /** Read the live status, then test recording goals and undoing them on the watch. */
    @Test
    fun goalAndUndo() {
        waitForPairedText(ANIMAL)

        // Read the phone's cap, countdown, pull direction, and score.
        composeRule.onNodeWithText("Half cap in", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Pull in").assertIsDisplayed()
        composeRule.onNode(
            hasContentDescription("Pull direction from Animal toward Viscous Coupling")
        ).assertIsDisplayed()
        composeRule.onNode(hasContentDescription("ABBA ratio M2")).assertIsDisplayed()
        assertEquals(2, composeRule.onAllNodesWithText("0").fetchSemanticsNodes().size)

        // Goal for Animal. It updates the score, starts a countdown, and changes the ratio.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForText("Undo")
        assertEquals(1, composeRule.onAllNodesWithText("1").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("0").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Next: 20 seconds for a hand").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("ABBA ratio W1")).assertIsDisplayed()

        // Goal for Viscous Coupling. 1-1.
        composeRule.onNodeWithText(VISCOUS_COUPLING).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForText("Undo")
        assertEquals(2, composeRule.onAllNodesWithText("1").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("0").fetchSemanticsNodes().size)

        // Undo returns the score to Animal 1, Viscous Coupling 0.
        composeRule.onNode(
            hasContentDescription("Undo Goal by Viscous Coupling")
        ).performClick()

        // The phone verifies the Undo result, then records a goal for Animal.

        // Wait for the score to become 2-0, indicating the phone is done.
        waitForPairedText("2")
        assertEquals(1, composeRule.onAllNodesWithText("2").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("0").fetchSemanticsNodes().size)

        // The watch can then undo that phone-recorded goal.
        composeRule.onNode(
            hasContentDescription("Undo Goal by Animal")
        ).performClick()
        waitForText("1")
        assertEquals(1, composeRule.onAllNodesWithText("1").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("0").fetchSemanticsNodes().size)
    }

    /** Test recording time violations and pull violations from the watch. */
    @Test
    fun timeAndPullViolations() {
        waitForPairedText(ANIMAL)

        // Call an offsides on Animal, and then undo it. (Including a cancel first.)
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Offsides").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Offsides").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo Offsides on Animal")
        composeRule.onNodeWithText("Undo").performClick()
        waitForNoContentDescription("Undo Offsides on Animal")

        // Do it again, but switch to Majority pull violation.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Offsides")
        composeRule.onNodeWithText("Offsides").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("→ Majority pull viol.").performClick()
        waitForText("→ Offsides")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo Majority pull violation on Animal")

        // Record a false start on VC as well. (Including a cancel first.)
        composeRule.onNodeWithText(VISCOUS_COUPLING).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("False start").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("False start").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo False start on Viscous Coupling")

        // Record a time-violation on Animal. (Including a cancel first.)
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Time viol.")
        composeRule.onNodeWithText("Time viol.").performClick()
        waitForText("Cancel")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Time viol.")
        composeRule.onNodeWithText("Time viol.").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo Time violation on Animal")
    }

    /** Test recording timeout, technical foul, and blue card from the watch. */
    @Test
    fun timeoutAndMisconduct() {
        waitForPairedText(VISCOUS_COUPLING)

        // Timeout by VC
        composeRule.onNodeWithText(VISCOUS_COUPLING).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Timeout (2)").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Timeout (2)").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo Timeout by Viscous Coupling")

        // Technical foul on Animal
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Tech").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Tech").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo Technical foul on Animal")

        // Blue card on VC
        composeRule.onNodeWithText(VISCOUS_COUPLING).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Card").performClick()
        waitForText("Blue")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Card").performClick()
        waitForText("Blue")
        composeRule.onNodeWithText("Blue").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Blue")
        composeRule.onNodeWithText("Blue").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo Blue card on Viscous Coupling")
    }

    /** Defer and accept both the half-cap and halftime confirmations on the watch. */
    @Test
    fun halftimeConfirmation() {
        waitForPairedText(ANIMAL)

        // The first goal ends after half-cap time, but defer the resulting notice.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForText("Half cap")
        composeRule.onNodeWithText("Not yet").performClick()
        waitForContentDescription("Undo Goal by Animal")

        // After a Viscous Coupling goal, half cap is offered again. Apply it this time.
        composeRule.onNodeWithText(VISCOUS_COUPLING).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForText("Half cap")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo Apply half cap")

        // Animal scores into the new halftime target, but defer halftime.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForText("Halftime")
        composeRule.onNodeWithText("Not yet").performClick()
        waitForContentDescription("Undo Goal by Animal")

        // Viscous Coupling scores the next goal; confirm the resulting halftime notice.
        composeRule.onNodeWithText(VISCOUS_COUPLING).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForText("Halftime")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo Start halftime")
        waitForText("Halftime")
        assertEquals(2, composeRule.onAllNodesWithText("2").fetchSemanticsNodes().size)
    }

    /** Defer and accept soft-cap, hard-cap, and game-over confirmations on the watch. */
    @Test
    fun gameWinningGoal() {
        waitForPairedText(ANIMAL)

        // Soft cap is due after the opening goal. Defer it and keep playing at 1-0.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForText("Soft cap")
        composeRule.onNodeWithText("Not yet").performClick()
        waitForContentDescription("Undo Goal by Animal")

        // Tie the score and accept the renewed soft-cap notice, making this a game to 2.
        composeRule.onNodeWithText(VISCOUS_COUPLING).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForText("Soft cap")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo Apply soft cap")
        assertEquals(2, composeRule.onAllNodesWithText("1").fetchSemanticsNodes().size)

        // Animal reaches the winning target, but Not yet leaves the game playable at 2-1.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForText("Game over")
        composeRule.onNodeWithText("Not yet").performClick()
        waitForContentDescription("Undo Goal by Animal")
        waitForText("2")

        // Another Animal goal offers game over again. Confirm it
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForText("Game over")
        composeRule.onNodeWithText("OK").performClick()

        // Game over. The phone verifies the final score of 3-1.
        // Now the phone prepares a fresh hard-cap game.
        // The phone records the first goal, which triggers a hard cap message.
        waitForPairedText("Hard cap")

        // Defer hard cap at 1-0. The watch can still record the tying goal afterward.
        composeRule.onNodeWithText("Not yet").performClick()
        waitForContentDescription("Undo Goal by Animal")
        composeRule.onNodeWithText(VISCOUS_COUPLING).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()

        // The hard cap is offered again. This time confirm it.
        waitForText("Hard cap")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo Apply hard cap")
        assertEquals(2, composeRule.onAllNodesWithText("1").fetchSemanticsNodes().size)

        // Since the game is tied, play one more point.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForText("Game over")
        composeRule.onNodeWithText("OK").performClick()
        waitForNoText("OK")
        waitForText("Game over")
        assertEquals(1, composeRule.onAllNodesWithText("2").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("1").fetchSemanticsNodes().size)
    }

    /** Test entering and recording a player card entirely on the watch. */
    @Test
    fun playerCardEntryOnWatch() {
        waitForPairedText(ANIMAL)

        // Enter a number on the watch and record the red card directly.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Card (2)")
        composeRule.onNodeWithText("Card (2)").performClick()
        waitForText("Assess a card")
        composeRule.onNodeWithText("Red").performClick()
        enterPlayerNumber("9")
        composeRule.onNodeWithText("Record card").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo Red on #9 of Animal")

        // Cancel the card picker to return to the team's actions.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Card (4)")
        composeRule.onNodeWithText("Card (4)").performClick()
        waitForText("Assess a card")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Goal")

        // Cancel the numbered-card screen to return to the card picker.
        composeRule.onNodeWithText("Card (4)").performClick()
        composeRule.onNodeWithText("Red").performClick()
        waitForText("Red card")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Assess a card")

        // Dismiss the notice that prevents assigning another yellow card to a suspended player.
        composeRule.onNodeWithText("Yellow").performClick()
        enterPlayerNumber("8")
        composeRule.onNodeWithText("Record card").performClick()
        waitForText("Invalid card assignment")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Change number (8)")

        // Start entering that card on the phone, then cancel the phone workflow from the watch.
        composeRule.onNodeWithText("Enter details on phone").performClick()
        waitForText("Continue on phone")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Assess a card")

        // Return to the team actions and score a goal to tell the phone all card checks are done.
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForContentDescription("Undo Goal by Animal")
    }

    /** Begin a card on the watch, let the phone finish it, and observe the synchronized result. */
    @Test
    fun playerCardPhoneHandoff() {
        waitForPairedText(VISCOUS_COUPLING)
        composeRule.onNodeWithText(VISCOUS_COUPLING).performClick()
        composeRule.onNodeWithText("Card").performClick()
        composeRule.onNodeWithText("Yellow").performClick()
        enterPlayerNumber("17")
        composeRule.onNodeWithText("Enter details on phone").performClick()

        // The watch is disabled except for a Cancel option while entering details on the phone.
        waitForText("Continue on phone")

        // The phone finishes entering the name and reason for the card recipient.

        // The phone verifies the yellow card, then records a goal to release the watch for
        // the next entry. The score distinguishes this from the original 0-0 game screen.
        waitForPairedText("1")

        // Two Animal players share #3. Record card cannot choose between their identities,
        // so the watch requires the observer to continue on the phone.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Card (1)")
        composeRule.onNodeWithText("Card (1)").performClick()
        composeRule.onNodeWithText("Red").performClick()
        enterPlayerNumber("3")
        composeRule.onNodeWithText("Record card").performClick()
        waitForText("Multiple players")
        composeRule.onNodeWithText("Continue on phone").performClick()

        // The phone selects Mark and confirms his red card. The watch receives the recorded result.
        waitForPairedContentDescription("Undo Red on #3 of Animal")
    }

    private fun enterPlayerNumber(number: String) {
        composeRule.onNodeWithText("Enter player number").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput(number)
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performImeAction()
        waitForText("Change number ($number)")
        composeRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = UI_ACTION_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForNoText(text: String) {
        composeRule.waitUntil(timeoutMillis = UI_ACTION_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForPairedText(text: String) {
        composeRule.waitUntil(timeoutMillis = PAIRED_TEST_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForContentDescription(description: String) {
        composeRule.waitUntil(timeoutMillis = UI_ACTION_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForPairedContentDescription(description: String) {
        composeRule.waitUntil(timeoutMillis = PAIRED_TEST_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForNoContentDescription(description: String) {
        composeRule.waitUntil(timeoutMillis = UI_ACTION_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes().isEmpty()
        }
    }
}

private const val UI_ACTION_TIMEOUT_MILLIS = 10_000L
private const val PAIRED_TEST_TIMEOUT_MILLIS = 120_000L
private const val ANIMAL = "Animal"
private const val VISCOUS_COUPLING = "Viscous Coupling"
