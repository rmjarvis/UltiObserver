package rmjarvis.ultiobserver

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide device discovery and routing for the app's timing sounds. */
internal class SoundOutputManager(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val preferences = context.getSharedPreferences("sound_output", Context.MODE_PRIVATE)
    private val selection = SoundOutputSelection(preferences.getString("selected", null)) { key ->
        preferences.edit().putString("selected", key).apply()
    }
    private val mutableState = MutableStateFlow(SoundOutputState(
        alsoMuteVibrations = preferences.getBoolean("also_mute_vibrations", false),
    ))
    val state = mutableState.asStateFlow()
    private var devices = emptyList<AudioDeviceInfo>()
    private var activeTrack: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())
    private val routingListener = AudioRouting.OnRoutingChangedListener {
        refresh()
    }

    init {
        refresh()
        audioManager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                refresh()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                refresh()
            }
        }, handler)
    }

    /** Refresh connected outputs, clearing a selection that has disconnected. */
    fun refresh() {
        devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isMediaOutput() }
        val outputs = devices.map { it.soundOutput() }
        val previousKey = selection.selectedKey
        selection.reconcile(outputs)
        if (previousKey != selection.selectedKey) activeTrack?.setPreferredDevice(null)
        val automatic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audioManager.getAudioDevicesForAttributes(timingSoundAudioAttributes())
                .map { it.outputName() }.distinct().joinToString().ifEmpty { null }
        } else {
            null
        }
        val track = activeTrack
        val playingOn = if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            if (Build.VERSION.SDK_INT >= 36) {
                track.routedDevices.map { it.outputName() }.distinct().joinToString()
                    .ifEmpty { null }
            } else {
                track.routedDevice?.outputName()
            }
        } else {
            null
        }
        mutableState.value = mutableState.value.copy(
            outputs = outputs,
            selectedKey = selection.selectedKey,
            playingOn = playingOn,
            lastPlayedOn = playingOn ?: mutableState.value.lastPlayedOn,
            automaticOutput = automatic,
        )
    }

    /** Apply a user selection to the current sound and subsequent cues. */
    fun select(key: String?) {
        selection.select(key)
        if (key == SOUND_OUTPUT_NONE) {
            activeTrack?.stop()
            detach()
        }
        mutableState.value = mutableState.value.copy(selectionError = null, lastPlayedOn = null)
        refresh()
        activeTrack?.let { applySelection(it) }
    }

    /** Remember whether None also suppresses vibrations. */
    fun setAlsoMuteVibrations(muted: Boolean) {
        preferences.edit().putBoolean("also_mute_vibrations", muted).apply()
        mutableState.value = mutableState.value.copy(alsoMuteVibrations = muted)
    }

    /** Attach the single currently playing track, applying the latest output preference. */
    fun attach(track: AudioTrack) {
        activeTrack?.removeOnRoutingChangedListener(routingListener)
        activeTrack = track
        refresh()
        applySelection(track)
        track.addOnRoutingChangedListener(routingListener, handler)
    }

    fun detach() {
        activeTrack?.removeOnRoutingChangedListener(routingListener)
        activeTrack = null
        refresh()
    }

    private fun applySelection(track: AudioTrack) {
        val output = selection.reconcile(state.value.outputs)
        val device = output?.let { selected -> devices.single { it.id == selected.id } }
        if (!track.setPreferredDevice(device) && output != null) {
            selection.select(null)
            track.setPreferredDevice(null)
            mutableState.value = mutableState.value.copy(
                selectionError = "Selecting ${output.name} failed. Reverting to Android default.",
            )
        }
        refresh()
    }
}

internal fun timingSoundAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

private fun AudioDeviceInfo.soundOutput(): SoundOutput {
    val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) address else ""
    return SoundOutput(id, "$type:$address:$productName", outputName())
}

private fun AudioDeviceInfo.outputName(): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "Phone speaker"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
    else -> productName.toString()
}

private val mediaOutputTypes = setOf(
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_HDMI_ARC,
    AudioDeviceInfo.TYPE_HDMI_EARC,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL,
    AudioDeviceInfo.TYPE_AUX_LINE,
    AudioDeviceInfo.TYPE_DOCK,
    AudioDeviceInfo.TYPE_HEARING_AID,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
)

private fun AudioDeviceInfo.isMediaOutput(): Boolean = type in mediaOutputTypes
