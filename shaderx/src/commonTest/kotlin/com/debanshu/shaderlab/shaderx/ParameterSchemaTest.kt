package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.parameter.*
import com.debanshu.shaderlab.shaderx.uniform.ColorUniform
import kotlin.test.*

class ParameterSchemaTest {
    private val amount = FloatParameterDefinition("test", "amount", .5f, 0f..1f, step = .1f)
    private val color = ColorParameterDefinition("test", "color", 0x80402010L)
    private val enabled = BooleanParameterDefinition("test", "enabled", true)
    private val schema = ParameterSchema("test", listOf(amount, color, enabled))

    @Test
    fun defaultsUpdatesResetAndPrecisionAreIndependent() {
        val original = schema.defaults()
        val updated = original.withValue(amount.key, .12345679f).withValue(color.key, 0x12456789L)
            .withValue(enabled.key, false)
        assertEquals(.5f, original[amount.key])
        assertEquals(.12345679f, updated[amount.key], "Step is a display hint, not quantization")
        assertEquals(0x12456789L, updated[color.key])
        assertFalse(updated[enabled.key])
        assertEquals(.5f, updated.reset(amount.key)[amount.key])
        assertEquals(0x12456789L, updated.reset(amount.key)[color.key])
        assertEquals(original, updated.reset())
        assertEquals(original.hashCode(), updated.reset().hashCode())
        assertNotEquals(updated, original)
    }

