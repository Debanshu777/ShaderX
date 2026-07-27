package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.effect.impl.AudioDistortionEffect
import com.debanshu.shaderlab.shaderx.uniform.TextureUniform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AudioDistortionEffectTest {
    @Test
    fun structuralEquality_excludesFftDataAndTime() {
        val base = AudioDistortionEffect(fftSize = 64f)
        val newFrame = base.withFftData(ImageBitmap(64, 1)).withTime(1.5f)
        assertEquals(base, newFrame)
        assertEquals(base.hashCode(), newFrame.hashCode())
    }

    @Test
    fun buildFrameUniforms_resolvesLiveSourceEachCall() {
        var current = ImageBitmap(8, 1)
        val effect = AudioDistortionEffect(fftSize = 8f).withFftDataSource { current }
        assertTrue(
            effect
                .buildFrameUniforms()
                .filterIsInstance<TextureUniform>()
                .single()
                .image === current,
        )
        val next = ImageBitmap(8, 1)
        current = next
        assertTrue(
            effect
                .buildFrameUniforms()
                .filterIsInstance<TextureUniform>()
                .single()
                .image === next,
        )
    }

    @Test
    fun shaderSource_samplesContentNotBars() {
        val src = AudioDistortionEffect().shaderSource
        assertTrue(src.contains("content.eval(sampleCoord)"), "Must warp content sampling")
        assertTrue(!src.contains("barColor"), "Must not draw spectrum bars")
        assertNotNull(src)
    }
}
