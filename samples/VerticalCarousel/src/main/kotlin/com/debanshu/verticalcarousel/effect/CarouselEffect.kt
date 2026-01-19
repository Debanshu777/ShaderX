package com.debanshu.verticalcarousel.effect

import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.PercentageParameter
import com.debanshu.shaderlab.shaderx.parameter.PixelParameter
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

/**
 * A custom shader effect for the vertical carousel that combines multiple visual effects:
 * - Scale transformation based on distance from center
 * - Vignette darkening at edges
 * - Chromatic aberration during scroll velocity
 * - Cylindrical stretch distortion for 3D carousel appearance
 *
 * This effect is applied to each carousel item, with the [distanceFromCenter] parameter
 * controlling the intensity of all effects. Items at the center (0.0) appear normal,
 * while items at the edges (1.0) have maximum effect applied.
 *
 * @property distanceFromCenter Normalized distance from viewport center (0.0 = center, 1.0 = edge)
 * @property scrollVelocity Current scroll velocity for chromatic aberration effect
 * @property vignetteIntensity Strength of the vignette darkening effect
 * @property scaleAmount How much to scale down edge items (0.0 = no scale, 1.0 = full scale down)
 * @property stretchIntensity Maximum stretch amount at edges for cylindrical distortion (0.0 - 1.0)
 * @property scrollDirection Direction indicator (-1 = above center, +1 = below center, 0 = at center)
 */
