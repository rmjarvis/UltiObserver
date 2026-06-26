package rmjarvis.ultiobserver

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.FileInputStream
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

/// Tests for Android timing-alert playback after the app has chosen the cues to deliver.
@RunWith(AndroidJUnit4::class)
class TestTimingAlerts {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Test the compact alert information sent from the app screen to the background service.
     *
     * The live-game screen sends only the timing-alert details the background service needs:
     * current sound/vibration preferences, countdown cues, and cap cues that still have audible or
     * haptic alerts enabled. This test checks that the handoff keeps those user choices and drops
     * cues the user has silenced.
     */
    @Test
    fun servicePayloads() {
        val preferences = TimingAlertPreferences(
            globalMode = TimingAlertGlobalMode.SOUNDS_ON,
            soundVolume = 0.7f,
            vibrateWithSounds = true,
            cueModes = mapOf(
                TimingCueId.HALF_CAP to TimingAlertMode.DING,
                TimingCueId.SOFT_CAP to TimingAlertMode.NONE,
            ),
            cueRepeatCounts = mapOf(TimingCueId.HALF_CAP to 3),
        )
        val setup = newGameSetupState(
            now = LocalDateTime.of(2026, 5, 19, 10, 0),
        )
        val liveState = createLiveGameState(setup)

        // Starting or updating the background service carries the current user playback settings.
        val updateIntent = TimingAlertForegroundService.updateIntent(
            context = context,
            liveState = liveState,
            timingAlertPreferences = preferences,
        )
        assertEquals(TimingAlertForegroundService.ACTION_UPDATE, updateIntent.action)
        val encodedSnapshot = updateIntent.getStringExtra(
            TimingAlertForegroundService.EXTRA_TIMING_ALERT_SNAPSHOT,
        )
        val snapshot = appStateJson.decodeFromString<TimingAlertServiceSnapshot>(
            encodedSnapshot!!,
        )
        assertEquals(0.7f, snapshot.soundVolume, 0f)
        assertTrue(snapshot.vibrateWithSounds)

        // A live state without an active countdown still sends cap alert information, but there
        // are no countdown cues for the service to schedule.
        val noCountdownSnapshot = liveState.copy(countdown = null).timingAlertSnapshot(preferences)
        assertTrue(noCountdownSnapshot.countdownCues.isEmpty())

        // Countdown conversion includes future cues even when none are due right now.
        val futureCountdownCues = CountdownState(
            kind = CountdownKind.TIME_OUT,
            label = "Offense set in",
            durationSeconds = 70,
            targetEpoch = 70_000L,
        ).timingAlertServiceCues(1_000L, preferences)
        assertEquals(TimingCueId.TIMEOUT_CLEAR_FIELD, futureCountdownCues.first().id)

        // If a countdown cue is already due, the same conversion includes that cue immediately.
        val dueCountdownCues = CountdownState(
            kind = CountdownKind.TIME_OUT,
            label = "Offense set in",
            durationSeconds = 70,
            targetEpoch = 70_000L,
        ).timingAlertServiceCues(40_000L, preferences)
        assertEquals(TimingCueId.TIMEOUT_CLEAR_FIELD, dueCountdownCues.first().id)

        // Silenced cues are left out of the background payload so the service does not wake later
        // just to decide that nothing should happen.  Here SOFT_CAP has NONE for the cue.
        val cuePayloads = listOf(
            timingCueDisplay(TimingCueId.HALF_CAP, 1_000L),
            timingCueDisplay(TimingCueId.SOFT_CAP, 2_000L),
        ).timingAlertServiceCues(preferences)
        assertEquals(
            listOf(
                TimingAlertServiceCue(
                    id = TimingCueId.HALF_CAP,
                    targetEpoch = 1_000L,
                    alertMode = TimingAlertMode.DING,
                    repeatCount = 3,
                )
            ),
            cuePayloads,
        )
        assertEquals("HALF_CAP:1000", cuePayloads.single().alertKey())

        // The global alert mode can also silence cues.  When timing alerts are globally off, even
        // cues with a sound setting are omitted from the service payload.
        assertTrue(
            listOf(timingCueDisplay(TimingCueId.HALF_CAP, 1_000L))
                .timingAlertServiceCues(
                    preferences.copy(globalMode = TimingAlertGlobalMode.OFF),
                )
                .isEmpty()
        )

        // The service can ask separately for cues due right now and for the next future cue.  That
        // lets it play overdue alerts promptly while still sleeping until the next scheduled one.
        val serviceCues = listOf(
            timingAlertServiceCue(TimingCueId.HALF_CAP, 1_000L),
            timingAlertServiceCue(TimingCueId.HARD_CAP, 2_000L),
        )
        assertEquals(
            listOf(timingAlertServiceCue(TimingCueId.HALF_CAP, 1_000L)),
            serviceCues.dueTimingAlertServiceCues(1_500L),
        )
        assertEquals(
            timingAlertServiceCue(TimingCueId.HARD_CAP, 2_000L),
            serviceCues.nextTimingAlertServiceCue(1_500L),
        )
        assertTrue(serviceCues.dueTimingAlertServiceCues(3_200L).isEmpty())
    }

