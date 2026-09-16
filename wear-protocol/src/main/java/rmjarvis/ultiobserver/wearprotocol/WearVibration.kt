package rmjarvis.ultiobserver.wearprotocol

import kotlinx.serialization.Serializable

const val WATCH_VIBRATION_CAPABILITY = "ultiobserver_watch_vibration"
const val WATCH_VIBRATION_PATH = "/ultiobserver/vibration"

/** One phone-scheduled vibration pulse, delivered without retaining or replaying it. */
@Serializable
data class WearVibrationRequest(val durationMillis: Long)

/** Whether the watch submitted this pulse to its vibrator. */
@Serializable
data class WearVibrationResponse(val accepted: Boolean)
