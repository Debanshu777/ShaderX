package io.github.debanshu.shaderx.validation

import com.debanshu.shaderlab.shaderx.authoring.DefinedAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.authoring.ShaderBindingKind
import com.debanshu.shaderlab.shaderx.parameter.*
import com.debanshu.shaderlab.shaderx.uniform.*
import sx06.generated.adversarial.ExportedShader
import kotlin.test.*

/** Compiles and invokes the producer's actual Kotlin output from a non-friend SDK consumer. */
class GeneratedPlaygroundConsumerTest {
    @Test fun generatedImageTemplatesRequireCallerInputsAndPreserveNamesModesAndLiveProviders() {
        val image = androidx.compose.ui.graphics.ImageBitmap(2, 2)
        val static = sx07.generated.image.ExportedShader.effect(image) as com.debanshu.shaderlab.shaderx.authoring.DefinedShaderEffect
        assertTrue(static.shaderSource.contains("BODY INPUT_ARGUMENTS INPUT_PARAMETERS"))
        val texture = static.buildUniforms(8f, 8f).single() as TextureUniform
        assertEquals("image_input", texture.name)
        assertSame(image, texture.image)
        assertEquals(ShaderTileMode.REPEAT, texture.tileModeX)
        assertEquals(ShaderTileMode.MIRROR, texture.tileModeY)
        var calls = 0
        val provider = com.debanshu.shaderlab.shaderx.authoring.ShaderFrameProvider {
            calls++
            listOf(TextureUniform("live_image", image, ShaderTileMode.REPEAT, ShaderTileMode.MIRROR), FloatUniform("live_amount", .25f))
        }
        val live = sx07.generated.liveimage.ExportedShader.effect(image, provider) as DefinedAnimatedShaderEffect
        assertEquals(0, calls)
        assertFalse(live.isAnimating)
        val structural = live.buildUniforms(8f, 8f).single { it.name == "image_input" } as TextureUniform
        assertSame(image, structural.image)
        assertEquals(ShaderTileMode.MIRROR, structural.tileModeX)
        assertEquals(ShaderTileMode.REPEAT, structural.tileModeY)
        assertEquals(1, calls)
        val frame = live.buildFrameUniforms()
        assertEquals(2, calls)
        assertEquals(FloatUniform("live_amount", .25f), frame.single { it.name == "live_amount" })
        val frameImage = frame.single { it.name == "live_image" } as TextureUniform
        assertSame(image, frameImage.image)
        assertEquals(ShaderTileMode.REPEAT, frameImage.tileModeX)
        assertEquals(ShaderTileMode.MIRROR, frameImage.tileModeY)
    }

    @Test fun generatedDefinitionsReproduceExactSchemaValuesSourceAndBindings() {
        val effect = ExportedShader.effect() as DefinedAnimatedShaderEffect
        val definition = effect.definition
        assertEquals("Fixture \$ \"\"\"\n", definition.displayName)
        assertTrue(definition.program.source.startsWith("// dollar \$path; backslash \\; triple \"\"\"\n// second line\n"))
        assertEquals(ParameterValue.FloatValue(0.12345679f), effect.getTypedParameterValue("when"))
        assertEquals(ParameterValue.ColorValue(0x20123456L), effect.getTypedParameterValue("a-b"))
        assertEquals(ParameterValue.BooleanValue(true), effect.getTypedParameterValue("a b"))
        assertFalse(effect.isAnimating)
        val amount = definition.schema.definition("when") as FloatParameterDefinition
        assertNull(amount.range)
        assertEquals((-0.0f).toBits(), amount.defaultValue.toBits())
        assertEquals("Amount \$ \\ \"\"\"\n", amount.metadata.label)
        assertEquals("Help\nline", amount.metadata.description)
        assertEquals(7, amount.metadata.order)
        assertEquals("gain", amount.metadata.semanticRole)
        assertEquals("µpx", amount.unit)
        assertEquals(.0001f, amount.step)
        assertEquals(2, amount.decimalPlaces)
        assertEquals(listOf("amount", "tint", "enabled", "size", "elapsed", "vector", "mode", "matrix", "constantColor"),
            definition.program.bindings.map { it.name })
        val constants = definition.program.bindings.map { it.descriptor }.filter { it.kind == ShaderBindingKind.CONSTANT }
            .associateBy { it.name }
        assertContentEquals(floatArrayOf(Float.MIN_VALUE, Float.MAX_VALUE), (constants.getValue("vector").constant as FloatUniform).values)
        assertContentEquals(intArrayOf(Int.MIN_VALUE), (constants.getValue("mode").constant as IntUniform).values)
        assertContentEquals(floatArrayOf(1f, -0f, -1f, 1e-10f), (constants.getValue("matrix").constant as MatrixUniform).values)
        assertEquals(ColorUniform("constantColor", .12345679f, .3f, .7f, .125f), constants.getValue("constantColor").constant)
        val uniforms = effect.withTime(2f).buildUniforms(32f, 16f)
        assertEquals(FloatUniform("size", 32f, 16f), uniforms.single { it.name == "size" })
        assertEquals(FloatUniform("elapsed", 2f), uniforms.single { it.name == "elapsed" })
        assertEquals(ColorUniform("tint", 0x20123456L), uniforms.single { it.name == "tint" })
        assertEquals(IntUniform("enabled", 1), uniforms.single { it.name == "enabled" })
    }
}
