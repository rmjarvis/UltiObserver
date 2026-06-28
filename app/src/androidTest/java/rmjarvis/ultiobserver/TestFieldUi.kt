package rmjarvis.ultiobserver

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for the live field diagram layout and orientation-dependent labels.
@RunWith(AndroidJUnit4::class)
class TestFieldUi : MainActivityUiTestFixtures() {
    /**
     * Test mixed-division gender-ratio chooser markers as the choosing team moves around the field.
     */
    @Test
    fun genderRatioChooserLayout() {
        val setup = newGameSetupState().copy(
            rules = GameRules(genderRatioRule = GenderRatioRule.GEN_ZONE),
            division = GameDivision.MIXED,
            teamOne = TeamIdentity("Aardvarks", TeamColorChoice.WHITE),
            teamTwo = TeamIdentity("Beagles", TeamColorChoice.BLUE),
            pullingTeam = TeamId.TEAM_ONE,
            pullingFromEnd = FieldEnd.FAR,
            firstHalfGenZone = FieldEnd.FAR,
        )
        startLiveGameProgrammatically(setup)

        // With the Gen Zone at the top field end, the top team chooses the point ratio.
        composeRule.onAllNodesWithText("Chooses gender ratio").assertCountEquals(1)
        assertChooserMarkerAboveFieldMidpoint()

        // Moving the Gen Zone to the bottom end uses the bottom-row inline chooser marker.
        updateLiveGameState { it.copy(firstHalfGenZone = FieldEnd.NEAR) }
        composeRule.onAllNodesWithText("Chooses gender ratio").assertCountEquals(1)
        assertChooserMarkerBelowFieldMidpoint()

        // Flipping the displayed field end puts the same choosing team at the top again through
        // the opposite pull-orientation calculation.
        updateLiveGameState { it.copy(topDisplayedEnd = FieldEnd.NEAR) }
        composeRule.onAllNodesWithText("Chooses gender ratio").assertCountEquals(1)
        assertChooserMarkerAboveFieldMidpoint()
    }

    /**
     * Test the field's compact mixed-ratio badge for known ABBA ratios.
     */
    @Test
    fun genderRatioBadge() {
        val setup = newGameSetupState().copy(
            rules = GameRules(genderRatioRule = GenderRatioRule.ABBA),
            division = GameDivision.MIXED,
            initialGenderRatio = GenderRatio.FOUR_MEN_THREE_WOMEN,
        )
        startLiveGameProgrammatically(setup)

        // The opening point shows the setup-selected ABBA ratio in the center field strip.
        waitForText("4M/3W")

        // Later ABBA points can show the opposite ratio.
        updateLiveGameState {
            it.copy(
                teamOne = it.teamOne.copy(score = 1),
                initialGenderRatio = GenderRatio.FOUR_MEN_THREE_WOMEN,
            )
        }
        waitForText("4W/3M")
    }

    /**
     * Test the live field's quick reference dialog for setup-entered team staff.
     */
    @Test
    fun teamInformationDialog() {
        // Seed staff names through setup so the live field exposes the team-info action.
        val setup = newGameSetupState().copy(
            teamOne = TeamIdentity(
                name = "Viscous Coupling",
                color = TeamColorChoice.WHITE,
                coaches = "Coach Alpha\n\nCoach Beta",
                fieldCaptains = "Casey Captain",
            ),
            teamTwo = TeamIdentity(
                name = "Animal",
                color = TeamColorChoice.BLUE,
                spiritCaptains = "Riley Spirit",
            ),
        )
        startLiveGameProgrammatically(setup)

        // The dialog should show nonblank setup staff fields, using plural labels only when the
        // field has multiple entered lines.
        composeRule.onNodeWithTag("live-${TeamId.TEAM_ONE.name}-team-info").performClick()
        waitForText("Viscous Coupling")
        composeRule.onNodeWithText("Coaches").assertIsDisplayed()
        composeRule.onNodeWithText("Coach Alpha\n\nCoach Beta").assertIsDisplayed()
        composeRule.onNodeWithText("Field captain").assertIsDisplayed()
        composeRule.onNodeWithText("Casey Captain").assertIsDisplayed()
        composeRule.onAllNodesWithText("Spirit captain").assertCountEquals(0)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onAllNodesWithText("Coaches").assertCountEquals(0)

        // The bottom team row uses the same quick-reference dialog through the opposite field
        // slot callback.
        composeRule.onNodeWithTag("live-${TeamId.TEAM_TWO.name}-team-info").performClick()
        waitForText("Animal")
        composeRule.onNodeWithText("Spirit captain").assertIsDisplayed()
        composeRule.onNodeWithText("Riley Spirit").assertIsDisplayed()
        composeRule.onAllNodesWithText("Coaches").assertCountEquals(0)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onAllNodesWithText("Riley Spirit").assertCountEquals(0)
    }

