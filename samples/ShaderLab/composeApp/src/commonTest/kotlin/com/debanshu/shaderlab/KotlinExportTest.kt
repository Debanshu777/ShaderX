package com.debanshu.shaderlab

import com.debanshu.shaderlab.authoring.CustomDefinitions
import com.debanshu.shaderlab.playground.*
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.parameter.*
import com.debanshu.shaderlab.shaderx.uniform.*
import kotlin.test.*

/** This exact fixture is generated into the independent consumer by the JVM producer test. */
object AdversarialExportFixture {
    const val SOURCE = "// dollar \$path; backslash \\; triple \"\"\"\n// second line\n" +
        "uniform shader content;\nuniform float amount;\nlayout(color) uniform half4 tint;\nuniform int enabled;\n" +
        "uniform float2 size;\nuniform float elapsed;\nuniform float2 vector;\nuniform int mode;\n" +
        "uniform float2x2 matrix;\nlayout(color) uniform half4 constantColor;\n" +
        "half4 main(float2 p) { return content.eval(p); }"
    val amount = FloatParameterDefinition("fixture", "when", -0.0f,
        metadata = ParameterMetadata("Amount \$ \\ \"\"\"\n", "Help\nline", 7, "gain"),
        unit = "µpx", step = 0.0001f, decimalPlaces = 2)
    val tint = ColorParameterDefinition("fixture", "a-b", 0x01234567L)
    val enabled = BooleanParameterDefinition("fixture", "a b", false)
    val definition = ShaderDefinition("fixture", "Fixture \$ \"\"\"\n", ShaderProgram(SOURCE, listOf(
        ShaderBinding.float("amount", amount.key), ShaderBinding.color("tint", tint.key),
        ShaderBinding.boolean("enabled", enabled.key), ShaderBinding.resolution("size"), ShaderBinding.time("elapsed"),
        ShaderBinding.uniform(FloatUniform("vector", floatArrayOf(Float.MIN_VALUE, Float.MAX_VALUE))),
        ShaderBinding.uniform(IntUniform("mode", Int.MIN_VALUE)),
        ShaderBinding.uniform(MatrixUniform("matrix", floatArrayOf(1f, -0f, -1f, 1e-10f))),
        ShaderBinding.uniform(ColorUniform("constantColor", .12345679f, .3f, .7f, .125f)),
    )), ParameterSchema("fixture", listOf(amount, tint, enabled)))
    fun effect(): DefinedAnimatedShaderEffect = definition.animatedEffect(
        definition.schema.defaults().withValue(amount.key, 0.12345679f).withValue(tint.key, 0x20123456L)
            .withValue(enabled.key, true), isAnimating = false,
    )
}

class KotlinExportTest {
    @Test fun backgroundExportKeepsSourceValuesAndCallerInputs() {
        val draft = WorkbenchStarters.background.copy(currentValues = mapOf("intensity" to ParameterValue.FloatValue(.375f)))
        val code = KotlinExport.generate(draft.build().effect, WorkbenchOutput.BACKGROUND)
        assertTrue(code.contains("proceduralBackground()"))
        assertTrue(code.contains("Modifier.shaderBackground("))
        assertTrue(code.contains("0.375f"))
        assertTrue(code.contains("ShaderBinding.resolution"))
        assertFails { KotlinExport.generate(WorkbenchStarters.tint.build().effect, WorkbenchOutput.BACKGROUND) }
    }

    @Test fun sourceIsDeterministicAndPreservesTypedValuesAndEscapes() {
        val source = KotlinExport.generate(AdversarialExportFixture.effect())
        assertEquals(source, KotlinExport.generate(AdversarialExportFixture.effect()))
        assertTrue(source.contains("0.12345679f"))
        assertTrue(source.contains("0x20123456L"))
        assertTrue(source.contains("ParameterValue.BooleanValue(true)"))
        assertTrue(source.contains("\\\$path"))
        assertTrue(source.contains("\\\\"))
        assertTrue(source.contains("\\\"\\\"\\\""))
        assertTrue(source.contains("parameter0.key"))
        assertTrue(source.contains("parameter1.key"))
        assertTrue(source.contains("parameter2.key"))
        assertTrue(source.contains("isAnimating = false"))
        assertFalse(source.contains("val when"))
        assertTrue(source.contains("null, ParameterMetadata"), "Unbounded float must stay unbounded")
    }

