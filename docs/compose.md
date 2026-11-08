---
title: Compose shader modifiers, animation, and error handling
description: Integrate ShaderX with Compose state, handle shader errors, chain Android effects, scope GPU caches, and use animation sessions and live FFT textures.
---

# Compose integration

Start with the [static quickstart](getting-started.md). This guide describes **0.1.4**, including animation sessions and texture inputs. The static examples also apply to 0.1.3.

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

The 0.1.4 modifiers preserve the 0.1.3 argument order: `effect`, `factory`, `knownSize`, then `onError` or `onResult`. Existing fourth-position callbacks and trailing lambdas keep their meaning. Live-data invalidation uses a separate overload with an explicit `frameDataKey`; use named arguments such as `frameDataKey = currentFrame, onError = onShaderError` when selecting it.

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

<a id="animated-effects-in-development"></a>

## Animated effects

In 0.1.4, `Modifier.shaderEffect` owns a `ShaderSession` and refreshes animated uniforms each frame. Pass the effect directly:

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

Shader compilation happens when a session is created; frame refreshes update uniforms. Configuration, factory, or size changes can recreate a session. Session identity uses structural equality, so different configurations with the same hash still get separate sessions. `animate = false` stops time advancement, while per-frame inputs still refresh. A composite containing animated children does not use this automatic session path.

The modifier starts its clock at zero when it creates a session. Toggling `animate`, changing structural configuration, or resizing therefore restarts that clock. While paused, the shader uses the supplied effect's `time`; it does not retain the last automatically generated phase. Use a manually managed session if your app needs its own pause, resume, or seek timeline.

The older `rememberShaderEffect` helper is not needed for this API. It is intended for manually managed render effects; avoid pairing it with the session-owning modifier. In 0.1.4, the helper tracks time independently from effect equality, and `rememberRenderEffect` / `rememberRenderEffectResult` include time and factory changes when refreshing their result. The modifier still offers the session path for routine animation.

<a id="live-textures-and-audio-in-development"></a>

## Live textures and audio

Use an `ImageBitmap` source lambda for live FFT data so the session can pull the latest image without changing structural effect identity:

```kotlin
import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect

fun spectrumEffect(latestSpectrum: () -> ImageBitmap): AudioVisualizerEffect =
    AudioVisualizerEffect(fftSize = 64f).withFftDataSource(latestSpectrum)
```

Pass the result to `shaderEffect`. Supply a bitmap with normalized magnitudes in the red channel, laid out across a single row; keep `fftSize` consistent with the number of bins. The source should return the latest prepared bitmap rather than perform microphone capture or expensive FFT work during rendering.

For custom shaders, declare `uniform shader fftData;` and bind `TextureUniform("fftData", bitmap)`. Child shaders sample **pixel coordinates**: the center of bin `i` is `float2(i + 0.5, 0.5)`. Emit changing textures from `buildFrameUniforms`. Keep time and live images out of structural equality. Change `frameDataKey` on the modifier when replacing data excluded from equality; the loop receives the latest instance while retaining the existing session. Result callbacks also follow the latest callback supplied by composition.

A creation failure, zero-sized target, or `null` effect clears the previous filter so the original content can draw. A transient frame-refresh failure reports the error and retains the last successful frame; the loop retries on the next frame so a live data source can recover without rebuilding its session.

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

The helper closes the factory automatically. Do not close the process-wide default factory. For manually created factories, close them when done. The default cache size is 50 entries and must be positive. Android animated sessions own a separate shader; Skia sessions own a builder that retains the shared compiled effect. In 0.1.4, closing a Skia session releases that builder, and cache eviction or clearing releases the cache's reference without invalidating an active session. Do not refresh a closed session.

Custom `ShaderFactory` implementations that provide the 0.1.3 methods can keep using static rendering in 0.1.4. The added `createAnimatedSession` method has a default that returns `ShaderError.UnsupportedEffect`. Override it and provide a correctly owned `ShaderSession` to support the modifier's animation path; the default reports the unsupported operation through the result or error callback.

## Size and first render

By default, shader dimensions come from `onSizeChanged`, so the initial render may precede the effect. If the content size is already known, pass `knownSize = IntSize(widthPx, heightPx)`. These are **pixels**, not dp; update the supplied size when the content changes. The modifier still creates the render effect asynchronously after composition, so this is not a guarantee of a shader on the first displayed frame.
