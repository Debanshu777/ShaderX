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
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.compose.controlledShaderEffect
import com.debanshu.shaderlab.shaderx.compose.rememberShaderPlayback
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.impl.InvertEffect
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.playback.ShaderPlayback
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.impl.use
import kotlin.math.abs
import kotlin.test.*

/** Actual Compose/Skia pipeline graphics layers, with no ImageProcessor export inference. */
class PipelinePlaybackTest {
    @Test
    fun zeroSizeClearsFilterAndPositiveSizeRestoresExternalPhase() {
        val factory = ShaderFactory.create()
        val size = mutableStateOf(IntSize(32, 32))
        lateinit var playback: ShaderPlayback
        val chain = CompositeEffect.of(clockChild("r"), clockChild("g"))
        val scene = ImageComposeScene(32, 32) {
            playback = rememberShaderPlayback(.25, initiallyPlaying = false)
            Box(Modifier.size(32.dp).controlledShaderEffect(chain, playback, factory = factory,
                knownSize = size.value).background(ComposeColor.White))
        }
        try {
            near(64, Color.getR(pixel(scene, 16)))
            size.value = IntSize.Zero
            val unfiltered = pixel(scene, 32)
            near(255, Color.getR(unfiltered)); near(255, Color.getG(unfiltered))
            assertEquals(.25, playback.positionSeconds)
            playback.seekTo(.5)
            near(255, Color.getR(pixel(scene, 48)))
            size.value = IntSize(32, 32)
            val resumed = pixel(scene, 64)
            near(128, Color.getR(resumed)); near(128, Color.getG(resumed))
            assertEquals(.5, playback.positionSeconds)
            assertFalse(scene.hasInvalidations())
        } finally { scene.close(); factory.close() }
    }

    @Test
    fun pausedChainSettlesLiveRevisionRefreshesAndLateChildFailureRetainsWholeFilter() {
        val factory = TrackingPipelineFactory()
        var live = .125f
        var fail = false
        var calls = 0
        val liveChild = ShaderDefinition("pipeline_live", program = ShaderProgram("""
            uniform shader content; uniform float live;
            half4 main(float2 p) { half4 c = content.eval(p); return half4(c.r, c.g, live * c.a, c.a); }
        """.trimIndent(), listOf(ShaderBinding.frameFloat("live")))).animatedEffect(frameProvider = ShaderFrameProvider {
            calls++; check(!fail) { "Temporary input failure" }; listOf(FloatUniform("live", live))
        })
        val chain = mutableStateOf(CompositeEffect.of(clockChild("r"), liveChild))
        val revision = mutableLongStateOf(0)
        val errors = mutableListOf<ShaderError>()
        lateinit var playback: ShaderPlayback
        val scene = ImageComposeScene(32, 32) {
            playback = rememberShaderPlayback(.25, initiallyPlaying = false)
            Box(Modifier.size(32.dp).controlledShaderEffect(chain.value, playback, revision.longValue,
                factory, IntSize(32, 32), onError = { errors += it }).background(ComposeColor.White))
        }
        try {
            val initial = pixel(scene, 16)
            near(64, Color.getR(initial)); near(32, Color.getB(initial))
            assertFalse(scene.hasInvalidations())
            val settled = calls
            repeat(5) { pixel(scene, 1000L + it * 100) }
            assertEquals(settled, calls, "Paused pipelines must not poll live inputs")
            live = .75f
            assertEquals(initial, pixel(scene, 1600), "Captured input changes require revision")
            revision.longValue++
            val updated = pixel(scene, 1616)
            near(191, Color.getB(updated))
            assertFalse(scene.hasInvalidations())
            fail = true
            playback.seekTo(.5)
            assertEquals(updated, pixel(scene, 1632), "Failing later child retains the whole previous chain")
            assertTrue(errors.isNotEmpty())
            fail = false
            revision.longValue++
            near(128, Color.getR(pixel(scene, 1648)))
            val valid = chain.value
            chain.value = CompositeEffect.of(clockChild("r"), ShaderDefinition("bad_late_child",
                program = ShaderProgram("invalid shader source")).effect())
            val lastGood = pixel(scene, 1664)
            near(128, Color.getR(lastGood)); near(191, Color.getB(lastGood))
            chain.value = valid
            revision.longValue++
            assertEquals(lastGood, pixel(scene, 1680))
            assertFalse(scene.hasInvalidations())
        } finally {
            scene.close()
            assertEquals(factory.created, factory.closed)
            factory.close()
        }
    }

