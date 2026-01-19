package com.debanshu.shaderlab.shaderx.effect.impl

import com.debanshu.shaderlab.shaderx.effect.BlurEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.PixelParameter

/**
 * Native blur effect using platform-optimized implementations.
 *
 * On Android, uses `RenderEffect.createBlurEffect()`.
 * On iOS/Desktop, uses Skia's `ImageFilter.makeBlur()`.
 *
 * @property radius Blur radius in pixels (minimum 0.1)
 */
public data class NativeBlurEffect(
    override val radius: Float = 10f,
) : BlurEffect {
    override val id: String = ID
    override val displayName: String = "Blur"

    override val parameters: List<ParameterSpec> =
        listOf(
            PixelParameter(
                id = PARAM_RADIUS,
                label = "Radius",
                range = 0f..50f,
                defaultValue = radius,
            ),
        )

    override fun withParameter(
        parameterId: String,
        value: Float,
    ): NativeBlurEffect =
        when (parameterId) {
            PARAM_RADIUS -> copy(radius = value)
            else -> this
        }

    override fun withTypedParameter(
        parameterId: String,
        value: ParameterValue,
    ): NativeBlurEffect =
        when (parameterId) {
            PARAM_RADIUS -> {
                when (value) {
                    is ParameterValue.FloatValue -> copy(radius = value.value)
                    else -> this
                }
            }

            else -> {
                this
            }
        }

    override fun getTypedParameterValue(parameterId: String): ParameterValue? =
        when (parameterId) {
            PARAM_RADIUS -> ParameterValue.FloatValue(radius)
            else -> null
        }

    public companion object {
        public const val ID: String = "blur"
        public const val PARAM_RADIUS: String = "radius"
    }
}