    /**
     * Test how the field header shares space between team names and field-end names.
     */
    @Test
    fun fieldHeaderLongNames() {
        // Make some long names that will necessarily use ellipses, even on the wide
        // Pixel Fold screen.  Note that the end name is in a smaller font, so those
        // names need to be longer to make sure they get the ellipses.
        val longTeamOneName = "A weirdly long team name that takes up too much space, " +
            "even on the Pixel Fold's wide screen"
        val longTeamTwoName = "Another oddly verbose name for an Ultimate team that no one " +
            "would ever use"
        val longFarEndName = "Along the edge of the parking lot by the old rotting tree " +
            "with all the moss and lichen and the poison ivy that is practically on the " +
            "field by the back cone"
        val longNearEndName = "The side opposite the parking lot with all the garbage cans " +
            "and recycling cans, and they even have some green compost containers there, " +
            "as though anyone is going to use those"

        // Long team names should not clobber short field-end names.
        val longTeamSetup = newGameSetupState().copy(
            teamOne = TeamIdentity(longTeamOneName, TeamColorChoice.WHITE),
            teamTwo = TeamIdentity(longTeamTwoName, TeamColorChoice.BLUE),
            farEndName = "Far",
            nearEndName = "Near",
        )
        startLivePointProgrammatically(longTeamSetup)
        assertTopFieldHeader(
            longTeamOneName,
            "Far",
            expectedTeamEllipsized = true,
            expectedEndEllipsized = false,
        )
        updateLiveGameState { it.copy(topDisplayedEnd = FieldEnd.NEAR) }
        assertTopFieldHeader(
            longTeamTwoName,
            "Near",
            expectedTeamEllipsized = true,
            expectedEndEllipsized = false,
        )

        // Long field-end names should not clobber short team names.
        val longEndSetup = newGameSetupState().copy(
            teamOne = TeamIdentity("Amp", TeamColorChoice.WHITE),
            teamTwo = TeamIdentity("Animal", TeamColorChoice.BLUE),
            farEndName = longFarEndName,
            nearEndName = longNearEndName,
        )
        startLivePointProgrammatically(longEndSetup)
        assertTopFieldHeader(
            "Amp",
            longFarEndName,
            expectedTeamEllipsized = false,
            expectedEndEllipsized = true,
        )
        updateLiveGameState { it.copy(topDisplayedEnd = FieldEnd.NEAR) }
        assertTopFieldHeader(
            "Animal",
            longNearEndName,
            expectedTeamEllipsized = false,
            expectedEndEllipsized = true,
        )

        // When both labels are long, the start of each one should still have header space.
        val longBothSetup = newGameSetupState().copy(
            teamOne = TeamIdentity(longTeamOneName, TeamColorChoice.WHITE),
            teamTwo = TeamIdentity(longTeamTwoName, TeamColorChoice.BLUE),
            farEndName = longFarEndName,
            nearEndName = longNearEndName,
        )
        startLivePointProgrammatically(longBothSetup)
        assertTopFieldHeader(
            longTeamOneName,
            longFarEndName,
            expectedTeamEllipsized = true,
            expectedEndEllipsized = true,
        )
        updateLiveGameState { it.copy(topDisplayedEnd = FieldEnd.NEAR) }
        assertTopFieldHeader(
            longTeamTwoName,
            longNearEndName,
            expectedTeamEllipsized = true,
            expectedEndEllipsized = true,
        )
    }

    /// Assert that the chooser marker is rendered in the top half of the live field diagram.
    private fun assertChooserMarkerAboveFieldMidpoint() {
        assertTrue(chooserMarkerBounds().center.y < fieldDiagramBounds().center.y)
    }

    /// Assert that the chooser marker is rendered in the bottom half of the live field diagram.
    private fun assertChooserMarkerBelowFieldMidpoint() {
        assertTrue(chooserMarkerBounds().center.y > fieldDiagramBounds().center.y)
    }

    /// Return the visible bounds for the gender-ratio chooser marker.
    private fun chooserMarkerBounds(): Rect {
        return composeRule.onNodeWithText("Chooses gender ratio")
            .fetchSemanticsNode()
            .boundsInRoot
    }

    /// Return the visible bounds for the field diagram.
    private fun fieldDiagramBounds(): Rect {
        return composeRule.onNodeWithTag("live-field-diagram")
            .fetchSemanticsNode()
            .boundsInRoot
    }

    /**
     * Assert that the top team and field-end labels both occupy visible header space.
     *
     * @param teamName The team name expected in the top field header.
     * @param endName The field-end name expected in the top field header.
     * @param expectedTeamEllipsized Whether the team label should be visually ellipsized.
     * @param expectedEndEllipsized Whether the field-end label should be visually ellipsized.
     */
    private fun assertTopFieldHeader(
        teamName: String,
        endName: String,
        expectedTeamEllipsized: Boolean,
        expectedEndEllipsized: Boolean,
    ) {
        val teamBounds = composeRule.onNodeWithText(teamName)
            .fetchSemanticsNode()
            .boundsInRoot
        val endBounds = composeRule.onNodeWithText(endName)
            .fetchSemanticsNode()
            .boundsInRoot

        assertEquals(expectedTeamEllipsized, isTextEllipsized(teamName))
        assertEquals(expectedEndEllipsized, isTextEllipsized(endName))
        assertTrue(teamBounds.right < endBounds.left)
    }

    /// Return whether the rendered single-line text has been visually ellipsized.
    private fun isTextEllipsized(text: String): Boolean {
        val textLayoutResults = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                action(textLayoutResults)
            }
        return textLayoutResults.single().isLineEllipsized(0)
    }

    /// Update the live game state directly to focus this test on field rendering.
    private fun updateLiveGameState(update: (GameState) -> GameState) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateLiveGame(update(activity.appViewModel.liveState!!))
        }
        composeRule.waitForIdle()
    }
}
