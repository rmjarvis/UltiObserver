package rmjarvis.ultiobserver.wearprotocol

import kotlinx.serialization.Serializable
import rmjarvis.ultiobserver.VibrationPattern

const val WATCH_VIBRATION_CAPABILITY = "ultiobserver_watch_vibration_v2"
const val WATCH_VIBRATION_PATH = "/ultiobserver/vibration-v2"

/** One complete phone-scheduled vibration, delivered without retaining or replaying it. */
@Serializable
data class WearVibrationRequest(val pattern: VibrationPattern)

/** Whether the watch submitted the complete pattern to its vibrator. */
@Serializable
data class WearVibrationResponse(val accepted: Boolean)