    /**
     * Test how the background service reacts when the active game or alert settings change.
     *
     * A service update is the normal "here is the current game" message from the app.  The service
     * should refresh its copy of the alert plan, keep the phone awake only while a near-term
     * countdown cue remains, schedule a later cap alert when Android allows it, and stop abandoned
     * work after its watchdog expires.
     */
    @Test
    fun serviceUpdates() {
        val emptyPlatform = FakeTimingAlertServicePlatform(now = 1_000L)
        val emptyController = newController(emptyPlatform)

        // Releasing a service that never received game state still cancels stale Android work.
        emptyController.release()
        assertEquals(1, emptyPlatform.cancelCapAlarmCalls)
        assertEquals(1, emptyPlatform.releaseWakeLockCalls)

        val platform = FakeTimingAlertServicePlatform(now = 1_000L)
        val controller = newController(platform)
        var controllerReleased = false
        try {
            // Before the service has any game snapshot, it has no alert work to keep alive.
            controller.updateCapTimingAlertAlarm(now = 1_000L)
            assertEquals(1, platform.cancelCapAlarmCalls)
            controller.updateTimingAlertWakeLock(now = 1_000L)
            assertEquals(1, platform.releaseWakeLockCalls)

            // A malformed update still starts through the normal service path, then shuts down
            // cleanly because there is no game information to deliver.
            controller.handleTimingAlertUpdate(null)
            assertEquals(1, platform.foregroundStarts)
            assertEquals(1, platform.stopSelfCalls)
            assertEquals(2, platform.cancelCapAlarmCalls)
            assertEquals(2, platform.releaseWakeLockCalls)

            // If there is no active countdown, the service does not keep the CPU awake.  It still
            // schedules the next future cap cue, skipping any cap cue whose time has already
            // passed.
            val futureCapCue = timingAlertServiceCue(TimingCueId.HARD_CAP, 2_000L)
            val updateSnapshot = serviceSnapshot(
                countdownCues = emptyList(),
                capCues = listOf(
                    timingAlertServiceCue(TimingCueId.HALF_CAP, 500L),
                    futureCapCue,
                ),
            )
            controller.handleTimingAlertUpdate(updateSnapshot)
            assertSame(updateSnapshot, controller.timingAlertSnapshot)
            assertEquals(2, platform.foregroundStarts)
            assertEquals(1, platform.createdPlayers.size)
            assertEquals(futureCapCue, platform.scheduledCapAlarms.single().cue)

            // If Android will not let the app schedule exact alarms, the service cancels any stale
            // cap alarm rather than pretending the future cap alert is scheduled.
            platform.exactAlarmAccess = false
            platform.scheduledCapAlarms.clear()
            controller.handleTimingAlertUpdate(updateSnapshot)
            assertTrue(platform.scheduledCapAlarms.isEmpty())
            assertEquals(3, platform.cancelCapAlarmCalls)

            // Countdown cues are near-term alerts, so the service keeps a wake lock while it waits
            // to play them accurately.
            platform.exactAlarmAccess = true
            platform.acquireWakeLockCalls = 0
            controller.handleTimingAlertUpdate(
                serviceSnapshot(
                    countdownCues = listOf(
                        timingAlertServiceCue(TimingCueId.OFFENSE_TWENTY, 10_000L),
                    ),
                    capCues = emptyList(),
                )
            )
            assertTrue(platform.acquireWakeLockCalls > 0)

            // Repeated update messages are normal while the UI recomposes.  They should refresh
            // state without starting duplicate countdown delivery loops.
            val playerCountBeforeDuplicateStart = platform.createdPlayers.size
            controller.startScheduleLoop()
            assertEquals(playerCountBeforeDuplicateStart, platform.createdPlayers.size)

            // When all cap cues are in the past, the service cancels any pending cap alarm.
            controller.handleTimingAlertUpdate(
                serviceSnapshot(
                    capCues = listOf(timingAlertServiceCue(TimingCueId.HALF_CAP, 900L)),
                )
            )
            assertTrue(platform.cancelCapAlarmCalls >= 4)

            // Releasing a service after a real update also releases the sound player it created.
            controller.release()
            controllerReleased = true
            assertTrue(platform.soundPlayer.released)
        } finally {
            if (!controllerReleased) {
                controller.release()
            }
        }

        // The real lifetime watchdog is long enough for a game.  With a short injected lifetime,
        // the same path proves the service stops if no later app update refreshes the game state.
        val watchdogPlatform = FakeTimingAlertServicePlatform(now = 1_000L)
        val watchdogController = newController(watchdogPlatform, maxLifetimeMillis = 1L)
        try {
            watchdogController.handleTimingAlertUpdate(serviceSnapshot())
            runBlocking {
                waitFor { watchdogPlatform.stopSelfCalls > 0 }
            }
        } finally {
            watchdogController.release()
        }
    }

