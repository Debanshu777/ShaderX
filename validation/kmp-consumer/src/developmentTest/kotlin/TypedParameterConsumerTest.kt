package shaderx.consumer

import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.applyTo
import com.debanshu.shaderlab.shaderx.parameter.valuesFrom
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class TypedParameterConsumerTest {
    @Test
    fun publicPilotExamplesPreservePrecisionAlphaAndTime() {
        val gradient = configuredGradient()
        assertEquals(0x80402010L, gradient.color1)
        assertEquals(.12345679f, gradient.intensity)
        val wave = configuredWave(WaveEffect(time = 42f))
        assertEquals(50f, wave.amplitude)
        assertEquals(42f, wave.time)
        assertFalse(wave.animate)
        assertEquals(GradientEffect(), GradientEffect.SCHEMA.valuesFrom(gradient).reset().applyTo(gradient))
    }

    @Test
    fun independentEffectUsesPublicSchemaForValuesAndUniforms() {
        val original = TypedEditableEffect()
        val values = TypedEditableEffect.SCHEMA.valuesFrom(original)
            .withValue(TypedEditableEffect.AMOUNT.key, .12345679f)
        val updated = values.applyTo(original) as TypedEditableEffect
        assertEquals(ParameterValue.FloatValue(.12345679f), updated.getTypedParameterValue("amount"))
        assertEquals(listOf(FloatUniform("amount", .12345679f)), updated.buildUniforms(16f, 16f))
        assertEquals(.5f, original.values[TypedEditableEffect.AMOUNT.key])
        assertFailsWith<IllegalArgumentException> { values.withValue("amount", ParameterValue.BooleanValue(true)) }
        val factory = ConsumerFactory(ShaderFactory.create())
        try {
            assertNotNull(factory.createRenderEffect(updated, 16f, 16f).getOrNull())
        } finally {
            factory.close()
        }
    }
}
