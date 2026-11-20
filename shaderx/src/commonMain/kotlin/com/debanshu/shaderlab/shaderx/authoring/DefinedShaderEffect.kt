package com.debanshu.shaderlab.shaderx.authoring

import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.*
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/** Immutable generated static adapter; getters, dispatch and equality need no consumer code. */
public class DefinedShaderEffect internal constructor(
    public val definition: ShaderDefinition,
    public val values: ParameterValues,
) : RuntimeShaderEffect {
    init { require(values.schema === definition.schema) { "Definition '${definition.id}' requires its own schema values" } }
    override val id: String get() = definition.id
    override val displayName: String get() = definition.displayName
    override val shaderSource: String get() = definition.program.source
    /** Legacy descriptor bridge. Editors should read [definition].schema for nullable bounds/units/roles. */
    override val parameters: List<ParameterSpec> get() = definition.schema.legacyDescriptors()
    public operator fun <T : Any> get(key: ParameterKey<T>): T = values[key]
    public fun <T : Any> withValue(key: ParameterKey<T>, value: T, policy: ParameterRangePolicy = ParameterRangePolicy.REJECT): DefinedShaderEffect =
        DefinedShaderEffect(definition, values.withValue(key, value, policy))
    override fun withTypedParameter(parameterId: String, value: ParameterValue): DefinedShaderEffect =
        DefinedShaderEffect(definition, values.withValue(parameterId, value))
    override fun getTypedParameterValue(parameterId: String): ParameterValue = values.value(parameterId)
    override fun getParameterValue(parameterId: String): Float = values.value(parameterId).toFloat()
    override fun buildUniforms(width: Float, height: Float): List<Uniform> = definition.uniforms(values, width, height, 0f)
    override fun equals(other: Any?): Boolean = other is DefinedShaderEffect && definition === other.definition && values == other.values
    override fun hashCode(): Int = 31 * definition.hashCode() + values.hashCode()
}

/**
 * Immutable generated animated adapter. Time is excluded from equality; values, definition,
 * playback flag and provider identity are included. Configuration changes replace native state.
 * Retain providers across frames. Use controlledShaderEffect with an externally owned ShaderPlayback
 * to preserve phase across configuration changes and refresh live data by explicit revision.
 */
public class DefinedAnimatedShaderEffect internal constructor(
    public val definition: ShaderDefinition,
    public val values: ParameterValues,
    override val isAnimating: Boolean,
    override val time: Float,
    public val frameProvider: ShaderFrameProvider?,
) : AnimatedShaderEffect {
    init {
        require(values.schema === definition.schema) { "Definition '${definition.id}' requires its own schema values" }
        require(time.isFinite()) { "Definition '${definition.id}' requires finite seconds" }
    }
    override val id: String get() = definition.id
    override val displayName: String get() = definition.displayName
    override val shaderSource: String get() = definition.program.source
    /** Legacy descriptor bridge. Unbounded floats use the full finite range; prefer [definition].schema. */
    override val parameters: List<ParameterSpec> get() = definition.schema.legacyDescriptors()
    public operator fun <T : Any> get(key: ParameterKey<T>): T = values[key]
    public fun <T : Any> withValue(key: ParameterKey<T>, value: T, policy: ParameterRangePolicy = ParameterRangePolicy.REJECT): DefinedAnimatedShaderEffect =
        DefinedAnimatedShaderEffect(definition, values.withValue(key, value, policy), isAnimating, time, frameProvider)
    override fun withTypedParameter(parameterId: String, value: ParameterValue): DefinedAnimatedShaderEffect =
        DefinedAnimatedShaderEffect(definition, values.withValue(parameterId, value), isAnimating, time, frameProvider)
    /** Explicit playback flag separate from schema values. Paused instances still refresh live data. */
    public fun withPlayback(isAnimating: Boolean): DefinedAnimatedShaderEffect =
        DefinedAnimatedShaderEffect(definition, values, isAnimating, time, frameProvider)
    override fun withTime(newTime: Float): DefinedAnimatedShaderEffect =
        DefinedAnimatedShaderEffect(definition, values, isAnimating, newTime, frameProvider)
    override fun getTypedParameterValue(parameterId: String): ParameterValue = values.value(parameterId)
    override fun getParameterValue(parameterId: String): Float = values.value(parameterId).toFloat()
    override fun buildUniforms(width: Float, height: Float): List<Uniform> =
        definition.uniforms(values, width, height, time) + definition.frameUniforms(time, frameProvider).filter { uniform ->
            definition.program.bindings.none { it.clock && it.name == uniform.name }
        }
    override fun buildFrameUniforms(): List<Uniform> = definition.frameUniforms(time, frameProvider)
    override fun equals(other: Any?): Boolean = other is DefinedAnimatedShaderEffect && definition === other.definition &&
        values == other.values && isAnimating == other.isAnimating && frameProvider === other.frameProvider
    // Provider equality is referential. A user provider may implement mutable hashCode; omitting
    // it keeps this immutable key stable (unequal providers are allowed to collide).
    override fun hashCode(): Int = 31 * (31 * definition.hashCode() + values.hashCode()) + isAnimating.hashCode()
}

private fun ParameterSchema.legacyDescriptors(): List<ParameterSpec> = definitions.map { definition ->
    when (definition) {
        is FloatParameterDefinition -> FloatParameter(definition.key.id, definition.metadata.label,
            definition.range ?: -Float.MAX_VALUE..Float.MAX_VALUE, definition.defaultValue, definition.decimalPlaces)
        is ColorParameterDefinition -> definition.legacyColor()
        is BooleanParameterDefinition -> definition.legacyToggle()
    }
}
