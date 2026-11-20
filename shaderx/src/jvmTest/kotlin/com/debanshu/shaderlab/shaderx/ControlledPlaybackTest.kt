package com.debanshu.shaderlab.shaderx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.proceduralBackground
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderFrameProvider
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram
import com.debanshu.shaderlab.shaderx.compose.controlledShaderEffect
import com.debanshu.shaderlab.shaderx.compose.rememberShaderPlayback
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderSession
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.parameter.FloatParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterSchema
import com.debanshu.shaderlab.shaderx.playback.ShaderPlayback
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.impl.use
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Actual Compose graphics layers and Skia pixels, including the paused invalidation boundary. */
class ControlledPlaybackTest {
    @Test
    fun pausedNestedSnapshotRequiresExplicitRevisionAndRetainsIndependentChildTime() {
        val factory = TrackingFactory()
        val revision = mutableLongStateOf(0)
        var value = .25f
        var calls = 0
        val child = ShaderDefinition("child-clock", program = ShaderProgram(
            "uniform float value; uniform float time; half4 main(float2 p) { return half4(value,time,0,1); }",
            listOf(ShaderBinding.frameFloat("value"), ShaderBinding.time())))
            .let { com.debanshu.shaderlab.shaderx.authoring.ShaderNode.snapshot(it.proceduralBackground(), timeSeconds=.5f,
                frameProvider=ShaderFrameProvider { listOf(FloatUniform("value",value)) }) }
        var node = child
        val provider = ShaderFrameProvider { calls++; listOf(com.debanshu.shaderlab.shaderx.uniform.NestedUniform("child", node)) }
        val definition = ShaderDefinition("nested-live", program=ShaderProgram(
            "uniform shader content; uniform shader child; half4 main(float2 p) { return child.eval(p)*content.eval(p).a; }",
            listOf(ShaderBinding.frameNested("child"))))
        val effect = definition.animatedEffect(frameProvider=provider)
        val playback = ShaderPlayback(initiallyPlaying=false)
        val scene = ImageComposeScene(32,32) {
            Box(Modifier.size(32.dp).controlledShaderEffect(effect,playback,revision.longValue,
                factory,IntSize(32,32)).background(ComposeColor.White))
        }
        try {
            near(64,Color.getR(pixel(scene,16)))
            val settled=calls
            repeat(4) { pixel(scene,32L+it*16L) }
            assertEquals(settled,calls)
            value=.75f
            node=com.debanshu.shaderlab.shaderx.authoring.ShaderNode.snapshot(child.background,timeSeconds=.5f,
                frameProvider=ShaderFrameProvider { listOf(FloatUniform("value",value)) })
            near(64,Color.getR(pixel(scene,128)))
            revision.longValue++
            near(191,Color.getR(pixel(scene,144)))
            playback.seekTo(9.0)
            near(128,Color.getG(pixel(scene,160)),"Parent seek cannot replace child time")
            assertFalse(scene.hasInvalidations())
        } finally { scene.close(); factory.close() }
    }

