package shaderx.consumer

import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.parameter.FloatParameter
import com.debanshu.shaderlab.shaderx.parameter.FloatParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterMetadata
import com.debanshu.shaderlab.shaderx.parameter.ParameterRangePolicy
import com.debanshu.shaderlab.shaderx.parameter.ParameterSchema
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.ParameterValues
import com.debanshu.shaderlab.shaderx.parameter.applyTo
import com.debanshu.shaderlab.shaderx.parameter.valuesFrom
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

// Development API fixture: excluded from explicitly selected published 0.1.3 consumers.
fun configuredGradient(): GradientEffect = GradientEffect.SCHEMA.defaults()
    .withValue(GradientEffect.COLOR_1.key, 0x80402010L)
    .withValue(GradientEffect.INTENSITY.key, .12345679f)
    .applyTo(GradientEffect()) as GradientEffect

fun configuredWave(current: WaveEffect): WaveEffect = WaveEffect.SCHEMA.valuesFrom(current)
    .withValue(WaveEffect.AMPLITUDE.key, 100f, ParameterRangePolicy.CLAMP)
    .withValue(WaveEffect.ANIMATE.key, false)
    .applyTo(current) as WaveEffect

// A consumer can use metadata/values for its own effect and uniform input without internal bases,
// handler casts, reflection, serialization dependencies, or any new renderer interface requirement.
class TypedEditableEffect(val values: ParameterValues = SCHEMA.defaults()) : RuntimeShaderEffect {
    init { require(values.schema === SCHEMA) }
    override val id = "external_typed"
    override val displayName = "External typed"
    override val parameters: List<ParameterSpec> = listOf(
        FloatParameter(AMOUNT.key.id, AMOUNT.metadata.label, requireNotNull(AMOUNT.range), AMOUNT.defaultValue, AMOUNT.decimalPlaces),
    )
    override val shaderSource = """
        uniform shader content;
        uniform float amount;
        half4 main(float2 p) { return content.eval(p) * amount; }
    """.trimIndent()
    override fun buildUniforms(width: Float, height: Float): List<Uniform> =
        listOf(FloatUniform(AMOUNT.key.id, values[AMOUNT.key]))
    override fun withTypedParameter(parameterId: String, value: ParameterValue): TypedEditableEffect =
        TypedEditableEffect(values.withValue(parameterId, value))
    override fun getTypedParameterValue(parameterId: String): ParameterValue = values.value(parameterId)
    override fun getParameterValue(parameterId: String): Float = values.value(parameterId).toFloat()

    companion object {
        val AMOUNT = FloatParameterDefinition("external_typed", "amount", .5f, 0f..1f, ParameterMetadata("Amount", "Multiplier"), step = .1f)
        val SCHEMA = ParameterSchema("external_typed", listOf(AMOUNT))
    }
}
