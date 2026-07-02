package rmjarvis.ultiobserver

import android.app.Instrumentation
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.Intents.init
import androidx.test.espresso.intent.Intents.release
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.LocalTime
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme

/// Tests for game-over summary UI states.
@RunWith(AndroidJUnit4::class)
class TestGameSummaryUi : MainActivityUiTestFixtures() {
    /**
     * Test the game-over summary branch for teams with no player-specific cards.
     */
    @Test
    fun gameSummaryWithNoPlayerCards() {
        // If a team has no yellow or red cards issued, then the game summary says that.
        startLiveGameProgrammatically()
        endCurrentGameProgrammatically()
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Game summary")
        waitForText("No yellow or red cards issued.")
        composeRule.onNodeWithText("Share").assertIsDisplayed()

        // The summary page exposes the completed game's event log.
        composeRule.onNodeWithText("Event log").performClick()
        waitForText("Event log")
        waitForText("Game over", substring = true)
        dismissDialog(text = "OK")
    }

    /**
     * Test the game summary Share action invokes the supplied share callback.
     */
    @Test
    fun gameSummaryShareButton() {
        // We don't actually test the Android share action here.
        // Instead we have the onShareSummary action be just to change the `shared`
        // variable to true.
        var shared = false
        val state = newSetupGameState(now = 123_000L).startGame().copy(
            phase = GamePhase.GAME_OVER,
            observerNames = listOf("Mike", "Gary"),
            fieldName = "Field 7",
            endEpoch = System.currentTimeMillis(),
        )
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                UltiObserverTheme(dynamicColor = false) {
                    GameOverSummary(
                        state = state,
                        onShowEventLog = {},
                        onShareSummary = { shared = true },
                        summaryActionText = "Undo End game",
                        onSummaryAction = {},
                    )
                }
            }
        }

        // Observer metadata appears in the visible completed-game summary when it is present.
        waitForText("Observers: Mike, Gary")
        waitForText("Field: Field 7")

        // Clicking Share invokes the callback supplied to the summary composable.
        // I.e. it should change shared to true.
        composeRule.onNodeWithText("Share").performClick()
        assertTrue(shared)
    }

    /**
     * Test that current and archived summaries share the same compact tournament misconduct report.
     */
    @Test
    fun shareButtonSharesText() {
        // Seed a finished game with score and misconduct entries for compact summary sharing.
        clearArchivedGamesProgrammatically()
        val expectedShareText = """
            UltiObserver Game Summary
            Philly Open
            May 19, 2026, 10:00 AM
            Animal 15, Viscous Coupling 12
            Misconduct:
              Animal:
                #7 Yellow
                #7 Yellow
                #12 Red
                1 Blue, 2 Techs
        """.trimIndent()

        // Programmatically set up a game to match the above summary info.
        startLiveGameProgrammatically(
            newSetupGameState(now = 123_000L).copy(
                tournamentName = "Philly Open",
                startDate = LocalDate.of(2026, 5, 19),
                startTime = LocalTime.of(10, 0),
                teamOne = TeamState("Viscous Coupling", TeamColorChoice.WHITE),
                teamTwo = TeamState("Animal", TeamColorChoice.RED),
            )
        )
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    teamOne = TeamState("Viscous Coupling", TeamColorChoice.WHITE, score = 12),
                    teamTwo = TeamState(
                        name = "Animal",
                        color = TeamColorChoice.RED,
                        score = 15,
                        technicalFouls = 2,
                        blueCards = 1,
                    ),
                    teamTwoPlayers = listOf(
                        playerRecordWithCards(jerseyNumber = "7", yellows = 2),
                        PlayerRecord(
                            jerseyNumber = "12",
                            cards = listOf(InGamePlayerCardEvent(CardType.RED, index = 2)),
                        ),
                    ),
                )
            )
        }
        composeRule.waitForIdle()

        // Now end the game to trigger the game summary screen.
        // Use a helper function (below) to check that Share does share the expected text.
        endCurrentGameProgrammatically()
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Game summary")
        assertNextShareText(expectedShareText)

        // The completed-game summary can archive directly, and the archived summary shares
        // the same payload.
        composeRule.onNodeWithText("Archive game").performClick()
        waitForText("See archived/saved games")
        composeRule.onNodeWithText("See archived/saved games").performClick()
        waitForText("Archived/saved games")
        composeRule.onNodeWithText("Archived games", substring = true).performClick()
        waitForText("Archived games")
        composeRule.onNodeWithText("Viscous Coupling 12 - 15 Animal").performClick()
        waitForText("Game summary")
        assertNextShareText(expectedShareText)

        // Restore the archive so the Home-screen archive action stays covered too.
        composeRule.onNodeWithText("Restore game").performClick()
        waitForText("Game summary")
        tapTopBarBack()
        waitForText("Completed game")

        // The completed-game card on Home reopens the same summary before it is archived.
        composeRule.onNodeWithText("Viscous Coupling 12 - 15 Animal").performClick()
        waitForText("Game summary")
        assertNextShareText(expectedShareText)
        tapTopBarHome()
        waitForText("Completed game")

        // Archive the game from Home and verify it returns to the archive list.
        composeRule.onNodeWithText("Archive completed game").performClick()
        waitForText("See archived/saved games")
        composeRule.onNodeWithText("See archived/saved games").performClick()
        waitForText("Archived/saved games")
        composeRule.onNodeWithText("Archived games", substring = true).performClick()
        waitForText("Archived games")
        waitForText("Viscous Coupling 12 - 15 Animal")
    }

    /// Click Share, assert the outgoing Android chooser payload, and cancel the chooser.
    private fun assertNextShareText(expectedShareText: String) {
        val expectedIntent = chooserWithShareText(expectedShareText)
        init()
        try {
            intending(expectedIntent).respondWith(Instrumentation.ActivityResult(0, null))
            composeRule.onNodeWithText("Share").performClick()
            intended(expectedIntent)
        } finally {
            release()
        }
        composeRule.waitForIdle()
    }

    /// Return an Espresso matcher for the nested share intent created by Android's chooser wrapper.
    private fun chooserWithShareText(
        expectedShareText: String,
    ): TypeSafeMatcher<Intent> = object : TypeSafeMatcher<Intent>() {
        override fun describeTo(description: Description) {
            description.appendText(
                "chooser wrapping ACTION_SEND text/plain with expected game summary text"
            )
        }

        override fun matchesSafely(intent: Intent): Boolean {
            if (intent.action != Intent.ACTION_CHOOSER) {
                return false
            }
            val sendIntent = intent.parcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return false
            return sendIntent.action == Intent.ACTION_SEND &&
                sendIntent.type == "text/plain" &&
                sendIntent.getStringExtra(Intent.EXTRA_SUBJECT) == "UltiObserver Game Summary" &&
                sendIntent.getStringExtra(Intent.EXTRA_TEXT) == expectedShareText
        }
    }
}

/// Return a typed parcelable extra across Android API levels.
private inline fun <reified T> Intent.parcelableExtra(name: String): T? {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name)
    }
}
