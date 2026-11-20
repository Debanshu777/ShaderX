package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.parameter.*
import com.debanshu.shaderlab.shaderx.uniform.*
import kotlin.test.*

class ShaderDefinitionTest {
    private val source = "uniform shader content; half4 main(float2 p) { return content.eval(p); }"

    @Test fun declarationsRejectDuplicateReservedAndForeignOrMissingKeys() {
        assertFailsWith<IllegalArgumentException> { ShaderProgram(source, listOf(ShaderBinding.float("content", 1f))) }
        assertFailsWith<IllegalArgumentException> { ShaderProgram(source, listOf(ShaderBinding.float("x", 1f), ShaderBinding.time("x"))) }
        val amount = FloatParameterDefinition("custom", "amount", .5f, 0f..1f)
        val foreign = FloatParameterDefinition("custom", "amount", .5f, 0f..1f)
        val schema = ParameterSchema("custom", listOf(amount))
        assertFailsWith<IllegalArgumentException> { ShaderDefinition("custom", "Custom", ShaderProgram(source), schema) }
        assertFailsWith<IllegalArgumentException> {
            ShaderDefinition("custom", "Custom", ShaderProgram(source, listOf(ShaderBinding.float("x", foreign.key))), schema)
        }
        val definition = ShaderDefinition("custom", "Custom", ShaderProgram(source, listOf(ShaderBinding.float("x", amount.key))), schema)
        assertFailsWith<IllegalArgumentException> { definition.effect(ParameterSchema("custom", listOf(foreign)).defaults()) }
        assertFailsWith<IllegalArgumentException> { definition.effect().withTypedParameter("amount", ParameterValue.BooleanValue(true)) }
        assertFailsWith<IllegalArgumentException> { definition.effect().getTypedParameterValue("absent") }
    }

    @Test fun minimalPathSnapshotsBindingsAndUsesExplicitPixelDimensions() {
        val bindings = mutableListOf(ShaderBinding.resolution("viewport"), ShaderBinding.float("strength", .5f))
        val program = ShaderProgram(source, bindings)
        bindings.clear()
        val effect = ShaderDefinition("minimal", "Minimal", program).effect()
        assertTrue(effect.parameters.isEmpty())
        assertEquals(listOf(FloatUniform("viewport", 200f, 100f), FloatUniform("strength", .5f)), effect.buildUniforms(200f, 100f))
        assertFailsWith<IllegalArgumentException> { effect.buildUniforms(Float.NaN, 1f) }
        assertFailsWith<IllegalArgumentException> { effect.buildUniforms(0f, 1f) }
        assertFailsWith<IllegalArgumentException> { ShaderBinding.float("bad", Float.POSITIVE_INFINITY) }
    }

    @Test fun structuralEscapeBindingsSnapshotVectorsIntegersAndMatricesWithoutEditorMetadata() {
        val vector = floatArrayOf(.25f, .75f)
        val integers = intArrayOf(1, 2)
        val matrix = floatArrayOf(1f, 0f, 0f, 1f)
        val effect = ShaderDefinition("minimal", program = ShaderProgram(source, listOf(
            ShaderBinding.uniform(FloatUniform("vector", vector)),
            ShaderBinding.uniform(IntUniform("indices", integers)),
            ShaderBinding.uniform(MatrixUniform("transform", matrix)),
        ))).effect()
        vector[0] = 99f; integers[0] = 99; matrix[0] = 99f
        assertEquals("minimal", effect.displayName)
        val first = effect.buildUniforms(8f, 8f)
        assertEquals(FloatUniform("vector", .25f, .75f), first[0])
        assertEquals(IntUniform("indices", 1, 2), first[1])
        assertEquals(MatrixUniform.identity2x2("transform"), first[2])
        (first[0] as FloatUniform).values[0] = 99f
        assertEquals(FloatUniform("vector", .25f, .75f), effect.buildUniforms(8f, 8f)[0])
        assertFailsWith<IllegalArgumentException> { ShaderBinding.uniform(IntUniform("unsupported", IntArray(5))) }
    }

    @Test fun liveProvidersMustMatchExactSafeDeclarationsBeforeAnyAssignment() {
        val definition = ShaderDefinition("live", "Live", ShaderProgram(source,
            listOf(ShaderBinding.time("elapsed"), ShaderBinding.frameFloat("level"))))
        assertFailsWith<IllegalArgumentException> { definition.effect() }
        assertFailsWith<IllegalArgumentException> { definition.animatedEffect() }
        var supplied: List<Uniform> = listOf(FloatUniform("level", .5f))
        val effect = definition.animatedEffect(frameProvider = ShaderFrameProvider { supplied })
        assertEquals(listOf(FloatUniform("elapsed", 2f), FloatUniform("level", .5f)), effect.withTime(2f).buildFrameUniforms())
        val payload = floatArrayOf(.25f)
        supplied = listOf(FloatUniform("level", payload))
        val validated = effect.buildFrameUniforms()
        payload[0] = Float.NaN
        assertEquals(FloatUniform("level", .25f), validated.last(), "Validated payloads must not alias caller-owned arrays")
        val invalid = listOf(
            emptyList(), listOf(ColorUniform("level", 0xFFFFFFFFL)),
            listOf(FloatUniform("level", Float.NaN)), listOf(FloatUniform("level", 1f, 2f)),
            listOf(FloatUniform("level", 1f), FloatUniform("level", 2f)),
            listOf(FloatUniform("level", 1f), FloatUniform("elapsed", 4f)),
        )
        for (candidate in invalid) {
            supplied = candidate
            val failure = assertFailsWith<IllegalArgumentException> { effect.buildFrameUniforms() }
            assertTrue(failure.message.orEmpty().contains("live"))
            assertTrue(failure.message.orEmpty().contains("binding"))
        }
        val throwing = definition.animatedEffect(frameProvider = ShaderFrameProvider { error("sensor unavailable") })
        assertTrue(assertFailsWith<IllegalArgumentException> { throwing.buildFrameUniforms() }.message.orEmpty().contains("sensor unavailable"))
    }

    @Test fun generatedEqualitySeparatesConfigurationAndTimeWithoutNativeState() {
        val color = ColorParameterDefinition("custom", "color", 0x804080C0L)
        val enabled = BooleanParameterDefinition("custom", "enabled", true)
        val definition = ShaderDefinition("custom", "Custom", ShaderProgram(source, listOf(
            ShaderBinding.color("tint", color.key), ShaderBinding.boolean("enabled", enabled.key), ShaderBinding.time(),
        )), ParameterSchema("custom", listOf(color, enabled)))
        val effect = definition.animatedEffect()
        assertEquals(effect, effect.withTime(20f))
        assertEquals(effect.hashCode(), effect.withTime(20f).hashCode())
        assertNotEquals(effect, effect.withPlayback(false))
        assertNotEquals(effect, effect.withValue(color.key, 0xFF000000L))
        assertEquals(true, effect[enabled.key])
        assertEquals(IntUniform("enabled", 0), effect.withValue(enabled.key, false).buildUniforms(8f, 8f).first { it.name == "enabled" })
        assertFailsWith<IllegalArgumentException> { effect.withTime(Float.NaN) }
        assertEquals(listOf(FloatUniform("time", 0f)), effect.buildFrameUniforms())
    }
}
