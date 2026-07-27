package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect
import com.debanshu.shaderlab.shaderx.uniform.TextureUniform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies the live-data contract for [AudioVisualizerEffect]: config equality excludes the
 * spectrum image / live source / time, and [AudioVisualizerEffect.buildFrameUniforms] always
 * resolves the latest image (snapshot or [AudioVisualizerEffect.withFftDataSource]).
 */
class AudioVisualizerEffectTest {
    @Test
    fun structuralEquality_excludesFftDataAndTime() {
        val base = AudioVisualizerEffect(fftSize = 64f)
        val newFrame = base.withFftData(ImageBitmap(64, 1)).withTime(1.5f)
        assertEquals(base, newFrame, "Changing fftData/time must not change structural identity")
        assertEquals(base.hashCode(), newFrame.hashCode())
    }

    @Test
    fun structuralEquality_excludesLiveSource() {
        val a = AudioVisualizerEffect(fftSize = 64f).withFftDataSource { ImageBitmap(8, 1) }
        val b = AudioVisualizerEffect(fftSize = 64f).withFftDataSource { ImageBitmap(64, 1) }
        assertEquals(a, b, "Live source identity must not affect structural equality")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun structuralEquality_honorsConfig() {
        assertTrue(AudioVisualizerEffect(fftSize = 32f) != AudioVisualizerEffect(fftSize = 64f))
    }

    @Test
    fun buildFrameUniforms_emitsLatestTexture() {
        val image = ImageBitmap(64, 1)
        val effect = AudioVisualizerEffect(fftSize = 64f).withFftData(image).withTime(2f)
        val frame = effect.buildFrameUniforms()

        val texture = frame.filterIsInstance<TextureUniform>().firstOrNull()
        assertNotNull(texture, "buildFrameUniforms must emit the fftData texture")
        assertEquals("fftData", texture.name)
        assertTrue(texture.image === image, "The latest image instance must be emitted")
    }

    @Test
    fun buildFrameUniforms_resolvesLiveSourceEachCall() {
        var current = ImageBitmap(8, 1)
        val effect = AudioVisualizerEffect(fftSize = 8f).withFftDataSource { current }

        val first = effect.buildFrameUniforms().filterIsInstance<TextureUniform>().single()
        assertTrue(first.image === current)

        val next = ImageBitmap(8, 1)
        current = next
        val second = effect.buildFrameUniforms().filterIsInstance<TextureUniform>().single()
        assertTrue(
            second.image === next,
            "Live source must be re-invoked every buildFrameUniforms call",
        )
    }

    @Test
    fun withFftData_returnsNewInstanceBoundToImage() {
        val image = ImageBitmap(64, 1)
        val effect = AudioVisualizerEffect().withFftData(image)
        assertTrue(effect.fftData === image)
    }
}