    @Test
    fun sharedClockMatchesChildrenWhileIndependentPipelineKeepsOwnTimeAndEvictionCannotInvalidateSnapshots() {
        val factory = TrackingPipelineFactory()
        val firstTimes = mutableListOf<Float>()
        val secondTimes = mutableListOf<Float>()
        val chain = CompositeEffect.of(clockChild("r", firstTimes), clockChild("g", secondTimes))
        lateinit var shared: ShaderPlayback
        lateinit var independent: ShaderPlayback
        val scene = ImageComposeScene(64, 32) {
            shared = rememberShaderPlayback(.1)
            independent = rememberShaderPlayback(.6, initiallyPlaying = false)
            Row {
                Box(Modifier.size(32.dp).controlledShaderEffect(chain, shared, factory = factory,
                    knownSize = IntSize(32, 32)).background(ComposeColor.White))
                Box(Modifier.size(32.dp).controlledShaderEffect(chain, independent, factory = factory,
                    knownSize = IntSize(32, 32)).background(ComposeColor.White))
            }
        }
        try {
            pixel(scene, 16)
            pixel(scene, 216)
            assertTrue(shared.positionSeconds > .2)
            val sharedPixel = pixel(scene, 232)
            near(Color.getR(sharedPixel), Color.getG(sharedPixel))
            val independentPixel = pixel(scene, 232, 40)
            near(153, Color.getR(independentPixel)); near(153, Color.getG(independentPixel))
            assertEquals(firstTimes, secondTimes, "Every child samples exactly the same refresh timestamps")
            shared.isPlaying = false
            pixel(scene, 248)
            assertFalse(scene.hasInvalidations())
            val beforeEviction = pixel(scene, 264)
            repeat(6) { index ->
                factory.createRenderEffect(ShaderDefinition("evict_$index", program = ShaderProgram("""
                    uniform shader content;
                    half4 main(float2 p) { return content.eval(p); } // $index
                """.trimIndent())).effect(), 32f, 32f).getOrThrow()
            }
            factory.clearCache()
            assertEquals(beforeEviction, pixel(scene, 280), "Active whole filters retain native children after eviction")
            shared.seekTo(.375)
            val seek = pixel(scene, 296)
            near(96, Color.getR(seek)); near(96, Color.getG(seek))
            near(153, Color.getR(pixel(scene, 296, 40)))
            assertFalse(scene.hasInvalidations())
        } finally {
            scene.close()
            assertEquals(factory.created, factory.closed)
            factory.close()
        }
    }

    @Test
    fun sizeSourceAndValuesReplacementAreTransactionalAndClosedSessionRejectsRefresh() {
        ShaderFactory.create(1).use { factory ->
            fun spatial(sourceSuffix: String = "", gain: Float = 0f) = ShaderDefinition("pipeline_spatial", program = ShaderProgram("""
                uniform shader content; uniform float2 resolution; uniform float gain;
                half4 main(float2 p) { return half4(p.x / resolution.x + gain, 0.0, 0.0, 1.0) * content.eval(p).a; }
                // $sourceSuffix
            """.trimIndent(), listOf(ShaderBinding.resolution(), ShaderBinding.uniform(FloatUniform("gain", gain))))).effect()
            val original = CompositeEffect.of(spatial(), InvertEffect())
            val session = factory.createPipelineSession(original, 16f, 16f).getOrThrow()
            val first = session.currentRenderEffect
            val firstPixel = rasterPixel(first)
            near(120, Color.getR(firstPixel))
            val resized = session.refresh(CompositeEffect.of(spatial("new source", .125f), InvertEffect()), 32f, 32f, .25f).getOrThrow()
            assertTrue(first !== resized)
            near(155, Color.getR(rasterPixel(resized)))
            assertEquals(firstPixel, rasterPixel(first), "Replacing size/source/values leaves old snapshots usable")
            val failed = session.refresh(CompositeEffect.of(spatial(), ShaderDefinition("late_bad",
                program = ShaderProgram("bad source")).effect()), 64f, 64f, .5f)
            assertIs<ShaderResult.Failure<RenderEffect>>(failed)
            assertSame(resized, session.currentRenderEffect)
            near(155, Color.getR(rasterPixel(session.currentRenderEffect)))
            assertIs<ShaderResult.Failure<RenderEffect>>(session.refresh(original, 0f, 16f, .5f))
            assertSame(resized, session.currentRenderEffect)
            val returned = session.refresh(original, 16f, 16f, .5f).getOrThrow()
            assertSame(returned, session.currentRenderEffect)
            assertEquals(firstPixel, rasterPixel(returned))
            session.close(); session.close()
            assertIs<ShaderResult.Failure<RenderEffect>>(session.refresh(original, 16f, 16f, .5f))
        }
    }