    @Test
    fun pausedProviderSettlesAndExplicitRevisionRepaintsWithoutAllocatingProvider() {
        val factory = TrackingFactory()
        val revision = mutableLongStateOf(0)
        var live = .25f
        var calls = 0
        var fail = false
        val provider = ShaderFrameProvider {
            calls++
            check(!fail) { "Temporary live source failure" }
            listOf(FloatUniform("live", live))
        }
        val definition = ShaderDefinition("controlled_live", program = ShaderProgram("""
            uniform shader content;
            uniform float live;
            half4 main(float2 p) { return half4(live, 0.0, 0.0, 1.0) * content.eval(p).a; }
        """.trimIndent(), listOf(ShaderBinding.frameFloat("live"))))
        val effect = mutableStateOf(definition.animatedEffect(frameProvider = provider))
        val errors = mutableListOf<ShaderError>()
        lateinit var playback: ShaderPlayback
        val scene = ImageComposeScene(32, 32) {
            playback = rememberShaderPlayback(initiallyPlaying = false)
            Box(Modifier.size(32.dp).controlledShaderEffect(effect.value, playback, revision.longValue,
                factory, IntSize(32, 32), onError = { errors += it }).background(ComposeColor.White))
        }
        try {
            near(64, Color.getR(pixel(scene, 16)))
            assertFalse(scene.hasInvalidations(), "Paused scene must have no pending clock, recomposition or draw work")
            val settledCalls = calls
            repeat(8) { pixel(scene, 32L + it * 100L) }
            assertEquals(settledCalls, calls, "Paused unchanged inputs must stop calling providers")
            assertFalse(scene.hasInvalidations(), "Rendering a settled scene must not schedule a next frame")
            live = .75f
            near(64, Color.getR(pixel(scene, 1000)), "Changing captured data alone does not invalidate")
            revision.longValue++
            near(191, Color.getR(pixel(scene, 1016)), "Explicit revision repaints a paused graphics layer")
            assertFalse(scene.hasInvalidations(), "Live revision renders once and settles without a continuous clock")
            assertEquals(1, factory.created)
            fail = true
            revision.longValue++
            near(191, Color.getR(pixel(scene, 1032)), "Failure keeps the last successful filter")
            assertTrue(errors.isNotEmpty())
            fail = false
            live = .5f
            revision.longValue++
            near(128, Color.getR(pixel(scene, 1048)), "Next revision recovers on the retained session")
            playback.seekTo(.375)
            pixel(scene, 1064)
            assertEquals(.375, playback.positionSeconds)
            effect.value = definition.animatedEffect(frameProvider = ShaderFrameProvider {
                listOf(FloatUniform("live", .125f))
            })
            near(32, Color.getR(pixel(scene, 1080)), "Provider identity replacement uses the same external timeline")
            assertEquals(.375, playback.positionSeconds)
            assertTrue(!scene.hasInvalidations())
            assertFalse(scene.hasInvalidations(), "Paused seek renders once and settles")
        } finally {
            scene.close()
            assertEquals(factory.created, factory.closed)
            factory.close()
        }
    }

    @Test
    fun editsResizeSourceAndFactoryReplacementKeepExternalPhaseAndRecoverTransactionally() {
        val gain = FloatParameterDefinition("controlled_time", "gain", .1f)
        fun definition(extra: String = "") = ShaderDefinition("controlled_time", program = ShaderProgram("""
            uniform shader content;
            uniform float time;
            uniform float gain;
            uniform float2 resolution;
            half4 main(float2 p) { return half4(time + gain, p.x / resolution.x, 0.0, 1.0) * content.eval(p).a; }
            // $extra
        """.trimIndent(), listOf(ShaderBinding.time(), ShaderBinding.float("gain", gain.key), ShaderBinding.resolution())),
            schema = ParameterSchema("controlled_time", listOf(gain)))
        val original = definition().animatedEffect()
        val effect = mutableStateOf(original)
        val size = mutableStateOf(IntSize(16, 16))
        val first = TrackingFactory()
        val second = TrackingFactory()
        val factory = mutableStateOf<ShaderFactory>(first)
        val errors = mutableListOf<ShaderError>()
        lateinit var playback: ShaderPlayback
        val scene = ImageComposeScene(32, 32) {
            playback = rememberShaderPlayback(initialPositionSeconds = .25, initiallyPlaying = false)
            Box(Modifier.size(32.dp).controlledShaderEffect(effect.value, playback, factory = factory.value,
                knownSize = size.value, onError = { errors += it }).background(ComposeColor.White))
        }
        try {
            val small = pixel(scene, 16)
            near(89, Color.getR(small))
            effect.value = original.withValue(gain.key, .25f)
            near(128, Color.getR(pixel(scene, 32)))
            assertEquals(.25, playback.positionSeconds)
            size.value = IntSize(32, 32)
            val large = pixel(scene, 48)
            assertTrue(Color.getG(small) > Color.getG(large) + 40)
            assertEquals(.25, playback.positionSeconds)
            effect.value = ShaderDefinition("broken", program = ShaderProgram("invalid source")).animatedEffect()
            assertEquals(large, pixel(scene, 64), "Failed replacement keeps the rendered filter")
            assertTrue(errors.isNotEmpty())
            effect.value = definition("new program identity").animatedEffect()
            near(89, Color.getR(pixel(scene, 80)))
            val rejecting = object : ShaderFactory by second {
                override fun createAnimatedSession(effect: AnimatedShaderEffect, width: Float, height: Float): ShaderResult<ShaderSession> =
                    ShaderResult.failure(ShaderError.Unknown("Factory temporarily unavailable"))
            }
            factory.value = rejecting
            near(89, Color.getR(pixel(scene, 88)), "Failed factory replacement retains pixels")
            assertTrue(first.closed < first.created, "Old native session stays owned until replacement succeeds")
            assertEquals(.25, playback.positionSeconds)
            factory.value = second
            near(89, Color.getR(pixel(scene, 96)))
            assertEquals(first.created, first.closed)
            assertEquals(.25, playback.positionSeconds)
            playback.seekTo(.5)
            near(153, Color.getR(pixel(scene, 112)))
        } finally {
            scene.close()
            assertEquals(first.created, first.closed)
            assertEquals(second.created, second.closed)
            first.close(); second.close()
        }
    }

