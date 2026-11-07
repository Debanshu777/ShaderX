package com.debanshu.shaderlab.shaderx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderSession
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rasterizes real Compose graphics layers and Skia shaders; no window, screenshot goldens, or mocks. */
class ComposeShaderPixelTest {
    @Test
    fun animation_repaintsActualPixelsWithoutCompositionInputChanges() {
        val factory = TrackingFactory()
        val effect = PixelEffect(animate = true)
        val scene = ImageComposeScene(32, 32) {
            Box(Modifier.size(32.dp).shaderEffect(effect, factory, IntSize(32, 32)).background(ComposeColor.White))
        }
        try {
            pixel(scene, 0)
            val initial = pixel(scene, 16)
            val later = pixel(scene, 516)
            assertTrue(Color.getR(later) > Color.getR(initial) + 80, "Animation must repaint the red channel")
            assertEquals(Color.getG(initial), Color.getG(later))
            assertEquals(1, factory.createdSessions)
        } finally {
            scene.close()
            assertEquals(factory.createdSessions, factory.closedSessions)
            factory.close()
        }
    }

    @Test
    fun pauseAndResume_useSuppliedTimeThenRestartWhileLiveFramesKeepUpdating() {
        val factory = TrackingFactory()
        val effect = mutableStateOf(PixelEffect(time = 0.25f), referentialEqualityPolicy())
        val frameKey = mutableIntStateOf(0)
        val scene = ImageComposeScene(32, 32) {
            Box(Modifier.size(32.dp).shaderEffect(effect.value, factory, IntSize(32, 32), frameKey.intValue).background(ComposeColor.White))
        }
        try {
            pixel(scene, 0)
            val paused = pixel(scene, 16)
            val stillPaused = pixel(scene, 516)
            assertEquals(paused, stillPaused)
            assertTrue(Color.getR(paused) in 62..65)

            effect.value = PixelEffect(level = 0.25f, time = 0.25f)
            frameKey.intValue++
            val updated = pixel(scene, 532)
            assertEquals(0.25f, (factory.lastFrame as PixelEffect).level, "The existing session must receive the new instance")
            val nativePixel = rasterPixel(factory.lastRenderEffect!!)
            assertTrue(Color.getR(nativePixel) in 126..129, "Native filter must contain latest uniforms: ${Color.getR(nativePixel)}")
            assertTrue(Color.getR(updated) in 126..129, "Paused live input must repaint: paused=$paused, updated=$updated, red=${Color.getR(updated)}")
            assertEquals(1, factory.createdSessions, "Live data must retain its structural session")

            effect.value = PixelEffect(level = 0.25f, animate = true)
            pixel(scene, 548)
            val resumed = pixel(scene, 564)
            val advancing = pixel(scene, 764)
            assertTrue(Color.getR(advancing) > Color.getR(resumed) + 30)
            assertEquals(2, factory.createdSessions)
            assertEquals(1, factory.closedSessions)
        } finally {
            scene.close()
            factory.close()
        }
    }

    @Test
    fun resizeZeroSizeRemovalAndFailureRecovery_doNotLeaveStalePixels() {
        val factory = TrackingFactory()
        val effect = mutableStateOf<ShaderEffect?>(PixelEffect(level = 0.25f))
        val knownSize = mutableStateOf(IntSize(16, 16))
        val failures = mutableListOf<ShaderError>()
        val scene = ImageComposeScene(32, 32) {
            Box(Modifier.size(32.dp).shaderEffect(effect.value, factory, knownSize.value, onError = { failures += it }).background(ComposeColor.White))
        }
        try {
            pixel(scene, 0)
            val small = pixel(scene, 16)
            knownSize.value = IntSize(32, 32)
            val large = pixel(scene, 32)
            assertTrue(Color.getG(small) > Color.getG(large) + 40, "Resize must update resolution uniforms: small=${Color.getG(small)}, large=${Color.getG(large)}")

            knownSize.value = IntSize.Zero
            assertEquals(Color.WHITE, pixel(scene, 48), "Invalid dimensions must clear the prior render effect")
            knownSize.value = IntSize(32, 32)
            assertTrue(Color.getR(pixel(scene, 64)) in 62..65)

            effect.value = PixelEffect(broken = true)
            assertEquals(Color.WHITE, pixel(scene, 80), "Compilation failure must clear the prior shader")
            assertTrue(failures.isNotEmpty())
            effect.value = PixelEffect(level = 0.75f)
            assertTrue(Color.getR(pixel(scene, 96)) in 190..193, "A valid replacement must recover")

            effect.value = null
            assertEquals(Color.WHITE, pixel(scene, 112), "Removing the effect must restore original pixels")
            assertEquals(factory.createdSessions, factory.closedSessions)
        } finally {
            scene.close()
            factory.close()
        }
    }

    @Test
    fun transientFrameFailure_reportsErrorAndRecoversWithoutRecreatingSession() {
        val factory = TrackingFactory()
        val effect = mutableStateOf(PixelEffect(level = 0.25f), referentialEqualityPolicy())
        val frameKey = mutableIntStateOf(0)
        val failures = mutableListOf<ShaderError>()
        val scene = ImageComposeScene(32, 32) {
            Box(Modifier.size(32.dp).shaderEffect(effect.value, factory, IntSize(32, 32), frameKey.intValue) { failures += it }.background(ComposeColor.White))
        }
        try {
            val initial = pixel(scene, 16)
            effect.value = PixelEffect(level = 0.5f, failFrame = true)
            frameKey.intValue++
            assertEquals(initial, pixel(scene, 32), "A transient frame failure retains the last valid frame")
            assertTrue(failures.isNotEmpty())
            effect.value = PixelEffect(level = 0.75f)
            frameKey.intValue++
            assertTrue(Color.getR(pixel(scene, 48)) in 190..193, "Live frames must recover on the existing session")
            assertEquals(1, factory.createdSessions)
        } finally {
            scene.close()
            factory.close()
        }
    }

