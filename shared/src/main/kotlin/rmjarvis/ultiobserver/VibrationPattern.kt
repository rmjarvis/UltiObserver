package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable

/** A complete timing vibration, submitted as one action to either device's vibrator. */
@Serializable
data class VibrationPattern(
    private val durationMillis: Long,
    private val repeatCount: Int,
    private val gapMillis: Long,
) {
    /** Alternating off/on durations for Android's finite waveform API. */
    fun timings(): LongArray = LongArray(repeatCount * 2) { index ->
        when {
            index == 0 -> 0L
            index % 2 == 0 -> gapMillis
            else -> durationMillis
        }
    }
}