    @Test
    fun sharedPlaybackPauseResumeSpeedSeekAndDriverSuspensionHaveNoCatchUp() {
        val first = TrackingFactory()
        val second = TrackingFactory()
        val independentFactory = TrackingFactory()
        val active = mutableStateOf(true)
        val reducedMotion = mutableStateOf(false)
        val effect = clockEffect()
        lateinit var shared: ShaderPlayback
        lateinit var independent: ShaderPlayback
        val scene = ImageComposeScene(96, 32) {
            shared = rememberShaderPlayback(initialPositionSeconds = .1, active = active.value, reducedMotion = reducedMotion.value)
            independent = rememberShaderPlayback(initialPositionSeconds = .6, initiallyPlaying = false)
            Row {
                Box(Modifier.size(32.dp).controlledShaderEffect(effect, shared, factory = first, knownSize = IntSize(32, 32)).background(ComposeColor.White))
                Box(Modifier.size(32.dp).controlledShaderEffect(effect, shared, factory = second, knownSize = IntSize(32, 32)).background(ComposeColor.White))
                Box(Modifier.size(32.dp).controlledShaderEffect(effect, independent, factory = independentFactory, knownSize = IntSize(32, 32)).background(ComposeColor.White))
            }
        }
        try {
            pixel(scene, 16)
            pixel(scene, 216)
            assertTrue(shared.positionSeconds > .2)
            assertTrue(scene.hasInvalidations(), "Playing driver requests another frame")
            assertEquals(first.lastTime, second.lastTime, "Shared effects sample exactly the same timeline")
            assertEquals(.6, independent.positionSeconds)
            shared.isPlaying = false
            pixel(scene, 232)
            val paused = shared.positionSeconds
            val refreshes = first.refreshes
            pixel(scene, 1232)
            assertEquals(paused, shared.positionSeconds)
            assertEquals(refreshes, first.refreshes)
            assertFalse(scene.hasInvalidations(), "Pausing cancels the driver frame request")
            shared.isPlaying = true
            pixel(scene, 1248)
            assertEquals(paused, shared.positionSeconds, "Resume establishes a new anchor")
            pixel(scene, 1348)
            assertTrue(abs(shared.positionSeconds - paused - .1) < .00001)
            shared.speed = 2.0
            pixel(scene, 1364)
            val accelerated = shared.positionSeconds
            pixel(scene, 1464)
            assertTrue(abs(shared.positionSeconds - accelerated - .2) < .00001)
            active.value = false
            pixel(scene, 1480)
            val hidden = shared.positionSeconds
            pixel(scene, 2480)
            assertEquals(hidden, shared.positionSeconds)
            assertFalse(scene.hasInvalidations(), "Inactive driver has no outstanding frame request")
            active.value = true
            pixel(scene, 2496)
            assertEquals(hidden, shared.positionSeconds)
            reducedMotion.value = true
            pixel(scene, 2512)
            val reduced = shared.positionSeconds
            pixel(scene, 3512)
            assertEquals(reduced, shared.positionSeconds)
            assertFalse(scene.hasInvalidations(), "Reduced motion cancels automatic frame requests")
            shared.seekTo(.25)
            near(64, Color.getR(pixel(scene, 3528)))
            assertEquals(first.lastTime, second.lastTime)
            reducedMotion.value = false
            shared.speed = 0.0
            pixel(scene, 3544)
            pixel(scene, 4544)
            assertEquals(.25, shared.positionSeconds, "Zero speed schedules no advancing timeline")
            assertFalse(scene.hasInvalidations(), "Zero speed leaves no clock, composition or draw requests")
        } finally {
            scene.close()
            for (factory in listOf(first, second, independentFactory)) {
                assertEquals(factory.created, factory.closed)
                factory.close()
            }
        }
    }

