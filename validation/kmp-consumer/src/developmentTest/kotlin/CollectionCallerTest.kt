package shaderx.consumer

import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.impl.ColorMatrix
import com.debanshu.shaderlab.shaderx.effect.impl.EffectCollection
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.factory.createPipelineSession
import com.debanshu.shaderlab.shaderx.parameter.FloatParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/** Ordinary separately compiled callers: public declaration keys, no sample or internal dispatch. */
class CollectionCallerTest {
    @Test
    fun namedArgumentsUseStrictSharedDefinitionsWithoutCasts() {
        val effect = EffectCollection.adjustColor(brightness = .125f, contrast = 1.2f, saturation = .75f)
        assertSame(EffectCollection.colorAdjustment, effect.definition)
        assertEquals(.125f, effect.getParameterValue("brightness"))
        assertEquals(2.5f, EffectCollection.dither(amount = 1f, levels = 2.5f).getParameterValue("levels"))
        assertEquals(2.5f, EffectCollection.dots(amount = 1f, cellSize = 2.5f).getParameterValue("cellSize"))
        assertFailsWith<IllegalArgumentException> { EffectCollection.adjustColor(brightness = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { EffectCollection.dither(levels = 9f) }
        assertFailsWith<IllegalArgumentException> { EffectCollection.dots(cellSize = 1f) }
    }

    @Test
    fun typedDeclarationKeysKeepValuesAndMetadataIndependentOfNativeState() {
        val definition = EffectCollection.colorAdjustment
        val brightness = definition.schema.definition("brightness") as FloatParameterDefinition
        val defaults = definition.schema.defaults()
        val edited = defaults.withValue(brightness.key, .125f)
        val first = definition.effect(edited)
        val second = first.withValue(brightness.key, -.25f)
        assertSame(definition, first.definition)
        assertEquals(.125f, first[brightness.key])
        assertEquals(-.25f, second[brightness.key])
        assertEquals(0f, defaults[brightness.key])
        assertEquals("color-adjustment", brightness.metadata.semanticRole)
        assertEquals(-1f..1f, brightness.range)
        assertNotEquals(first, second)
        assertFailsWith<IllegalArgumentException> { first.withValue(brightness.key, Float.NaN) }
        assertFailsWith<IllegalArgumentException> { first.withValue(brightness.key, 1.01f) }
        assertEquals(first, definition.effect(edited))
    }

    @Test
    fun matrixAndArtCompileThroughTheSamePublicFactoryAndOrderedPipeline() {
        val coefficients = ColorMatrix.identity().coefficients.apply {
            this[0] = 0f; this[2] = 1f
            this[10] = 1f; this[12] = 0f
        }
        val matrix = ColorMatrix(coefficients)
        coefficients.fill(0f)
        val swap = matrix.effect()
        assertEquals(1f, swap.getParameterValue("m02"))
        assertEquals(1f, swap.getParameterValue("m20"))
        val dither = EffectCollection.orderedDither.effect()
            .withTypedParameter("amount", ParameterValue.FloatValue(1f))
            .withTypedParameter("levels", ParameterValue.FloatValue(2.5f))
        val halftone = EffectCollection.halftone.effect()
            .withTypedParameter("amount", ParameterValue.FloatValue(1f))
        assertEquals(2.5f, dither.getParameterValue("levels"), "Rendering rounds without changing stored settings")
        val factory = ShaderFactory.create()
        try {
            for (effect in listOf(EffectCollection.colorAdjustment.effect(), swap, dither, halftone)) {
                factory.createRenderEffect(effect, 32f, 24f).getOrThrow()
            }
            val pipeline = factory.createPipelineSession(CompositeEffect.of(swap, dither), 32f, 24f).getOrThrow()
            try {
                pipeline.currentRenderEffect
            } finally {
                pipeline.close()
            }
        } finally {
            factory.close()
        }
    }
}