    /**
     * Test delivery of countdown alerts that need to play while the phone may be asleep.
     *
     * Countdown cues are close enough to game action that the service waits for them directly.  The
     * important behavior is that each due cue is played once, delayed cues play at their target
     * time, sound and vibration preferences are both honored, and a race during teardown does not
     * crash the alert path.
     */
    @Test
    fun countdownCueDelivery() = runBlocking {
        // If the scheduler starts before any game alert plan is installed, it exits without trying
        // to play anything.
        val emptyPlanPlatform = FakeTimingAlertServicePlatform(now = 1_000L)
        val emptyPlanController = newController(emptyPlanPlatform)
        try {
            emptyPlanController.runTimingAlertLoop()
            assertTrue(emptyPlanPlatform.soundPlayer.playedSounds.isEmpty())
        } finally {
            emptyPlanController.release()
        }

        // If service teardown cancels the scheduler scope before the loop is entered, the loop
        // exits immediately without trying to read or deliver alerts.
        val releasedPlatform = FakeTimingAlertServicePlatform(now = 1_000L)
        val releasedController = newController(releasedPlatform)
        releasedController.release()
        releasedController.runTimingAlertLoop()
        assertTrue(releasedPlatform.soundPlayer.playedSounds.isEmpty())

        // If the installed game alert plan has no countdown cues, the scheduler has no near-term
        // cue to wait for and exits quietly.
        val noCountdownCuePlatform = FakeTimingAlertServicePlatform(now = 1_000L)
        val noCountdownCueController = newController(noCountdownCuePlatform)
        try {
            noCountdownCueController.handleTimingAlertUpdate(serviceSnapshot())
            noCountdownCueController.runTimingAlertLoop()
            assertTrue(noCountdownCuePlatform.soundPlayer.playedSounds.isEmpty())
        } finally {
            noCountdownCueController.release()
        }

        val platform = FakeTimingAlertServicePlatform(now = 1_000L)
        val controller = newController(platform)
        try {
            // A cue that is already due plays once and is removed before playback.  Removing it
            // first prevents the scheduler from looping back and playing the same cue again.
            val dueCue = timingAlertServiceCue(
                TimingCueId.HALF_CAP,
                1_000L,
                alertMode = TimingAlertMode.DING,
                repeatCount = 2,
            )
            controller.handleTimingAlertUpdate(
                serviceSnapshot(
                    soundVolume = 0.6f,
                    countdownCues = listOf(dueCue),
                )
            )
            waitFor { controller.timingAlertSnapshot?.countdownCues?.isEmpty() == true }
            platform.soundPlayer.completeLoad(
                timingAlertSoundIds().getValue(
                    TimingAlertSoundClip(TimingAlertSound.DING, 2),
                )
            )
            waitFor { platform.soundPlayer.playedSounds.isNotEmpty() }
            assertEquals(
                listOf(
                    PlayedTimingAlertSound(
                        soundId = timingAlertSoundIds().getValue(
                            TimingAlertSoundClip(TimingAlertSound.DING, 2),
                        ),
                        volume = 0.6f,
                    )
                ),
                platform.soundPlayer.playedSounds,
            )

            // A cue that is still outside the short delivery window is kept for a later scheduler
            // pass rather than being played early.
            platform.soundPlayer.playedSounds.clear()
            val pollingPlatform = FakeTimingAlertServicePlatform(now = 1_000L)
            val pollingController = newController(pollingPlatform, scheduleCheckMillis = 10L)
            try {
                pollingController.handleTimingAlertUpdate(
                    serviceSnapshot(
                        countdownCues = listOf(
                            timingAlertServiceCue(
                                TimingCueId.HARD_CAP,
                                2_000L,
                                alertMode = TimingAlertMode.DING,
                            )
                        ),
                    )
                )
                delay(25L)
                assertTrue(pollingPlatform.soundPlayer.playedSounds.isEmpty())
                assertEquals(
                    listOf(timingAlertServiceCue(TimingCueId.HARD_CAP, 2_000L)),
                    pollingController.timingAlertSnapshot?.countdownCues,
                )
            } finally {
                pollingController.release()
            }

            // A cue that is barely in the future is close enough that the service waits directly
            // until the cue time rather than sleeping for another normal polling interval.
            platform.soundPlayer.playedSounds.clear()
            val nearFutureCue = timingAlertServiceCue(
                TimingCueId.SOFT_CAP,
                1_001L,
                alertMode = TimingAlertMode.BEEP,
            )
            controller.handleTimingAlertUpdate(
                serviceSnapshot(
                    soundVolume = 0.4f,
                    countdownCues = listOf(nearFutureCue),
                )
            )
            waitFor { controller.timingAlertSnapshot?.countdownCues?.isEmpty() == true }
            platform.soundPlayer.completeLoad(
                timingAlertSoundIds().getValue(
                    TimingAlertSoundClip(TimingAlertSound.BEEP, 1),
                )
            )
            waitFor { platform.soundPlayer.playedSounds.isNotEmpty() }
            assertEquals(
                listOf(
                    PlayedTimingAlertSound(
                        soundId = timingAlertSoundIds().getValue(
                            TimingAlertSoundClip(TimingAlertSound.BEEP, 1),
                        ),
                        volume = 0.4f,
                    )
                ),
                platform.soundPlayer.playedSounds,
            )

            // Pure vibration cues do not use SoundPool.  Repeated vibration settings become
            // repeated haptic pulses.
            controller.handleTimingAlertUpdate(
                serviceSnapshot(
                    vibrationDurationMillis = 0L,
                    countdownCues = listOf(
                        timingAlertServiceCue(
                            TimingCueId.DEFENSE_TWENTY,
                            1_000L,
                            alertMode = TimingAlertMode.VIBRATE,
                            repeatCount = 2,
                        )
                    ),
                )
            )
            waitFor { platform.performedHaptics.size >= 2 }
            assertEquals(listOf(0L, 0L), platform.performedHaptics.takeLast(2))

            // Sound cues can still ask for one paired haptic pulse when the user enables
            // "vibrate with sounds" in timing-alert settings.
            platform.soundPlayer.playedSounds.clear()
            controller.handleTimingAlertUpdate(
                serviceSnapshot(
                    soundVolume = 0.2f,
                    vibrationDurationMillis = 15L,
                    vibrateWithSounds = true,
                    countdownCues = listOf(
                        timingAlertServiceCue(
                            TimingCueId.HARD_CAP,
                            1_000L,
                            alertMode = TimingAlertMode.DING,
                        )
                    ),
                )
            )
            waitFor { platform.performedHaptics.lastOrNull() == 15L }

            // Defensive coverage for a cue delivered after the snapshot has disappeared.  This is
            // not expected in normal use, but a service teardown racing a delivery coroutine should
            // not crash playback.
            val racingTeardownController = newController(platform)
            racingTeardownController.playTimingAlerts(
                cues = listOf(
                    timingAlertServiceCue(
                        TimingCueId.HALF_CAP,
                        2_000L,
                        alertMode = TimingAlertMode.DING,
                    )
                ),
                snapshot = serviceSnapshot(),
                player = platform.createdPlayers.first(),
            )
        } finally {
            controller.release()
        }
    }

