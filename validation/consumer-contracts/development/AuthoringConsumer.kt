package shaderx.consumer

import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.authoring.DefinedAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.authoring.DefinedShaderEffect
import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram
import com.debanshu.shaderlab.shaderx.parameter.ColorParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.FloatParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterSchema

// Complete public consumer examples, shared by independent Android-only and KMP modules.
// No renderer handles, dispatch, getters, equality overrides, registry, or editor are required.
object ConsumerTint {
    val COLOR = ColorParameterDefinition("consumer_tint", "color", 0x804080C0L)
    val definition = ShaderDefinition(
        id = "consumer_tint",
        displayName = "Consumer tint",
        schema = ParameterSchema("consumer_tint", listOf(COLOR)),
        program = ShaderProgram(
            source = """
                uniform shader content;
                layout(color) uniform half4 tintColor;
                half4 main(float2 p) {
                    half4 sampled = content.eval(p);
                    return half4(tintColor.rgb * tintColor.a, tintColor.a) * sampled.a;
                }
            """.trimIndent(),
            bindings = listOf(ShaderBinding.color("tintColor", COLOR.key)),
        ),
    )

    fun effect(color: Long = COLOR.defaultValue): DefinedShaderEffect =
        definition.effect().withValue(COLOR.key, color)
}

object ConsumerDistortion {
    val AMPLITUDE = FloatParameterDefinition("consumer_distortion", "amplitude", 4f, 0f..32f, unit = "px")
    val definition = ShaderDefinition(
        id = "consumer_distortion",
        displayName = "Consumer distortion",
        schema = ParameterSchema("consumer_distortion", listOf(AMPLITUDE)),
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
                ShaderBinding.float("strength", AMPLITUDE.key),
            ),
        ),
    )

    fun effect(amplitude: Float = AMPLITUDE.defaultValue): DefinedAnimatedShaderEffect =
        definition.animatedEffect().withValue(AMPLITUDE.key, amplitude)
}

// Minimal source-plus-bindings path: no editable schema or metadata is needed.
// The extra input samples pixels in the image's own coordinate space. The image remains caller-owned.
fun imageOverlay(image: ImageBitmap): DefinedShaderEffect = ShaderDefinition(
    id = "consumer_image_overlay",
    displayName = "Consumer image overlay",
    program = ShaderProgram(
        source = """
            uniform shader content;
            uniform shader overlay;
            half4 main(float2 p) {
                return mix(content.eval(p), overlay.eval(p), 0.5);
            }
        """.trimIndent(),
        bindings = listOf(ShaderBinding.image("overlay", image)),
    ),
).effect()
