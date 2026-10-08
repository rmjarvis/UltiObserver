package rmjarvis.ultiobserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Selection follows an explicit device until it disappears, then stays Automatic. */
class TestSoundOutput {
    @Test
    fun selectionAndDisconnection() {
        val speaker = SoundOutput(1, "speaker", "Phone speaker")
        val earbud = SoundOutput(2, "earbud", "Earbud")
        val watch = SoundOutput(3, "watch", "Watch")
        var savedKey: String? = null
        val selection = SoundOutputSelection(savedKey) { savedKey = it }

        // Automatic does not pin whichever device Android happens to be using.
        assertNull(selection.reconcile(listOf(speaker, earbud, watch)))
        assertNull(savedKey)

        // An explicit selection is saved and restored across connection-ID changes on restart.
        selection.select(earbud.key)
        assertEquals(earbud, selection.reconcile(listOf(speaker, earbud, watch)))
        assertEquals(earbud.key, savedKey)
        val restarted = SoundOutputSelection(savedKey) { savedKey = it }
        val reconnectedEarbud = earbud.copy(id = 20)
        val restoredOutput = restarted.reconcile(listOf(speaker, reconnectedEarbud))!!
        assertEquals(reconnectedEarbud, restoredOutput)
        assertEquals(20, restoredOutput.id)

        // Losing that output returns to Automatic, allowing Android to choose the watch or phone.
        assertNull(restarted.reconcile(listOf(speaker, watch)))
        assertNull(restarted.selectedKey)
        assertNull(savedKey)

        // Reconnecting the earbud does not restore the former explicit selection.
        assertNull(restarted.reconcile(listOf(speaker, reconnectedEarbud, watch)))

        // The observer can also explicitly return to Automatic while the output is connected.
        restarted.select(speaker.key)
        assertEquals(speaker, restarted.reconcile(listOf(speaker, watch)))
        restarted.select(null)
        assertNull(restarted.reconcile(listOf(speaker, watch)))
        assertNull(savedKey)
    }

    @Test
    fun muteSelection() {
        // Muting survives device changes and a restart until another output is selected.
        var savedKey: String? = null
        val selection = SoundOutputSelection(savedKey) { savedKey = it }
        selection.select(SOUND_OUTPUT_NONE)
        assertNull(selection.reconcile(emptyList()))
        assertEquals(SOUND_OUTPUT_NONE, savedKey)
        val restarted = SoundOutputSelection(savedKey) { savedKey = it }
        val speaker = SoundOutput(1, "speaker", "Phone speaker")
        assertNull(restarted.reconcile(listOf(speaker)))
        assertEquals(true, SoundOutputState(selectedKey = restarted.selectedKey).muted)

        // Selecting an output enables sounds again.
        restarted.select(speaker.key)
        assertEquals(speaker, restarted.reconcile(listOf(speaker)))
        assertEquals(false, SoundOutputState(selectedKey = restarted.selectedKey).muted)
        assertEquals(speaker.key, savedKey)
    }

    /** The vibration override applies only while None is selected. */
    @Test
    fun vibrationMute() {
        // Sound muting leaves vibrations enabled unless the observer opts in.
        val soundOnly = SoundOutputState(selectedKey = SOUND_OUTPUT_NONE)
        assertEquals(false, soundOnly.vibrationsMuted)
        assertEquals("All sounds are currently muted.", soundOnly.soundMuteNote)
        assertNull(soundOnly.vibrationMuteNote)

        // Opting in explains why vibration tests are disabled as well.
        val quiet = soundOnly.copy(alsoMuteVibrations = true)
        assertEquals(true, quiet.vibrationsMuted)
        assertEquals("All sounds and vibrations are currently muted.", quiet.vibrationMuteNote)

        // Another output restores vibrations without forgetting the preference for next time.
        val automatic = quiet.copy(selectedKey = null)
        assertEquals(false, automatic.vibrationsMuted)
        assertEquals(true, automatic.alsoMuteVibrations)
        assertNull(automatic.soundMuteNote)
        assertNull(automatic.vibrationMuteNote)
        assertEquals(true, automatic.copy(selectedKey = SOUND_OUTPUT_NONE).vibrationsMuted)
    }

    /** Descriptions follow the selected output without showing unrelated routing information. */
    @Test
    fun outputDescription() {
        // Android default names the anticipated destination when it is known.
        val speaker = SoundOutput(1, "speaker", "Phone speaker")
        val earbud = SoundOutput(2, "earbud", "Earbud")
        val automatic = SoundOutputState(
            outputs = listOf(speaker, earbud),
            automaticOutput = earbud.name,
        )
        assertEquals(
            "Using the default audio channel according to your Android settings (currently Earbud).",
            automatic.description(),
        )

        // An explicit output describes only that choice, even when Android's default differs.
        assertEquals(
            "Using Phone speaker.",
            automatic.copy(selectedKey = speaker.key).description(),
        )

        // The requested device and Android's reported route remain separate information.
        val routed = automatic.copy(
            selectedKey = speaker.key,
            playingOn = earbud.name,
            lastPlayedOn = earbud.name,
        )
        assertEquals(listOf(speaker, earbud), routed.outputs)
        assertEquals(speaker.key, routed.selectedKey)
        assertEquals(earbud.name, routed.automaticOutput)
        assertEquals(earbud.name, routed.playingOn)
        assertEquals(earbud.name, routed.lastPlayedOn)
        assertEquals("Using Phone speaker.", routed.description())
        assertNull(routed.selectionError)

        // A rejected selection reports the error alongside Android default, not a stale request.
        val rejected = automatic.copy(selectionError = "Output unavailable")
        assertEquals("Output unavailable", rejected.selectionError)
        assertEquals(automatic.description(), rejected.description())

        // None describes muting instead of the device Android would otherwise use.
        assertEquals(
            "Sounds are muted.",
            automatic.copy(selectedKey = SOUND_OUTPUT_NONE).description(),
        )

        // Android versions that cannot report the default destination omit its parenthetical.
        assertEquals(
            "Using the default audio channel according to your Android settings.",
            automatic.copy(automaticOutput = null).description(),
        )
    }

    @Test
    fun unavailableOrAmbiguousSavedOutput() {
        // A device missing on process startup must not retain a stale preference.
        var savedKey: String? = "earbud"
        val missing = SoundOutputSelection(savedKey) { savedKey = it }
        assertNull(missing.reconcile(emptyList()))
        assertNull(savedKey)

        // Older Android versions expose no address; identical names must not select arbitrarily.
        val first = SoundOutput(1, "earbud", "Earbud")
        val second = first.copy(id = 2)
        val ambiguous = SoundOutputSelection(first.key) { savedKey = it }
        assertNull(ambiguous.reconcile(listOf(first, second)))
        assertNull(savedKey)
    }
}