    /**
     * Test delivery of cap alerts that may happen long after the app was last opened.
     *
     * Cap cues can be far enough in the future that Android owns the wakeup.  When that wakeup
     * arrives, the service should play the requested cap alert, remove it from the future alert
     * list, and schedule the next cap alert if one remains.
     */
    @Test
    fun capAlarmDelivery() = runBlocking {
        val platform = FakeTimingAlertServicePlatform(now = 1_000L)
        val controller = newController(platform)
        try {
            // If Android wakes the service without the saved game alert plan, the service has
            // nothing useful to do and stops immediately.
            controller.handleCapAlarm(alarmSnapshot = null, alarmCue = null)
            assertEquals(1, platform.foregroundStarts)
            assertEquals(1, platform.stopSelfCalls)

            // If the game alert plan is present but the specific cap cue is missing, foreground
            // mode starts but no sound, haptic, or next alarm is delivered.
            val alarmSnapshot = serviceSnapshot()
            controller.handleCapAlarm(alarmSnapshot = alarmSnapshot, alarmCue = null)
            assertEquals(2, platform.foregroundStarts)
            assertTrue(platform.performedHaptics.isEmpty())

            // A real cap alarm briefly keeps the CPU awake around playback, removes the delivered
            // cap cue, and schedules the next future cap alert from the same game.
            val alarmCue = timingAlertServiceCue(
                TimingCueId.HALF_CAP,
                1_000L,
                alertMode = TimingAlertMode.DING,
            )
            val nextCapCue = timingAlertServiceCue(TimingCueId.HARD_CAP, 2_000L)
            controller.handleCapAlarm(
                alarmSnapshot = serviceSnapshot(capCues = listOf(alarmCue, nextCapCue)),
                alarmCue = alarmCue,
            )
            waitFor { platform.scheduledCapAlarms.isNotEmpty() }
            assertTrue(platform.acquireWakeLockCalls > 0)
            assertEquals(listOf(nextCapCue), controller.timingAlertSnapshot?.capCues)
            assertEquals(nextCapCue, platform.scheduledCapAlarms.single().cue)

            // A later alarm reuses the existing sound player rather than rebuilding audio
            // resources for every cap wakeup.
            val playerCountBeforeSecondAlarm = platform.createdPlayers.size
            controller.handleCapAlarm(
                alarmSnapshot = serviceSnapshot(capCues = listOf(nextCapCue)),
                alarmCue = nextCapCue,
            )
            waitFor { controller.timingAlertSnapshot?.capCues?.isEmpty() == true }
            assertEquals(playerCountBeforeSecondAlarm, platform.createdPlayers.size)
        } finally {
            controller.release()
        }
    }

