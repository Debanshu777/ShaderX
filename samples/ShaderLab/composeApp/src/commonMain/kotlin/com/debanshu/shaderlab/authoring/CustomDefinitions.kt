package com.debanshu.shaderlab.authoring

import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.BooleanParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ColorParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.FloatParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterMetadata
import com.debanshu.shaderlab.shaderx.parameter.ParameterSchema

/** Sample-local declarations, separate from ShaderX's built-in catalog and native resources. */
object CustomDefinitions {
    val TINT_COLOR = ColorParameterDefinition(
        "custom_tint", "tint", 0x804080C0L, ParameterMetadata("Tint"),
    )
    val tint = ShaderDefinition(
        id = "custom_tint",
        displayName = "Custom tint",
        schema = ParameterSchema("custom_tint", listOf(TINT_COLOR)),
        program = ShaderProgram(
            source = """
                uniform shader content;
                layout(color) uniform half4 tintColor;
                half4 main(float2 p) {
                    half4 sampled = content.eval(p);
                    half4 tint = half4(tintColor.rgb * tintColor.a, tintColor.a) * sampled.a;
                    return mix(sampled, tint, 0.5);
                }
            """.trimIndent(),
            bindings = listOf(ShaderBinding.color("tintColor", TINT_COLOR.key)),
        ),
    )

    val DISTORTION_AMOUNT = FloatParameterDefinition(
        "custom_distortion", "amplitude", 4f, 0f..32f,
        ParameterMetadata("Amplitude"), unit = "px",
    )
    val distortion = ShaderDefinition(
        id = "custom_distortion",
        displayName = "Custom distortion",
        schema = ParameterSchema("custom_distortion", listOf(DISTORTION_AMOUNT)),
        program = ShaderProgram(
            source = """
                uniform shader content;
                uniform float2 viewport;
                uniform float elapsed;
                uniform float strength;
                half4 main(float2 p) {
                    float offset = sin(p.y / viewport.y * 12.0 + elapsed) * strength;
                    return content.eval(p + float2(offset, 0.0));
                }
            """.trimIndent(),
            bindings = listOf(
                ShaderBinding.resolution("viewport"),
                ShaderBinding.time("elapsed"),
                ShaderBinding.float("strength", DISTORTION_AMOUNT.key),
            ),
        ),
    )

    val EDITOR_AMOUNT = FloatParameterDefinition(
        "custom_controls", "when", 0.12345679f, metadata = ParameterMetadata(
            "Gain", "Finite numeric entry without artificial bounds.", semanticRole = "gain",
        ), unit = "×", decimalPlaces = 3,
    )
    val EDITOR_COLOR = ColorParameterDefinition("custom_controls", "gain color", 0x804080C0L,
        ParameterMetadata("ARGB tint", "Eight hexadecimal digits: alpha, red, green, blue."))
    val EDITOR_ENABLED = BooleanParameterDefinition("custom_controls", "gain-color", true,
        ParameterMetadata("Apply tint", "A typed boolean, independent of the playback clock."))
    val controls = ShaderDefinition(
        id = "custom_controls", displayName = "Custom controls starter",
        schema = ParameterSchema("custom_controls", listOf(EDITOR_AMOUNT, EDITOR_COLOR, EDITOR_ENABLED)),
        program = ShaderProgram(
            source = """
                uniform shader content;
                uniform float gain;
                layout(color) uniform half4 tint;
                uniform int enabled;
                half4 main(float2 p) {
                    half4 sampled = content.eval(p);
                    half4 color = half4(tint.rgb * tint.a, tint.a) * sampled.a;
                    return enabled == 1 ? mix(sampled, color, clamp(gain, 0.0, 1.0)) : sampled;
                }
            """.trimIndent(),
            bindings = listOf(ShaderBinding.float("gain", EDITOR_AMOUNT.key),
                ShaderBinding.color("tint", EDITOR_COLOR.key), ShaderBinding.boolean("enabled", EDITOR_ENABLED.key)),
        ),
    )

    /** Fresh immutable instances reuse shared declarations; sessions later own native state. */
    fun effects(): List<ShaderEffect> = listOf(tint.effect(), distortion.animatedEffect(), controls.effect())
}