    @Test
    fun repeatedConfigurationAndCacheEviction_keepRenderingAndCloseEverySession() {
        val factory = TrackingFactory(cacheSize = 2)
        val effect = mutableStateOf(PixelEffect())
        val scene = ImageComposeScene(32, 32) {
            Box(Modifier.size(32.dp).shaderEffect(effect.value, factory, IntSize(32, 32)).background(ComposeColor.White))
        }
        try {
            pixel(scene, 0)
            repeat(100) { index ->
                val level = (index % 3 + 1) / 4f
                effect.value = PixelEffect(level = level, configuration = index + 1)
                val rendered = pixel(scene, (index + 1L) * 16L)
                assertTrue(kotlin.math.abs(Color.getR(rendered) - (level * 255).toInt()) <= 1)
                assertTrue(factory.cacheSize <= 2)
                assertEquals(factory.createdSessions - 1, factory.closedSessions)
                if (index % 10 == 0) factory.clearCache()
            }
        } finally {
            scene.close()
            assertEquals(factory.createdSessions, factory.closedSessions)
            factory.close()
        }
    }

    private fun rasterPixel(effect: RenderEffect): Int = Surface.makeRasterN32Premul(32, 32).use { surface ->
        Paint().use { paint ->
            paint.color = Color.WHITE
            paint.imageFilter = effect.asSkiaImageFilter()
            surface.canvas.drawRect(Rect.makeWH(32f, 32f), paint)
        }
        surface.makeImageSnapshot().use { image ->
            Bitmap.makeFromImage(image).use { it.getColor(8, 8) }
        }
    }

    private fun pixel(scene: ImageComposeScene, millis: Long): Int {
        Snapshot.sendApplyNotifications()
        // Settle recomposition, the frame callback, and the graphics-layer invalidation in order.
        // Reuse the timestamp so this does not advance the animation while inputs settle.
        repeat(2) { scene.render(millis * 1_000_000L).close() }
        return scene.render(millis * 1_000_000L).use { image ->
            Bitmap.makeFromImage(image).use { it.getColor(8, 8) }
        }
    }

    private class PixelEffect(
        val level: Float = 0f,
        val animate: Boolean = false,
        override val time: Float = 0f,
        val configuration: Int = 0,
        val broken: Boolean = false,
        val failFrame: Boolean = false,
    ) : AnimatedShaderEffect {
        override val id = "pixel_test"
        override val displayName = "Pixel test"
        override val shaderSource = if (broken) "invalid shader" else """
            uniform shader content;
            uniform float2 resolution;
            uniform float time;
            uniform float level;
            half4 main(float2 p) {
                half alpha = content.eval(p).a;
                return half4(clamp(level + time, 0.0, 1.0), p.x / resolution.x, 0.0, alpha);
            }
            // Distinct sources exercise capacity eviction: $configuration
        """.trimIndent()
        override val parameters: List<ParameterSpec> = emptyList()
        override val isAnimating get() = animate
        override fun buildUniforms(width: Float, height: Float): List<Uniform> =
            listOf(FloatUniform("resolution", width, height), FloatUniform("time", time), FloatUniform("level", level))
        override fun buildFrameUniforms(): List<Uniform> {
            check(!failFrame) { "Temporary frame source failure" }
            return listOf(FloatUniform("time", time), FloatUniform("level", level))
        }
        override fun withTime(newTime: Float) = PixelEffect(level, animate, newTime, configuration, broken, failFrame)
        override fun withTypedParameter(parameterId: String, value: ParameterValue) = this
        override fun equals(other: Any?) = other is PixelEffect && animate == other.animate && configuration == other.configuration && broken == other.broken
        override fun hashCode() = 31 * configuration + animate.hashCode() + broken.hashCode()
    }

    private class TrackingFactory(cacheSize: Int = 4) : ShaderFactory {
        private val delegate = ShaderFactory.create(cacheSize)
        var createdSessions = 0
        var closedSessions = 0
        var lastFrame: RuntimeShaderEffect? = null
        var lastRenderEffect: RenderEffect? = null
        override val cacheSize get() = delegate.cacheSize
        override fun isSupported() = delegate.isSupported()
        override fun clearCache() = delegate.clearCache()
        override fun close() = delegate.close()
        override fun createRenderEffect(effect: ShaderEffect, width: Float, height: Float) = delegate.createRenderEffect(effect, width, height)
        override fun createAnimatedSession(effect: AnimatedShaderEffect, width: Float, height: Float): ShaderResult<ShaderSession> =
            delegate.createAnimatedSession(effect, width, height).map { session ->
                createdSessions++
                object : ShaderSession {
                    override val currentRenderEffect: RenderEffect get() = session.currentRenderEffect
                    override fun refresh(effect: RuntimeShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> {
                        lastFrame = effect
                        return session.refresh(effect, width, height).onSuccess { lastRenderEffect = it }
                    }
                    override fun close() {
                        closedSessions++
                        session.close()
                    }
                }
            }
    }
}