    /**
     * Test the phone audio and vibration wrappers used after a timing alert is selected.
     *
     * Most timing-alert tests use fakes so they are deterministic.  This smoke test exercises the
     * real Android wrappers enough to prove that loading a bundled sound, asking for playback,
     * releasing audio resources, checking vibration availability, and checking exact-alarm access
     * are callable on the current emulator.
     */
    @Test
    fun androidAudioWrappers() {
        // The public context constructor wires TimingAlertPlayer to the real Android audio layer.
        val timingAlertPlayer = TimingAlertPlayer(context)
        timingAlertPlayer.release()

        // The real SoundPool adapter can load one bundled timing-alert clip, report that it loaded,
        // accept a play request, and release without blocking the test thread.
        val soundPlayer = AndroidTimingAlertSoundPlayer()
        val loadLatch = CountDownLatch(1)
        var loadedSoundId: Int? = null
        var loadStatus: Int? = null
        soundPlayer.setOnLoadCompleteListener { sampleId, status ->
            loadedSoundId = sampleId
            loadStatus = status
            loadLatch.countDown()
        }
        val soundId = soundPlayer.load(context, R.raw.timing_tick, 1)
        assertTrue(loadLatch.await(3, TimeUnit.SECONDS))
        assertEquals(soundId, loadedSoundId)
        assertEquals(0, loadStatus)
        soundPlayer.play(soundId, 0.05f, 0.05f, 1, 0, 1f)
        soundPlayer.release()

        // Haptic helpers should be safe whether this emulator reports vibration hardware or not.
        val hasHaptics = context.hasTimingCueHaptics()
        context.performTimingCueHaptic(1L)
        assertEquals(hasHaptics, context.hasTimingCueHaptics())

        // Exact-alarm access is always available before Android 12; newer Android versions can
        // make it depend on app policy, so the test only asserts the stable platform rule.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            assertTrue(context.hasExactTimingAlertAlarmAccess())
        } else {
            context.hasExactTimingAlertAlarmAccess()
        }
    }

    /**
     * Test the Android settings shortcut used when cap alerts need exact-alarm access.
     *
     * Older Android versions do not expose a per-app Alarms & reminders settings page, so the
     * helper should quietly do nothing.  Newer versions should be able to launch that system
     * screen from the app's Activity context.  The broader UI test clicks this link when the
     * warning dialog naturally appears; this wrapper test covers the platform launch directly so
     * coverage does not depend on the emulator's current exact-alarm policy.
     */
    @Test
    fun exactAlarmSettingsLauncher() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            context.openExactAlarmSettings()
            return
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.openExactAlarmSettings()
            }
            waitForFocusedPackage("com.android.settings")
            pressSystemBack()
            waitForFocusedPackage(context.packageName)
        }
    }

    /**
     * Test the Android service entry point that receives timing-alert work from the app.
     *
     * The controller tests cover the detailed decisions with fakes.  This smoke test checks that
     * real Android service commands can decode the app's saved alert plan, enter foreground mode,
     * create the notification/audio machinery, and then shut down cleanly.
     */
    @Test
    fun foregroundServiceWrapper() {
        try {
            // The timing-alert service is only started by commands; Android binding requests get no
            // binder back.
            assertNull(TimingAlertForegroundService().onBind(Intent()))

            // Malformed service commands without an action are defensive cleanup paths.  Android
            // should be able to deliver one without leaving a foreground timing-alert service
            // running.
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimingAlertForegroundService::class.java),
            )

            // Unknown service commands take the same cleanup path.
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimingAlertForegroundService::class.java)
                    .setAction("rmjarvis.ultiobserver.UNKNOWN_TIMING_ALERT_ACTION"),
            )

            // Malformed update commands still enter foreground mode, then stop because there is no
            // game alert snapshot to deliver.
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimingAlertForegroundService::class.java)
                    .setAction(TimingAlertForegroundService.ACTION_UPDATE),
            )

            // Malformed cap-alarm commands follow the same real Android entry point, but do not
            // have enough payload to play or schedule anything.
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimingAlertForegroundService::class.java)
                    .setAction(TimingAlertForegroundService.ACTION_CAP_ALARM),
            )

            // A normal app update goes through Android's service command path rather than calling
            // the controller directly.  Use a live-point timeout state so the snapshot carries a
            // future countdown cue and the service needs to hold a wake lock.
            val setup = newGameSetupState(now = LocalDateTime.of(2026, 5, 19, 10, 0))
            val serviceUpdateNow = System.currentTimeMillis()
            val timeoutState = createLiveGameState(setup)
                .beginLivePoint(serviceUpdateNow)
                .assessTimeout(TeamId.TEAM_ONE, serviceUpdateNow)
                .state
            val normalUpdateIntent = TimingAlertForegroundService.updateIntent(
                context = context,
                liveState = timeoutState,
                timingAlertPreferences = TimingAlertPreferences(
                    globalMode = TimingAlertGlobalMode.SOUNDS_ON,
                ),
            )
            ContextCompat.startForegroundService(
                context,
                normalUpdateIntent,
            )

            // Compose may resend the same live-game update while the countdown is still active.
            // The service should keep the existing wake lock rather than acquiring a duplicate.
            ContextCompat.startForegroundService(
                context,
                normalUpdateIntent,
            )

            // A cap alarm command can also reach the real service.  The delivered cap cue is
            // removed, and a later cue from the same snapshot is handed to Android's alarm
            // scheduler.
            val now = System.currentTimeMillis()
            val capCue = timingAlertServiceCue(
                TimingCueId.HALF_CAP,
                now,
                alertMode = TimingAlertMode.VIBRATE,
            )
            val laterCapCue = timingAlertServiceCue(
                TimingCueId.HARD_CAP,
                now + 60_000L,
                alertMode = TimingAlertMode.VIBRATE,
            )
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimingAlertForegroundService::class.java)
                    .setAction(TimingAlertForegroundService.ACTION_CAP_ALARM)
                    .putExtra(
                        TimingAlertForegroundService.EXTRA_TIMING_ALERT_SNAPSHOT,
                        appStateJson.encodeToString(
                            serviceSnapshot(
                                vibrationDurationMillis = 1L,
                                capCues = listOf(capCue, laterCapCue),
                            )
                        ),
                    )
                    .putExtra(
                        TimingAlertForegroundService.EXTRA_CAP_CUE,
                        appStateJson.encodeToString(capCue),
                    ),
            )

            // Give Android's main-thread service commands time to reach onStartCommand before
            // cleanup stops the service.
            Thread.sleep(500L)
        } finally {
            context.stopService(Intent(context, TimingAlertForegroundService::class.java))
        }
    }

    /**
     * Test that unrelated broadcasts do not accidentally start timing-alert playback.
     *
     * Android can deliver many broadcasts to an app.  The cap-alarm receiver should ignore anything
     * that is not one of our scheduled cap alert wakeups.
     */
    @Test
    fun capAlarmReceiverActionGuard() {
        val receiver = TimingAlertAlarmReceiver()
        val ignoredIntent = Intent("rmjarvis.ultiobserver.UNRELATED")

        // Non-cap-alarm broadcasts are ignored without retargeting the message to the service.
        receiver.onReceive(context, ignoredIntent)
        assertNull(ignoredIntent.component)

        // A real cap-alarm broadcast is retargeted to the foreground service with its payload.
        val capCue = timingAlertServiceCue(
            TimingCueId.HALF_CAP,
            System.currentTimeMillis(),
            alertMode = TimingAlertMode.VIBRATE,
        )
        val capAlarmIntent = Intent(TimingAlertForegroundService.ACTION_CAP_ALARM)
            .putExtra(
                TimingAlertForegroundService.EXTRA_TIMING_ALERT_SNAPSHOT,
                appStateJson.encodeToString(
                    serviceSnapshot(
                        vibrationDurationMillis = 1L,
                        capCues = listOf(capCue),
                    )
                ),
            )
            .putExtra(
                TimingAlertForegroundService.EXTRA_CAP_CUE,
                appStateJson.encodeToString(capCue),
            )
        try {
            receiver.onReceive(context, capAlarmIntent)
            assertEquals(
                TimingAlertForegroundService::class.java.name,
                capAlarmIntent.component?.className,
            )
            Thread.sleep(300L)
        } finally {
            context.stopService(Intent(context, TimingAlertForegroundService::class.java))
        }
    }

    /// Press Back while Android Settings, rather than a Compose screen, is in the foreground.
    private fun pressSystemBack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        instrumentation.waitForIdleSync()
    }

    /**
     * Wait until a package owns the focused window.
     *
     * @param packageName The Android package expected to appear in the focused window.
     */
    private fun waitForFocusedPackage(packageName: String) {
        val deadline = System.currentTimeMillis() + 5_000L
        var focus = focusedWindowLine()
        while (packageName !in focus && System.currentTimeMillis() < deadline) {
            Thread.sleep(100L)
            focus = focusedWindowLine()
        }
        assertTrue("Timed out waiting for $packageName focus; last focus was $focus", packageName in focus)
    }

    /// Return Android's current focused-window line.
    private fun focusedWindowLine(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("dumpsys window").use { descriptor ->
            val text = FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
                reader.readText()
            }
            return text.lineSequence()
                .firstOrNull { line ->
                    "mCurrentFocus=" in line || "mFocusedApp=" in line
                }
                .orEmpty()
        }
    }

    /// Build a timing-alert service controller with fake Android boundaries.
    private fun newController(
        platform: FakeTimingAlertServicePlatform,
        scheduleCheckMillis: Long = 1L,
        maxLifetimeMillis: Long = 60_000L,
    ): TimingAlertForegroundServiceController {
        return TimingAlertForegroundServiceController(
            platform = platform,
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            scheduleCheckMillis = scheduleCheckMillis,
            maxLifetimeMillis = maxLifetimeMillis,
        )
    }

    /// Build a minimal service snapshot for controller tests.
    private fun serviceSnapshot(
        soundVolume: Float = 0.5f,
        vibrationDurationMillis: Long = 40L,
        vibrateWithSounds: Boolean = false,
        countdownCues: List<TimingAlertServiceCue> = emptyList(),
        capCues: List<TimingAlertServiceCue> = emptyList(),
    ): TimingAlertServiceSnapshot {
        return TimingAlertServiceSnapshot(
            soundVolume = soundVolume,
            vibrationDurationMillis = vibrationDurationMillis,
            vibrateWithSounds = vibrateWithSounds,
            countdownCues = countdownCues,
            capCues = capCues,
        )
    }

    /// Build a compact service cue for the common one-repeat sound case.
    private fun timingAlertServiceCue(
        id: TimingCueId,
        targetEpoch: Long,
        alertMode: TimingAlertMode = TimingAlertMode.DING,
        repeatCount: Int = 1,
    ): TimingAlertServiceCue {
        return TimingAlertServiceCue(
            id = id,
            targetEpoch = targetEpoch,
            alertMode = alertMode,
            repeatCount = repeatCount,
        )
    }

    /// Build a display cue used before conversion into service payload form.
    private fun timingCueDisplay(id: TimingCueId, targetEpoch: Long): TimingCueDisplay {
        return TimingCueDisplay(
            id = id,
            message = id.label,
            remaining = java.time.Duration.ZERO,
            countdownTime = java.time.Duration.ZERO,
            targetEpoch = targetEpoch,
        )
    }

    /// Wait briefly for a foreground-service coroutine assertion to become true.
    private suspend fun waitFor(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) {
                return
            }
            delay(10L)
        }
        assertTrue(condition())
    }
}

