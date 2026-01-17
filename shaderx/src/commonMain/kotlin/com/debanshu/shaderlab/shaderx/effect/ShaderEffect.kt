package com.debanshu.shaderlab.shaderx.effect

import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue

/**
 * Base interface for all shader effects in the library.
 *
 * This interface defines the common contract for shader effects, including
 * identification, display information, and parameter management.
 *
 * Implementations should be immutable data classes that return new instances
 * when parameters are modified via [withParameter] or [withTypedParameter].
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
     * This is the legacy Float-based API for backwards compatibility.
     * For type-safe updates, use [withTypedParameter].
     *
     * @param parameterId The ID of the parameter to update
     * @param value The new value for the parameter
     * @return A new [ShaderEffect] instance with the updated parameter
     */
    public fun withParameter(parameterId: String, value: Float): ShaderEffect

    /**
     * Creates a new effect instance with the specified typed parameter value updated.
     *
     * This method provides type-safe parameter updates and is the recommended
     * way to update parameters, especially for non-float types like colors.
     *
     * @param parameterId The ID of the parameter to update
     * @param value The new typed value for the parameter
     * @return A new [ShaderEffect] instance with the updated parameter
     */
    public fun withTypedParameter(parameterId: String, value: ParameterValue): ShaderEffect {
        // Default implementation delegates to Float-based method for backwards compatibility
        return withParameter(parameterId, value.toFloat())
    }

    /**
     * Gets the current value of a parameter.
     *
     * @param parameterId The ID of the parameter to retrieve
     * @return The current value, or the default value if not set
     */
    public fun getParameterValue(parameterId: String): Float =
        parameters.find { it.id == parameterId }?.defaultValue ?: 0f

    /**
     * Gets the current typed value of a parameter.
     *
     * @param parameterId The ID of the parameter to retrieve
     * @return The current typed value, or the default typed value if not set
     */
    public fun getTypedParameterValue(parameterId: String): ParameterValue? =
        parameters.find { it.id == parameterId }?.getTypedDefaultValue()
}
