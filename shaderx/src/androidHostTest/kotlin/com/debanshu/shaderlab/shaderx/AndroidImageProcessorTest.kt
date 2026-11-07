package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ImageProcessor
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderSession
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertTrue

/** No Android runtime or Robolectric: invoking a bitmap API here would throw from android.jar. */
class AndroidImageProcessorTest {
    @Test
    fun validImage_returnsUnsupportedProcessingFailureWithoutRendering() {
        val png = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j+V8AAAAASUVORK5CYII=",
        )
        assertUnsupported(png)
    }

    @Test
    fun invalidBytes_rejectBeforeAttemptingDecode() {
        assertUnsupported(byteArrayOf())
        assertUnsupported(byteArrayOf(1, 2, 3))
    }

    @Test
    fun invalidDimensions_doNotTriggerAllocationOrUniformWork() {
        val result = ImageProcessor.create(UnusedFactory).process(
            ByteArray(1024), UnusedEffect, Float.POSITIVE_INFINITY, Float.NaN,
        )
        assertProcessingFailure(result)
    }

    private fun assertUnsupported(bytes: ByteArray) {
        assertProcessingFailure(ImageProcessor.create(UnusedFactory).process(bytes, UnusedEffect))
    }

    private fun assertProcessingFailure(result: ShaderResult<ByteArray>) {
        assertTrue(result is ShaderResult.Failure)
        assertTrue(result.error is ShaderError.ProcessingError)
        assertTrue(result.error.message.contains("unsupported on Android"))
    }

    private object UnusedEffect : RuntimeShaderEffect {
        override val id: String get() = error("Unsupported export must not inspect the effect")
        override val displayName: String get() = error("Unused")
        override val shaderSource: String get() = error("Unsupported export must not compile shaders")
        override val parameters: List<ParameterSpec> get() = error("Unused")
        override fun buildUniforms(width: Float, height: Float): List<Uniform> = error("Unsupported export must not build uniforms")
        override fun withTypedParameter(parameterId: String, value: ParameterValue) = this
    }

    private object UnusedFactory : ShaderFactory {
        override fun createRenderEffect(effect: ShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> = error("Must not render")
        override fun createAnimatedSession(effect: AnimatedShaderEffect, width: Float, height: Float): ShaderResult<ShaderSession> = error("Must not create sessions")
        override fun isSupported(): Boolean = error("Must not query the device")
        override fun clearCache(): Unit = error("Must not clear shared resources")
        override fun close(): Unit = error("Must not close shared resources")
        override val cacheSize: Int get() = error("Must not query cache")
    }
}
