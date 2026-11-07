---
title: Compose shader modifiers, animation, and error handling
description: Integrate ShaderX with Compose state, handle shader errors, chain Android effects, scope GPU caches, and use development animation sessions and live FFT textures.
---

# Compose integration

Start with the [static quickstart](getting-started.md). Examples in this guide that use animation sessions or texture inputs require **main / 0.1.4 development**; the other examples also apply to 0.1.3.

## Handle rendering errors

The modifier accepts `null` to disable an effect and an `onError` callback to report failures. On initial shader creation failure, content remains visible without the effect.

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderError

@Composable
fun EffectPhoto(
    painter: Painter,
    effect: ShaderEffect?,
    onShaderError: (ShaderError) -> Unit,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painter,
        contentDescription = "Photo preview",
        modifier = modifier.shaderEffect(effect, onError = onShaderError),
    )
}
```

Use `shaderEffectWithResult` if you need both success and failure callbacks. Factory operations return `ShaderResult`; use `onSuccess`, `onFailure`, `fold`, `map`, or `flatMap` to compose results. Parameter validation can still throw for incompatible types, so these result types are not a promise that every API is exception-free.

## Update parameters

Keep effect values immutable and replace them when state changes. For example, pass `GrayscaleEffect(intensity = intensity)` from a composable whose `intensity` is state. Generic parameter editors can use the [typed parameter API](effects.md#discover-and-edit-parameters).

## Chain effects on Android

Import the `plus` extension to apply effects in order:

```kotlin
import com.debanshu.shaderlab.shaderx.effect.plus
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.VignetteEffect

val filmEffect = GrayscaleEffect() + VignetteEffect(radius = 0.6f)
```

Pass `filmEffect` to `shaderEffect`. This multi-effect chain requires **Android API 33+**. Skia backends return `UnsupportedEffect`. Choose a single effect for those platforms or combine the operations in one custom shader.

`CompositeEffect.of(...)` is the named equivalent. Composite parameter identifiers use the child index followed by U+001F and the original parameter ID, such as `"0\u001Fintensity"`. This keeps two children's `intensity` parameters distinct.

## Animated effects in development

On `main`, `Modifier.shaderEffect` owns a `ShaderSession` and refreshes animated uniforms each frame. Pass the effect directly:

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect

@Composable
fun WavePhoto(painter: Painter, modifier: Modifier = Modifier) {
    Image(
        painter = painter,
        contentDescription = "Photo with animated wave distortion",
        modifier = modifier.shaderEffect(
            WaveEffect(amplitude = 15f, frequency = 6f, animate = true),
        ),
    )
}
```

Shader compilation happens when a session is created; frame refreshes update uniforms. Configuration or size changes can recreate a session. `animate = false` stops time advancement, while per-frame inputs still refresh. A composite containing animated children does not use this automatic session path.

The older `rememberShaderEffect` helper is not needed for this API. It is intended for manually managed render effects; avoid pairing it with the session-owning modifier.

## Live textures and audio in development

Use an `ImageBitmap` source lambda for live FFT data so the session can pull the latest image without changing structural effect identity:

```kotlin
import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect

fun spectrumEffect(latestSpectrum: () -> ImageBitmap): AudioVisualizerEffect =
    AudioVisualizerEffect(fftSize = 64f).withFftDataSource(latestSpectrum)
```

Pass the result to `shaderEffect`. Supply a bitmap with normalized magnitudes in the red channel, laid out across a single row; keep `fftSize` consistent with the number of bins. The source should return the latest prepared bitmap rather than perform microphone capture or expensive FFT work during rendering.

For custom shaders, declare `uniform shader fftData;` and bind `TextureUniform("fftData", bitmap)`. Child shaders sample **pixel coordinates**: the center of bin `i` is `float2(i + 0.5, 0.5)`. Emit changing textures from `buildFrameUniforms`. Keep time and live images out of structural equality. `frameDataKey` on the modifier can force invalidation when replacing data excluded from equality.

See the [sample audio module](https://github.com/Debanshu777/ShaderX/tree/main/samples/ShaderLab/audiolib) for platform microphone capture and FFT packing. That module is not included in the published ShaderX artifact.

## Scope the shader cache

The default `LocalShaderFactory` is a process-wide singleton. To release a gallery's cached GPU resources when it leaves composition:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.debanshu.shaderlab.shaderx.compose.LocalShaderFactory
import com.debanshu.shaderlab.shaderx.compose.rememberShaderFactory

@Composable
fun ShaderScope(content: @Composable () -> Unit) {
    val factory = rememberShaderFactory(maxCacheSize = 16)
    CompositionLocalProvider(LocalShaderFactory provides factory) {
        content()
    }
}
```

The helper closes the factory automatically. Do not close the process-wide default factory. For manually created factories, close them when done. The default cache size is 50 entries; animated sessions own their shader separately from the shared compilation cache.

## Size and first render

By default, shader dimensions come from `onSizeChanged`, so the initial render may precede the effect. If the content size is already known, pass `knownSize = IntSize(widthPx, heightPx)`. These are **pixels**, not dp; update the supplied size when the content changes. The modifier still creates the render effect asynchronously after composition, so this is not a guarantee of a shader on the first displayed frame.