/// Fake Android platform boundary for foreground-service controller tests.
private class FakeTimingAlertServicePlatform(
    var now: Long,
) : TimingAlertServicePlatform {
    val soundPlayer = FakeTimingAlertSoundPlayer()
    val createdPlayers = mutableListOf<TimingAlertPlayer>()
    val scheduledCapAlarms = mutableListOf<ScheduledCapAlarm>()
    val performedHaptics = mutableListOf<Long>()
    var exactAlarmAccess = true
    var foregroundStarts = 0
    var stopSelfCalls = 0
    var cancelCapAlarmCalls = 0
    var acquireWakeLockCalls = 0
    var releaseWakeLockCalls = 0

    override fun startForeground() {
        foregroundStarts += 1
    }

    override fun createPlayer(): TimingAlertPlayer {
        val player = TimingAlertPlayer(
            soundPlayer = soundPlayer,
            loadSound = { _, clip -> timingAlertSoundIds().getValue(clip) },
        )
        createdPlayers += player
        return player
    }

    override fun currentTimeMillis(): Long {
        return now
    }

    override fun acquireWakeLock() {
        acquireWakeLockCalls += 1
    }

    override fun releaseWakeLock() {
        releaseWakeLockCalls += 1
    }

    override fun scheduleCapAlarm(
        snapshot: TimingAlertServiceSnapshot,
        cue: TimingAlertServiceCue,
    ) {
        scheduledCapAlarms += ScheduledCapAlarm(snapshot, cue)
    }

    override fun cancelCapAlarm() {
        cancelCapAlarmCalls += 1
    }

    override fun hasExactAlarmAccess(): Boolean {
        return exactAlarmAccess
    }

    override suspend fun performHaptic(durationMillis: Long) {
        performedHaptics += durationMillis
    }

    override fun stopSelf() {
        stopSelfCalls += 1
    }
}

