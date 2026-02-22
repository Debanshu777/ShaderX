package com.debanshu.shaderlab.shaderx.effect

import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue

/**
 * A composite effect that combines multiple shader effects in sequence.
 *
 * Each effect is applied in order, with the output of one becoming
 * the input of the next. Chaining is supported on Android (API 31+);
 * on other platforms only the last effect is applied.
 *
 * ## Usage
 * ```kotlin
 * val effect = GrayscaleEffect() + VignetteEffect()
 *
 * // Or using CompositeEffect directly
 * val effect = CompositeEffect.of(
 *     GrayscaleEffect(),
 *     VignetteEffect(),
 *     NativeBlurEffect(radius = 5f)
 * )
 *
 * Image(
 *     painter = painterResource("photo.png"),
 *     modifier = Modifier.shaderEffect(effect)
 * )
 * ```
 *
 * @property effects The list of effects to apply in order
 */
public data class CompositeEffect(
    public val effects: List<ShaderEffect>,
) : ShaderEffect {

    init {
        require(effects.isNotEmpty()) { "CompositeEffect requires at least one effect" }
    }

    override val id: String = "composite_${effects.joinToString("_") { it.id }}"

    override val displayName: String = effects.joinToString(" + ") { it.displayName }

    /**
     * Combined parameters from all contained effects.
     *
     * Parameter IDs are prefixed with the effect index to avoid collisions.
     * For example, if both effects have an "intensity" parameter, they become
     * "0_intensity" and "1_intensity".
     */
    override val parameters: List<ParameterSpec> =
        effects.flatMapIndexed { index, effect ->
            effect.parameters.map { param ->
                createPrefixedParameter(index, param)
            }
        }

    override fun withParameter(parameterId: String, value: Float): CompositeEffect {
        val (index, originalId) = parseParameterId(parameterId) ?: return this

        val updatedEffects = effects.toMutableList()
        if (index in effects.indices) {
            updatedEffects[index] = effects[index].withParameter(originalId, value)
        }

        return copy(effects = updatedEffects)
    }

    override fun withTypedParameter(parameterId: String, value: ParameterValue): CompositeEffect {
        val (index, originalId) = parseParameterId(parameterId) ?: return this

        val updatedEffects = effects.toMutableList()
        if (index in effects.indices) {
            updatedEffects[index] = effects[index].withTypedParameter(originalId, value)
        }

        return copy(effects = updatedEffects)
    }

    override fun getParameterValue(parameterId: String): Float {
        val (index, originalId) = parseParameterId(parameterId) ?: return 0f
        return effects.getOrNull(index)?.getParameterValue(originalId) ?: 0f
    }

    override fun getTypedParameterValue(parameterId: String): ParameterValue? {
        val (index, originalId) = parseParameterId(parameterId) ?: return null
        return effects.getOrNull(index)?.getTypedParameterValue(originalId)
    }

    /**
     * Adds another effect to this composite.
     */
    public operator fun plus(other: ShaderEffect): CompositeEffect = when (other) {
        is CompositeEffect -> CompositeEffect(effects + other.effects)
        else -> CompositeEffect(effects + other)
    }

    /**
     * Returns the effect at the given index.
     */
    public operator fun get(index: Int): ShaderEffect = effects[index]

    /**
     * Returns the number of effects in this composite.
     */
    public val size: Int get() = effects.size

    private fun parseParameterId(parameterId: String): Pair<Int, String>? {
        val underscoreIndex = parameterId.indexOf('_')
        if (underscoreIndex <= 0) return null

        val indexStr = parameterId.substring(0, underscoreIndex)
        val originalId = parameterId.substring(underscoreIndex + 1)

        return indexStr.toIntOrNull()?.let { it to originalId }
    }

    public companion object {
        /**
         * Creates a composite effect from the given effects.
         */
        public fun of(vararg effects: ShaderEffect): CompositeEffect =
            CompositeEffect(effects.toList())

        /**
         * Creates a composite effect from a list of effects.
         */
        public fun of(effects: List<ShaderEffect>): CompositeEffect =
            CompositeEffect(effects)
    }
}

/**
 * Creates a prefixed version of a parameter spec for composite effects.
 *
 * Exhaustive over [ParameterSpec] sealed hierarchy. Add handling for any new
 * [ParameterSpec] subtypes when extending the parameter system.
 */
private fun createPrefixedParameter(effectIndex: Int, delegate: ParameterSpec): ParameterSpec {
    return when (delegate) {
        is com.debanshu.shaderlab.shaderx.parameter.FloatParameter ->
            com.debanshu.shaderlab.shaderx.parameter.FloatParameter(
                id = "${effectIndex}_${delegate.id}",
                label = delegate.label,
                range = delegate.range,
                defaultValue = delegate.defaultValue,
                decimalPlaces = delegate.decimalPlaces
            )
        is com.debanshu.shaderlab.shaderx.parameter.PercentageParameter ->
            com.debanshu.shaderlab.shaderx.parameter.PercentageParameter(
                id = "${effectIndex}_${delegate.id}",
                label = delegate.label,
                defaultValue = delegate.defaultValue
            )
        is com.debanshu.shaderlab.shaderx.parameter.PixelParameter ->
            com.debanshu.shaderlab.shaderx.parameter.PixelParameter(
                id = "${effectIndex}_${delegate.id}",
                label = delegate.label,
                range = delegate.range,
                defaultValue = delegate.defaultValue
            )
        is com.debanshu.shaderlab.shaderx.parameter.ToggleParameter ->
            com.debanshu.shaderlab.shaderx.parameter.ToggleParameter(
                id = "${effectIndex}_${delegate.id}",
                label = delegate.label,
                isEnabledByDefault = delegate.isEnabledByDefault
            )
        is com.debanshu.shaderlab.shaderx.parameter.ColorParameter ->
            com.debanshu.shaderlab.shaderx.parameter.ColorParameter(
                id = "${effectIndex}_${delegate.id}",
                label = delegate.label,
                defaultColor = delegate.defaultColor
            )
    }
}

/**
 * Combines two shader effects into a composite effect.
 *
 * The effects are applied in order: first `this`, then `other`.
 *
 * ## Usage
 * ```kotlin
 * val effect = GrayscaleEffect() + VignetteEffect() + NativeBlurEffect()
 * ```
 */
public operator fun ShaderEffect.plus(other: ShaderEffect): CompositeEffect = when {
    this is CompositeEffect && other is CompositeEffect ->
        CompositeEffect(this.effects + other.effects)
    this is CompositeEffect ->
        CompositeEffect(this.effects + other)
    other is CompositeEffect ->
        CompositeEffect(listOf(this) + other.effects)
    else ->
        CompositeEffect(listOf(this, other))
}

