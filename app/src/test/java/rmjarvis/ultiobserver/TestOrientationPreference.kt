package rmjarvis.ultiobserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/// Tests for resolving the active-game orientation preference and physical field-end anchor.
class TestOrientationPreference {
    /** Orientation choices provide the labels and explanations shown in Settings. */
    @Test
    fun settingText() {
        assertEquals("Portrait", OrientationPreference.PORTRAIT.label)
        assertEquals(
            "Show teams at the top and bottom of the active game screen.",
            OrientationPreference.PORTRAIT.description,
        )
        assertEquals("Landscape", OrientationPreference.LANDSCAPE.label)
        assertEquals(
            "Show teams on the left and right of the active game screen.",
            OrientationPreference.LANDSCAPE.description,
        )
        assertEquals("Auto-rotate", OrientationPreference.AUTO_ROTATE.label)
        assertEquals(
            "Follow the phone's orientation if Android's auto-rotate is enabled. " +
            "Otherwise, it will use the current phone orientation when the active " +
            "game screen opens each time.",
            OrientationPreference.AUTO_ROTATE.description,
        )
    }

    /** Auto-rotate enters and responds to Android setting changes in each orientation. */
    @Test
    fun autoRotateEntryOrientation() {
        val phoneTopEnd = FieldEnd.FAR
        val expectedDisplays = listOf(
            ActiveGameFullOrientation.PORTRAIT to
                ActiveGameDisplay(
                    orientation = ActiveGameOrientation.PORTRAIT,
                    layout = ActiveGameOrientation.PORTRAIT,
                    topOrLeftDisplayedEnd = FieldEnd.FAR,
                ),
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_LEFT to
                ActiveGameDisplay(
                    orientation = ActiveGameOrientation.LANDSCAPE,
                    layout = ActiveGameOrientation.PORTRAIT,
                    topOrLeftDisplayedEnd = FieldEnd.FAR,
                ),
            ActiveGameFullOrientation.REVERSE_PORTRAIT to
                ActiveGameDisplay(
                    orientation = ActiveGameOrientation.PORTRAIT,
                    layout = ActiveGameOrientation.PORTRAIT,
                    topOrLeftDisplayedEnd = FieldEnd.NEAR,
                ),
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT to
                ActiveGameDisplay(
                    orientation = ActiveGameOrientation.LANDSCAPE,
                    layout = ActiveGameOrientation.PORTRAIT,
                    topOrLeftDisplayedEnd = FieldEnd.NEAR,
                ),
        )

        expectedDisplays.forEach { (displayOrientation, expectedDisplay) ->
            // With Android auto-rotate enabled, entry does not establish an app-level lock and
            // the currently displayed orientation determines the active-game geometry.
            val unlockedOrientation = AutoRotateOrientationLock()
            unlockedOrientation.recordHeldOrientation(displayOrientation)
            unlockedOrientation.enterActiveGame(systemAutoRotateEnabled = true)
            assertNull(unlockedOrientation.lockedOrientation)
            assertEquals(
                expectedDisplay,
                OrientationPreference.AUTO_ROTATE.displayFor(displayOrientation, phoneTopEnd),
            )

            // With Android auto-rotate disabled, entry locks the orientation in which the phone
            // is already being held and later rendering resolves from that fixed orientation.
            val lockedOrientation = AutoRotateOrientationLock()
            lockedOrientation.recordHeldOrientation(displayOrientation)
            lockedOrientation.enterActiveGame(systemAutoRotateEnabled = false)
            assertEquals(displayOrientation, lockedOrientation.lockedOrientation)
            assertEquals(
                expectedDisplay,
                OrientationPreference.AUTO_ROTATE.displayFor(
                    lockedOrientation.lockedOrientation!!,
                    phoneTopEnd,
                ),
            )
        }

        // Later sensor samples do not change an existing session lock. Enabling Android
        // auto-rotate releases it; disabling auto-rotate fixes the currently rendered
        // orientation; and leaving the active game clears it.
        val orientationLock = AutoRotateOrientationLock()
        orientationLock.recordHeldOrientation(ActiveGameFullOrientation.PORTRAIT)
        orientationLock.enterActiveGame(systemAutoRotateEnabled = false)
        orientationLock.recordHeldOrientation(
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT
        )
        assertEquals(
            ActiveGameFullOrientation.PORTRAIT,
            orientationLock.lockedOrientation,
        )
        assertEquals(
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT,
            orientationLock.latestHeldOrientation,
        )

        orientationLock.systemAutoRotateChanged(
            enabled = true,
            currentDisplayOrientation = ActiveGameFullOrientation.PORTRAIT,
        )
        assertNull(orientationLock.lockedOrientation)
        orientationLock.systemAutoRotateChanged(
            enabled = false,
            currentDisplayOrientation = ActiveGameFullOrientation.REVERSE_PORTRAIT,
        )
        assertEquals(
            ActiveGameFullOrientation.REVERSE_PORTRAIT,
            orientationLock.lockedOrientation,
        )
        orientationLock.leaveActiveGame()
        assertNull(orientationLock.lockedOrientation)

        // An unknown sensor value should not replace the last usable held orientation.
        orientationLock.recordHeldOrientation(sensorDegrees = 90)
        assertEquals(
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT,
            orientationLock.latestHeldOrientation,
        )
        orientationLock.recordHeldOrientation(
            sensorDegrees = android.view.OrientationEventListener.ORIENTATION_UNKNOWN
        )
        assertEquals(
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT,
            orientationLock.latestHeldOrientation,
        )

        // A disabled Auto-rotate session normally resolves from its established lock. If Android
        // has not supplied a usable sensor sample, it falls back to the rendered display instead.
        orientationLock.enterActiveGame(systemAutoRotateEnabled = false)
        assertEquals(
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT,
            orientationLock.resolvedOrientation(
                currentDisplayOrientation = ActiveGameFullOrientation.PORTRAIT,
            ),
        )
        assertEquals(
            ActiveGameFullOrientation.REVERSE_PORTRAIT,
            AutoRotateOrientationLock().resolvedOrientation(
                currentDisplayOrientation = ActiveGameFullOrientation.REVERSE_PORTRAIT,
            ),
        )
    }