/// Cap alarm request recorded by the fake service platform.
private data class ScheduledCapAlarm(
    val snapshot: TimingAlertServiceSnapshot,
    val cue: TimingAlertServiceCue,
)

/// Fake SoundPool boundary for controller tests.
private class FakeTimingAlertSoundPlayer : TimingAlertSoundPlayer {
    val playedSounds = mutableListOf<PlayedTimingAlertSound>()
    var released = false
    private var loadCompleteListener: ((sampleId: Int, status: Int) -> Unit)? = null

    override fun setOnLoadCompleteListener(listener: (sampleId: Int, status: Int) -> Unit) {
        loadCompleteListener = listener
    }

    override fun load(context: Context, resId: Int, priority: Int): Int {
        error("Controller tests inject loadSound directly.")
    }

    override fun play(
        soundId: Int,
        leftVolume: Float,
        rightVolume: Float,
        priority: Int,
        loop: Int,
        rate: Float,
    ) {
        playedSounds += PlayedTimingAlertSound(soundId, leftVolume)
    }

    override fun release() {
        released = true
    }

    /// Complete a fake SoundPool load.
    fun completeLoad(soundId: Int, status: Int = 0) {
        loadCompleteListener?.invoke(soundId, status)
    }
}

/// Sound playback recorded by the fake SoundPool boundary.
private data class PlayedTimingAlertSound(
    val soundId: Int,
    val volume: Float,
)

/// Return stable fake SoundPool ids for each timing-alert sound clip.
private fun timingAlertSoundIds(): Map<TimingAlertSoundClip, Int> {
    return TimingAlertSound.entries.flatMapIndexed { soundIndex, sound ->
        (MIN_TIMING_ALERT_REPEAT_COUNT..MAX_TIMING_ALERT_REPEAT_COUNT).map { repeatCount ->
            TimingAlertSoundClip(sound, repeatCount) to soundIndex * 10 + repeatCount
        }
    }
        .toMap()
}
