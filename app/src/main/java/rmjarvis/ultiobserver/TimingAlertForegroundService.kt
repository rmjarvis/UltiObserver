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
 * This service is the Android-owned runtime used after Compose has created or updated a game.
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
 * for notification permission just for this (it will if watch notifications are enabled thought),
 * so users may never see this notification on phones where notifications default to off.
 * If the user enables notifications, Android shows it as a silent "Timing alerts are active."
 * status notification.
 *
 * The service is allowed to live across normal app backgrounding so one game can keep using the
 * same alert runtime. A watchdog stops it after 3 hours without a fresh update or cap alarm,
 * which bounds the cost if the user abandons a game without reopening UltiObserver.
 */
class TimingAlertForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private val controller by lazy {
        TimingAlertForegroundServiceController(
            platform = androidTimingAlertServicePlatform(),
            serviceScope = serviceScope,
        )
    }

    /// Return null because this service uses start commands rather than bound calls.
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Dispatch Android start commands into either a state update or a cap-alarm delivery.
     *
     * ACTION_UPDATE is sent by Compose whenever active game state or alert settings change.
     * ACTION_CAP_ALARM is sent by TimingAlertAlarmReceiver after AlarmManager fires a scheduled cap
     * PendingIntent. Android uses a nullable intent in the Service API, but this service only has
     * explicit app and AlarmManager commands.  A null command would violate that owned entry path,
     * so fail loudly instead of silently masking it.
     *
     * @param intent Android command message identifying the service action and carrying serialized
     * extras.
     * @param flags Android service restart flags.
     * @param startId Android start id for this service command.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val commandIntent = intent!!
        val commandAction = commandIntent.action
        if (commandAction == ACTION_UPDATE) {
            controller.handleTimingAlertUpdate(
                commandIntent.getStringExtra(EXTRA_TIMING_ALERT_SNAPSHOT)
                    ?.let { encoded ->
                        appStateJson.decodeFromString<TimingAlertServiceSnapshot>(encoded)
                    }
            )
        } else if (commandAction == ACTION_CAP_ALARM) {
            controller.handleCapAlarm(
                alarmSnapshot = commandIntent.getStringExtra(EXTRA_TIMING_ALERT_SNAPSHOT)
                    ?.let { encoded ->
                        appStateJson.decodeFromString<TimingAlertServiceSnapshot>(encoded)
                    },
                alarmCue = commandIntent.getStringExtra(EXTRA_CAP_CUE)
                    ?.let { encoded ->
                        appStateJson.decodeFromString<TimingAlertServiceCue>(encoded)
                    },
            )
        } else {
            startTimingAlertForeground()
            controller.stopTimingAlertService()
        }
        return START_NOT_STICKY
    }

    /// Release alarm, wake-lock, and coroutine resources when Android stops the service.
    override fun onDestroy() {
        controller.release()
        super.onDestroy()
    }

    /// Build the Android platform callbacks used by the service controller.
    private fun androidTimingAlertServicePlatform(): TimingAlertServicePlatform {
        return object : TimingAlertServicePlatform {
            override fun startForeground() {
                startTimingAlertForeground()
            }

            override fun timingAlertPlayer(): TimingAlertPlayer {
                return (application as UltiObserverApplication).timingAlertPlayer
            }

            override fun currentTimeMillis(): Long {
                return System.currentTimeMillis()
            }

            override fun acquireWakeLock() {
                acquireTimingAlertWakeLock()
            }

            override fun releaseWakeLock() {
                releaseTimingAlertWakeLock()
            }

            override fun scheduleCapAlarm(
                snapshot: TimingAlertServiceSnapshot,
                cue: TimingAlertServiceCue,
            ) {
                alarmManager().setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    cue.targetEpoch,
                    capTimingAlertAlarmIntent(snapshot, cue),
                )
            }

            override fun cancelCapAlarm() {
                cancelCapTimingAlertAlarm()
            }

            override fun hasExactAlarmAccess(): Boolean {
                return hasExactTimingAlertAlarmAccess()
            }

            override suspend fun performHaptic(durationMillis: Long) {
                performTimingCueHaptic(durationMillis)
            }

            override fun postWatchStatusNotification(
                content: WatchNotificationContent,
                alert: Boolean,
            ) {
                postWatchNotification(WATCH_STATUS_NOTIFICATION_ID, content, alert)
            }

            override fun postWatchCueNotification(
                content: WatchNotificationContent,
                alert: Boolean,
            ) {
                postWatchNotification(WATCH_CAP_NOTIFICATION_ID, content, alert)
            }

            override fun cancelWatchNotifications() {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.cancel(WATCH_STATUS_NOTIFICATION_ID)
                notificationManager.cancel(WATCH_CAP_NOTIFICATION_ID)
            }

            override fun stopSelf() {
                this@TimingAlertForegroundService.stopSelf()
            }
        }
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
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            TIMING_ALERT_SERVICE_CHANNEL_ID,
            "Background timing status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Timing alert service is active."
        }
        notificationManager.createNotificationChannel(channel)
        val watchChannel = NotificationChannel(
            WATCH_NOTIFICATION_CHANNEL_ID,
            "Watch timing cues",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Shows live game timing on a paired watch."
            setSound(null, null)
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(watchChannel)
    }

    /** Post one ordinary dismissible notification that companion watches can mirror. */
    private fun postWatchNotification(
        notificationId: Int,
        content: WatchNotificationContent,
        alert: Boolean,
    ) {
        createNotificationChannel()
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, WATCH_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_watch_notification)
            .setContentTitle(content.title)
            .setContentIntent(openAppIntent)
            .setSilent(!alert)
        if (content.body != null) {
            builder.setContentText(content.body)
        }
        getSystemService(NotificationManager::class.java).notify(notificationId, builder.build())
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
            .setSmallIcon(R.drawable.ic_watch_notification)
            .setContentTitle("UltiObserver timing alert service")
            .setContentText("Timing alerts are active.")
            .setContentIntent(openAppIntent)
            .setLocalOnly(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    /// Keep the CPU awake while countdown timing-alert delivery needs precise scheduling.
    private fun acquireTimingAlertWakeLock() {
        if (wakeLock != null) {
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
        wakeLock?.release()
        wakeLock = null
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

    companion object {
        internal const val ACTION_UPDATE = "rmjarvis.ultiobserver.TIMING_ALERT_UPDATE"
        internal const val ACTION_CAP_ALARM = "rmjarvis.ultiobserver.TIMING_ALERT_CAP_ALARM"
        internal const val EXTRA_TIMING_ALERT_SNAPSHOT =
            "rmjarvis.ultiobserver.extra.TIMING_ALERT_SNAPSHOT"
        internal const val EXTRA_CAP_CUE = "rmjarvis.ultiobserver.extra.CAP_CUE"
        private const val TIMING_ALERT_SERVICE_CHANNEL_ID = "timing_alert_service"
        private const val TIMING_ALERT_SERVICE_NOTIFICATION_ID = 1001
        private const val TIMING_ALERT_CAP_ALARM_REQUEST_CODE = 1002
        private const val WATCH_STATUS_NOTIFICATION_ID = 1003
        private const val WATCH_CAP_NOTIFICATION_ID = 1004
        private const val WATCH_NOTIFICATION_CHANNEL_ID = "watch_timing_cues"

        /**
         * Build the app-to-service command containing the latest active-game alert state.
         *
         * @param context Android context used to target the service.
         * @param liveState Active game state whose timing alerts should be delivered.
         * @param settings Current settings controlling phone alerts and watch notifications.
         */
        internal fun updateIntent(
            context: Context,
            liveState: GameState,
            settings: Settings,
        ): Intent {
            val snapshot = liveState.timingAlertSnapshot(settings)
            return Intent(context, TimingAlertForegroundService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_TIMING_ALERT_SNAPSHOT, appStateJson.encodeToString(snapshot))
        }
    }
}

/**
 * Android operations used by the timing-alert service controller.
 *
 * The foreground service owns these platform actions at runtime. Tests can replace them with fakes
 * so the alert scheduling and delivery decisions can be checked without real wake locks, alarms,
 * or haptic hardware.
 */
internal interface TimingAlertServicePlatform {
    /// Put the Android service into foreground mode with its notification.
    fun startForeground()

    /// Return the process-wide sound player shared by Android app components.
    fun timingAlertPlayer(): TimingAlertPlayer

    /// Return the current epoch millis for scheduling decisions.
    fun currentTimeMillis(): Long

    /// Acquire the wake lock used for near-term countdown cue delivery.
    fun acquireWakeLock()

    /// Release the wake lock used for near-term countdown cue delivery.
    fun releaseWakeLock()

    /**
     * Schedule a cap cue through Android's exact-alarm mechanism.
     *
     * @param snapshot Active service snapshot to attach to the alarm payload.
     * @param cue Cap cue to deliver when the alarm fires.
     */
    fun scheduleCapAlarm(snapshot: TimingAlertServiceSnapshot, cue: TimingAlertServiceCue)

    /// Cancel any pending cap timing-alert alarm.
    fun cancelCapAlarm()

    /// Return whether exact alarms are currently available to this app.
    fun hasExactAlarmAccess(): Boolean

    /**
     * Perform one haptic pulse.
     *
     * @param durationMillis Requested vibration duration.
     */
    suspend fun performHaptic(durationMillis: Long)

    /** Post or update the main game-status notification mirrored to a watch. */
    fun postWatchStatusNotification(content: WatchNotificationContent, alert: Boolean)

    /** Post or update a cap-cue notification without replacing countdown status. */
    fun postWatchCueNotification(content: WatchNotificationContent, alert: Boolean)

    /// Remove watch-facing status and cap notifications.
    fun cancelWatchNotifications()

    /// Ask Android to stop this foreground service instance.
    fun stopSelf()
}

/**
 * Testable controller for foreground timing-alert delivery.
 *
 * This class owns UltiObserver's timing-alert decisions: when a snapshot starts or stops service
 * work, when a wake lock is needed, which cap cue should be scheduled, and how due cues are
 * delivered. The Android Service wrapper is intentionally thin and only translates Intents into
 * these controller calls.
 *
 * @param platform Android boundary used for wake locks, alarms, haptics, and service commands.
 * @param serviceScope Coroutine scope tied to the service lifetime.
 * @param scheduleCheckMillis Normal polling interval for countdown timing alerts.
 * @param maxLifetimeMillis Watchdog lifetime before an abandoned service stops itself.
 */
internal class TimingAlertForegroundServiceController(
    private val platform: TimingAlertServicePlatform,
    private val serviceScope: CoroutineScope,
    private val scheduleCheckMillis: Long = TIMING_ALERT_SERVICE_SCHEDULE_CHECK_MS,
    private val maxLifetimeMillis: Long = TIMING_ALERT_SERVICE_MAX_LIFETIME_MS,
) {
    private var timingAlertPlayer: TimingAlertPlayer? = null
    private var scheduleJob: Job? = null
    private var lifetimeJob: Job? = null
    internal var timingAlertSnapshot: TimingAlertServiceSnapshot? = null
        private set
    private var lastWatchStatusContent: WatchNotificationContent? = null

    /**
     * Apply the latest active-game state sent from Compose and start background alert delivery.
     *
     * The app shell resends this update whenever the live state or timing-alert settings change.
     * That makes this service a replaceable consumer of the current snapshot rather than an owner
     * of game rules. Valid updates refresh the maximum service lifetime, replace in-memory state,
     * start the countdown loop, and reschedule the next cap alarm from the latest snapshot.
     *
     * @param updatedSnapshot Decoded ACTION_UPDATE payload, or null for an invalid command.
     */
    fun handleTimingAlertUpdate(updatedSnapshot: TimingAlertServiceSnapshot?) {
        platform.startForeground()
        if (updatedSnapshot == null) {
            stopTimingAlertService()
            return
        }

        refreshTimingAlertServiceLifetime()
        timingAlertSnapshot = updatedSnapshot
        updateWatchStatusFromSnapshot(updatedSnapshot)
        if (timingAlertPlayer == null) {
            timingAlertPlayer = platform.timingAlertPlayer()
        }
        updateTimingAlertWakeLock(now = platform.currentTimeMillis())
        updateCapTimingAlertAlarm(now = platform.currentTimeMillis())
        startScheduleLoop()
    }

    /**
     * Deliver one cap alarm that woke the app through AlarmManager.
     *
     * AlarmManager sends a broadcast rather than calling this service directly.
     * TimingAlertAlarmReceiver forwards that broadcast to the service as ACTION_CAP_ALARM. The
     * service then briefly acquires the wake lock around playback, because an alarm can wake the
     * app without keeping the CPU awake for the whole sound/vibration sequence.
     *
     * @param alarmSnapshot Snapshot payload attached to the alarm.
     * @param alarmCue Cap cue payload attached to the alarm.
     */
    fun handleCapAlarm(
        alarmSnapshot: TimingAlertServiceSnapshot?,
        alarmCue: TimingAlertServiceCue?,
    ) {
        platform.startForeground()
        if (alarmSnapshot == null) {
            stopTimingAlertService()
            return
        }
        if (alarmCue == null) {
            return
        }

        refreshTimingAlertServiceLifetime()
        timingAlertSnapshot = alarmSnapshot
        val player = timingAlertPlayer ?: platform.timingAlertPlayer()
            .also { timingAlertPlayer = it }
        serviceScope.launch {
            platform.acquireWakeLock()
            playTimingAlerts(listOf(alarmCue), alarmSnapshot, player)
            updateCapTimingAlertAlarm(now = platform.currentTimeMillis())
            updateTimingAlertWakeLock(now = platform.currentTimeMillis())
        }
    }

    /// Release alarm, wake-lock, and coroutine resources owned by this service instance.
    fun release() {
        scheduleJob?.cancel()
        lifetimeJob?.cancel()
        platform.cancelCapAlarm()
        platform.releaseWakeLock()
        platform.cancelWatchNotifications()
        serviceScope.cancel()
    }

    /// Release service-held resources and ask Android to stop this service.
    fun stopTimingAlertService() {
        platform.releaseWakeLock()
        platform.cancelCapAlarm()
        platform.cancelWatchNotifications()
        platform.stopSelf()
    }

    /// Start the countdown scheduler coroutine if it is not already active.
    internal fun startScheduleLoop() {
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
    internal suspend fun runTimingAlertLoop() {
        while (serviceScope.isActive) {
            val snapshot = timingAlertSnapshot ?: return
            val player = timingAlertPlayer!!
            val now = platform.currentTimeMillis()
            updateTimingAlertWakeLock(now)
            val dueTimingAlerts = snapshot.countdownCues.dueTimingAlertServiceCues(now)
            if (dueTimingAlerts.isNotEmpty()) {
                playTimingAlerts(dueTimingAlerts, snapshot, player)
                continue
            }

            val nextTimingAlert = snapshot.countdownCues.nextTimingAlertServiceCue(now) ?: return

            val deliveryWindow = timingAlertDeliveryWindow(
                millisUntilNextAlert = nextTimingAlert.targetEpoch - now,
                scheduleCheckMillis = scheduleCheckMillis,
            )
            delay(deliveryWindow.delayMillis)
            if (deliveryWindow.readyToPlay) {
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
    internal suspend fun playTimingAlerts(
        cues: List<TimingAlertServiceCue>,
        snapshot: TimingAlertServiceSnapshot,
        player: TimingAlertPlayer,
    ) {
        // This is effectively a one-cue list in normal operation. Keep the list shape because the
        // due-cue helper naturally returns an empty list for "none due" and can defensively return
        // more than one cue if Android wakes the service after multiple scheduled instants.
        cues.forEach { cue ->
            removeTimingAlertServiceCue(cue)
            deliverWatchCue(cue)
            if (cue.alertMode == TimingAlertMode.VIBRATE) {
                repeat(cue.repeatCount) { pulseIndex ->
                    platform.performHaptic(snapshot.vibrationDurationMillis)
                    if (pulseIndex < cue.repeatCount - 1) {
                        delay(snapshot.vibrationDurationMillis + TIMING_ALERT_REPEAT_HAPTIC_GAP_MS)
                    }
                }
            } else if (cue.alertMode != TimingAlertMode.NONE) {
                player.play(
                    sound = cue.alertMode.toTimingAlertSound(),
                    repeatCount = cue.repeatCount,
                    volume = snapshot.soundVolume,
                    priority = TIMING_ALERT_CUE_PRIORITY,
                )
                if (snapshot.vibrateWithSounds) {
                    platform.performHaptic(snapshot.vibrationDurationMillis)
                }
            }
        }
    }

    /** Update watch-visible state after one scheduled countdown or cap cue fires. */
    private fun deliverWatchCue(cue: TimingAlertServiceCue) {
        val snapshot = timingAlertSnapshot ?: return
        val scoreLine = snapshot.watchScoreLine ?: return
        val alert = snapshot.watchNotificationMode == WatchNotificationMode.ALERTING &&
            cue.watchAlertEnabled
        val countdownSeconds = cue.countdownSeconds
        if (countdownSeconds == null) {
            platform.postWatchCueNotification(
                content = WatchNotificationContent(
                    title = scoreLine,
                    body = cue.watchText,
                ),
                alert = alert,
            )
        } else {
            val nextCueText = if (countdownSeconds == 0) {
                null
            } else {
                snapshot.countdownCues.firstOrNull()?.watchText
            }
            val content = watchNotificationContent(scoreLine, nextCueText)
            platform.postWatchStatusNotification(content, alert)
            lastWatchStatusContent = content
        }
    }

    /** Apply a new app snapshot to the current dismissible watch-status notification. */
    private fun updateWatchStatusFromSnapshot(snapshot: TimingAlertServiceSnapshot) {
        if (snapshot.watchNotificationMode == WatchNotificationMode.OFF) {
            platform.cancelWatchNotifications()
            lastWatchStatusContent = null
        } else {
            val content = watchNotificationContent(
                snapshot.watchScoreLine!!,
                snapshot.countdownCues.firstOrNull()?.watchText,
            )
            if (content != lastWatchStatusContent) {
                platform.postWatchStatusNotification(content, alert = false)
                lastWatchStatusContent = content
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
            delay(maxLifetimeMillis)
            stopTimingAlertService()
        }
    }

    /// Acquire or release the partial wake lock according to current countdown cue state.
    internal fun updateTimingAlertWakeLock(now: Long) {
        val snapshot = timingAlertSnapshot ?: run {
            platform.releaseWakeLock()
            return
        }
        if (snapshot.countdownCues.nextTimingAlertServiceCue(now) != null ||
            snapshot.countdownCues.dueTimingAlertServiceCues(now).isNotEmpty()
        ) {
            platform.acquireWakeLock()
        } else {
            platform.releaseWakeLock()
        }
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
    internal fun updateCapTimingAlertAlarm(now: Long) {
        val snapshot = timingAlertSnapshot
        if (snapshot == null) {
            platform.cancelCapAlarm()
            return
        }
        val capCue = snapshot.capCues.firstOrNull { cue ->
            cue.targetEpoch > now
        }
        if (capCue == null) {
            platform.cancelCapAlarm()
            return
        }
        if (!platform.hasExactAlarmAccess()) {
            platform.cancelCapAlarm()
            return
        }
        platform.scheduleCapAlarm(snapshot, capCue)
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
 * @param watchNotificationMode Whether watch status is off, silent, or cue-alerting.
 * @param watchScoreLine Current score text for the mirrored notification.
 */
@Serializable
internal data class TimingAlertServiceSnapshot(
    val soundVolume: Float,
    val vibrationDurationMillis: Long,
    val vibrateWithSounds: Boolean,
    val countdownCues: List<TimingAlertServiceCue>,
    val capCues: List<TimingAlertServiceCue>,
    val watchNotificationMode: WatchNotificationMode,
    val watchScoreLine: String?,
)

/**
 * Serializable cue payload sent to the foreground service or stored in AlarmManager.
 *
 * @param id Timing cue identity used for the stable alert key.
 * @param targetEpoch Epoch millis when this cue should fire.
 * @param alertMode Effective alert mode to play.
 * @param repeatCount Number of repeated sounds or haptic pulses to deliver.
 * @param countdownSeconds Nominal countdown value for this cue, or null for a cap cue.
 * @param watchText Wording shown as the next watch cue.
 * @param watchAlertEnabled Whether the individual cue setting is not Off.
 */
@Serializable
internal data class TimingAlertServiceCue(
    val id: TimingCueId,
    val targetEpoch: Long,
    val alertMode: TimingAlertMode,
    val repeatCount: Int,
    val countdownSeconds: Int? = null,
    val watchText: String = id.label,
    val watchAlertEnabled: Boolean = true,
) {
    /// Build the same stable deduplication key as TimingCueDisplay.
    fun alertKey(): String {
        return "${id.name}:$targetEpoch"
    }
}

/// Build the compact timing-alert snapshot sent across Android component boundaries.
internal fun GameState.timingAlertSnapshot(
    settings: Settings,
): TimingAlertServiceSnapshot {
    val now = System.currentTimeMillis()
    val timingAlertPreferences = settings.timingAlerts
    val countdownCues = countdown?.timingAlertServiceCues(
        now,
        timingAlertPreferences,
    ) ?: emptyList()
    val watchEnabled = timingAlertPreferences.watchNotificationMode != WatchNotificationMode.OFF
    return TimingAlertServiceSnapshot(
        soundVolume = timingAlertPreferences.soundVolume,
        vibrationDurationMillis = timingAlertPreferences.vibrationDurationMillis,
        vibrateWithSounds = timingAlertPreferences.vibrateWithSounds,
        countdownCues = countdownCues,
        capCues = upcomingCapTimingCues(now).timingAlertServiceCues(timingAlertPreferences),
        watchNotificationMode = timingAlertPreferences.watchNotificationMode,
        watchScoreLine = if (watchEnabled) watchNotificationScoreLine(settings) else null,
    )
}

/// Return compact cues for all countdown alerts that may still need delivery.
internal fun CountdownState.timingAlertServiceCues(
    now: Long,
    timingAlertPreferences: TimingAlertPreferences,
): List<TimingAlertServiceCue> {
    val dueCue = dueTimingCue(now)
    return ((if (dueCue == null) emptyList() else listOf(dueCue)) + upcomingTimingCues(now))
        .distinctBy { cue -> cue.alertKey() }
        .timingAlertServiceCues(timingAlertPreferences, countdownCues = true)
}

/// Return compact cues that need phone playback or watch delivery.
internal fun List<TimingCueDisplay>.timingAlertServiceCues(
    timingAlertPreferences: TimingAlertPreferences,
    countdownCues: Boolean = false,
): List<TimingAlertServiceCue> {
    return mapNotNull { cue ->
        val alertMode = timingAlertPreferences.alertModeFor(cue.id)
        val countdownSeconds = if (countdownCues) {
            cue.countdownTime.seconds.toInt()
        } else {
            null
        }
        val watchCueNeeded = timingAlertPreferences.sendsCueToWatch(
            cue.id,
            countdownSeconds,
        )
        if (alertMode == TimingAlertMode.NONE && !watchCueNeeded) {
            null
        } else {
            TimingAlertServiceCue(
                id = cue.id,
                targetEpoch = cue.targetEpoch,
                alertMode = alertMode,
                repeatCount = timingAlertPreferences.repeatCountFor(cue.id),
                countdownSeconds = countdownSeconds,
                watchText = cue.message,
                watchAlertEnabled = timingAlertPreferences.settingsModeFor(cue.id) !=
                    TimingAlertMode.NONE,
            )
        }
    }
}

/// Return compact cues due within the alert-delivery window.
internal fun List<TimingAlertServiceCue>.dueTimingAlertServiceCues(
    now: Long,
): List<TimingAlertServiceCue> {
    return filter { cue -> now - cue.targetEpoch in 0L..TIMING_ALERT_DUE_WINDOW_MS }
}

/// Return the next compact cue whose target has not passed yet.
internal fun List<TimingAlertServiceCue>.nextTimingAlertServiceCue(
    now: Long,
): TimingAlertServiceCue? {
    return firstOrNull { cue -> cue.targetEpoch > now }
}

internal const val TIMING_ALERT_SERVICE_SCHEDULE_CHECK_MS = 250L
internal const val TIMING_ALERT_SERVICE_MAX_LIFETIME_MS = 3 * 60 * 60 * 1000L

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