    @Test
    fun laterProviderCannotMutateEarlierStaticUniformSnapshotOrReplaceLastGoodChainOnFailure() {
        ShaderFactory.create(1).use { factory ->
            val aliased = floatArrayOf(.25f)
            val static = object : RuntimeShaderEffect {
                override val id = "pipeline_alias"
                override val displayName = id
                override val parameters: List<ParameterSpec> = emptyList()
                override val shaderSource = """
                    uniform shader content; uniform float level;
                    half4 main(float2 p) { return half4(level, 0.0, 0.0, 1.0) * content.eval(p).a; }
                """.trimIndent()
                override fun buildUniforms(width: Float, height: Float) = listOf(FloatUniform("level", aliased))
                override fun withTypedParameter(parameterId: String, value: ParameterValue): RuntimeShaderEffect = this
            }
            var fail = false
            val later = ShaderDefinition("pipeline_mutating_provider", program = ShaderProgram("""
                uniform shader content; uniform float live;
                half4 main(float2 p) { half4 c = content.eval(p); return half4(c.r, live * c.a, c.b, c.a); }
            """.trimIndent(), listOf(ShaderBinding.frameFloat("live")))).animatedEffect(frameProvider = ShaderFrameProvider {
                aliased[0] = .75f
                check(!fail) { "Later child failed after mutating caller array" }
                listOf(FloatUniform("live", .5f))
            })
            val chain = CompositeEffect.of(static, later)
            factory.createPipelineSession(chain, 16f, 16f).getOrThrow().use { session ->
                val first = session.currentRenderEffect
                val pixel = rasterPixel(first)
                near(64, Color.getR(pixel)); near(128, Color.getG(pixel))
                aliased[0] = .125f
                fail = true
                assertIs<ShaderResult.Failure<RenderEffect>>(session.refresh(chain, 16f, 16f, .5f))
                assertSame(first, session.currentRenderEffect)
                assertEquals(pixel, rasterPixel(first))
                fail = false
                aliased[0] = .5f
                near(128, Color.getR(rasterPixel(session.refresh(chain, 16f, 16f, .5f).getOrThrow())))
                assertEquals(pixel, rasterPixel(first))
            }
        }
    }

    private fun clockChild(channel: String, observed: MutableList<Float>? = null): DefinedAnimatedShaderEffect {
        val output = if (channel == "r") "half4(time * c.a, c.g, c.b, c.a)" else "half4(c.r, time * c.a, c.b, c.a)"
        return ShaderDefinition("pipeline_clock_$channel", program = ShaderProgram("""
            uniform shader content; uniform float time; uniform float marker;
            half4 main(float2 p) { half4 c = content.eval(p); return $output + marker * 0.0; }
        """.trimIndent(), listOf(ShaderBinding.time(), ShaderBinding.frameFloat("marker")))).animatedEffect(
            frameProvider = ShaderFrameProvider { time -> observed?.add(time); listOf(FloatUniform("marker", 0f)) })
    }

    private fun rasterPixel(filter: RenderEffect): Int = Surface.makeRasterN32Premul(16, 16).use { surface ->
        surface.canvas.clear(Color.TRANSPARENT)
        Paint().use { layer ->
            layer.imageFilter = filter.asSkiaImageFilter()
            surface.canvas.saveLayer(Rect.makeWH(16f, 16f), layer)
            Paint().use { content ->
                content.color = Color.WHITE
                surface.canvas.drawRect(Rect.makeWH(16f, 16f), content)
            }
            surface.canvas.restore()
        }
        surface.makeImageSnapshot().use { image -> Bitmap.makeFromImage(image).use { it.getColor(8, 8) } }
    }

    private fun near(expected: Int, actual: Int) = assertTrue(abs(expected - actual) <= 3, "Expected $expected got $actual")
    private fun pixel(scene: ImageComposeScene, millis: Long, x: Int = 8): Int {
        Snapshot.sendApplyNotifications()
        repeat(2) { scene.render(millis * 1_000_000).close() }
        return scene.render(millis * 1_000_000).use { image -> Bitmap.makeFromImage(image).use { it.getColor(x, 8) } }
    }

    private class TrackingPipelineFactory(private val delegate: ShaderFactory = ShaderFactory.create(1)) :
        ShaderFactory by delegate, ShaderPipelineProvider {
        var created = 0
        var closed = 0
        override fun createPipelineSession(effect: CompositeEffect, width: Float, height: Float, timeSeconds: Float): ShaderResult<ShaderPipelineSession> =
            delegate.createPipelineSession(effect, width, height, timeSeconds).map { session ->
                created++
                object : ShaderPipelineSession {
                    override val currentRenderEffect get() = session.currentRenderEffect
                    override fun refresh(effect: CompositeEffect, width: Float, height: Float, timeSeconds: Float) = session.refresh(effect, width, height, timeSeconds)
                    override fun close() { closed++; session.close() }
                }
            }
    }
}
