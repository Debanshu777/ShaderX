package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.PlasmaEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM tests for [com.debanshu.shaderlab.shaderx.factory.ShaderSession] lifecycle and refresh.
 */
class ShaderSessionTest {
    @Test
    fun createAnimatedSession_waveEffect_succeeds() {
        val factory = ShaderFactory.create()
        val result = factory.createAnimatedSession(WaveEffect(animate = true), 200f, 200f)
        assertTrue(result is ShaderResult.Success, "Expected Success, got $result")
        result.getOrNull()?.close()
        factory.close()
    }

    @Test
    fun session_refresh_withUpdatedTime_succeeds() {
        val factory = ShaderFactory.create()
        val sessionResult = factory.createAnimatedSession(WaveEffect(animate = true), 200f, 200f)
        assertTrue(sessionResult is ShaderResult.Success)
        val session = (sessionResult as ShaderResult.Success).value

        val refresh0 = session.refresh(WaveEffect(animate = true, time = 0f), 200f, 200f)
        val refresh1 = session.refresh(WaveEffect(animate = true, time = 1.5f), 200f, 200f)

        assertTrue(refresh0 is ShaderResult.Success)
        assertTrue(refresh1 is ShaderResult.Success)
        assertTrue(
            refresh0.value !== refresh1.value,
            "Each refresh must return a new RenderEffect wrapper so Compose repaints",
        )
        session.close()
        factory.close()
    }

    @Test
    fun session_refresh_doesNotGrowCompileCache() {
        val factory = ShaderFactory.create()
        val sessionResult = factory.createAnimatedSession(PlasmaEffect(animate = true), 200f, 200f)
        assertTrue(sessionResult is ShaderResult.Success)
        val session = (sessionResult as ShaderResult.Success).value
        val cacheAfterCreate = factory.cacheSize

        repeat(30) { frame ->
            session.refresh(PlasmaEffect(animate = true, time = frame * 0.016f), 200f, 200f)
        }

        assertEquals(cacheAfterCreate, factory.cacheSize, "Refresh must not add cache entries")
        session.close()
        factory.close()
    }

    @Test
    fun session_close_isIdempotent() {
        val factory = ShaderFactory.create()
        val session = factory.createAnimatedSession(WaveEffect(), 100f, 100f).getOrNull()!!
        session.close()
        session.close()
        factory.close()
    }

    @Test
    fun staticEffect_createRenderEffect_doesNotUseSession() {
        val factory = ShaderFactory.create()
        val result = factory.createRenderEffect(GrayscaleEffect(), 200f, 200f)
        assertTrue(result is ShaderResult.Success)
        factory.close()
    }

    @Test
    fun animatedEffects_allCompileViaSession() {
        val factory = ShaderFactory.create()
        val animatedEffects =
            ShaderX
                .builtInEffects()
                .filterIsInstance<com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect>()
        val failures = mutableListOf<String>()

        animatedEffects.forEach { effect ->
            val sessionResult = factory.createAnimatedSession(effect, 200f, 200f)
            if (sessionResult is ShaderResult.Failure) {
                failures += "${effect.id}: ${sessionResult.error.message}"
            } else {
                sessionResult.getOrNull()?.close()
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        factory.close()
    }
}
