package io.github.debanshu.shaderx.validation

import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.uniform.*
import kotlin.test.*

/** Executes actual generated source compiled outside the sample and library modules. */
class GeneratedBackgroundConsumerTest {
    @Test fun generatedBackgroundPreservesValuesResolutionTimeAndExplicitImageRequirements() {
        val static = sx08.generated.background0.ExportedShader.effect() as DefinedShaderEffect
        assertEquals(ParameterValue.FloatValue(.375f), static.getTypedParameterValue("intensity"))
        assertFalse(static.shaderSource.contains("content"))
        static.definition.proceduralBackground()
        val resolution = static.buildUniforms(80f, 40f).filterIsInstance<FloatUniform>().single { it.name == "resolution" }
        assertContentEquals(floatArrayOf(80f, 40f), resolution.values)
        val animated = sx08.generated.background1.ExportedShader.effect() as DefinedAnimatedShaderEffect
        assertTrue(animated.definition.program.bindings.any { it.descriptor.kind == ShaderBindingKind.TIME })
        val image = ImageBitmap(2, 3)
        val external = sx08.generated.background2.ExportedShader.effect(image) as DefinedShaderEffect
        val texture = external.buildUniforms(80f, 40f).filterIsInstance<TextureUniform>().single()
        assertSame(image, texture.image)
        assertEquals(ShaderTileMode.REPEAT, texture.tileModeX)
        assertEquals(ShaderTileMode.MIRROR, texture.tileModeY)
        val provider = ShaderFrameProvider { listOf(FloatUniform("level", floatArrayOf(.75f))) }
        val live = sx08.generated.live.ExportedShader.effect(provider) as DefinedAnimatedShaderEffect
        assertSame(provider, live.frameProvider)
        assertEquals(.75f, live.buildFrameUniforms().filterIsInstance<FloatUniform>().single().values.single())
    }
}
