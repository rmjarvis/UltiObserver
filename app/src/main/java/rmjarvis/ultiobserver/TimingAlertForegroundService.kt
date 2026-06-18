package rmjarvis.ultiobserver

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Foreground Android service for timing alerts while the screen may be asleep.
 *
 * This service is the Android-owned runtime used after Compose has created or updated a live game.
 * Compose does not call service methods directly. Instead it sends an Intent, which is Android's
 * message envelope for components such as services, activities, and broadcast receivers. Intent
 * extras are primitive values, so this file serializes a compact alert snapshot to JSON before
 * crossing that component boundary, then decodes it when the service receives the command.
 *
 * Two Android tools are combined here because they solve different sleep problems:
 *
 * - Countdown cues are close to recent user activity and often need second-level timing. The
 *   service keeps a partial wake lock only while an active countdown still has due or upcoming
 *   cues. A partial wake lock keeps the CPU awake without turning on the screen, so the scheduler
 *   can wait accurately enough for pull, timeout, misconduct, and halftime countdown alerts.
 * - Cap cues may be far away from any recent phone activity and there are only a few per game. The
 *   service schedules the next alert-enabled cap cue with AlarmManager. Android owns that
 *   PendingIntent until the cap time arrives, wakes the app with a broadcast, and the broadcast
 *   receiver starts this service long enough to play the cap sound or haptic cue.
 *
 * Android requires every foreground service to show a notification object. On Android 13+ the user
 * may still hide that notification from the drawer by denying notification permission, but the
 * service must provide the notification when calling startForeground. UltiObserver does not prompt
 * for notification permission, so users may never see this notification on phones where
 * notifications default to off. If the user enables notifications manually, Android shows it as a
 * silent "Timing alerts are active." status notification.
 *
 * The service is allowed to live across normal app backgrounding so one game can keep using the
 * same alert runtime. A watchdog stops it after 3 hours without a fresh update or cap alarm,
 * which bounds the cost if the user abandons a game without reopening UltiObserver.
 */
class TimingAlertForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timingAlertPlayer: TimingAlertPlayer? = null
    private var scheduleJob: Job? = null
    private var lifetimeJob: Job? = null
    private var timingAlertSnapshot: TimingAlertServiceSnapshot? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /// Return null because this service uses start commands rather than bound calls.
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Dispatch Android start commands into either a state update or a cap-alarm delivery.
     *
     * ACTION_UPDATE is sent by Compose whenever active game state or alert settings change.
     * ACTION_CAP_ALARM is sent by TimingAlertAlarmReceiver after AlarmManager fires a scheduled cap
     * PendingIntent. Returning START_NOT_STICKY tells Android not to recreate this service later
     * with a null intent if it is killed; Compose or AlarmManager will send a fresh command when
     * needed.
     *
     * @param intent Android command message identifying the service action and carrying serialized
     * extras.
     * @param flags Android service restart flags.
     * @param startId Android start id for this service command.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE -> handleTimingAlertUpdate(intent)
            ACTION_CAP_ALARM -> handleCapAlarm(intent)
            else -> stopTimingAlertService()
        }
        return START_NOT_STICKY
    }

    /// Release playback, alarm, wake-lock, and coroutine resources when Android stops the service.
    override fun onDestroy() {
        scheduleJob?.cancel()
        lifetimeJob?.cancel()
        timingAlertPlayer?.release()
        cancelCapTimingAlertAlarm()
        releaseTimingAlertWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    /// Put this service into the foreground with Android's required persistent notification.
    private fun startTimingAlertForeground() {
        createNotificationChannel()
        val notification = buildTimingAlertNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                TIMING_ALERT_SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(TIMING_ALERT_SERVICE_NOTIFICATION_ID, notification)
        }
    }

    /// Create the notification channel used by the foreground service on Android 8+.
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            TIMING_ALERT_SERVICE_CHANNEL_ID,
            "Timing alerts",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps UltiObserver timing alerts active."
        }
        notificationManager.createNotificationChannel(channel)
    }

    /// Build the foreground-service notification, including a tap target back to the app.
    private fun buildTimingAlertNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, TIMING_ALERT_SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timing_alert_notification)
            .setContentTitle("UltiObserver timing alerts")
            .setContentText("Timing alerts are active.")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    /**
     * Apply the latest active-game state sent from Compose and start background alert delivery.
     *
     * The app shell resends this update whenever the live state or timing-alert settings change.
     * That makes this service a replaceable consumer of the current snapshot rather than an owner
     * of game rules. Valid updates refresh the maximum service lifetime, replace in-memory state,
     * start the countdown loop, and reschedule the next cap alarm from the latest snapshot.
     *
     * @param intent ACTION_UPDATE message carrying compact alert state.
     */
    private fun handleTimingAlertUpdate(intent: Intent) {
        startTimingAlertForeground()
        val updatedSnapshot = intent.getStringExtra(EXTRA_TIMING_ALERT_SNAPSHOT)
            ?.let { encoded -> appStateJson.decodeFromString<TimingAlertServiceSnapshot>(encoded) }

        if (updatedSnapshot == null) {
            stopTimingAlertService()
            return
        }

        refreshTimingAlertServiceLifetime()
        timingAlertSnapshot = updatedSnapshot
        if (timingAlertPlayer == null) {
            timingAlertPlayer = TimingAlertPlayer(context = this)
        }
        updateTimingAlertWakeLock(now = System.currentTimeMillis())
        updateCapTimingAlertAlarm(now = System.currentTimeMillis())
        startScheduleLoop()
    }

    /// Start the countdown scheduler coroutine if it is not already active.
    private fun startScheduleLoop() {
        if (scheduleJob?.isActive == true) {
            return
        }
        scheduleJob = serviceScope.launch {
            runTimingAlertLoop()
        }
    }

    /**
     * Deliver active-countdown cues while holding a wake lock only when countdown cues remain.
     *
     * The loop intentionally ignores cap cues. Cap cues are scheduled through AlarmManager so the
     * CPU can sleep between distant cap times. Countdown cues are checked here because their
     * spacing can be much tighter than Android's idle-alarm cadence.
     */
    private suspend fun runTimingAlertLoop() {
        while (serviceScope.isActive) {
            val snapshot = timingAlertSnapshot ?: return
            val player = timingAlertPlayer ?: return
            val now = System.currentTimeMillis()
            updateTimingAlertWakeLock(now)
            val dueTimingAlerts = snapshot.countdownCues.dueTimingAlertServiceCues(now)
            if (dueTimingAlerts.isNotEmpty()) {
                playTimingAlerts(dueTimingAlerts, snapshot, player)
                continue
            }

            val nextTimingAlert = snapshot.countdownCues.nextTimingAlertServiceCue(now)
            if (nextTimingAlert == null) {
                return
            }

            val readyToPlay = waitForTimingAlertDeliveryWindow(
                millisUntilNextAlert = nextTimingAlert.targetEpoch - now,
                scheduleCheckMillis = TIMING_ALERT_SERVICE_SCHEDULE_CHECK_MS,
                delayMillis = { millis -> delay(millis) },
            )
            if (readyToPlay) {
                playTimingAlerts(listOf(nextTimingAlert), snapshot, player)
            }
        }
    }

    /**
     * Deliver timing alerts and remove them before playback to avoid loop repeats.
     *
     * @param cues The cues to deliver.
     * @param snapshot Compact alert settings for playback.
     * @param player Sound player used for audible cues.
     */
    private suspend fun playTimingAlerts(
        cues: List<TimingAlertServiceCue>,
        snapshot: TimingAlertServiceSnapshot,
        player: TimingAlertPlayer,
    ) {
        // This is effectively a one-cue list in normal operation. Keep the list shape because the
        // due-cue helper naturally returns an empty list for "none due" and can defensively return
        // more than one cue if Android wakes the service after multiple scheduled instants.
        cues.forEach { cue ->
            removeTimingAlertServiceCue(cue)
            if (cue.alertMode == TimingAlertMode.VIBRATE) {
                repeat(cue.repeatCount) { pulseIndex ->
                    performTimingCueHaptic(snapshot.vibrationDurationMillis)
                    if (pulseIndex < cue.repeatCount - 1) {
                        delay(snapshot.vibrationDurationMillis + TIMING_ALERT_REPEAT_HAPTIC_GAP_MS)
                    }
                }
            } else {
                player.play(
                    cue.alertMode.toTimingAlertSound(),
                    cue.repeatCount,
                    snapshot.soundVolume,
                )
                if (snapshot.vibrateWithSounds) {
                    performTimingCueHaptic(snapshot.vibrationDurationMillis)
                }
            }
        }
    }

    /**
     * Remove a delivered cue from the in-memory service snapshot.
     *
     * @param cue The cue that has been accepted for playback.
     */
    private fun removeTimingAlertServiceCue(cue: TimingAlertServiceCue) {
        val currentSnapshot = timingAlertSnapshot ?: return
        val alertKey = cue.alertKey()
        timingAlertSnapshot = currentSnapshot.copy(
            countdownCues = currentSnapshot.countdownCues.filterNot { it.alertKey() == alertKey },
            capCues = currentSnapshot.capCues.filterNot { it.alertKey() == alertKey },
        )
    }

    /// Refresh the watchdog that stops abandoned foreground timing-alert services.
    private fun refreshTimingAlertServiceLifetime() {
        lifetimeJob?.cancel()
        lifetimeJob = serviceScope.launch {
            delay(TIMING_ALERT_SERVICE_MAX_LIFETIME_MS)
            stopTimingAlertService()
        }
    }

    /// Release service-held resources and ask Android to stop this service.
    private fun stopTimingAlertService() {
        releaseTimingAlertWakeLock()
        cancelCapTimingAlertAlarm()
        stopSelf()
    }

    /// Acquire or release the partial wake lock according to current countdown cue state.
    private fun updateTimingAlertWakeLock(now: Long) {
        val snapshot = timingAlertSnapshot ?: run {
            releaseTimingAlertWakeLock()
            return
        }
        if (snapshot.countdownCues.nextTimingAlertServiceCue(now) != null ||
            snapshot.countdownCues.dueTimingAlertServiceCues(now).isNotEmpty()
        ) {
            acquireTimingAlertWakeLock()
        } else {
            releaseTimingAlertWakeLock()
        }
    }

    /// Keep the CPU awake while countdown timing-alert delivery needs precise scheduling.
    private fun acquireTimingAlertWakeLock() {
        if (wakeLock?.isHeld == true) {
            return
        }
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:TimingAlertForegroundService",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    /// Release any wake lock held for countdown timing-alert delivery.
    private fun releaseTimingAlertWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wakeLock = null
    }

    /**
     * Schedule the next alert-enabled cap cue, or cancel the cap alarm when none should fire.
     *
     * The snapshot builder has already removed cap cues with no alert. This method only chooses the
     * next strictly future cap cue so an exact-boundary cap is not rescheduled after its alarm
     * fires.
     *
     * @param now Current epoch millis used to choose the next future cap.
     */
    private fun updateCapTimingAlertAlarm(now: Long) {
        val snapshot = timingAlertSnapshot
        if (snapshot == null) {
            cancelCapTimingAlertAlarm()
            return
        }
        val capCue = snapshot.capCues.firstOrNull { cue ->
            cue.targetEpoch > now
        }
        if (capCue == null) {
            cancelCapTimingAlertAlarm()
            return
        }
        if (!hasExactTimingAlertAlarmAccess()) {
            cancelCapTimingAlertAlarm()
            return
        }
        alarmManager().setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            capCue.targetEpoch,
            capTimingAlertAlarmIntent(snapshot, capCue),
        )
    }

    /// Cancel any pending cap timing-alert alarm for this app.
    private fun cancelCapTimingAlertAlarm() {
        alarmManager().cancel(capTimingAlertAlarmIntent())
    }

    /// Return Android's system alarm scheduler interface; this service does not own it.
    private fun alarmManager(): AlarmManager {
        return getSystemService(AlarmManager::class.java)
    }

    /**
     * Build the PendingIntent that AlarmManager stores and later sends for a cap cue.
     *
     * PendingIntent is an Android token saying, "send this Intent later as this app." The version
     * used for scheduling includes the current serialized alert snapshot and cap cue to play. The
     * empty default version uses the same action and request code so AlarmManager can find and
     * cancel the previously scheduled token.
     *
     * @param snapshot Active timing-alert snapshot to include in the alarm payload.
     * @param cue Cap cue to deliver when the alarm fires.
     */
    private fun capTimingAlertAlarmIntent(
        snapshot: TimingAlertServiceSnapshot? = null,
        cue: TimingAlertServiceCue? = null,
    ): PendingIntent {
        val intent = Intent(this, TimingAlertAlarmReceiver::class.java)
            .setAction(ACTION_CAP_ALARM)
        if (snapshot != null) {
            intent
                .putExtra(EXTRA_TIMING_ALERT_SNAPSHOT, appStateJson.encodeToString(snapshot))
                .putExtra(EXTRA_CAP_CUE, appStateJson.encodeToString(cue!!))
        }
        return PendingIntent.getBroadcast(
            this,
            TIMING_ALERT_CAP_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Deliver one cap alarm that woke the app through AlarmManager.
     *
     * AlarmManager sends a broadcast rather than calling this service directly.
     * TimingAlertAlarmReceiver forwards that broadcast to this service as ACTION_CAP_ALARM. The
     * service then briefly acquires the wake lock around playback, because an alarm can wake the
     * app without keeping the CPU awake for the whole sound/vibration sequence.
     *
     * @param intent ACTION_CAP_ALARM message carrying serialized alert state and cue data.
     */
    private fun handleCapAlarm(intent: Intent) {
        startTimingAlertForeground()
        val alarmSnapshot = intent.getStringExtra(EXTRA_TIMING_ALERT_SNAPSHOT)
            ?.let { encoded -> appStateJson.decodeFromString<TimingAlertServiceSnapshot>(encoded) }
        val alarmCue = intent.getStringExtra(EXTRA_CAP_CUE)
            ?.let { encoded -> appStateJson.decodeFromString<TimingAlertServiceCue>(encoded) }
        if (alarmSnapshot == null) {
            stopTimingAlertService()
            return
        }
        if (alarmCue == null) {
            return
        }

        refreshTimingAlertServiceLifetime()
        timingAlertSnapshot = alarmSnapshot
        val player = timingAlertPlayer ?: TimingAlertPlayer(context = this)
            .also { timingAlertPlayer = it }
        serviceScope.launch {
            acquireTimingAlertWakeLock()
            playTimingAlerts(listOf(alarmCue), alarmSnapshot, player)
            updateCapTimingAlertAlarm(now = System.currentTimeMillis())
            updateTimingAlertWakeLock(now = System.currentTimeMillis())
        }
    }

    companion object {
        private const val ACTION_UPDATE = "rmjarvis.ultiobserver.TIMING_ALERT_UPDATE"
        internal const val ACTION_CAP_ALARM = "rmjarvis.ultiobserver.TIMING_ALERT_CAP_ALARM"
        private const val EXTRA_TIMING_ALERT_SNAPSHOT =
            "rmjarvis.ultiobserver.extra.TIMING_ALERT_SNAPSHOT"
        private const val EXTRA_CAP_CUE = "rmjarvis.ultiobserver.extra.CAP_CUE"
        private const val TIMING_ALERT_SERVICE_CHANNEL_ID = "timing_alert_service"
        private const val TIMING_ALERT_SERVICE_NOTIFICATION_ID = 1001
        private const val TIMING_ALERT_CAP_ALARM_REQUEST_CODE = 1002
        private const val TIMING_ALERT_SERVICE_SCHEDULE_CHECK_MS = 250L
        private const val TIMING_ALERT_SERVICE_MAX_LIFETIME_MS = 3 * 60 * 60 * 1000L

        /**
         * Build the app-to-service command containing the latest active-game alert state.
         *
         * @param context Android context used to target the service.
         * @param liveState Active game state whose timing alerts should be delivered.
         * @param timingAlertPreferences User alert preferences to apply.
         */
        fun updateIntent(
            context: Context,
            liveState: GameState,
            timingAlertPreferences: TimingAlertPreferences,
        ): Intent {
            val snapshot = liveState.timingAlertSnapshot(timingAlertPreferences)
            return Intent(context, TimingAlertForegroundService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_TIMING_ALERT_SNAPSHOT, appStateJson.encodeToString(snapshot))
        }
    }
}

/**
 * Compact service payload containing only timing-alert state needed outside the Compose process.
 *
 * @param soundVolume Playback volume for sound alerts.
 * @param vibrationDurationMillis Vibration length for vibration alerts.
 * @param vibrateWithSounds Whether sound alerts should also vibrate.
 * @param countdownCues Upcoming countdown cues for near-term wake-lock scheduling.
 * @param capCues Relevant cap cues that may need exact alarms later in the game.
 */
@Serializable
private data class TimingAlertServiceSnapshot(
    val soundVolume: Float,
    val vibrationDurationMillis: Long,
    val vibrateWithSounds: Boolean,
    val countdownCues: List<TimingAlertServiceCue>,
    val capCues: List<TimingAlertServiceCue>,
)

/**
 * Serializable cue payload sent to the foreground service or stored in AlarmManager.
 *
 * @param id Timing cue identity used for the stable alert key.
 * @param targetEpoch Epoch millis when this cue should fire.
 * @param alertMode Effective alert mode to play.
 * @param repeatCount Number of repeated sounds or haptic pulses to deliver.
 */
@Serializable
private data class TimingAlertServiceCue(
    val id: TimingCueId,
    val targetEpoch: Long,
    val alertMode: TimingAlertMode,
    val repeatCount: Int,
) {
    /// Build the same stable deduplication key as TimingCueDisplay.
    fun alertKey(): String {
        return "${id.name}:$targetEpoch"
    }
}

/// Build the compact timing-alert snapshot sent across Android component boundaries.
private fun GameState.timingAlertSnapshot(
    timingAlertPreferences: TimingAlertPreferences,
): TimingAlertServiceSnapshot {
    val now = System.currentTimeMillis()
    return TimingAlertServiceSnapshot(
        soundVolume = timingAlertPreferences.soundVolume,
        vibrationDurationMillis = timingAlertPreferences.vibrationDurationMillis,
        vibrateWithSounds = timingAlertPreferences.vibrateWithSounds,
        countdownCues = countdown?.timingAlertServiceCues(
            now,
            timingAlertPreferences,
        ) ?: emptyList(),
        capCues = upcomingCapTimingCues(now).timingAlertServiceCues(timingAlertPreferences),
    )
}

/// Return compact cues for all countdown alerts that may still need delivery.
private fun CountdownState.timingAlertServiceCues(
    now: Long,
    timingAlertPreferences: TimingAlertPreferences,
): List<TimingAlertServiceCue> {
    val dueCue = dueTimingCue(now)
    return ((if (dueCue == null) emptyList() else listOf(dueCue)) + upcomingTimingCues(now))
        .distinctBy { cue -> cue.alertKey() }
        .timingAlertServiceCues(timingAlertPreferences)
}

/// Return compact, alert-enabled cues with effective per-cue playback settings.
private fun List<TimingCueDisplay>.timingAlertServiceCues(
    timingAlertPreferences: TimingAlertPreferences,
): List<TimingAlertServiceCue> {
    return mapNotNull { cue ->
        val alertMode = timingAlertPreferences.alertModeFor(cue.id)
        if (alertMode == TimingAlertMode.NONE) {
            null
        } else {
            TimingAlertServiceCue(
                id = cue.id,
                targetEpoch = cue.targetEpoch,
                alertMode = alertMode,
                repeatCount = timingAlertPreferences.repeatCountFor(cue.id),
            )
        }
    }
}

/// Return compact cues due within the alert-delivery window.
private fun List<TimingAlertServiceCue>.dueTimingAlertServiceCues(
    now: Long,
): List<TimingAlertServiceCue> {
    return filter { cue -> now - cue.targetEpoch in 0L..TIMING_ALERT_DUE_WINDOW_MS }
}

/// Return the next compact cue whose target has not passed yet.
private fun List<TimingAlertServiceCue>.nextTimingAlertServiceCue(
    now: Long,
): TimingAlertServiceCue? {
    return firstOrNull { cue -> cue.targetEpoch > now }
}

/// Return whether Android currently allows this app to schedule exact timing-alert alarms.
internal fun Context.hasExactTimingAlertAlarmAccess(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

/// Open Android's exact-alarm settings for this app when the platform exposes that screen.
internal fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return
    }
    startActivity(
        Intent(
            AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:$packageName"),
        )
    )
}

