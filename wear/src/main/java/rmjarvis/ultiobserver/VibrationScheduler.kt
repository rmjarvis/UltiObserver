package rmjarvis.ultiobserver

import rmjarvis.ultiobserver.wearprotocol.WearCountdownSnapshot

/** Schedule the current countdown independently of rendering and phone message arrival times. */
internal class VibrationScheduler(
    private val clock: () -> Long,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val keepAwake: (Boolean) -> Unit,
    private val vibrate: (VibrationPattern) -> Unit,
) {
    private data class CueKey(private val message: String, private val phoneEpochMillis: Long)
    private data class Cue(val key: CueKey, val deadline: Long, val pattern: VibrationPattern)

    private var cues = emptyList<Cue>()
    private val delivered = mutableSetOf<CueKey>()
    private var cancelTimer: (() -> Unit)? = null
    private var awake = false
    private var vibratingUntil = 0L

    /** Replace pending cues; phoneNow uses the same calibrated clock as the displayed countdown. */
    fun update(countdown: WearCountdownSnapshot?, phoneNow: Long) {
        val now = clock()
        cues = if (countdown != null && countdown.pausedAtEpochMillis == null) {
            countdown.cues.mapNotNull { cue ->
                cue.vibration?.let { pattern ->
                    Cue(
                        CueKey(cue.message, cue.targetEpochMillis),
                        now + cue.targetEpochMillis - phoneNow,
                        pattern,
                    )
                }
            }.sortedBy { it.deadline }
        } else {
            emptyList()
        }
        refresh()
    }

    /** Cancel future cues without interrupting a native vibration already in progress. */
    fun stop() {
        cues = emptyList()
        refresh()
    }

    private fun refresh() {
        cancelTimer?.invoke()
        cancelTimer = null
        val now = clock()
        // Match the phone's short delivery grace period, including the one-second cue lead.
        cues = cues.filter { it.key !in delivered && now - it.deadline <= 2_100L }
        val next = cues.firstOrNull()
        val needsAwake = next != null || now < vibratingUntil
        if (needsAwake != awake) {
            awake = needsAwake
            keepAwake(awake)
        }
        if (next != null && next.deadline <= now && now >= vibratingUntil) {
            delivered += next.key
            vibrate(next.pattern)
            vibratingUntil = now + next.pattern.timings().sum()
            refresh()
        } else if (needsAwake) {
            val deadline = if (now < vibratingUntil) vibratingUntil else next!!.deadline
            cancelTimer = schedule(deadline - now) { refresh() }
        }
    }
}
