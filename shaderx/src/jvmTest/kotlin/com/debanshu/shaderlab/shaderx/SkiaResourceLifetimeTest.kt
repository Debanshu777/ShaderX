package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.SepiaEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.factory.SkiaImageProcessor
import com.debanshu.shaderlab.shaderx.factory.SkiaShaderFactory
import com.debanshu.shaderlab.shaderx.factory.SkiaShaderSession
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.TextureUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkiaResourceLifetimeTest {
    @Test
    fun factory_evictionAndClear_closeCompiledWrappers() {
        val factory = SkiaShaderFactory(1)
        try {
            val first = factory.getOrCreateEffect(GrayscaleEffect().shaderSource)
            val second = factory.getOrCreateEffect(SepiaEffect().shaderSource)
            assertTrue(first.isClosed, "Capacity eviction must release the cache's native reference")
            assertFalse(second.isClosed)
            factory.clearCache()
            assertTrue(second.isClosed)
        } finally {
            factory.close()
        }
    }

    @Test
    fun activeSession_survivesCacheEvictionAndClear() {
        val factory = SkiaShaderFactory(1)
        val effect = WaveEffect()
        val session = factory.createAnimatedSession(effect, 20f, 20f).getOrThrow()
        try {
            factory.createRenderEffect(GrayscaleEffect(), 20f, 20f).getOrThrow()
            assertTrue(session.refresh(effect.withTime(1f), 20f, 20f) is ShaderResult.Success)
            factory.clearCache()
            assertTrue(session.refresh(effect.withTime(2f), 20f, 20f) is ShaderResult.Success)
        } finally {
            session.close()
            factory.close()
        }
    }

    @Test
    fun session_close_releasesBuilderAndRejectsRefresh() {
        RuntimeEffect.makeForShader(WaveEffect().shaderSource).use { compiled ->
            val builder = RuntimeShaderBuilder(compiled)
            SkiaShaderFactory.applyUniforms(builder, WaveEffect().buildUniforms(20f, 20f))
            val filter = ImageFilter.makeRuntimeShader(builder, "content", null)
            try {
                val session = SkiaShaderSession(builder, filter.asComposeRenderEffect())
                session.close()
                session.close()
                assertTrue(builder.isClosed)
                val result = session.refresh(WaveEffect(), 20f, 20f)
                assertTrue(result is ShaderResult.Failure)
                assertTrue(result.error.message.contains("closed"))
            } finally {
                filter.close()
                if (!builder.isClosed) builder.close()
            }
        }
    }

    @Test
    fun offlineTextureFilter_rendersAfterTemporaryNativeWrappersClose() {
        Surface.makeRasterN32Premul(2, 2).use { surface ->
            surface.canvas.clear(Color.RED)
            surface.makeImageSnapshot().use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { encoded ->
                    val texture = image.toComposeImageBitmap()
                    try {
                        val effect = object : RuntimeShaderEffect {
                            override val id = "texture_lifetime"
                            override val displayName = "Texture lifetime"
                            override val shaderSource = """
                                uniform shader content;
                                uniform shader texture;
                                half4 main(float2 p) { return texture.eval(p) * content.eval(p).a; }
                            """.trimIndent()
                            override val parameters: List<ParameterSpec> = emptyList()
                            override fun buildUniforms(width: Float, height: Float): List<Uniform> =
                                listOf(TextureUniform("texture", texture))
                            override fun withTypedParameter(parameterId: String, value: ParameterValue) = this
                        }
                        val factory = SkiaShaderFactory()
                        try {
                            val bytes = SkiaImageProcessor(factory).process(encoded.bytes, effect, 2f, 2f).getOrThrow()
                            Image.makeFromEncoded(bytes).use { output ->
                                Bitmap.makeFromImage(output).use { pixels ->
                                    assertEquals(Color.RED, pixels.getColor(0, 0))
                                }
                            }
                        } finally {
                            factory.close()
                        }
                    } finally {
                        texture.asSkiaBitmap().close()
                    }
                }
            }
        }
    }
}
