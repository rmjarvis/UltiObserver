package rmjarvis.ultiobserver

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/** Control service binding independently of the real Activity's lifecycle. */
@SdkSuppress(minSdkVersion = 29)
class TestWatchActivity {
    @Test
    fun ambientSleepAndWake() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val originalAmbient = device.executeShellCommand("settings get global ambient_enabled").trim()
        try {
            // Always-on display lets KEYCODE_SLEEP enter ambient mode instead of powering off.
            device.executeShellCommand("settings put global ambient_enabled 1")
            device.executeShellCommand("input keyevent KEYCODE_WAKEUP")
            val binding = ControlledBinding()
            withControlledBinding(binding) { scenario ->
                scenario.onActivity { activity ->
                    val manager = GameStateManager()
                    // Use a real Activity and ambient observer, with a stable connection screen.
                    binding.connection.onServiceConnected(binding.component, manager.ManagerBinder())
                }
                assertTrue(device.wait(Until.hasObject(By.text("Connecting…")), 5_000L))

                // A system sleep event removes the interactive screen through the ambient callback.
                device.executeShellCommand("input keyevent KEYCODE_SLEEP")
                assertTrue(device.wait(Until.gone(By.text("Connecting…")), 5_000L))

                // A system wake event restores that same screen without recreating the Activity.
                device.executeShellCommand("input keyevent KEYCODE_WAKEUP")
                assertTrue(device.wait(Until.hasObject(By.text("Connecting…")), 5_000L))
            }
        } finally {
            device.executeShellCommand("input keyevent KEYCODE_WAKEUP")
            if (originalAmbient == "null") {
                device.executeShellCommand("settings delete global ambient_enabled")
            } else {
                device.executeShellCommand("settings put global ambient_enabled $originalAmbient")
            }
        }
    }

    @Test
    fun serviceDisconnection() {
        val binding = ControlledBinding()
        withControlledBinding(binding) { scenario ->
            // Android delivers a manager after the Activity has started waiting for its service.
            scenario.onActivity { activity ->
                val manager = GameStateManager()
                binding.connection.onServiceConnected(binding.component, manager.ManagerBinder())
                assertSame(manager, activity.boundManager)
            }

            // A disconnection clears the manager, returning the Activity to its waiting state.
            scenario.onActivity { activity ->
                binding.connection.onServiceDisconnected(binding.component)
                assertNull(activity.boundManager)
            }

            // Destroying the disconnected Activity still releases Android's binding registration.
            scenario.close()
            assertEquals(1, binding.unbindings)
        }
    }

    @Test
    fun destroyedBeforeServiceConnection() {
        val binding = ControlledBinding()
        withControlledBinding(binding) { scenario ->
            // The Activity can be visible while Android has not yet delivered its service binder.
            scenario.onActivity { activity ->
                assertNull(activity.boundManager)
                assertEquals(0, binding.unbindings)
            }

            // Closing in that state releases the pending binding without needing a manager.
            scenario.close()
            assertEquals(1, binding.unbindings)
        }
    }

    /** Capture binding requests without starting a service or delivering asynchronous callbacks. */
    private class ControlledBinding {
        val component = ComponentName("rmjarvis.ultiobserver", GameStateManager::class.java.name)
        lateinit var connection: ServiceConnection
        var unbindings = 0

        fun context(base: Context): Context = object : ContextWrapper(base) {
            override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
                return if (intent.component == component) {
                    assertEquals(Context.BIND_AUTO_CREATE, flags)
                    connection = conn
                    true
                } else {
                    // Older Wear OS ambient support binds its own system service.
                    super.bindService(intent, conn, flags)
                }
            }

            override fun unbindService(conn: ServiceConnection) {
                if (conn === connection) {
                    unbindings++
                } else {
                    super.unbindService(conn)
                }
            }
        }
    }

    /** Replace only the binding calls before WatchActivity.onCreate runs. */
    private fun withControlledBinding(
        binding: ControlledBinding,
        block: (ActivityScenario<WatchActivity>) -> Unit,
    ) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val callbacks = object : Application.ActivityLifecycleCallbacks by
            mock(Application.ActivityLifecycleCallbacks::class.java) {
            override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is WatchActivity) {
                    // ActivityScenario owns the real Activity; substitute its Context's binding
                    // operations before startup rather than relying on callback timing.
                    ContextWrapper::class.java.getDeclaredField("mBase").apply {
                        isAccessible = true
                        set(activity, binding.context(activity.baseContext))
                    }
                }
            }
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        try {
            ActivityScenario.launch<WatchActivity>(
                Intent(application, WatchActivity::class.java),
            ).use { block(it) }
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }
}
