package com.debanshu.shaderlab.shaderx

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.IntSize
import com.debanshu.shaderlab.shaderx.compose.rememberRenderEffect
import com.debanshu.shaderlab.shaderx.compose.rememberRenderEffectResult
import com.debanshu.shaderlab.shaderx.compose.rememberShaderEffect
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.compose.shaderEffectWithResult
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderSession
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ComposeShaderLifecycleTest {
    @Test
    fun modifierCallbacks_preservePublishedPositionalNamedAndTrailingCalls() = compositionTest {
        val factory = LegacyFactory()
        val effect = GrayscaleEffect()
        val size = IntSize(20, 20)
        val errors = mutableListOf<String>()
        val results = mutableListOf<String>()
        val positionalError: (ShaderError) -> Unit = {
            assertSame(factory.error, it)
            errors += "positional"
        }
        val positionalResult: (ShaderResult<RenderEffect>) -> Unit = {
            assertSame(factory.error, assertIs<ShaderResult.Failure<*>>(it).error)
            results += "positional"
        }
        content {
            Modifier.shaderEffect(effect, factory, size, positionalError)
            Modifier.shaderEffect(effect, factory, size) { errors += "trailing" }
            Modifier.shaderEffect(effect, factory, size, onError = { errors += "named" })
            Modifier.shaderEffect(effect, factory, size, frameDataKey = "frame", onError = { errors += "keyed" })
            Modifier.shaderEffectWithResult(effect, factory, size, positionalResult)
            Modifier.shaderEffectWithResult(effect, factory, size) { results += "trailing" }
            Modifier.shaderEffectWithResult(effect, factory, size, onResult = { results += "named" })
            Modifier.shaderEffectWithResult(effect, factory, size, frameDataKey = "frame", onResult = { results += "keyed" })
            // Compile both sets of default arguments without allocating native effects.
            Modifier.shaderEffect(null)
            Modifier.shaderEffect(null, frameDataKey = "frame")
            Modifier.shaderEffectWithResult(null)
            Modifier.shaderEffectWithResult(null, frameDataKey = "frame")
        }
        frames()
        assertEquals(listOf("keyed", "named", "positional", "trailing"), errors.sorted())
        assertEquals(listOf("keyed", "named", "positional", "trailing"), results.sorted())
        assertEquals(8, factory.renderCalls)
    }

    @Test
    fun legacyFactory_withoutSessionOverride_reportsUnsupportedAnimation() = compositionTest {
        val factory = LegacyFactory()
        val effect = WaveEffect()
        val direct = assertIs<ShaderResult.Failure<*>>(factory.createAnimatedSession(effect, 20f, 20f))
        val directError = assertIs<ShaderError.UnsupportedEffect>(direct.error)
        assertEquals(effect.id, directError.effectId)
        var callbackError: ShaderError? = null
        content {
            Modifier.shaderEffect(effect, factory, IntSize(20, 20)) { callbackError = it }
        }
        frames()
        assertEquals(directError, callbackError)
        assertEquals(0, factory.renderCalls)
    }

    @Test
    fun equalLiveFrame_reachesExistingSessionAndLatestCallback() = compositionTest {
        val factory = RecordingFactory()
        val effect = mutableStateOf(FrameEffect(frame = 1), referentialEqualityPolicy())
        val frameKey = mutableIntStateOf(0)
        var firstCalls = 0
        var latestCalls = 0
        val callback = mutableStateOf<(ShaderResult<RenderEffect>) -> Unit>({ firstCalls++ })
        content {
            Modifier.shaderEffectWithResult(effect.value, factory, IntSize(20, 20), frameKey.intValue, callback.value)
        }
        frames()
        effect.value = FrameEffect(frame = 2)
        frameKey.intValue++
        callback.value = { latestCalls++ }
        frames()
        assertEquals(1, factory.sessions.size)
        assertEquals(2, (factory.sessions.single().frames.last() as FrameEffect).frame)
        assertTrue(firstCalls > 0)
        assertTrue(latestCalls > 0)
        dispose()
        factory.close()
    }

    @Test
    fun hashCollision_recreatesSessionForUnequalConfiguration() = compositionTest {
        val factory = RecordingFactory()
        val effect = mutableStateOf(FrameEffect(configuration = 1))
        content { Modifier.shaderEffect(effect.value, factory, IntSize(20, 20)) }
        frames()
        effect.value = FrameEffect(configuration = 2)
        frames()
        assertEquals(2, factory.sessions.size)
        assertTrue(factory.sessions.first().closed)
        assertEquals(2, (factory.sessions.last().frames.last() as FrameEffect).configuration)
        dispose()
        factory.close()
    }

    @Test
    fun factoryReplacement_recreatesAnimatedAndStaticResults() = compositionTest {
        val first = RecordingFactory()
        val second = RecordingFactory()
        val factory = mutableStateOf<ShaderFactory>(first)
        val animated = FrameEffect()
        val static = GrayscaleEffect()
        content {
            Modifier.shaderEffect(animated, factory.value, IntSize(20, 20))
            Modifier.shaderEffect(static, factory.value, IntSize(20, 20))
            rememberRenderEffect(static, 20f, 20f, factory.value)
            rememberRenderEffectResult(static, 20f, 20f, factory.value)
        }
        frames()
        factory.value = second
        frames()
        assertTrue(first.sessions.single().closed)
        assertEquals(1, second.sessions.size)
        assertEquals(3, second.rendered.size)
        dispose()
        first.close()
        second.close()
    }

    @Test
    fun manualAnimation_updatesRenderEffectDespiteStructuralEquality() = compositionTest {
        val factory = RecordingFactory()
        val effect = FrameEffect(animate = true)
        content {
            val animated = rememberShaderEffect(effect)
            rememberRenderEffect(animated, 20f, 20f, factory)
        }
        frames()
        frames()
        val times = factory.rendered.filterIsInstance<FrameEffect>().map { it.time }
        assertTrue(times.distinct().size > 1, "Manual animation should create effects for changing frame times: $times")
        assertTrue(times.last() > times.first())
        dispose()
        factory.close()
    }

    private class FrameEffect(
        val configuration: Int = 1,
        val frame: Int = 0,
        val animate: Boolean = false,
        override val time: Float = 0f,
    ) : AnimatedShaderEffect {
        override val id = "frame_test"
        override val displayName = "Frame test"
        override val shaderSource = "unused by recording factory"
        override val parameters: List<ParameterSpec> = emptyList()
        override val isAnimating get() = animate
        override fun buildUniforms(width: Float, height: Float): List<Uniform> = emptyList()
        override fun withTime(newTime: Float) = FrameEffect(configuration, frame, animate, newTime)
        override fun withTypedParameter(parameterId: String, value: ParameterValue) = this
        override fun equals(other: Any?) = other is FrameEffect && configuration == other.configuration && animate == other.animate
        override fun hashCode() = 1 // Deliberate legal collision between unequal configurations.
    }

    // Implements only the factory members required by published 0.1.3.
    private class LegacyFactory : ShaderFactory {
        val error = ShaderError.UnsupportedEffect("Static rendering disabled in this test", "legacy_test")
        var renderCalls = 0
        override fun createRenderEffect(effect: ShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> {
            renderCalls++
            return ShaderResult.failure(error)
        }
        override fun isSupported() = true
        override fun clearCache() {}
        override fun close() {}
        override val cacheSize = 0
    }

    private class RecordingFactory : ShaderFactory {
        private val filter = ImageFilter.makeBlur(1f, 1f, FilterTileMode.CLAMP)
        private val renderEffect = filter.asComposeRenderEffect()
        val rendered = mutableListOf<ShaderEffect>()
        val sessions = mutableListOf<RecordingSession>()
        override fun createRenderEffect(effect: ShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> {
            rendered += effect
            return ShaderResult.success(renderEffect)
        }
        override fun createAnimatedSession(effect: AnimatedShaderEffect, width: Float, height: Float): ShaderResult<ShaderSession> =
            ShaderResult.success(RecordingSession(renderEffect).also { sessions += it })
        override fun isSupported() = true
        override fun clearCache() {}
        override fun close() = filter.close()
        override val cacheSize = 0
    }

    private class RecordingSession(override val currentRenderEffect: RenderEffect) : ShaderSession {
        val frames = mutableListOf<RuntimeShaderEffect>()
        var closed = false
        override fun refresh(effect: RuntimeShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> {
            check(!closed)
            frames += effect
            return ShaderResult.success(currentRenderEffect)
        }
        override fun close() { closed = true }
    }

    private class NoOpApplier : AbstractApplier<Unit>(Unit) {
        override fun insertBottomUp(index: Int, instance: Unit) {}
        override fun insertTopDown(index: Int, instance: Unit) {}
        override fun move(from: Int, to: Int, count: Int) {}
        override fun remove(index: Int, count: Int) {}
        override fun onClear() {}
    }

    private class Harness(val composition: Composition, val clock: BroadcastFrameClock) {
        private var frameNanos = 0L
        fun content(block: @Composable () -> Unit) = composition.setContent(block)
        fun dispose() = composition.dispose()
        suspend fun frames() {
            Snapshot.sendApplyNotifications()
            repeat(5) {
                yield()
                frameNanos += 16_000_000L
                clock.sendFrame(frameNanos)
                yield()
            }
        }
    }

    private fun compositionTest(test: suspend Harness.() -> Unit) = runBlocking {
        withTimeout(10_000) {
            val clock = BroadcastFrameClock()
            val recomposer = Recomposer(coroutineContext + clock)
            val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
            val composition = Composition(NoOpApplier(), recomposer)
            try {
                Harness(composition, clock).test()
            } finally {
                composition.dispose()
                recomposer.close()
                runner.cancelAndJoin()
            }
        }
    }
}
