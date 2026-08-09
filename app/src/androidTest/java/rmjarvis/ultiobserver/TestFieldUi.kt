package rmjarvis.ultiobserver

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.device.EspressoDevice
import androidx.test.espresso.device.action.ScreenOrientation
import androidx.test.espresso.device.action.setScreenOrientation as screenOrientationAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for the active-game field diagram and orientation-dependent labels.
@RunWith(AndroidJUnit4::class)
class TestFieldUi : MainActivityUiTestFixtures() {
    /** Auto-rotate delegates enabled rotation to Android and fixes the view when disabled. */
    @Test
    fun autoRotateSystemSettingBehavior() {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            uiAutomation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SETTINGS)
        } catch (_: NoSuchMethodError) {
            // API 26 doesn't allow this, so just skip this test on those devices.
            return
        }
        val originalAutoRotate = readSystemAutoRotate()
        val originalScreenOrientation = currentScreenOrientation()

        try {
            // With Android auto-rotate enabled, the active-game screen follows real device
            // rotations while retaining the portrait Near/Far field-end layout.
            setSystemAutoRotate(true)
            setAutoRotateOrientationPreference()
            setAutomaticallyLockLivePoint(true)
            startLiveGameProgrammatically()
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
                composeRule.activity.requestedOrientation,
            )
            setScreenOrientation(ScreenOrientation.PORTRAIT)
            assertAutoRotateDisplay(
                expectedOrientation = ActiveGameOrientation.PORTRAIT,
                stage = "initial portrait",
            )
            assertLiveScreen()

            // Restarting the visible activity rechecks the unchanged Android setting without
            // resetting the current Auto-rotate session.
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitForIdle()
            assertTrue(readSystemAutoRotate())
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
                composeRule.activity.requestedOrientation,
            )
            assertLiveScreen()

            setScreenOrientation(ScreenOrientation.LANDSCAPE)
            assertTrue(readSystemAutoRotate())
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
                composeRule.activity.requestedOrientation,
            )
            assertAutoRotateDisplay(
                expectedOrientation = ActiveGameOrientation.LANDSCAPE,
                stage = "landscape rotation",
            )
            assertLiveScreen()

            setScreenOrientation(ScreenOrientation.PORTRAIT)
            assertAutoRotateDisplay(
                expectedOrientation = ActiveGameOrientation.PORTRAIT,
                stage = "return to portrait",
            )
            assertLiveScreen()

            // A normal field action keeps the same Auto-rotate session active rather than
            // re-entering it or resetting any fixed orientation.
            composeRule.onNodeWithText("Start point").performClick()
            waitForText("Slide right to unlock")
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
                composeRule.activity.requestedOrientation,
            )

            // Disabling Android auto-rotate fixes the currently rendered orientation.
            setSystemAutoRotate(false)
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                composeRule.activity.requestedOrientation,
            )
            assertLiveScreen()

            // Leaving clears the session lock. Re-entering while Android auto-rotate remains
            // disabled establishes a new lock for this visit to the active-game screen.
            clearCurrentGameProgrammatically()
            startLiveGameProgrammatically()
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                composeRule.activity.requestedOrientation,
            )
            assertLiveScreen()

            // Re-enabling Android auto-rotate releases the fixed orientation.
            setSystemAutoRotate(true)
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
                composeRule.activity.requestedOrientation,
            )

            // Screens outside the active game remain portrait.
            clearCurrentGameProgrammatically()
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                composeRule.activity.requestedOrientation,
            )
        } finally {
            try {
                clearCurrentGameProgrammatically()
                setSystemAutoRotate(originalAutoRotate)
                setScreenOrientation(originalScreenOrientation)
            } finally {
                uiAutomation.dropShellPermissionIdentity()
            }
        }
    }

    /**
     * Test mixed-division gender-ratio chooser markers as the choosing team moves around the field.
     */
    @Test
    fun genderRatioChooserLayout() {
        val setup = newSetupGameState(now = 123_000L).copy(
            rules = GameRules(genderRatioRule = GenderRatioRule.GEN_ZONE),
            division = GameDivision.MIXED,
            teamOne = TeamState("Aardvarks", TeamColorChoice.WHITE),
            teamTwo = TeamState("Beagles", TeamColorChoice.BLUE),
            pullingTeam = TeamId.TEAM_ONE,
            pullingFromEnd = FieldEnd.FAR,
            firstHalfGenZone = FieldEnd.FAR,
        )
        startLiveGameProgrammatically(setup)

        // With the Gen Zone at the top field end, the top team chooses the point ratio.
        composeRule.onAllNodesWithText("Chooses ratio").assertCountEquals(1)
        assertChooserMarkerAtFirstFieldEnd()

        // Moving the Gen Zone to the bottom end uses the bottom-row inline chooser marker.
        updateCurrentGameState { it.copy(firstHalfGenZone = FieldEnd.NEAR) }
        composeRule.onAllNodesWithText("Chooses ratio").assertCountEquals(1)
        assertChooserMarkerAtSecondFieldEnd()

        // Flipping the displayed field end puts the same choosing team at the top again through
        // the opposite pull-orientation calculation.
        updateCurrentGameState { it.copy(topDisplayedEnd = FieldEnd.NEAR) }
        composeRule.onAllNodesWithText("Chooses ratio").assertCountEquals(1)
        assertChooserMarkerAtFirstFieldEnd()
    }

    /**
     * Test the field's compact mixed-ratio badge for known ABBA ratios.
     */
    @Test
    fun genderRatioBadge() {
        val setup = newSetupGameState(now = System.currentTimeMillis()).copy(
            rules = GameRules(genderRatioRule = GenderRatioRule.ABBA),
            division = GameDivision.MIXED,
            initialGenderRatio = GenderRatio.FOUR_MEN_THREE_WOMEN,
        )
        setShowAbbaRatioAsSequence(true)
        startLiveGameProgrammatically(setup)

        // The opening point shows the default ABBA sequence shorthand in the center field strip.
        waitForText("M2")

        // Later ABBA points can show the next sequence shorthand.
        updateCurrentGameState {
            it.copy(
                teamOne = it.teamOne.copy(score = 1),
                initialGenderRatio = GenderRatio.FOUR_MEN_THREE_WOMEN,
            )
        }
        waitForText("W1")

        // The settings option can return the same badge to full-ratio text.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateSettings(
                activity.appViewModel.settings.withShowAbbaRatioAsSequence(false)
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
        val setup = newSetupGameState(now = 123_000L).copy(
            teamOne = TeamState(
                name = "Viscous Coupling",
                color = TeamColorChoice.WHITE,
                coaches = "Coach Alpha\n\nCoach Beta",
                fieldCaptains = "Casey Captain",
            ),
            teamTwo = TeamState(
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
     * Test the live field's rules reference.
     */
    @Test
    fun rulesReference() {
        // Seed representative rules through setup so the live field exposes the rules quick
        // reference and renders the JVM-computed row values.
        val setup = newSetupGameState(now = 123_000L).copy(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(
                gameTo = 15,
                halftimeMinutes = 7,
                useHalfCap = true,
                halfCapMinutes = 45,
                useSoftCap = true,
                nominalSoftCapMinutes = 90,
                useHardCap = true,
                nominalHardCapMinutes = 105,
                timeoutsPerHalf = 2,
                heatLevel = HeatLevel.LEVEL_1,
                waterBreakMinutes = 4,
                genderRatioRule = GenderRatioRule.ABBA,
            ),
            division = GameDivision.MIXED,
        )
        startLiveGameProgrammatically(setup)

        // Opening the rules affordance shows the rules reference in the field view.
        composeRule.onNodeWithTag("live-game-rules").performClick()
        waitForText("Game rules")
        composeRule.onNodeWithText("Game to").assertIsDisplayed()
        composeRule.onNodeWithText("Half cap").assertIsDisplayed()
        composeRule.onNodeWithText("10:45 AM").assertIsDisplayed()
        composeRule.onNodeWithText("11:30 AM").assertIsDisplayed()
        composeRule.onNodeWithText("11:45 AM").assertIsDisplayed()
        composeRule.onNodeWithText("Heat level").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Level 1").assertIsDisplayed()
        composeRule.onNodeWithText("60 sec").assertIsDisplayed()
        composeRule.onNodeWithText("Gender ratio").assertIsDisplayed()
        composeRule.onNodeWithText("ABBA").assertIsDisplayed()
        dismissDialog(text = "OK")
        composeRule.onAllNodesWithText("Game rules").assertCountEquals(0)

        // Level 2 visibly connects the adjusted caps, between-points time, and heat level.
        updateCurrentStateProgrammatically {
            copy(rules = rules.withHeatLevel(HeatLevel.LEVEL_2))
        }
        composeRule.onNodeWithTag("live-game-rules").performClick()
        waitForText("Game rules")
        composeRule.onNodeWithText("11:10 AM").assertIsDisplayed()
        composeRule.onNodeWithText("11:30 AM").assertIsDisplayed()
        composeRule.onNodeWithText("Level 2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("120 sec").assertIsDisplayed()
        dismissDialog(text = "OK")
        composeRule.onAllNodesWithText("Game rules").assertCountEquals(0)
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
        val longTeamSetup = newSetupGameState(now = 123_000L).copy(
            teamOne = TeamState(longTeamOneName, TeamColorChoice.WHITE),
            teamTwo = TeamState(longTeamTwoName, TeamColorChoice.BLUE),
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
        updateCurrentGameState { it.copy(topDisplayedEnd = FieldEnd.NEAR) }
        assertTopFieldHeader(
            longTeamTwoName,
            "Near",
            expectedTeamEllipsized = true,
            expectedEndEllipsized = false,
        )

        // Long field-end names should not clobber short team names.
        val longEndSetup = newSetupGameState(now = 123_000L).copy(
            teamOne = TeamState("Amp", TeamColorChoice.WHITE),
            teamTwo = TeamState("Animal", TeamColorChoice.BLUE),
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
        updateCurrentGameState { it.copy(topDisplayedEnd = FieldEnd.NEAR) }
        assertTopFieldHeader(
            "Animal",
            longNearEndName,
            expectedTeamEllipsized = false,
            expectedEndEllipsized = true,
        )

        // When both labels are long, the start of each one should still have header space.
        val longBothSetup = newSetupGameState(now = 123_000L).copy(
            teamOne = TeamState(longTeamOneName, TeamColorChoice.WHITE),
            teamTwo = TeamState(longTeamTwoName, TeamColorChoice.BLUE),
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
        updateCurrentGameState { it.copy(topDisplayedEnd = FieldEnd.NEAR) }
        assertTopFieldHeader(
            longTeamTwoName,
            longNearEndName,
            expectedTeamEllipsized = true,
            expectedEndEllipsized = true,
        )
    }

    /// Assert that the chooser marker is rendered at the top or left field end.
    private fun assertChooserMarkerAtFirstFieldEnd() {
        val chooserCenter = chooserMarkerBounds().center
        val fieldCenter = fieldDiagramBounds().center
        if (testOrientationPreference() == OrientationPreference.LANDSCAPE) {
            assertTrue(chooserCenter.x < fieldCenter.x)
        } else {
            assertTrue(chooserCenter.y < fieldCenter.y)
        }
    }

    /// Assert that the chooser marker is rendered at the bottom or right field end.
    private fun assertChooserMarkerAtSecondFieldEnd() {
        val chooserCenter = chooserMarkerBounds().center
        val fieldCenter = fieldDiagramBounds().center
        if (testOrientationPreference() == OrientationPreference.LANDSCAPE) {
            assertTrue(chooserCenter.x > fieldCenter.x)
        } else {
            assertTrue(chooserCenter.y > fieldCenter.y)
        }
    }

    /// Return the visible bounds for the gender-ratio chooser marker.
    private fun chooserMarkerBounds(): Rect {
        return composeRule.onNodeWithText("Chooses ratio")
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
        assertEquals(expectedTeamEllipsized, isTextEllipsized(teamName))
        assertEquals(expectedEndEllipsized, isTextEllipsized(endName))
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

    /// Update the current game state directly to focus this test on field rendering.
    private fun updateCurrentGameState(update: (GameState) -> GameState) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateCurrentGame(update(activity.appViewModel.currentGame!!))
        }
        composeRule.waitForIdle()
    }

    /// Return Android's current sensor-driven rotation setting.
    private fun readSystemAutoRotate(): Boolean {
        return Settings.System.getInt(
            composeRule.activity.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0,
        ) == 1
    }

    /// Change Android's sensor-driven rotation setting and wait for its observer callback.
    private fun setSystemAutoRotate(enabled: Boolean) {
        composeRule.activityRule.scenario.onActivity { activity ->
            assertTrue(
                Settings.System.putInt(
                    activity.contentResolver,
                    Settings.System.ACCELEROMETER_ROTATION,
                    if (enabled) 1 else 0,
                )
            )
        }
        composeRule.waitForIdle()
    }

    /// Return the current coarse device orientation used by Espresso Device.
    private fun currentScreenOrientation(): ScreenOrientation {
        return if (
            composeRule.activity.resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE
        ) {
            ScreenOrientation.LANDSCAPE
        } else {
            ScreenOrientation.PORTRAIT
        }
    }

    /// Rotate the emulator itself and wait synchronously for Android to apply the change.
    private fun setScreenOrientation(orientation: ScreenOrientation) {
        EspressoDevice.onDevice().perform(screenOrientationAction(orientation))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        composeRule.waitForIdle()
    }

    /// Verify Auto-rotate resolves the current screen geometry and its Near/Far field layout.
    private fun assertAutoRotateDisplay(
        expectedOrientation: ActiveGameOrientation,
        stage: String,
    ) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val expectedConfiguration = when (expectedOrientation) {
                ActiveGameOrientation.PORTRAIT -> Configuration.ORIENTATION_PORTRAIT
                ActiveGameOrientation.LANDSCAPE -> Configuration.ORIENTATION_LANDSCAPE
            }
            assertEquals(
                stage,
                expectedConfiguration,
                activity.resources.configuration.orientation,
            )
            val state = activity.appViewModel.currentGame!!
            val activeGameDisplay = activity.appViewModel.settings.orientationPreference.displayFor(
                displayOrientation = displayOrientation(activity.display!!.rotation),
                phoneTopEnd = state.topDisplayedEnd,
            )
            assertEquals(stage, expectedOrientation, activeGameDisplay.orientation)
            assertEquals(
                stage,
                ActiveGameOrientation.PORTRAIT,
                activeGameDisplay.layout,
            )
        }
    }
}
