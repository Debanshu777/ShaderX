package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM/Skia tests for the [com.debanshu.shaderlab.shaderx.uniform.TextureUniform] binding path
 * (`RuntimeShaderBuilder.child`) via an effect that declares an extra `uniform shader`.
 */
class TextureUniformSkiaTest {
    @Test
    fun createAnimatedSession_withTextureUniform_succeeds() {
        val factory = ShaderFactory.create()
        val effect = AudioVisualizerEffect(fftSize = 64f).withFftData(ImageBitmap(64, 1))
        val result = factory.createAnimatedSession(effect, 200f, 200f)
        assertTrue(result is ShaderResult.Success, "Expected Success, got $result")
        result.getOrNull()?.close()
        factory.close()
    }

    @Test
    fun refresh_reappliesUpdatedTexture_eachFrame() {
        val factory = ShaderFactory.create()
        val base = AudioVisualizerEffect(fftSize = 64f).withFftData(ImageBitmap(64, 1))
        val sessionResult = factory.createAnimatedSession(base, 200f, 200f)
        assertTrue(sessionResult is ShaderResult.Success)
        val session = sessionResult.value
        val cacheAfterCreate = factory.cacheSize

        // Simulate live FFT: a fresh image every frame.
        repeat(10) { frame ->
            val framed = base.withFftData(ImageBitmap(64, 1)).withTime(frame * 0.016f)
            val r = session.refresh(framed, 200f, 200f)
            assertTrue(r is ShaderResult.Success, "Frame $frame failed: $r")
        }

        assertEquals(
            cacheAfterCreate,
            factory.cacheSize,
            "Per-frame texture refresh must not grow the compile cache",
        )
        session.close()
        factory.close()
    }

    @Test
    fun refresh_resolvesLiveFftDataSource_eachFrame() {
        val factory = ShaderFactory.create()
        var current = ImageBitmap(64, 1)
        val effect = AudioVisualizerEffect(fftSize = 64f).withFftDataSource { current }
        val sessionResult = factory.createAnimatedSession(effect, 200f, 200f)
        assertTrue(sessionResult is ShaderResult.Success)
        val session = sessionResult.value

        // Same effect instance; only the source's target image changes — mirrors the sample app.
        repeat(10) { frame ->
            current = ImageBitmap(64, 1)
            val r = session.refresh(effect.withTime(frame * 0.016f), 200f, 200f)
            assertTrue(r is ShaderResult.Success, "Live-source frame $frame failed: $r")
        }

        session.close()
        factory.close()
    }

    @Test
    fun staticRenderEffect_withTextureUniform_succeeds() {
        val factory = ShaderFactory.create()
        val effect = AudioVisualizerEffect(fftSize = 64f).withFftData(ImageBitmap(64, 1))
        val result = factory.createRenderEffect(effect, 200f, 200f)
        assertTrue(result is ShaderResult.Success, "Expected Success, got $result")
        factory.close()
    }
}
