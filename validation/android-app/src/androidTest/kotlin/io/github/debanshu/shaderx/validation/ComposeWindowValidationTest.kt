package io.github.debanshu.shaderx.validation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.unit.IntSize
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.*

/** Tests the actual Android Compose modifier/window boundary, independently of factory raster tests. */
class ComposeWindowValidationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: ComposeValidationActivity

    @Before fun launch() {
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ComposeValidationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComposeValidationActivity
        settle()
    }
    @After fun finish() {
        if (::activity.isInitialized) {
            ui { activity.finish() }; settle()
            val deadline=android.os.SystemClock.elapsedRealtime()+5000
            while (!state { activity.isDestroyed } && android.os.SystemClock.elapsedRealtime()<deadline) Thread.sleep(50)
            assertTrue(state { activity.isDestroyed },"Actual Activity teardown completes")
            assertEquals(state { activity.tracking.created },state { activity.tracking.closed },"Activity destruction closes all owned sessions")
            assertEquals(1,state { activity.tracking.factoryCloses },"Activity closes its own factory exactly once")
            println("SX12 teardown created=${state { activity.tracking.created }} closed=${state { activity.tracking.closed }} factoryCloses=${state { activity.tracking.factoryCloses }}")
        }
    }
    private fun ui(action: () -> Unit) = instrumentation.runOnMainSync(action)
    // A playing Compose driver deliberately schedules frames, so global idle is not a completion signal.
    private fun settle() { ui {}; Thread.sleep(180); ui {} }
    private fun <T> state(read: () -> T): T { var result: T? = null; ui { result = read() }; @Suppress("UNCHECKED_CAST") return result as T }
    private fun near(expected: Int, actual: Int, tolerance: Int = 4) = assertTrue(abs(expected-actual)<=tolerance,"Expected $expected +/- $tolerance, got $actual")
    private fun pixel(bitmap: Bitmap, bounds: Rect) = bitmap.getPixel(bounds.centerX(), bounds.centerY())
    private fun controlPixel(bitmap: Bitmap) = pixel(bitmap, state { activity.controlledBounds })

    private fun screenshot(name: String): Bitmap {
        val size = state { activity.window.decorView.width to activity.window.decorView.height }
        assertTrue(size.first > 0 && size.second > 0)
        val bitmap = Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        var status = -1
        ui { PixelCopy.request(activity.window, bitmap, { result -> status = result; copied.countDown() }, Handler(Looper.getMainLooper())) }
        assertTrue(copied.await(5, TimeUnit.SECONDS), "PixelCopy timed out")
        assertEquals(PixelCopy.SUCCESS, status)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "sx12-compose").apply { mkdirs() }
        File(directory, "api${Build.VERSION.SDK_INT}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SX12 screenshot=${File(directory,"api${Build.VERSION.SDK_INT}-$name.png")} API=${Build.VERSION.SDK_INT} fingerprint=${Build.FINGERPRINT}")
        return bitmap
    }

    @Test fun controlledPauseLiveRevisionAndProviderReplacementSettle() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        screenshot("controlled-initial").useBitmap { near(64, Color.red(controlPixel(it))) }
        val initial = state { Triple(activity.providerCalls, activity.tracking.created, activity.tracking.refreshes) }
        Thread.sleep(250)
        assertEquals(initial, state { Triple(activity.providerCalls, activity.tracking.created, activity.tracking.refreshes) }, "Paused unchanged effect must settle")
        ui { activity.live = .75f }
        settle()
        screenshot("live-without-revision").useBitmap { near(64, Color.red(controlPixel(it))) }
        assertEquals(initial.first, state { activity.providerCalls })
        ui { activity.revision++ }; settle()
        screenshot("live-revision").useBitmap { near(191, Color.red(controlPixel(it))) }
        assertEquals(initial.second, state { activity.tracking.created }, "Live revision reuses external session")
        assertTrue(state { activity.providerCalls } > initial.first)
        ui { activity.providerFails = true; activity.revision++ }; settle()
        screenshot("live-failure-retained").useBitmap { near(191, Color.red(controlPixel(it))) }
        assertTrue(state { activity.errors } > 0)
        ui { activity.providerFails = false; activity.live = .5f; activity.revision++ }; settle()
        screenshot("live-recovered").useBitmap { near(128, Color.red(controlPixel(it))) }
        ui { activity.replaceProvider() }; settle()
        screenshot("provider-replacement").useBitmap { near(32, Color.red(controlPixel(it))) }
        assertEquals(.125, state { activity.playback.positionSeconds })
        ui { activity.playback.isPlaying = true }; settle()
        ui { activity.playback.isPlaying = false }; settle()
        assertTrue(state { activity.playback.positionSeconds } > .125)
        val paused = state { activity.playback.positionSeconds to activity.tracking.refreshes }
        Thread.sleep(250)
        assertEquals(paused, state { activity.playback.positionSeconds to activity.tracking.refreshes })
        println("SX12 controlled counts=${state { Triple(activity.providerCalls,activity.tracking.created,activity.tracking.refreshes) }} pausedPosition=${paused.first}")
    }

    @Test fun actualActivityBackgroundStopsDriverAndResumesWithoutCatchUp() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        assertTrue(state { activity.lifecycleActive })
        ui { activity.playback.isPlaying=true }; settle()
        assertTrue(state { activity.playback.positionSeconds } > .125)
        ui { assertTrue(activity.moveTaskToBack(true),"System must background the actual Activity task") }
        settle()
        assertFalse(state { activity.lifecycleActive },"Actual onPause must deactivate the driver")
        assertTrue(state { activity.playback.isPlaying },"Backgrounding preserves the user's play preference")
        val backgrounded=state { Triple(activity.playback.positionSeconds,activity.providerCalls,activity.tracking.refreshes) }
        Thread.sleep(1000)
        assertEquals(backgrounded,state { Triple(activity.playback.positionSeconds,activity.providerCalls,activity.tracking.refreshes) },
            "Paused Activity must not poll the provider or refresh sessions")
        val foregroundStart=android.os.SystemClock.elapsedRealtimeNanos()
        ui { instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext,ComposeValidationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        settle()
        assertTrue(state { activity.lifecycleActive },"Actual onResume must reactivate this Activity")
        ui { activity.playback.isPlaying=false }; settle()
        val resumed=state { activity.playback.positionSeconds }
        val foregroundElapsed=(android.os.SystemClock.elapsedRealtimeNanos()-foregroundStart)/1e9
        assertTrue(resumed > backgrounded.first,"Resumed foreground driver advances")
        assertTrue(resumed-backgrounded.first <= foregroundElapsed + .1,"Resume must not catch up the one-second background interval")
        screenshot("lifecycle-resumed").useBitmap { assertEquals(255,Color.alpha(controlPixel(it))) }
        println("SX12 lifecycle background=$backgrounded resumed=$resumed foregroundElapsed=$foregroundElapsed counts=${state { Triple(activity.providerCalls,activity.tracking.created,activity.tracking.refreshes) }}")
    }

    @Test fun colorSourceResizeFailureRecoveryAndDisposalPreservePhase() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val before = screenshot("replacement-initial").useBitmap { controlPixel(it) }
        ui { activity.replaceColor() }; settle()
        screenshot("color-replacement").useBitmap { near(192, Color.green(controlPixel(it))) }
        ui { val bounds=activity.controlledBounds; activity.knownSize=IntSize(bounds.width()*2,bounds.height()*2) }; settle()
        val resized = screenshot("known-size-replacement").useBitmap { controlPixel(it) }
        assertTrue(Color.blue(before) - Color.blue(resized) >= 9, "Resolution uniform must change rendered coordinates")
        val originalWidth=state { activity.controlledBounds.width() }
        ui { activity.knownSize=null; activity.boxSize=72 }; settle()
        assertTrue(state { activity.controlledBounds.width() } < originalWidth,"Actual Compose layout must resize")
        screenshot("layout-resized").useBitmap { near(Color.blue(before),Color.blue(controlPixel(it))) }
        ui { activity.replaceSource() }; settle()
        val replacement = screenshot("source-replacement").useBitmap { controlPixel(it) }
        near(96, Color.red(replacement))
        assertEquals(.125, state { activity.playback.positionSeconds })
        ui { activity.replaceWithBrokenSource() }; settle()
        screenshot("source-failure-retained").useBitmap { assertEquals(replacement,controlPixel(it)) }
        assertTrue(state { activity.errors } > 0)
        ui { activity.replaceSource() }; settle()
        screenshot("source-recovered").useBitmap { near(96,Color.red(controlPixel(it))) }
        assertEquals(.125,state { activity.playback.positionSeconds })
        ui { activity.shown=false }; settle()
        assertTrue(state { activity.tracking.created } > 0)
        assertEquals(state { activity.tracking.created },state { activity.tracking.closed },"Removal closes every owned session")
        assertEquals(0,state { activity.tracking.factoryCloses },"Modifier disposal leaves caller factory open")
        println("SX12 disposal created=${state { activity.tracking.created }} closed=${state { activity.tracking.closed }} refreshes=${state { activity.tracking.refreshes }}")
    }

    @Test fun sourceFreeBackgroundPreservesForegroundAndWholeFallback() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        screenshot("background-ready").useBitmap {
            assertEquals(Color.RED,pixel(it,state { activity.backgroundBounds }))
            assertEquals(Color.GREEN,pixel(it,state { activity.foregroundBounds }))
        }
        assertTrue(state { activity.backgroundReady } > 0)
        ui { activity.backgroundBroken=true }; settle()
        screenshot("background-fallback").useBitmap {
            assertEquals(Color.BLUE,pixel(it,state { activity.backgroundBounds }))
            assertEquals(Color.GREEN,pixel(it,state { activity.foregroundBounds }))
        }
        assertTrue(state { activity.errors } > 0)
        ui { activity.backgroundBroken=false }; settle()
        screenshot("background-recovered").useBitmap { assertEquals(Color.RED,pixel(it,state { activity.backgroundBounds })) }
    }

    @Test fun api31ComposeStartupUsesVisibleFallbackAndPreservesForeground() {
        assumeTrue(Build.VERSION.SDK_INT < 33)
        screenshot("startup-fallback").useBitmap {
            assertEquals(Color.WHITE,controlPixel(it),"Unsupported controlled shader leaves input visible")
            assertEquals(Color.BLUE,pixel(it,state { activity.backgroundBounds }))
            assertEquals(Color.GREEN,pixel(it,state { activity.foregroundBounds }))
        }
        assertTrue(state { activity.errors } >= 2)
        assertEquals(0,state { activity.tracking.created })
    }
    private inline fun <T> Bitmap.useBitmap(block: (Bitmap)->T):T = try { block(this) } finally { recycle() }
}
