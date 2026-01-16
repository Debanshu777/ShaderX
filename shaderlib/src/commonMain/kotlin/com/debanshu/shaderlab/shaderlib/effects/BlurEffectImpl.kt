package com.debanshu.shaderlab.shaderlib.effects

import com.debanshu.shaderlab.shaderlib.effect.BlurEffect
import com.debanshu.shaderlab.shaderlib.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderlib.parameter.PixelParameter

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

    override val parameters: List<ParameterSpec> = listOf(
        PixelParameter(
            id = PARAM_RADIUS,
            label = "Radius",
            range = 0f..50f,
            defaultValue = radius,
        ),
    )

    override fun withParameter(parameterId: String, value: Float): NativeBlurEffect =
        when (parameterId) {
            PARAM_RADIUS -> copy(radius = value)
            else -> this
        }

    public companion object {
        public const val ID: String = "blur"
        public const val PARAM_RADIUS: String = "radius"
    }
}
