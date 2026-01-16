package com.debanshu.shaderlab.shaderlib.effect

import com.debanshu.shaderlab.shaderlib.parameter.ParameterSpec

/**
 * Base interface for all shader effects in the library.
 *
 * This interface defines the common contract for shader effects, including
 * identification, display information, and parameter management.
 *
 * Implementations should be immutable data classes that return new instances
 * when parameters are modified via [withParameter].
 *
 * @see RuntimeShaderEffect for effects using custom shader code
 * @see NativeEffect for platform-optimized effects
 */
public interface ShaderEffect {
    /**
     * Unique identifier for this effect type.
     */
    public val id: String

    /**
     * Human-readable name for display in UI.
     */
    public val displayName: String

    /**
     * List of configurable parameters for this effect.
     * Each parameter defines its type, range, and default value.
     */
    public val parameters: List<ParameterSpec>

    /**
     * Creates a new effect instance with the specified parameter value updated.
     *
     * @param parameterId The ID of the parameter to update
     * @param value The new value for the parameter
     * @return A new [ShaderEffect] instance with the updated parameter
     */
    public fun withParameter(parameterId: String, value: Float): ShaderEffect

    /**
     * Gets the current value of a parameter.
     *
     * @param parameterId The ID of the parameter to retrieve
     * @return The current value, or the default value if not set
     */
    public fun getParameterValue(parameterId: String): Float =
        parameters.find { it.id == parameterId }?.defaultValue ?: 0f
}
