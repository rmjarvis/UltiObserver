package rmjarvis.ultiobserver

import android.app.Instrumentation
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.Intents.init
import androidx.test.espresso.intent.Intents.release
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.LocalTime
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
    /**
     * Test existing player-card details can be edited from completed, archived, and current
     * summaries.
     */
    @Test
    fun gameSummaryCardDetailsCanBeEdited() {
        clearArchivedGamesProgrammatically()
        startLiveGameProgrammatically(
            newSetupGameState(now = 123_000L).copy(
                teamOne = TeamState("Viscous Coupling", TeamColorChoice.WHITE),
                teamTwo = TeamState("Animal", TeamColorChoice.BLUE),
            )
        )
        updateCurrentStateProgrammatically {
            copy(
                rules = rules.copy(useHardCap = true),
                teamOne = teamOne.copy(score = 1),
                teamOnePlayers = listOf(playerRecordWithCards("7", yellows = 1)),
                teamTwoPlayers = listOf(playerRecordWithCards("23", yellows = 1)),
                pendingCapOffer = CapType.HARD,
            ).applyPendingCap(System.currentTimeMillis())
        }
        waitForText("Game over")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Game summary")

        // Both teams with an existing yellow/red card expose the summary edit action.
        composeRule.onAllNodesWithTag("summary-${TeamId.TEAM_TWO.name}-edit-cards")
            .assertCountEquals(1)
        composeRule.onNodeWithTag("summary-${TeamId.TEAM_ONE.name}-edit-cards")
            .performScrollTo()
            .performClick()
        waitForText("Edit existing cards")
        composeRule.onAllNodes(hasContentDescription("Edit #7", substring = true))
            .onFirst()
            .performClick()
        waitForText("Edit yellow card")
        composeRule.onNodeWithTag("card-player-name")
            .performTextReplacement("Casey Handler")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Edit existing cards")
        composeRule.onNodeWithTag("editable-player-cards-done").performClick()
        waitForText("#7 Casey Handler: Yellow card", substring = true)

        // The archived summary uses the same editor and persists the updated archive row.
        composeRule.onNodeWithText("Archive game").performClick()
        waitForText("See archived/saved games")
        composeRule.onNodeWithText("See archived/saved games").performClick()
        waitForText("Archived/saved games")
        composeRule.onNodeWithText("Archived games", substring = true).performClick()
        waitForText("Archived games")
        composeRule.onNodeWithText("Viscous Coupling 1 - 0 Animal").performClick()
        waitForText("Game summary")
        composeRule.onNodeWithTag("summary-${TeamId.TEAM_ONE.name}-edit-cards")
            .performScrollTo()
            .performClick()
        composeRule.onAllNodes(hasContentDescription("Edit #7 Casey Handler", substring = true))
            .onFirst()
            .performClick()
        composeRule.onNodeWithTag("card-player-name")
            .performTextReplacement("Archived Handler")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Edit existing cards")
        composeRule.onNodeWithTag("editable-player-cards-done").performClick()
        waitForText("#7 Archived Handler: Yellow card", substring = true)

        // The game-ending hard cap archives and restores with the standard Undo End game action.
        // Undoing it returns to the pending hard-cap state and keeps both summary edits.
        composeRule.onNodeWithText("Restore game").performClick()
        waitForText("Game summary")
        composeRule.onNodeWithText("Undo End game").performClick()
        assertLiveScreen()
        val resumed = accessCurrentGameState()
        assertEquals(CapType.HARD, resumed.pendingCapOffer)
        assertEquals("Archived Handler", resumed.playerCards(TeamId.TEAM_ONE).single().playerName)
        assertTrue(resumed.undoEntry!!.label.startsWith("Undo Edit yellow"))
        waitForText("Hard cap")
        composeRule.onNodeWithText("Not yet").performClick()

        // The still-current game summary edits Team Two and persists through the ViewModel path.
        openMoreActionsDialog()
        selectMoreActionsCategory("Game details")
        composeRule.onNodeWithText("Game summary").performClick()
        waitForText("Game summary")
        composeRule.onNodeWithTag("summary-${TeamId.TEAM_TWO.name}-edit-cards")
            .performScrollTo()
            .performClick()
        composeRule.onAllNodes(hasContentDescription("Edit #23", substring = true))
            .onFirst()
            .performClick()
        composeRule.onNodeWithTag("card-player-name")
            .performTextReplacement("Team Two Handler")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Edit existing cards")
        composeRule.onNodeWithTag("editable-player-cards-done").performClick()
        waitForText("#23 Team Two Handler: Yellow card", substring = true)
        assertEquals(
            "Team Two Handler",
            accessCurrentGameState().playerCards(TeamId.TEAM_TWO).single().playerName,
        )
    }

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

        // Teams without setup-entered coach/captain names do not expose summary info actions.
        composeRule.onAllNodesWithTag("summary-${TeamId.TEAM_ONE.name}-team-info")
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("summary-${TeamId.TEAM_TWO.name}-team-info")
            .assertCountEquals(0)

        // The summary page exposes the completed game's event log.
        composeRule.onNodeWithText("Event log").performClick()
        waitForText("Event log")
        waitForText("Game over", substring = true)
        dismissDialog(text = "OK")

        // The live game-over summary can undo the End game action and return to play.
        composeRule.onNodeWithText("Undo End game").performClick()
        assertLiveScreen()
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
        val state = newSetupGameState(now = 123_000L)
            .startGame(OrientationPreference.PORTRAIT)
            .copy(
            phase = GamePhase.GAME_OVER,
            observerNames = listOf("Mike", "Gary"),
            fieldName = "Field 7",
            endEpoch = System.currentTimeMillis(),
        )
        // This bit basically mocks the Android share action.  So it needs this kind
        // of ugly composeRule.activityRule block.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                UltiObserverTheme(dynamicColor = false) {
                    GameOverSummary(
                        state = state,
                        guidanceMode = RuleGuidanceMode.FULL,
                        onStateChange = {},
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
     * Test the game summary's quick reference dialog for setup-entered team staff.
     */
    @Test
    fun gameSummaryTeamInformationDialog() {
        val setup = newSetupGameState(now = 123_000L).copy(
            teamOne = TeamState(
                name = "Viscous Coupling",
                color = TeamColorChoice.WHITE,
                coaches = "Coach Alpha",
                fieldCaptains = "Casey Captain\nMorgan Captain",
            ),
            teamTwo = TeamState(
                name = "Animal",
                color = TeamColorChoice.BLUE,
                spiritCaptains = "Riley Spirit",
            ),
        )
        startLiveGameProgrammatically(setup)
        endCurrentGameProgrammatically()
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Game summary")

        // Team 1's summary info action opens its setup-entered coach and field-captain names.
        composeRule.onNodeWithTag("summary-${TeamId.TEAM_ONE.name}-team-info")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Coach").assertIsDisplayed()
        composeRule.onNodeWithText("Coach Alpha").assertIsDisplayed()
        composeRule.onNodeWithText("Field captains").assertIsDisplayed()
        composeRule.onNodeWithText("Casey Captain\nMorgan Captain").assertIsDisplayed()
        composeRule.onAllNodesWithText("Spirit captain").assertCountEquals(0)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onAllNodesWithText("Coach Alpha").assertCountEquals(0)

        // Team 2's summary info action uses the same dialog path for its spirit captain.
        composeRule.onNodeWithTag("summary-${TeamId.TEAM_TWO.name}-team-info")
            .performScrollTo()
            .performClick()
        waitForText("Spirit captain")
        waitForText("Riley Spirit")
        composeRule.onAllNodesWithText("Coach").assertCountEquals(0)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onAllNodesWithText("Riley Spirit").assertCountEquals(0)
    }

    /**
     * Test that current and archived summaries share the same compact tournament misconduct report.
     */
    @Test
    fun shareButtonSharesText() {
        // Seed a finished game with score and misconduct entries for summary and event-log sharing.
        clearArchivedGamesProgrammatically()
        val expectedShareText = """
            Animal 15, Viscous Coupling 12
            May 19, 2026, 10:00 AM
            Animal cards:
                #7 Yellow
                #7 Yellow
                #12 Red
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
        updateCurrentStateProgrammatically {
            copy(
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
        }

        // Now end the game to trigger the game summary screen.
        // Use a helper function (below) to check that Share does share the expected text.
        endCurrentGameProgrammatically()
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Game summary")
        val expectedEventLogShareText = accessCurrentGameState().eventLogShareText()
        assertNextShareText(expectedShareText)

        // The same summary exposes the full event log through its dialog Share action.
        composeRule.onNodeWithText("Event log").performClick()
        waitForText("Event log")
        waitForText("Game over", substring = true)
        assertNextShareText(
            expectedShareText = expectedEventLogShareText,
            expectedSubject = "UltiObserver Event Log",
            shareButtonTag = "event-log-share",
        )
        dismissDialog(text = "OK")

        // The completed-game summary can archive directly, and the archived summary shares
        // the same payload.
        composeRule.onNodeWithText("Archive game").performClick()
        waitForText("See archived/saved games")
        composeRule.onNodeWithText("See archived/saved games").performClick()
        waitForText("Archived/saved games")
        composeRule.onNodeWithText("Archived games", substring = true).performClick()
        waitForText("Archived games")
        composeRule.onNodeWithText("Animal 15 - 12 Viscous Coupling").performClick()
        waitForText("Game summary")
        assertNextShareText(expectedShareText)

        // Restore the archive so the Home-screen archive action stays covered too.
        composeRule.onNodeWithText("Restore game").performClick()
        waitForText("Game summary")
        tapTopBarBack()
        waitForText("Completed game")

        // The completed-game card on Home reopens the same summary before it is archived.
        composeRule.onNodeWithText("Animal 15 - 12 Viscous Coupling").performClick()
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
        waitForText("Animal 15 - 12 Viscous Coupling")
    }

    /// Click Share, assert the outgoing Android chooser payload, and cancel the chooser.
    private fun assertNextShareText(
        expectedShareText: String,
        expectedSubject: String = "UltiObserver Game Summary",
        shareButtonTag: String? = null,
    ) {
        val expectedIntent = chooserWithShareText(
            expectedShareText = expectedShareText,
            expectedSubject = expectedSubject,
        )
        init()
        try {
            intending(expectedIntent).respondWith(Instrumentation.ActivityResult(0, null))
            if (shareButtonTag == null) {
                composeRule.onNodeWithText("Share").performClick()
            } else {
                composeRule.onNodeWithTag(shareButtonTag).performClick()
            }
            intended(expectedIntent)
        } finally {
            release()
        }
        composeRule.waitForIdle()
    }

    /// Return an Espresso matcher for the nested share intent created by Android's chooser wrapper.
    private fun chooserWithShareText(
        expectedShareText: String,
        expectedSubject: String,
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
                sendIntent.getStringExtra(Intent.EXTRA_SUBJECT) == expectedSubject &&
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