    @Test fun standardRegistryIsExplicitAndRestoresStrictCompleteValues() {
        val registry = PlaygroundRegistry.standard()
        val entry = registry.entry(CustomDefinitions.controls.id)
        assertSame(CustomDefinitions.controls.schema, entry.schema)
        val snapshot = entry.schema.defaults().withValue(CustomDefinitions.EDITOR_ENABLED.key, false).snapshot()
        val restored = entry.restore(snapshot)
        assertEquals(snapshot, entry.schema.valuesFrom(restored).snapshot())
        assertFailsWith<IllegalArgumentException> { entry.restore(emptyMap()) }
        assertFailsWith<IllegalArgumentException> { entry.restore(snapshot + ("when" to ParameterValue.FloatValue(Float.NaN))) }
        assertFailsWith<IllegalArgumentException> { PlaygroundRegistry(listOf(entry, entry)) }
        assertFailsWith<IllegalArgumentException> { registry.entry("missing") }
    }

    @Test fun allPortableStartersExportWithoutResolvingProviders() {
        val registry = PlaygroundRegistry.standard()
        for (entry in registry.entries.filter { it.category != "Animated" || !it.id.contains("audio") }) {
            if (entry.id.contains("audio")) continue
            assertTrue(KotlinExport.generate(entry.starter).contains("fun effect()"), entry.id)
        }
        assertTrue(KotlinExport.generate(WaveEffect()).contains("controlledShaderEffect"))
    }

    @Test fun liveBindingsExplainExplicitRebindingWithoutInvokingProvider() {
        val definition = ShaderDefinition("live", program = ShaderProgram(
            "uniform shader content; uniform float live; half4 main(float2 p) { return content.eval(p); }",
            listOf(ShaderBinding.frameFloat("live"))))
        val effect = definition.animatedEffect(frameProvider = { error("Provider must not run during export") })
        val source = KotlinExport.generate(effect)
        assertTrue(source.contains("fun effect(frameProvider: ShaderFrameProvider)"))
        assertTrue(source.contains("ShaderBinding.frameFloat(\"live\")"))
        assertTrue(source.contains("frameProvider = frameProvider"))
        assertTrue(source.contains("remember(frameProvider)"))
    }
}

/** Images are caller inputs, and providers must never be evaluated by the source generator. */
object RebindingExportFixture {
    fun staticImage(image: androidx.compose.ui.graphics.ImageBitmap): DefinedShaderEffect = ShaderDefinition(
        "image-fixture", program = ShaderProgram(
            "// BODY INPUT_ARGUMENTS INPUT_PARAMETERS dollar \$value\n" +
                "uniform shader content; uniform shader image_input; half4 main(float2 p) { return image_input.eval(p); }",
            listOf(ShaderBinding.image("image_input", image, ShaderTileMode.REPEAT, ShaderTileMode.MIRROR)),
        ),
    ).effect()

    fun liveImage(image: androidx.compose.ui.graphics.ImageBitmap): DefinedAnimatedShaderEffect = ShaderDefinition(
        "live-image-fixture", program = ShaderProgram(
            "uniform shader content; uniform shader image_input; uniform shader live_image; uniform float live_amount; " +
                "half4 main(float2 p) { return mix(image_input.eval(p), live_image.eval(p), live_amount); }",
            listOf(
                ShaderBinding.uniform(TextureUniform("image_input", image, ShaderTileMode.MIRROR, ShaderTileMode.REPEAT)),
                ShaderBinding.frameImage("live_image"), ShaderBinding.frameFloat("live_amount"),
            ),
        ),
    ).animatedEffect(isAnimating = false, frameProvider = { error("Export must never invoke borrowed providers") })
}
