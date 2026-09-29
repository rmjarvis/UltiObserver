package rmjarvis.ultiobserver

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.WearableListenerService
import rmjarvis.ultiobserver.wearprotocol.WATCH_VIBRATION_PATH
import rmjarvis.ultiobserver.wearprotocol.WearProtocolCodec
import rmjarvis.ultiobserver.wearprotocol.WearVibrationRequest
import rmjarvis.ultiobserver.wearprotocol.WearVibrationResponse

/** Receive timing vibration patterns even when the watch screen is off or another app is visible. */
class VibrationService : WearableListenerService() {
    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray> {
        require(path == WATCH_VIBRATION_PATH)
        val vibration = WearProtocolCodec.decode(WearVibrationRequest.serializer(), request)
        val accepted = performVibration(vibration.pattern)
        return Tasks.forResult(WearProtocolCodec.encode(
            WearVibrationResponse.serializer(), WearVibrationResponse(accepted),
        ))
    }
}

/** Submit one complete alarm-category pattern and report hardware availability. */
internal fun Context.performVibration(pattern: VibrationPattern): Boolean {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        getSystemService(Vibrator::class.java)
    }
    val accepted = vibrator.hasVibrator()
    if (accepted) {
        val effect = VibrationEffect.createWaveform(pattern.timings(), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
        } else {
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
    }
    return accepted
}
