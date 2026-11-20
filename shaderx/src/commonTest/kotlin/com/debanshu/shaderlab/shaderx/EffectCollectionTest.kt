package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.impl.ColorMatrix
import com.debanshu.shaderlab.shaderx.effect.impl.EffectCollection
import com.debanshu.shaderlab.shaderx.parameter.*
import kotlin.test.*

/** Contracts shared by every backend; these assertions do not claim native compilation. */
class EffectCollectionTest {
    @Test
    fun matrixValidatesDefensivelyCopiesAndComparesByValue() {
        val input = ColorMatrix.identity().coefficients
        val first = ColorMatrix(input)
        val identical = ColorMatrix(input.copyOf())
        val hash = first.hashCode()
        input[0] = 7f
        first.coefficients.fill(-8f)
        assertEquals(1f, first.coefficients[0])
        assertEquals(identical, first)
        assertEquals(hash, first.hashCode())
        assertEquals(identical.hashCode(), hash)
        assertEquals(first.effect(), identical.effect())
        for(size in listOf(0, 19, 21)) assertFailsWith<IllegalArgumentException> { ColorMatrix(FloatArray(size)) }
        for(invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -8.01f, 8.01f)) {
            val array = first.coefficients.apply { this[7] = invalid }
            assertFailsWith<IllegalArgumentException> { ColorMatrix(array) }
        }
        val minimum = ColorMatrix(FloatArray(20) { -8f })
        val maximum = ColorMatrix(FloatArray(20) { 8f })
        assertEquals(-8f, minimum.effect().getParameterValue("m34"))
        assertEquals(8f, maximum.effect().getParameterValue("m34"))
        val positiveZero = ColorMatrix(FloatArray(20))
        val negativeZero = ColorMatrix(FloatArray(20).apply { this[0] = -0f })
        assertNotEquals(positiveZero, negativeZero)
        assertEquals(negativeZero.hashCode(), ColorMatrix(negativeZero.coefficients).hashCode())
    }

    @Test
    fun richDeclarationsOwnEveryEditableBindingAndRetainPresentationMetadata() {
        val definitions = listOf(EffectCollection.colorAdjustment, EffectCollection.colorMatrix,
            EffectCollection.orderedDither, EffectCollection.halftone)
        assertEquals(4, definitions.map { it.id }.distinct().size)
        for (definition in definitions) {
            val schema = definition.schema
            assertEquals(definition.id, schema.ownerId)
            assertTrue(schema.definitions.isNotEmpty())
            for (parameter in schema.definitions) {
                assertTrue(parameter.metadata.label.isNotBlank())
                assertTrue(parameter.metadata.description.orEmpty().isNotBlank(), parameter.key.id)
                assertTrue(parameter.metadata.semanticRole.orEmpty().isNotBlank(), parameter.key.id)
                assertTrue(definition.program.bindings.any { it.descriptor.parameterId == parameter.key.id })
                assertEquals(parameter.defaultValue, (schema.defaults().value(parameter.key.id) as ParameterValue.FloatValue).value)
            }
        }
    }

    @Test
    fun everyNumericEndpointIsAcceptedAndInvalidInputFailsWithoutMutatingValues() {
        for (definition in listOf(EffectCollection.colorAdjustment, EffectCollection.colorMatrix,
            EffectCollection.orderedDither, EffectCollection.halftone)) {
            for (parameter in definition.schema.definitions.filterIsInstance<FloatParameterDefinition>()) {
                val original = definition.schema.defaults()
                val range = assertNotNull(parameter.range)
                for (endpoint in listOf(range.start, range.endInclusive)) {
                    assertEquals(endpoint, original.withValue(parameter.key, endpoint)[parameter.key])
                }
                for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                    range.start - 1f, range.endInclusive + 1f)) {
                    assertFailsWith<IllegalArgumentException>("${definition.id}.${parameter.key.id}: $invalid") {
                        original.withValue(parameter.key, invalid)
                    }
                }
                assertEquals(parameter.defaultValue, original[parameter.key])
                assertFailsWith<IllegalArgumentException> {
                    original.withValue(parameter.key.id, ParameterValue.BooleanValue(true))
                }
            }
        }
    }

    @Test
    fun newStrictEffectsDoNotCoerceUnknownOrNonFiniteUpdates() {
        for (definition in listOf(EffectCollection.colorAdjustment, EffectCollection.colorMatrix,
            EffectCollection.orderedDither, EffectCollection.halftone)) {
            val effect = definition.effect()
            assertFailsWith<IllegalArgumentException> { effect.withParameter("missing", .5f) }
            val first = definition.schema.definitions.first().key.id
            assertFailsWith<IllegalArgumentException> { effect.withParameter(first, Float.NaN) }
            assertEquals(effect, definition.effect())
            assertEquals(effect.hashCode(), definition.effect().hashCode())
        }
    }
}