public data class CarouselEffect(
    private val distanceFromCenter: Float = 0f,
    private val scrollVelocity: Float = 0f,
    private val vignetteIntensity: Float = 0.4f,
    private val scaleAmount: Float = 0.35f,
    private val stretchIntensity: Float = 0.4f,
    private val scrollDirection: Float = 0f,
) : RuntimeShaderEffect {
    override val id: String = ID
    override val displayName: String = "Carousel"

    override val shaderSource: String =
        """
        uniform shader content;
        uniform float2 resolution;
        uniform float distanceFromCenter;
        uniform float scrollVelocity;
        uniform float vignetteIntensity;
        uniform float scaleAmount;
        uniform float stretchIntensity;
        uniform float scrollDirection;
        
        // Constants for tuning
        const float STRETCH_POWER = 2.5;
        const float STRETCH_SCALE = 0.8;
        const float COMPRESSION_SCALE = 1.0;
        const float ELONGATION_SCALE = 0.15;  // How much to stretch vertically at max distance
        const float ABERRATION_THRESHOLD = 0.5;
        const float ABERRATION_SCALE = 0.02;
        const float OPACITY_FADE = 0.3;
        
        // Apply paper curl/bend distortion - cards curve inward toward viewport center
        float2 applyStretchDistortion(float2 coord, float2 res) {
            if (abs(scrollDirection) < 0.1) {
                return coord; // Card at center, no distortion
            }
            
            float normalizedY = coord.y / res.y;
            float intensity = stretchIntensity * distanceFromCenter;
            float centerX = res.x * 0.5;
            float2 result;
            
            if (scrollDirection > 0.0) {
                float curveFactor = pow(normalizedY, STRETCH_POWER);
                float xCompression = 1.0 - curveFactor * intensity * COMPRESSION_SCALE;
                result.x = centerX + (coord.x - centerX) * xCompression;
                result.y = coord.y - curveFactor * intensity * res.y * STRETCH_SCALE;
            } else {
                float curveFactor = pow(1.0-normalizedY, STRETCH_POWER);
                float xCompression = 1.0 - curveFactor * intensity * COMPRESSION_SCALE;
                result.x = centerX + (coord.x - centerX) * xCompression;
                result.y = coord.y + curveFactor * intensity * res.y * STRETCH_SCALE;
            }
            
            // Calculate elongation factor (1.0 = no change, > 1.0 = taller)
            float elongation = 1.0 + (distanceFromCenter * stretchIntensity * ELONGATION_SCALE);
            
            // Apply elongation by scaling Y around the vertical center
            float centerY = res.y * 0.5;
            result.y = centerY + (result.y - centerY) * elongation;
            
            return result;
        }
        
        half4 main(float2 fragCoord) {
            float2 center = resolution * 0.5;
            
            // Apply cylindrical stretch distortion first
            float2 stretchedUV = applyStretchDistortion(fragCoord, resolution);
            
            // Apply scale transformation - zoom towards center
            float scale = 1.0 - (distanceFromCenter * scaleAmount);
            float2 scaledUV = center + (stretchedUV - center) / scale;
            
            // Check bounds after transformations
            if (scaledUV.x < 0.0 || scaledUV.x > resolution.x || 
                scaledUV.y < 0.0 || scaledUV.y > resolution.y) {
                return half4(0.0, 0.0, 0.0, 0.0);
            }
            
            // Sample the content
            half4 color = content.eval(scaledUV);
            
            // Apply chromatic aberration based on scroll velocity
            float aberrationAmount = abs(scrollVelocity) * ABERRATION_SCALE;
            if (aberrationAmount > ABERRATION_THRESHOLD) {
                float2 dir = normalize(scaledUV - center);
                float r = content.eval(scaledUV + dir * aberrationAmount).r;
                float b = content.eval(scaledUV - dir * aberrationAmount).b;
                color.r = (color.r + r) * 0.5;
                color.b = (color.b + b) * 0.5;
            }
            
            // Apply vignette effect that intensifies with distance
            float2 normalizedUV = fragCoord / resolution;
            float vignetteDist = distance(normalizedUV, float2(0.5, 0.5));
            float vignetteEffect = smoothstep(0.2, 0.8, vignetteDist);
            float vignetteFactor = 1.0 - (vignetteEffect * vignetteIntensity * (0.5 + distanceFromCenter * 0.5));
            
            // Apply opacity fade for edge items
            float opacity = 1.0 - (distanceFromCenter * OPACITY_FADE);
            
            return half4(color.rgb * vignetteFactor, color.a * opacity);
        }
        """.trimIndent()

    override val parameters: List<ParameterSpec> =
        listOf(
            PercentageParameter(
                id = PARAM_DISTANCE,
                label = "Distance from Center",
                defaultValue = distanceFromCenter,
            ),
            PixelParameter(
                id = PARAM_VELOCITY,
                label = "Scroll Velocity",
                range = -100f..100f,
                defaultValue = scrollVelocity,
            ),
            PercentageParameter(
                id = PARAM_VIGNETTE,
                label = "Vignette Intensity",
                defaultValue = vignetteIntensity,
            ),
            PercentageParameter(
                id = PARAM_SCALE,
                label = "Scale Amount",
                defaultValue = scaleAmount,
            ),
            PercentageParameter(
                id = PARAM_STRETCH,
                label = "Stretch Intensity",
                defaultValue = stretchIntensity,
            ),
            PixelParameter(
                id = PARAM_DIRECTION,
                label = "Scroll Direction",
                range = -1f..1f,
                defaultValue = scrollDirection,
            ),
        )

    override fun buildUniforms(
        width: Float,
        height: Float,
    ): List<Uniform> =
        listOf(
            FloatUniform("resolution", width, height),
            FloatUniform("distanceFromCenter", distanceFromCenter),
            FloatUniform("scrollVelocity", scrollVelocity),
            FloatUniform("vignetteIntensity", vignetteIntensity),
            FloatUniform("scaleAmount", scaleAmount),
            FloatUniform("stretchIntensity", stretchIntensity),
            FloatUniform("scrollDirection", scrollDirection),
        )

    override fun withParameter(
        parameterId: String,
        value: Float,
    ): CarouselEffect =
        when (parameterId) {
            PARAM_DISTANCE -> copy(distanceFromCenter = value)
            PARAM_VELOCITY -> copy(scrollVelocity = value)
            PARAM_VIGNETTE -> copy(vignetteIntensity = value)
            PARAM_SCALE -> copy(scaleAmount = value)
            PARAM_STRETCH -> copy(stretchIntensity = value)
            PARAM_DIRECTION -> copy(scrollDirection = value)
            else -> this
        }

    override fun withTypedParameter(
        parameterId: String,
        value: ParameterValue,
    ): CarouselEffect =
        when (parameterId) {
            PARAM_DISTANCE -> {
                when (value) {
                    is ParameterValue.FloatValue -> copy(distanceFromCenter = value.value)
                    else -> this
                }
            }

            PARAM_VELOCITY -> {
                when (value) {
                    is ParameterValue.FloatValue -> copy(scrollVelocity = value.value)
                    else -> this
                }
            }

            PARAM_VIGNETTE -> {
                when (value) {
                    is ParameterValue.FloatValue -> copy(vignetteIntensity = value.value)
                    else -> this
                }
            }

            PARAM_SCALE -> {
                when (value) {
                    is ParameterValue.FloatValue -> copy(scaleAmount = value.value)
                    else -> this
                }
            }

            PARAM_STRETCH -> {
                when (value) {
                    is ParameterValue.FloatValue -> copy(stretchIntensity = value.value)
                    else -> this
                }
            }

            PARAM_DIRECTION -> {
                when (value) {
                    is ParameterValue.FloatValue -> copy(scrollDirection = value.value)
                    else -> this
                }
            }

            else -> {
                this
            }
        }

    override fun getTypedParameterValue(parameterId: String): ParameterValue? =
        when (parameterId) {
            PARAM_DISTANCE -> ParameterValue.FloatValue(distanceFromCenter)
            PARAM_VELOCITY -> ParameterValue.FloatValue(scrollVelocity)
            PARAM_VIGNETTE -> ParameterValue.FloatValue(vignetteIntensity)
            PARAM_SCALE -> ParameterValue.FloatValue(scaleAmount)
            PARAM_STRETCH -> ParameterValue.FloatValue(stretchIntensity)
            PARAM_DIRECTION -> ParameterValue.FloatValue(scrollDirection)
            else -> null
        }

    public companion object {
        public const val ID: String = "carousel"
        public const val PARAM_DISTANCE: String = "distanceFromCenter"
        public const val PARAM_VELOCITY: String = "scrollVelocity"
        public const val PARAM_VIGNETTE: String = "vignetteIntensity"
        public const val PARAM_SCALE: String = "scaleAmount"
        public const val PARAM_STRETCH: String = "stretchIntensity"
        public const val PARAM_DIRECTION: String = "scrollDirection"
    }
}
