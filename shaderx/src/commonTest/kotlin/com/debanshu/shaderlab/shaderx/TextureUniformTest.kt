package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.uniform.ShaderTileMode
import com.debanshu.shaderlab.shaderx.uniform.TextureUniform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Contract tests for [TextureUniform] equality (by image reference identity). */
class TextureUniformTest {
    @Test
    fun equal_whenSameImageNameAndTileModes() {
        val image = ImageBitmap(4, 4)
        val a = TextureUniform("fftData", image)
        val b = TextureUniform("fftData", image)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun notEqual_whenDifferentImageInstance() {
        val a = TextureUniform("fftData", ImageBitmap(4, 4))
        val b = TextureUniform("fftData", ImageBitmap(4, 4))
        // ImageBitmap has no content equality — different instances are distinct.
        assertNotEquals(a, b)
    }

    @Test
    fun notEqual_whenDifferentName() {
        val image = ImageBitmap(4, 4)
        assertNotEquals(TextureUniform("fftData", image), TextureUniform("lut", image))
    }

    @Test
    fun notEqual_whenDifferentTileMode() {
        val image = ImageBitmap(4, 4)
        val a = TextureUniform("fftData", image, ShaderTileMode.CLAMP, ShaderTileMode.CLAMP)
        val b = TextureUniform("fftData", image, ShaderTileMode.REPEAT, ShaderTileMode.CLAMP)
        assertNotEquals(a, b)
    }

    @Test
    fun defaultsToClampTiling() {
        val u = TextureUniform("fftData", ImageBitmap(2, 2))
        assertEquals(ShaderTileMode.CLAMP, u.tileModeX)
        assertEquals(ShaderTileMode.CLAMP, u.tileModeY)
    }
}
