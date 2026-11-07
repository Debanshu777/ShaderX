package com.debanshu.shaderlab.shaderx.effect

import androidx.compose.runtime.Immutable
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue

/**
 * A composite effect that combines multiple shader effects in sequence.
 *
 * Each effect is applied in order, with the output of one becoming
 * the input of the next. Chaining is supported on Android (API 33+);
 * on other platforms a [ShaderError.UnsupportedEffect] error is returned.
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
@Immutable
public data class CompositeEffect(
    public val effects: List<ShaderEffect>,
) : ShaderEffect {

    init {
        require(effects.isNotEmpty()) { "CompositeEffect requires at least one effect" }
        // Validate that no leaf (non-composite) effect has a parameter ID containing
        // the reserved delimiter. CompositeEffect children are excluded because their
        // parameter IDs are already library-generated and contain the delimiter by design.
        effects.forEach { eff ->
            if (eff !is CompositeEffect) {
                eff.parameters.forEach { p ->
                    require(DELIMITER !in p.id) {
                        "Parameter ID '${p.id}' in effect '${eff.id}' contains the reserved delimiter (U+001F). " +
                            "Choose a parameter ID without this character."
                    }
                }
            }
        }
    }

    override val id: String = "composite_${effects.joinToString("_") { it.id }}"

    override val displayName: String = effects.joinToString(" + ") { it.displayName }

    /**
     * Combined parameters from all contained effects.
     *
     * Parameter IDs are prefixed with the effect index and a non-printing delimiter
     * (U+001F, ASCII Unit Separator) to avoid collisions even when effect IDs
     * contain underscores. For example, two effects with "intensity" become
     * "0\u001Fintensity" and "1\u001Fintensity".
     *
     * Prefixing is delegated to [com.debanshu.shaderlab.shaderx.parameter.ParameterSpec.withId],
     * which is a sealed-interface member — the compiler enforces that every [ParameterSpec]
     * subtype handles it.
     */
    override val parameters: List<ParameterSpec> =
        effects.flatMapIndexed { index, effect ->
            effect.parameters.map { param ->
                param.withId("$index$DELIMITER${param.id}")
            }
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
        val delimIndex = parameterId.indexOf(DELIMITER)
        if (delimIndex <= 0) return null

        val indexStr = parameterId.substring(0, delimIndex)
        val originalId = parameterId.substring(delimIndex + 1)

        return indexStr.toIntOrNull()?.let { it to originalId }
    }

    public companion object {
        /**
         * Non-printing delimiter used to separate the effect index from the
         * parameter ID in composite parameter names. U+001F cannot appear in
         * normal user-defined parameter IDs.
         */
        public const val DELIMITER: Char = '\u001F'

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
