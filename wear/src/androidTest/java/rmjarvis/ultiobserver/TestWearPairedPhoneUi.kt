package rmjarvis.ultiobserver

import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import rmjarvis.ultiobserver.wearprotocol.PHONE_STATE_CAPABILITY
import rmjarvis.ultiobserver.wearprotocol.WEAR_STATE_PATH

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

        // The cap row opens the phone's game rules, then returns to the scores.
        composeRule.onNodeWithText("Half cap in", substring = true).performClick()
        waitForText("Game to 15")
        composeRule.onNodeWithText("Game to 15").assertIsDisplayed()
        composeRule.onNodeWithText("Halftime", substring = true).assertExists()
        dismissNotice("Back")
        waitForText(ANIMAL)

        // Coach/captain names open locally, while a team without names has no information icon.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForContentDescription("Coach/captain information")
        composeRule.onNode(hasContentDescription("Coach/captain information")).performClick()
        waitForText("Alex")
        composeRule.onNodeWithText("Coach").assertIsDisplayed()
        composeRule.onNodeWithText("Pat\nSam").assertExists()
        composeRule.onNodeWithText("Lee").assertExists()
        dismissNotice("Back")
        waitForText("Goal")
        dismissNotice("Cancel")
        composeRule.onNodeWithText("Viscous Coupling").performClick()
        waitForText("Goal")
        composeRule.onNode(hasContentDescription("Coach/captain information")).assertDoesNotExist()
        dismissNotice("Cancel")

        // Open timing controls, pause the clock, and adjust it in both directions.
        composeRule.onNode(hasContentDescription("Countdown controls")).performClick()
        waitForText("Start point")
        composeRule.onNode(hasContentDescription("Pause")).performClick()
        waitForText("Paused")
        waitForContentDescription("Resume")
        val timerText = composeRule.onNode(hasContentDescription("Countdown controls"))
            .fetchSemanticsNode().config[SemanticsProperties.Text]
            .map { it.text }.first { it.matches(Regex("[0-9]+:[0-9]{2}")) }
        val timerParts = timerText.split(":").map(String::toInt)
        val seconds = timerParts[0] * 60 + timerParts[1]
        composeRule.onNode(hasContentDescription("+5")).performClick()
        waitForText("%d:%02d".format((seconds + 5) / 60, (seconds + 5) % 60))
        composeRule.onNode(hasContentDescription("−5")).performClick()
        waitForText(timerText)
        composeRule.onNode(hasContentDescription("Resume")).performClick()
        waitForContentDescription("Pause")
        dismissNotice("Back")
        waitForText(ANIMAL)

        // Cancelling a water break returns to timing controls; confirming returns to the scores.
        composeRule.onNode(hasContentDescription("Countdown controls")).performClick()
        composeRule.onNode(hasContentDescription("Water break")).performClick()
        waitForText("OK")
        dismissNotice("Cancel")
        waitForText("Start point")
        composeRule.onNode(hasContentDescription("Water break")).performClick()
        waitForText("OK")
        composeRule.onNodeWithText("OK").performClick()
        waitForText(ANIMAL)
        composeRule.onNodeWithText("Start point").assertDoesNotExist()

        // Reopen timing controls to start play after the water break.
        composeRule.onNode(hasContentDescription("Countdown controls")).performClick()
        waitForText("Start point")
        composeRule.onNodeWithText("Start point").performClick()
        waitForContentDescription("Undo Start point")
        composeRule.onNodeWithText("Start point").assertDoesNotExist()
        composeRule.onNodeWithText(ANIMAL).assertIsDisplayed()

        // Opening a team's actions does not commit anything. Cancel returns to the same score.
        composeRule.onNodeWithText(VISCOUS_COUPLING).performClick()
        waitForText("Goal")
        dismissNotice("Cancel")
        waitForText(ANIMAL)
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

        // The phone clears the post-goal countdown; restart it from the watch.
        waitForPairedText("Restart countdown")
        composeRule.onNodeWithText("Restart countdown").assertIsDisplayed().performClick()
        waitForContentDescription("Undo Restart countdown")
        composeRule.onNodeWithText("Restart countdown").assertDoesNotExist()
        composeRule.onNodeWithText("Pull in").assertIsDisplayed()

        // Undo the restart to return to the goal's undo entry.
        composeRule.onNodeWithText("Undo").performClick()
        waitForContentDescription("Undo Goal by Animal")
        composeRule.onNodeWithText("Restart countdown").assertIsDisplayed()

        // Start play instead of restarting the countdown, then undo to recover both choices.
        composeRule.onNodeWithText("Start point").assertIsDisplayed().performClick()
        waitForContentDescription("Undo Start point")
        composeRule.onNodeWithText("Start point").assertDoesNotExist()
        composeRule.onNodeWithText("Restart countdown").assertDoesNotExist()
        composeRule.onNodeWithText("Undo").performClick()
        waitForContentDescription("Undo Goal by Animal")
        composeRule.onNodeWithText("Start point").assertIsDisplayed()
        composeRule.onNodeWithText("Restart countdown").assertIsDisplayed()

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
        dismissNotice("Cancel")
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
        dismissNotice("Cancel")
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
        dismissNotice("Cancel")
        waitForText("Time viol.")
        composeRule.onNodeWithText("Time viol.").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo Time violation warning on Animal")
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
        dismissNotice("Cancel")
        waitForText("Goal")
        composeRule.onNodeWithText("Timeout (2)").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo Timeout by Viscous Coupling")

        // The timing panel mirrors Offense is set, then Continue point, from the phone.
        composeRule.onNode(hasContentDescription("Countdown controls")).performClick()
        waitForText("Offense is set")
        composeRule.onNodeWithText("Offense is set").performClick()
        waitForText("Continue point")
        composeRule.onNodeWithText("Offense is set").assertDoesNotExist()
        composeRule.onNodeWithText("Continue point").performClick()
        waitForText(ANIMAL)
        composeRule.onNodeWithText("Continue point").assertDoesNotExist()

        // Technical foul on Animal
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Tech").performClick()
        waitForText("OK")
        dismissNotice("Cancel")
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
        dismissNotice("Cancel")
        waitForText("Goal")
        composeRule.onNodeWithText("Card").performClick()
        waitForText("Blue")
        composeRule.onNodeWithText("Blue").performClick()
        waitForText("OK")
        dismissNotice("Cancel")
        waitForText("Blue")
        composeRule.onNodeWithText("Blue").performClick()
        waitForText("OK")
        composeRule.onNodeWithText("OK").performClick()
        waitForContentDescription("Undo Blue card on Viscous Coupling")

        // Two more blue cards reach the live-point misconduct penalty.
        for (cardCount in 1..2) {
            composeRule.onNodeWithText(VISCOUS_COUPLING).performClick()
            waitForText("Card ($cardCount)")
            composeRule.onNodeWithText("Card ($cardCount)").performClick()
            waitForText("Blue")
            composeRule.onNodeWithText("Blue").performClick()
            waitForText("OK")
            composeRule.onNodeWithText("OK").performClick()
            waitForText(VISCOUS_COUPLING)
        }
        waitForText("Start misconduct\ncountdown")
        composeRule.onNodeWithText("Start misconduct\ncountdown").assertIsDisplayed().assertIsEnabled()

        // Start the phone's 30-second offense-set countdown from the watch.
        composeRule.onNodeWithText("Start misconduct\ncountdown").performClick()
        waitForText("Offense set in")
        composeRule.onNodeWithText("Start misconduct\ncountdown").assertDoesNotExist()

        // Let the configured vibration cue pass while watching the countdown.
        waitForText("Next: 20 seconds, offense")
        waitForNoText("Next: 20 seconds, offense")

        // Continue the point after the cue, so the phone test can finish up.
        composeRule.onNode(hasContentDescription("Countdown controls")).performClick()
        waitForText("Offense is set")
        composeRule.onNodeWithText("Offense is set").performClick()
        waitForText("Continue point")
        composeRule.onNodeWithText("Continue point").performClick()
        waitForText(ANIMAL)
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
        dismissNotice("OK")
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
        dismissNotice("OK")
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
        dismissNotice("OK")
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
        dismissNotice("OK")

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
        dismissNotice("OK")
        waitForContentDescription("Undo Apply hard cap")
        assertEquals(2, composeRule.onAllNodesWithText("1").fetchSemanticsNodes().size)

        // Since the game is tied, play one more point.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForText("Game over")
        dismissNotice("OK")
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
        dismissNotice("Cancel")
        waitForText("Goal")

        // Cancel the numbered-card screen to return to the card picker.
        composeRule.onNodeWithText("Card (4)").performClick()
        composeRule.onNodeWithText("Red").performClick()
        waitForText("Red card")
        dismissNotice("Cancel")
        waitForText("Assess a card")

        // Dismiss the notice that prevents assigning another yellow card to a suspended player.
        composeRule.onNodeWithText("Yellow").performClick()
        enterPlayerNumber("8")
        composeRule.onNodeWithText("Record card").performClick()
        waitForText("Invalid card assignment")
        dismissNotice("OK")
        waitForText("Change number (8)")

        // Start entering that card on the phone, then cancel the phone workflow from the watch.
        composeRule.onNodeWithText("Enter details on phone").performClick()
        waitForText("Continue on phone")
        dismissNotice("Cancel")
        waitForText("Assess a card")

        // Return to the game and try a handoff for the other team. Cancelling it restores that
        // team's card picker, not Animal's.
        dismissNotice("Cancel")
        waitForText("Goal")
        dismissNotice("Cancel")
        waitForText(VISCOUS_COUPLING)
        composeRule.onNodeWithText(VISCOUS_COUPLING).performClick()
        waitForText("Card")
        composeRule.onNodeWithText("Card").performClick()
        composeRule.onNodeWithText("Yellow").performClick()
        waitForText("Enter details on phone")
        composeRule.onNodeWithText("Enter details on phone").performClick()
        waitForText("Continue on phone")
        dismissNotice("Cancel")
        waitForText("Assess a card")
        composeRule.onNodeWithText(VISCOUS_COUPLING).assertIsDisplayed()
        dismissNotice("Cancel")
        waitForText("Goal")
        dismissNotice("Cancel")
        waitForText(ANIMAL)

        // Score a goal to tell the phone all card checks are done.
        composeRule.onNodeWithText(ANIMAL).performClick()
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
        dismissNotice("Cancel")
        waitForText("Change number (3)")
        composeRule.onNodeWithText("Record card").performClick()
        waitForText("Multiple players")
        composeRule.onNodeWithText("Continue on phone").performClick()

        // The phone selects Mark and confirms his red card. The watch receives the recorded result.
        waitForPairedContentDescription("Undo Red on #3 of Animal")
    }

    /** Record actions and reach halftime without clicking OK on Timed guidance. */
    @Test
    fun timedGuidance() {
        waitForPairedText(VISCOUS_COUPLING)

        // Let the timeout confirmation expire, then verify the recorded action on the watch.
        composeRule.onNodeWithText(VISCOUS_COUPLING).performClick()
        waitForText("Timeout (2)")
        composeRule.onNodeWithText("Timeout (2)").performClick()
        waitForContentDescription("Undo Timeout by Viscous Coupling")

        // Technical-foul guidance also expires without an explicit confirmation.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Tech")
        composeRule.onNodeWithText("Tech").performClick()
        waitForContentDescription("Undo Technical foul on Animal")

        // Two Animal goals reach halftime. The halftime notice expires just like action guidance.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForContentDescription("Undo Goal by Animal")
        waitForText("1")
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForContentDescription("Undo Start halftime")
        waitForText("2")
    }

    /** Record actions and reach halftime with None guidance, retaining required notices. */
    @Test
    fun noGuidance() {
        waitForPairedText(ANIMAL)

        // Mixed-pull guidance is retained briefly in None mode. Leaving its default Offsides
        // selection alone records the violation when the notice expires.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Offsides")
        composeRule.onNodeWithText("Offsides").performClick()
        waitForContentDescription("Undo Offsides on Animal")

        // Ordinary time-violation guidance needs no displayed confirmation in None mode.
        composeRule.onNodeWithText(VISCOUS_COUPLING).performClick()
        waitForText("Time viol.")
        composeRule.onNodeWithText("Time viol.").performClick()
        waitForContentDescription("Undo Time violation warning on Viscous Coupling")

        // Two goals reach halftime, which also completes without an OK click.
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForContentDescription("Undo Goal by Animal")
        waitForText("1")
        composeRule.onNodeWithText(ANIMAL).performClick()
        waitForText("Goal")
        composeRule.onNodeWithText("Goal").performClick()
        waitForContentDescription("Undo Start halftime")
        waitForText("2")
    }

    /** Recover from a paired phone app that stops responding to requests. */
    @Test
    fun connectionRecovery() {
        waitForPairedText(ANIMAL)
        composeRule.onNodeWithText(ANIMAL).assertIsEnabled()
        assertEquals(2, composeRule.onAllNodesWithText("0").fetchSemanticsNodes().size)

        val dataClient = Wearable.getDataClient(composeRule.activity)
        val deletion = CountDownLatch(1)
        val listener = DataClient.OnDataChangedListener { events ->
            events.forEach { event ->
                if (event.type == DataEvent.TYPE_DELETED) {
                    assertNull(event.dataItem.data)
                    deletion.countDown()
                }
            }
        }
        Tasks.await(dataClient.addListener(
            listener, Uri.parse("wear://*$WEAR_STATE_PATH"), DataClient.FILTER_LITERAL,
        ), 30, TimeUnit.SECONDS)
        try {
            // The phone partner disables its request service to simulate an unresponsive app, not a
            // physical disconnection. The pairing remains intact, but the next goal gets no
            // acknowledgement and times out, leaving the old score readable and actions disabled.
            File(composeRule.activity.filesDir, "paired-recovery-disconnect").createNewFile()
            waitForRecoveryStage("disabled")

            // The deleted DataItem has no payload. It must not be decoded as a snapshot or clear
            // the last received game. Observe its delivery before asserting the retained score.
            assertTrue("State DataItem deletion was not delivered", deletion.await(30, TimeUnit.SECONDS))
            composeRule.onNodeWithText(ANIMAL).assertIsDisplayed()
            assertEquals(2, composeRule.onAllNodesWithText("0").fetchSemanticsNodes().size)

            // With request delivery still disabled, a goal times out and disables game actions.
            composeRule.onNodeWithText(ANIMAL).performClick()
            waitForText("Goal")
            composeRule.onNodeWithText("Goal").performClick()
            waitForPairedText("Lost connection")
            composeRule.onNodeWithText(ANIMAL).assertIsNotEnabled()
            composeRule.onNodeWithText(VISCOUS_COUPLING).assertIsNotEnabled()
            assertEquals(2, composeRule.onAllNodesWithText("0").fetchSemanticsNodes().size)

            // Restore phone request delivery. The file creation is our communication channel
            // with the parallel phone test function.
            File(composeRule.activity.filesDir, "paired-recovery-restore").createNewFile()
            waitForRecoveryStage("restored")

            // Use the Retry control. The fresh snapshot retains 0-0, proving that recovery
            // did not replay the unacknowledged goal.
            composeRule.onNodeWithText("Retry").performClick()
            waitForConnectedGame()
            composeRule.onNodeWithText(ANIMAL).assertIsEnabled()
            assertEquals(2, composeRule.onAllNodesWithText("0").fetchSemanticsNodes().size)

            // Exercise capability-loss and return callbacks against the now-responsive phone.
            verifyCapabilityRecovery()

            // A new goal works normally after the handshake and is verified by the phone partner.
            composeRule.onNodeWithText(ANIMAL).performClick()
            waitForText("Goal")
            composeRule.onNodeWithText("Goal").performClick()
            waitForContentDescription("Undo Goal by Animal")
            waitForText("1")
        } finally {
            Tasks.await(dataClient.removeListener(listener), 30, TimeUnit.SECONDS)
        }
    }

    /** Verify Android capability callbacks reach the session controller and trigger recovery. */
    private fun verifyCapabilityRecovery() {
        val phone = Tasks.await(
            Wearable.getCapabilityClient(composeRule.activity).getCapability(
                PHONE_STATE_CAPABILITY, CapabilityClient.FILTER_REACHABLE,
            ),
            30, TimeUnit.SECONDS,
        )
        assertTrue(phone.nodes.isNotEmpty())
        val connection = AtomicReference(ConnectionState.CONNECTING)
        val received = AtomicReference<ReceivedState>()
        val client = StateClient(
            composeRule.activity,
            onStateReceived = { received.set(it) },
            onConnectionStateChanged = { connection.set(it) },
        )
        try {
            // Use a separate client so injected callbacks do not alter the Activity's listeners.
            // Its initial session and subsequent recovery both use the real paired phone.
            composeRule.runOnIdle { client.start() }
            composeRule.waitUntil(timeoutMillis = PAIRED_TEST_TIMEOUT_MILLIS) {
                connection.get() == ConnectionState.CONNECTED
            }
            val original = received.get()

            // An empty capability node set represents the phone leaving reachability. Invoke
            // the Android callback directly: manifest capabilities cannot be withdrawn by API.
            // This proves callback forwarding, not Android's delivery of capability events.
            val unavailable = object : CapabilityInfo {
                override fun getName() = PHONE_STATE_CAPABILITY
                override fun getNodes() = emptySet<com.google.android.gms.wearable.Node>()
            }
            composeRule.runOnIdle { client.onCapabilityChanged(unavailable) }
            assertEquals(ConnectionState.DISCONNECTED, connection.get())
            assertEquals(original, received.get())

            // Returning nodes must be converted and forwarded too. The real phone supplies a
            // fresh startup snapshot before the client can report that it is connected again.
            composeRule.runOnIdle { client.onCapabilityChanged(phone) }
            composeRule.waitUntil(timeoutMillis = PAIRED_TEST_TIMEOUT_MILLIS) {
                connection.get() == ConnectionState.CONNECTED
            }
            assertEquals(original.snapshot.activeGame, received.get().snapshot.activeGame)
        } finally {
            composeRule.runOnIdle { client.stop() }
        }
    }

    private fun waitForRecoveryStage(stage: String) {
        catchAndDiagnoseFailure(composeRule) {
            composeRule.waitUntil(timeoutMillis = 60_000L) {
                File(composeRule.activity.filesDir, "paired-recovery-$stage").exists()
            }
        }
    }

    private fun waitForConnectedGame() {
        catchAndDiagnoseFailure(composeRule) {
            composeRule.waitUntil(timeoutMillis = PAIRED_TEST_TIMEOUT_MILLIS) {
                composeRule.onAllNodes(hasText(ANIMAL) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    /** Cover explicit controls on Small Round and equivalent platform Back on Large Round. */
    private fun dismissNotice(text: String) {
        waitForText(text)
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("getprop ro.boot.qemu.avd_name")
        val avdName = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader().use { it.readText().trim() }
        if (avdName == "Wear_OS_Large_Round") {
            pressBackUnconditionally()
        } else {
            composeRule.onNodeWithText(text).performClick()
        }
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
        catchAndDiagnoseFailure(composeRule) {
            composeRule.waitUntil(timeoutMillis = UI_ACTION_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun waitForNoText(text: String) {
        catchAndDiagnoseFailure(composeRule) {
            composeRule.waitUntil(timeoutMillis = UI_ACTION_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
            }
        }
    }

    private fun waitForPairedText(text: String) {
        catchAndDiagnoseFailure(composeRule) {
            composeRule.waitUntil(timeoutMillis = PAIRED_TEST_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun waitForContentDescription(description: String) {
        catchAndDiagnoseFailure(composeRule) {
            composeRule.waitUntil(timeoutMillis = UI_ACTION_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithContentDescription(description)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun waitForPairedContentDescription(description: String) {
        catchAndDiagnoseFailure(composeRule) {
            composeRule.waitUntil(timeoutMillis = PAIRED_TEST_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithContentDescription(description)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun waitForNoContentDescription(description: String) {
        catchAndDiagnoseFailure(composeRule) {
            composeRule.waitUntil(timeoutMillis = UI_ACTION_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithContentDescription(description)
                    .fetchSemanticsNodes().isEmpty()
            }
        }
    }
}

private const val UI_ACTION_TIMEOUT_MILLIS = 10_000L
private const val PAIRED_TEST_TIMEOUT_MILLIS = 120_000L
private const val ANIMAL = "Animal"
private const val VISCOUS_COUPLING = "Viscous Coupling"

/** Capture the live screen and UI tree when a wait fails, then rethrow the failure. */
internal fun catchAndDiagnoseFailure(composeRule: ComposeTestRule, action: () -> Unit) {
    try {
        action()
    } catch (failure: Throwable) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val name = "wait-failure-${System.currentTimeMillis()}"
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "wait-failures")
        // A capture failure must not hide the original wait failure.
        runCatching {
            directory.mkdirs()
            val tree = composeRule.onAllNodes(isRoot(), useUnmergedTree = true)
                .printToString(maxDepth = Int.MAX_VALUE)
            Log.e("UiTestFailure", tree)
            File(directory, "$name.txt").writeText(failure.stackTraceToString() + "\n" + tree)
        }.exceptionOrNull()?.let { failure.addSuppressed(it) }
        runCatching {
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            try {
                File(directory, "$name.png").outputStream().use { stream ->
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
            } finally {
                screenshot.recycle()
            }
        }.exceptionOrNull()?.let { failure.addSuppressed(it) }
        throw failure
    }
}
