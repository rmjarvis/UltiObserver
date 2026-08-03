package rmjarvis.ultiobserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** Auto-rotate enters in each held orientation with the appropriate Android setting behavior. */
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
            unlockedOrientation.recordHeldOrientation(
                heldOrientation = displayOrientation,
                autoRotateScreenActive = false,
                systemAutoRotateEnabled = true,
            )
            unlockedOrientation.enterActiveGame(systemAutoRotateEnabled = true)
            assertNull(unlockedOrientation.lockedOrientation)
            assertEquals(
                expectedDisplay,
                OrientationPreference.AUTO_ROTATE.displayFor(displayOrientation, phoneTopEnd),
            )

            // With Android auto-rotate disabled, entry locks the orientation in which the phone
            // is already being held and later rendering resolves from that fixed orientation.
            val lockedOrientation = AutoRotateOrientationLock()
            lockedOrientation.recordHeldOrientation(
                heldOrientation = displayOrientation,
                autoRotateScreenActive = false,
                systemAutoRotateEnabled = false,
            )
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

    /** A delayed first sensor sample completes the fixed orientation chosen on entry. */
    @Test
    fun delayedSensorReadingLocksOrientation() {
        val orientationLock = AutoRotateOrientationLock()

        // Returning from Home to an active game can be collected before Android supplies the
        // listener's first sensor sample, so entry initially has no orientation to lock.
        orientationLock.enterActiveGame(systemAutoRotateEnabled = false)
        assertNull(orientationLock.lockedOrientation)

        // The first usable sample completes that pending lock.
        val establishedLock = orientationLock.recordHeldOrientation(
            heldOrientation = ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT,
            autoRotateScreenActive = true,
            systemAutoRotateEnabled = false,
        )
        assertTrue(establishedLock)
        assertEquals(
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT,
            orientationLock.lockedOrientation,
        )

        // Later motion updates the latest held orientation without changing the session lock.
        orientationLock.recordHeldOrientation(
            heldOrientation = ActiveGameFullOrientation.PORTRAIT,
            autoRotateScreenActive = true,
            systemAutoRotateEnabled = false,
        )
        assertEquals(
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT,
            orientationLock.lockedOrientation,
        )
    }
}