    @Test
    fun invisibleEffectDoesNotPollLiveInputsAndRefreshesWhenShown() {
        val visible = mutableStateOf(true)
        var calls = 0
        var live = .25f
        val effect = ShaderDefinition("visible_live", program = ShaderProgram("""
            uniform shader content;
            uniform float live;
            half4 main(float2 p) { return half4(live, 0.0, 0.0, 1.0) * content.eval(p).a; }
        """.trimIndent(), listOf(ShaderBinding.frameFloat("live")))).animatedEffect(
            frameProvider = ShaderFrameProvider { calls++; listOf(FloatUniform("live", live)) })
        val revision = mutableLongStateOf(0)
        val factory = TrackingFactory()
        val scene = ImageComposeScene(32, 32) {
            val playback = rememberShaderPlayback(initiallyPlaying = false)
            Box(Modifier.size(32.dp).controlledShaderEffect(effect, playback, revision.longValue,
                factory, IntSize(32, 32), visible.value).background(ComposeColor.White))
        }
        try {
            near(64, Color.getR(pixel(scene, 16)))
            visible.value = false
            pixel(scene, 32)
            val hiddenCalls = calls
            live = .75f
            revision.longValue++
            pixel(scene, 48)
            pixel(scene, 1048)
            assertEquals(hiddenCalls, calls)
            assertFalse(scene.hasInvalidations(), "Hidden paused consumer schedules no continuous work")
            visible.value = true
            near(191, Color.getR(pixel(scene, 1064)))
            assertFalse(scene.hasInvalidations(), "Showing the new live revision renders once and settles")
        } finally {
            scene.close()
            assertEquals(factory.created, factory.closed)
            factory.close()
        }
    }

    private fun clockEffect() = ShaderDefinition("shared_clock", program = ShaderProgram("""
        uniform shader content;
        uniform float time;
        half4 main(float2 p) { return half4(time, 0.0, 0.0, 1.0) * content.eval(p).a; }
    """.trimIndent(), listOf(ShaderBinding.time()))).animatedEffect()

    private fun near(expected: Int, actual: Int, message: String = "") =
        assertTrue(abs(expected - actual) <= 3, "$message expected=$expected actual=$actual")

    private fun pixel(scene: ImageComposeScene, millis: Long): Int {
        Snapshot.sendApplyNotifications()
        repeat(2) { scene.render(millis * 1_000_000L).close() }
        return scene.render(millis * 1_000_000L).use { image ->
            Bitmap.makeFromImage(image).use { it.getColor(8, 8) }
        }
    }

    private class TrackingFactory : ShaderFactory {
        private val delegate = ShaderFactory.create()
        var created = 0
        var closed = 0
        var refreshes = 0
        var lastTime: Float? = null
        override val cacheSize get() = delegate.cacheSize
        override fun isSupported() = delegate.isSupported()
        override fun clearCache() = delegate.clearCache()
        override fun close() = delegate.close()
        override fun createRenderEffect(effect: ShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> =
            delegate.createRenderEffect(effect, width, height)
        override fun createAnimatedSession(effect: AnimatedShaderEffect, width: Float, height: Float): ShaderResult<ShaderSession> =
            delegate.createAnimatedSession(effect, width, height).map { session ->
                created++
                lastTime = effect.time
                object : ShaderSession {
                    override val currentRenderEffect get() = session.currentRenderEffect
                    override fun refresh(effect: RuntimeShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> {
                        refreshes++
                        lastTime = (effect as AnimatedShaderEffect).time
                        return session.refresh(effect, width, height)
                    }
                    override fun close() { closed++; session.close() }
                }
            }
    }
}
