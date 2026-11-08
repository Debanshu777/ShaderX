---
title: Write custom AGSL and SkSL shaders in Kotlin
description: Implement ShaderX's public RuntimeShaderEffect interface with a complete custom shader example, typed parameters, and guidance for portable Android and Skia rendering.
---

# Custom AGSL and SkSL effects

For effects in your own app or library, implement the public **`RuntimeShaderEffect`** interface. This example works with 0.1.3 and 0.1.4.

`AbstractRuntimeShaderEffect` and `AbstractAnimatedShaderEffect` use internal parameter handlers. They are implementation helpers for effects inside ShaderX; external consumers cannot implement their internal abstract members.

## A complete custom effect

This effect blends the image with a warm monochrome tint. Its shader preserves premultiplied alpha, and its public parameter methods return immutable copies.

```kotlin
import androidx.compose.runtime.Immutable
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.PercentageParameter
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform

@Immutable
data class WarmTintEffect(val intensity: Float = 0.5f) : RuntimeShaderEffect {
    init {
        require(intensity in 0f..1f) { "intensity must be between 0 and 1" }
    }

    override val id: String = "example_warm_tint"
    override val displayName: String = "Warm tint"
    override val parameters: List<ParameterSpec> = listOf(
        PercentageParameter("intensity", "Intensity", defaultValue = 0.5f),
    )

    override val shaderSource: String = """
        uniform shader content;
        uniform float intensity;

        half4 main(float2 fragCoord) {
            half4 color = content.eval(fragCoord);
            half luminance = dot(color.rgb, half3(0.299, 0.587, 0.114));
            half3 warm = luminance * half3(1.0, 0.75, 0.45);
            return half4(mix(color.rgb, warm, intensity), color.a);
        }
    """.trimIndent()

    override fun buildUniforms(width: Float, height: Float): List<Uniform> =
        listOf(FloatUniform("intensity", intensity))

    override fun withTypedParameter(
        parameterId: String,
        value: ParameterValue,
    ): WarmTintEffect {
        if (parameterId != "intensity") return this
        require(value is ParameterValue.FloatValue) { "intensity requires a float" }
        return copy(intensity = value.value.coerceIn(0f, 1f))
    }

    override fun getParameterValue(parameterId: String): Float =
        if (parameterId == "intensity") intensity else 0f

    override fun getTypedParameterValue(parameterId: String): ParameterValue? =
        if (parameterId == "intensity") ParameterValue.FloatValue(intensity) else null
}
```

Use `Modifier.shaderEffect(WarmTintEffect(intensity = 0.7f))` inside a composable, importing `com.debanshu.shaderlab.shaderx.compose.shaderEffect`.

## Shader contract

- Declare `uniform shader content;` for the content being transformed.
- Use `half4 main(float2 fragCoord)` as the entry point.
- Sample content using pixel coordinates with `content.eval(fragCoord)`.
- Keep uniform names and dimensions consistent between Kotlin and the shader source.
- Give each effect type a unique, stable ID. Avoid reusing an ID for unrelated shader programs.
- Test source on Android and Skia. AGSL and SkSL have a shared language foundation, but arbitrary GLSL programs are not directly interchangeable with them.

See [Android's AGSL documentation](https://developer.android.com/develop/ui/views/graphics/agsl) and [Skia's SkSL documentation](https://skia.org/docs/user/sksl/) for the shader languages.

The companion article, [Building GPU Shader Effects for Compose Multiplatform: From Scratch to ShaderX](https://medium.com/gitconnected/building-gpu-shader-effects-for-compose-multiplatform-from-scratch-to-shaderx-326659c4f47b), provides background on the project. Follow this guide's interface contract and [platform matrix](platforms.md) when adapting an example to the current library.

## Animated custom effects

In 0.1.4, external effects can implement `AnimatedShaderEffect`, including `time`, `isAnimating`, `withTime`, and `buildFrameUniforms`. Use configuration-only `equals` and `hashCode`; including time or changing textures tears down session identity. The [animated effect interface](https://github.com/Debanshu777/ShaderX/blob/main/shaderx/src/commonMain/kotlin/com/debanshu/shaderlab/shaderx/effect/AnimatedShaderEffect.kt) defines the contract.

When contributing an effect directly to ShaderX, you can use its internal handler helpers through the abstract base classes. Follow the [contributor checklist](contributing.md#add-an-effect) and register the new built-in effect in `ShaderX.builtInEffects()`.
