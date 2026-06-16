package rmjarvis.ultiobserver

import android.app.Instrumentation
import android.content.Intent
import androidx.activity.compose.setContent
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
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.Intents.init
import androidx.test.espresso.intent.Intents.release
import java.time.LocalDate
import java.time.LocalTime
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme

/// Tests for game-over summary UI states.
@RunWith(AndroidJUnit4::class)
class TestGameSummaryUi : MainActivityUiTestFixtures() {
    /// Test the game-over summary branch for teams with no player-specific cards.
    @Test
    fun gameSummaryShowsNoIssuedPlayerCards() {
        startLiveGameProgrammatically()

        endCurrentGameProgrammatically()
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Game summary")
        waitForText("No yellow or red cards issued.")
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Event log").performClick()
        waitForText("Event log")
        waitForText("Game over", substring = true)
        pressDialogBack()
    }

    /// Test the game summary Share action invokes the supplied share callback.
    @Test
    fun gameSummaryShareButtonInvokesCallback() {
        var shared = false
        val state = createLiveGameState(newGameSetupState()).copy(
            phase = GamePhase.GAME_OVER,
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
        composeRule.onNodeWithText("Share").performClick()

        assertTrue(shared)
    }

    /// Test that current and archived summaries share the same compact tournament misconduct report.
    @Test
    fun shareButtonSharesTournamentScoreAndMisconductSummary() {
        clearArchivedGamesProgrammatically()
        val expectedShareText = """
            UltiObserver Game Summary
            Philly Open - May 19, 2026, 10:00 AM
            Animal 15, Viscous Coupling 12
            Misconduct:
              Animal:
                #7 Yellow
                #7 Yellow
                #12 Red
                1 Blue, 2 Techs
        """.trimIndent()

        startLiveGameProgrammatically(
            newGameSetupState().copy(
                tournamentName = "Philly Open",
                startDate = LocalDate.of(2026, 5, 19),
                startTime = LocalTime.of(10, 0),
                teamOne = TeamSetup("Viscous Coupling", TeamColorChoice.WHITE),
                teamTwo = TeamSetup("Animal", TeamColorChoice.RED),
            )
        )
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    teamOne = TeamLiveState("Viscous Coupling", TeamColorChoice.WHITE, score = 12),
                    teamTwo = TeamLiveState(
                        name = "Animal",
                        color = TeamColorChoice.RED,
                        score = 15,
                        technicalFouls = 2,
                        blueCards = 1,
                    ),
                    teamTwoPlayers = listOf(
                        playerRecordWithCards(jerseyNumber = "7", yellows = 2),
                        playerRecordWithCards(jerseyNumber = "12", reds = 1),
                    ),
                )
            )
        }
        composeRule.waitForIdle()

        endCurrentGameProgrammatically()
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Game summary")
        assertNextShareText(expectedShareText)

        composeRule.onNodeWithText("Back").performClick()
        waitForText("Completed game")
        composeRule.onNodeWithText("Archive completed game").performClick()
        waitForText("See archived games")
        composeRule.onNodeWithText("See archived games").performClick()
        waitForText("Archived games")
        composeRule.onNodeWithText("Viscous Coupling 12 - 15 Animal").performClick()
        waitForText("Game summary")
        assertNextShareText(expectedShareText)
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
    private fun chooserWithShareText(expectedShareText: String) = object : TypeSafeMatcher<Intent>() {
        override fun describeTo(description: Description) {
            description.appendText("chooser wrapping ACTION_SEND text/plain with expected game summary text")
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