/// Broadcast receiver that forwards cap-alarm PendingIntents into the foreground service.
class TimingAlertAlarmReceiver : BroadcastReceiver() {
    /**
     * Start the foreground service with the cap alarm payload delivered by AlarmManager.
     *
     * @param context Android context receiving the alarm broadcast.
     * @param intent Alarm intent carrying the cap cue payload.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TimingAlertForegroundService.ACTION_CAP_ALARM) {
            return
        }
        intent.setClass(context, TimingAlertForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }
}

/**
 * Keep the timing-alert service synchronized with Compose app state.
 *
 * This side effect is the only bridge from the Compose tree into the Android service. Each relevant
 * state change sends a fresh ACTION_UPDATE Intent. When there is no active game, or alerts are off,
 * the same effect stops the service so wake locks and cap alarms are released.
 *
 * @param enabled Whether foreground timing-alert delivery should run.
 * @param liveState Active game state whose alerts should be delivered, or null to stop service.
 * @param timingAlertPreferences User alert preferences to apply.
 */
@Composable
internal fun TimingAlertForegroundServiceEffect(
    enabled: Boolean,
    liveState: GameState?,
    timingAlertPreferences: TimingAlertPreferences,
) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(enabled, liveState, timingAlertPreferences) {
        if (!enabled || liveState == null ||
            timingAlertPreferences.globalMode == TimingAlertGlobalMode.OFF
        ) {
            context.stopService(Intent(context, TimingAlertForegroundService::class.java))
            return@LaunchedEffect
        }
        ContextCompat.startForegroundService(
            context,
            TimingAlertForegroundService.updateIntent(
                context = context,
                liveState = liveState,
                timingAlertPreferences = timingAlertPreferences,
            ),
        )
    }
}
