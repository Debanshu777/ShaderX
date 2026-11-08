---
title: Shader effect catalog and Kotlin parameters
description: Explore ShaderX grayscale, sepia, blur, pixelation, gradients, wave distortion, and development audio effects, with Kotlin constructors and release availability.
---

# Built-in shader effects

Import effects from `com.debanshu.shaderlab.shaderx.effect.impl`. Pass an effect to `Modifier.shaderEffect` as shown in the [quickstart](getting-started.md).

## Published in 0.1.3

| Effect | Example | Parameters / purpose |
| --- | --- | --- |
| Grayscale | `GrayscaleEffect(intensity = 1f)` | `intensity`: 0–1; blend with luminance-based grayscale |
| Sepia | `SepiaEffect(intensity = 1f)` | `intensity`: 0–1; warm photograph tone |
| Gradient | `GradientEffect(intensity = 0.5f)` | `color1`, `color2`: ARGB `Long`; `intensity`: 0–1 |
| Vignette | `VignetteEffect(radius = 0.5f, intensity = 0.5f)` | Both parameters: 0–1; darken edges |
| Pixelation | `PixelateEffect(pixelSize = 10f)` | `pixelSize`: 1–100 pixels |
| Chromatic aberration | `ChromaticAberrationEffect(offset = 5f)` | `offset`: 0–20 pixels; separate color channels |
| Invert | `InvertEffect()` | Invert RGB while preserving alpha |
| Native blur | `NativeBlurEffect(radius = 10f)` | `radius`: 0–50 pixels; platform blur |
| Wave | `WaveEffect(amplitude = 10f, frequency = 5f)` | `amplitude`: 0–50 pixels; `frequency`: 1–20; `animate` and `time` |

These are the declared parameter ranges used by ShaderX's parameter API. Constructors and `copy` calls do not uniformly clamp values; keep directly supplied values in range. Use `withParameter` or `withTypedParameter` for validated updates to built-in effects.

Wave exists in 0.1.3, but the automatic session-based animation described in the [Compose guide](compose.md#animated-effects-in-development) requires `main` / 0.1.4 development. Do not assume the animation behavior of `main` is present in 0.1.3.

## Added on main / 0.1.4 development

These classes are not available in the published 0.1.3 artifact:

| Effect | Example | Purpose |
| --- | --- | --- |
| Animated gradient | `AnimatedGradientEffect(speed = 1f)` | Rotating gradient with `startColor` and `endColor` |
| Plasma | `PlasmaEffect(scale = 4f, speed = 1f, colorIntensity = 1f)` | Animated plasma colors |
| Ripple | `RippleEffect(frequency = 10f, speed = 1.5f, amplitude = 0.02f)` | Concentric distortion with configurable `decay` |
| Vortex | `VortexEffect(strength = 1f, radius = 0.5f, speed = 1f)` | Centered spiral distortion |
| Audio visualizer | `AudioVisualizerEffect(fftSize = 64f, barColorIntensity = 1f)` | Spectrum bars from an FFT texture |
| Audio distortion | `AudioDistortionEffect(fftSize = 64f, intensity = 1f)` | Content displacement from an FFT texture |

Each supports `animate` and `time`. Audio effects need spectrum data supplied by your app; they do not capture audio themselves. See [live textures](compose.md#live-textures-and-audio-in-development) and the [ShaderLab sample](samples.md#shaderlab).

## Discover and edit parameters

`ShaderX.builtInEffects()` returns the effects shipped with the version you are using:

```kotlin
import com.debanshu.shaderlab.shaderx.ShaderX
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue

val effects = ShaderX.builtInEffects()
val softer = GrayscaleEffect().withParameter(GrayscaleEffect.PARAM_INTENSITY, 0.5f)
val blueGradient = GradientEffect().withTypedParameter(
    GradientEffect.PARAM_COLOR_1,
    ParameterValue.ColorValue(0xFF1565C0),
)
```

Parameter updates return a new value. Store that value in Compose state to redraw your UI. Built-in static effects typically expose `copy`. `WaveEffect` also retains its 0.1.3 data-class API: `copy`, destructuring in `amplitude`, `frequency`, `animate`, `time` order, and concrete `WaveEffect` results from `withTime` and `withTypedParameter`. Its equality and hash code exclude `time`, including in development session animation. Other animated effects use immutable classes with parameter APIs; do not assume every effect is a data class.

For exact defaults and the full parameter definitions, browse the [effect source](https://github.com/Debanshu777/ShaderX/tree/main/shaderx/src/commonMain/kotlin/com/debanshu/shaderlab/shaderx/effect/impl) or generate the [API reference](index.md#source-and-api-reference).