    @Test
    fun finiteRangesRejectUnlessExplicitlyClamped() {
        val values = schema.defaults()
        assertFailsWith<IllegalArgumentException> { values.withValue(amount.key, 2f) }
        assertEquals(1f, values.withValue(amount.key, 2f, ParameterRangePolicy.CLAMP)[amount.key])
        assertEquals(0f, values.withValue("amount", ParameterValue.FloatValue(-1f), ParameterRangePolicy.CLAMP)[amount.key])
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for (policy in ParameterRangePolicy.entries) {
                val error = assertFailsWith<IllegalArgumentException> { values.withValue(amount.key, invalid, policy) }
                assertTrue(error.message.orEmpty().contains("test.amount"))
                assertTrue(error.message.orEmpty().contains("finite"))
            }
        }
        assertEquals(.5f, values[amount.key])
    }

    @Test
    fun dynamicAndErasedTypesAreStrictWithoutCoercion() {
        val values = schema.defaults()
        for ((id, input) in listOf(
            "amount" to ParameterValue.BooleanValue(true),
            "color" to ParameterValue.FloatValue(1f),
            "enabled" to ParameterValue.FloatValue(1f),
        )) {
            val error = assertFailsWith<IllegalArgumentException> { values.withValue(id, input) }
            assertTrue(error.message.orEmpty().contains("test.$id"))
            assertTrue(error.message.orEmpty().contains("requires"))
        }
        @Suppress("UNCHECKED_CAST")
        val erased = amount.key as ParameterKey<Any>
        for (wrong in listOf(true, 1L, "unsupported", doubleArrayOf(.1))) {
            assertFailsWith<IllegalArgumentException> { values.withValue(erased, wrong) }
        }
        assertEquals(.5f, values[amount.key])
    }

    @Test
    fun keysUseDeclarationIdentityAndUnknownIdsFail() {
        val values = schema.defaults()
        val duplicate = FloatParameterDefinition("test", "amount", .5f, 0f..1f)
        val foreign = FloatParameterDefinition("other", "amount", .5f, 0f..1f)
        for (key in listOf(duplicate.key, foreign.key)) {
            assertFailsWith<IllegalArgumentException> { values[key] }
            assertFailsWith<IllegalArgumentException> { values.withValue(key, .75f) }
            assertFailsWith<IllegalArgumentException> { values.reset(key) }
        }
        val error = assertFailsWith<IllegalArgumentException> { values.withValue("missing", ParameterValue.FloatValue(0f)) }
        assertEquals("Schema 'test' has no parameter 'missing'", error.message)
        assertFailsWith<IllegalArgumentException> { schema.definition("missing") }
        assertFailsWith<IllegalArgumentException> { values.value("missing") }
        assertSame(amount, schema.definition("amount"))
    }

    @Test
    fun invalidDefinitionsFailBeforeAnyValueSetExists() {
        assertFailsWith<IllegalArgumentException> { ParameterSchema("", emptyList()) }
        assertFailsWith<IllegalArgumentException> { ParameterSchema("test", listOf(amount, amount)) }
        assertFailsWith<IllegalArgumentException> { ParameterSchema("other", listOf(amount)) }
        assertFailsWith<IllegalArgumentException> { FloatParameterDefinition("", "x", 0f) }
        assertFailsWith<IllegalArgumentException> { FloatParameterDefinition("test", " ", 0f) }
        assertFailsWith<IllegalArgumentException> { ParameterMetadata(" ") }
        for (range in listOf(2f..1f, Float.NaN..1f, 0f..Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY..1f)) {
            assertFailsWith<IllegalArgumentException> { FloatParameterDefinition("test", "bad", 0f, range) }
        }
        for (default in listOf(-1f, 2f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { FloatParameterDefinition("test", "bad", default, 0f..1f) }
        }
        for (step in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { FloatParameterDefinition("test", "bad", 0f, step = step) }
        }
        for (decimals in listOf(-1, 10)) {
            assertFailsWith<IllegalArgumentException> { FloatParameterDefinition("test", "bad", 0f, decimalPlaces = decimals) }
        }
        for (argb in listOf(-1L, 0x100000000L)) {
            assertFailsWith<IllegalArgumentException> { ColorParameterDefinition("test", "bad", argb) }
            assertFailsWith<IllegalArgumentException> { schema.defaults().withValue(color.key, argb) }
        }
    }

    @Test
    fun unboundedFloatsRemainFiniteAndAllowExactBoundaryValues() {
        val unbounded = FloatParameterDefinition("test", "free", 0f)
        val values = ParameterSchema("test", listOf(unbounded)).defaults()
        assertNull(unbounded.range)
        assertEquals(Float.MAX_VALUE, values.withValue(unbounded.key, Float.MAX_VALUE)[unbounded.key])
        assertFailsWith<IllegalArgumentException> { values.withValue(unbounded.key, Float.NaN) }
        assertEquals(0f, schema.defaults().withValue(amount.key, 0f)[amount.key])
        assertEquals(1f, schema.defaults().withValue(amount.key, 1f)[amount.key])
    }

    @Test
    fun mutableInputAndReturnedCollectionsCannotChangeSnapshots() {
        val input = mutableListOf<ParameterDefinition<*>>(amount, color, enabled)
        val copied = ParameterSchema("test", input)
        input.clear()
        val definitions = copied.definitions
        (definitions as MutableList<*>).clear()
        assertEquals(3, copied.definitions.size)
        val values = copied.defaults()
        val output = values.snapshot()
        (output as MutableMap)["amount"] = ParameterValue.FloatValue(.9f)
        assertEquals(.5f, values[amount.key])
        assertEquals(ParameterValue.FloatValue(.5f), values.snapshot()["amount"])
    }

    @Test
    fun customMutableRangesAreSnapshotted() {
        val range = object : ClosedFloatingPointRange<Float> {
            var minimum = 0f
            var maximum = 1f
            override val start get() = minimum
            override val endInclusive get() = maximum
            override fun lessThanOrEquals(a: Float, b: Float) = a <= b
        }
        val definition = FloatParameterDefinition("test", "range", .5f, range)
        range.minimum = .9f
        range.maximum = 10f
        assertEquals(0f..1f, definition.range)
        val values = ParameterSchema("test", listOf(definition)).defaults()
        assertEquals(.25f, values.withValue(definition.key, .25f)[definition.key])
        assertFailsWith<IllegalArgumentException> { values.withValue(definition.key, 2f) }
    }

    @Test
    fun legacyAdapterPreservesRealTypesAndPresentationHints() {
        val legacy = mutableListOf<ParameterSpec>(
            PixelParameter("px", "Pixels", 0f..50f, 10f),
            FloatParameter("float", "Float", 0f..10f, 5f, 4),
            PercentageParameter("percent", "Percent", .5f),
            ColorParameter("color", "Color", 0x12456789L),
            ToggleParameter("toggle", "Toggle", true),
        )
        val converted = ParameterSchema.fromLegacy("test", legacy)
        legacy.clear()
        assertEquals(5, converted.definitions.size)
        assertEquals("px", (converted.definition("px") as FloatParameterDefinition).unit)
        assertEquals("%", (converted.definition("percent") as FloatParameterDefinition).unit)
        assertEquals(4, (converted.definition("float") as FloatParameterDefinition).decimalPlaces)
        assertEquals(ParameterValue.ColorValue(0x12456789L), converted.defaults().value("color"))
        assertEquals(ParameterValue.BooleanValue(true), converted.defaults().value("toggle"))
        assertFailsWith<IllegalArgumentException> {
            ParameterSchema.fromLegacy("test", listOf(FloatParameter("bad", "Bad", 0f..1f, 2f)))
        }
    }

    @Test
    fun pilotValuesRoundTripWithAlphaTimeAndPlaybackIntact() {
        val gradient = GradientEffect()
        val values = GradientEffect.SCHEMA.valuesFrom(gradient).withValue(GradientEffect.COLOR_1.key, 0x12456789L)
        val updated = values.applyTo(gradient) as GradientEffect
        assertEquals(0x12456789L, updated.color1)
        assertEquals(GradientEffect.DEFAULT_COLOR_1, gradient.color1)
        assertEquals(0x12 / 255f, (updated.buildUniforms(32f, 32f)[1] as ColorUniform).alpha)
        assertEquals(gradient, values.reset().applyTo(updated))
        val wave = WaveEffect(amplitude = 12f, frequency = 8f, animate = false, time = 42f)
        val edited = WaveEffect.SCHEMA.valuesFrom(wave).withValue(WaveEffect.AMPLITUDE.key, .12345679f)
            .applyTo(wave) as WaveEffect
        assertEquals(.12345679f, edited.amplitude)
        assertEquals(42f, edited.time)
        assertFalse(edited.animate)
        assertEquals(edited, edited.withTime(99f))
        assertFailsWith<IllegalArgumentException> { GradientEffect.SCHEMA.valuesFrom(wave) }
        assertFailsWith<IllegalArgumentException> { values.applyTo(wave) }
        assertFailsWith<IllegalArgumentException> { WaveEffect.SCHEMA.valuesFrom(WaveEffect(amplitude = Float.NaN)) }
        assertFailsWith<IllegalArgumentException> { WaveEffect.SCHEMA.valuesFrom(WaveEffect(amplitude = 100f)) }
    }

    @Test
    fun legacyPilotsRetainPermissiveConstructorsCopyAndUpdates() {
        val gradient = GradientEffect(intensity = 2f)
        assertEquals(3f, gradient.copy(intensity = 3f).intensity)
        assertEquals(1f, (gradient.withParameter("intensity", 4f) as GradientEffect).intensity)
        assertSame(gradient, gradient.withParameter("missing", 1f))
        assertTrue((gradient.withParameter("intensity", Float.NaN) as GradientEffect).intensity.isNaN())
        val wave = WaveEffect(amplitude = 100f, time = 42f)
        assertEquals(100f, wave.amplitude)
        assertEquals(50f, (wave.withParameter("amplitude", Float.POSITIVE_INFINITY) as WaveEffect).amplitude)
        assertFalse((wave.withParameter("animate", .5f) as WaveEffect).animate)
        assertSame(wave, wave.withParameter("missing", 1f))
    }

    @Test
    fun applyingSameOwnerSnapshotStillValidatesTargetTypeRangeAndIds() {
        val target = GradientEffect(intensity = .25f)
        val wide = FloatParameterDefinition(GradientEffect.ID, GradientEffect.PARAM_INTENSITY, 2f, 0f..100f)
        val wrongType = ColorParameterDefinition(GradientEffect.ID, GradientEffect.PARAM_INTENSITY, 0xFF112233L)
        val unknown = FloatParameterDefinition(GradientEffect.ID, "missing", 0f)
        for (definition in listOf(wide, wrongType, unknown)) {
            val values = ParameterSchema(GradientEffect.ID, listOf(definition)).defaults()
            val error = assertFailsWith<IllegalArgumentException> { values.applyTo(target) }
            assertTrue(error.message.orEmpty().contains(GradientEffect.ID))
            assertTrue(error.message.orEmpty().contains(definition.key.id))
        }
        assertEquals(.25f, target.intensity)
    }

    @Test
    fun completeBuiltInCatalogAdaptsWithoutInventingNonNumericRanges() {
        for (effect in ShaderX.builtInEffects()) {
            val converted = ParameterSchema.fromLegacy(effect.id, effect.parameters)
            assertEquals(effect.parameters.size, converted.definitions.size, effect.id)
            for (spec in effect.parameters) {
                val definition = converted.definition(spec.id)
                assertEquals(spec.getTypedDefaultValue(), converted.defaults().value(spec.id), "${effect.id}.${spec.id}")
                when (spec) {
                    is ColorParameter -> assertTrue(definition is ColorParameterDefinition)
                    is ToggleParameter -> assertTrue(definition is BooleanParameterDefinition)
                    else -> {
                        assertTrue(definition is FloatParameterDefinition)
                        assertEquals(spec.range, definition.range)
                    }
                }
            }
            assertEquals(converted.defaults(), converted.valuesFrom(effect), effect.id)
        }
    }
}
