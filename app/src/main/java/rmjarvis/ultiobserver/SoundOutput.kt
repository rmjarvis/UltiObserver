package rmjarvis.ultiobserver

/** A connected output, with a persistent identity separate from Android's connection ID. */
internal data class SoundOutput(val id: Int, val key: String, val name: String)

internal const val SOUND_OUTPUT_NONE = "none"

/** Output choices and Android's current routing information for the Home dialog. */
internal data class SoundOutputState(
    val outputs: List<SoundOutput> = emptyList(),
    val selectedKey: String? = null,
    val alsoMuteVibrations: Boolean = false,
    val playingOn: String? = null,
    val lastPlayedOn: String? = null,
    val automaticOutput: String? = null,
    val selectionError: String? = null,
) {
    val muted: Boolean get() = selectedKey == SOUND_OUTPUT_NONE
    val vibrationsMuted: Boolean get() = muted && alsoMuteVibrations
    val soundMuteNote: String? get() = if (muted) "All sounds are currently muted." else null
    val vibrationMuteNote: String? get() = if (vibrationsMuted) {
        "All sounds and vibrations are currently muted."
    } else {
        null
    }

    /** Describe the selected output, including Android's default destination when known. */
    fun description(): String {
        return if (muted) {
            "Sounds are muted."
        } else if (selectedKey == null) {
            var description = "Using the default audio channel according to your Android settings"
            if (automaticOutput != null) description += " (currently $automaticOutput)"
            "$description."
        } else {
            val selected = outputs.single { it.key == selectedKey }
            "Using ${selected.name}."
        }
    }
}

/** Remembers an explicit output only while it remains unambiguously available. */
internal class SoundOutputSelection(
    savedKey: String?,
    private val save: (String?) -> Unit,
) {
    var selectedKey: String? = savedKey
        private set

    fun select(key: String?) {
        selectedKey = key
        save(key)
    }

    fun reconcile(outputs: List<SoundOutput>): SoundOutput? {
        val key = selectedKey ?: return null
        if (key == SOUND_OUTPUT_NONE) return null
        val output = outputs.singleOrNull { it.key == key }
        if (output == null) select(null)
        return output
    }
}