    /** Fixed orientation choices ignore the phone's current readable orientation. */
    @Test
    fun fixedDisplays() {
        val displayOrientation = ActiveGameFullOrientation.REVERSE_PORTRAIT
        val portraitDisplay = OrientationPreference.PORTRAIT.displayFor(
            displayOrientation,
            FieldEnd.FAR,
        )
        assertEquals(ActiveGameOrientation.PORTRAIT, portraitDisplay.orientation)
        assertEquals(ActiveGameOrientation.PORTRAIT, portraitDisplay.layout)
        assertEquals(FieldEnd.FAR, portraitDisplay.topOrLeftDisplayedEnd)

        val landscapeDisplay = OrientationPreference.LANDSCAPE.displayFor(
            displayOrientation,
            FieldEnd.FAR,
        )
        assertEquals(ActiveGameOrientation.LANDSCAPE, landscapeDisplay.orientation)
        assertEquals(ActiveGameOrientation.LANDSCAPE, landscapeDisplay.layout)
        assertEquals(FieldEnd.FAR, landscapeDisplay.topOrLeftDisplayedEnd)
    }

    /** Android display and sensor values map to each readable active-game orientation. */
    @Test
    fun androidOrientationValues() {
        assertEquals(
            ActiveGameFullOrientation.PORTRAIT,
            displayOrientation(android.view.Surface.ROTATION_0),
        )
        assertEquals(
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_LEFT,
            displayOrientation(android.view.Surface.ROTATION_90),
        )
        assertEquals(
            ActiveGameFullOrientation.REVERSE_PORTRAIT,
            displayOrientation(android.view.Surface.ROTATION_180),
        )
        assertEquals(
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT,
            displayOrientation(android.view.Surface.ROTATION_270),
        )
        assertThrows(IllegalStateException::class.java) {
            displayOrientation(-1)
        }

        assertNull(heldOrientation(android.view.OrientationEventListener.ORIENTATION_UNKNOWN))
        assertEquals(ActiveGameFullOrientation.PORTRAIT, heldOrientation(0))
        assertEquals(ActiveGameFullOrientation.PORTRAIT, heldOrientation(44))
        assertEquals(
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT,
            heldOrientation(45),
        )
        assertEquals(ActiveGameFullOrientation.REVERSE_PORTRAIT, heldOrientation(135))
        assertEquals(
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_LEFT,
            heldOrientation(225),
        )
        assertEquals(ActiveGameFullOrientation.PORTRAIT, heldOrientation(315))

        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActiveGameFullOrientation.PORTRAIT.requestedActivityOrientation,
        )
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_LEFT.requestedActivityOrientation,
        )
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActiveGameFullOrientation.REVERSE_PORTRAIT.requestedActivityOrientation,
        )
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT.requestedActivityOrientation,
        )
    }

    /** Display callbacks update the activity only when Android reports its own display. */
    @Test
    fun displayChangeTarget() {
        var updateCount = 0
        val updateActivityDisplay = { updateCount += 1 }

        handleDisplayChange(
            changedDisplayId = 4,
            activityDisplayId = 4,
            onActivityDisplayChanged = updateActivityDisplay,
        )
        assertEquals(1, updateCount)

        handleDisplayChange(
            changedDisplayId = 7,
            activityDisplayId = 4,
            onActivityDisplayChanged = updateActivityDisplay,
        )
        assertEquals(1, updateCount)
    }
}
