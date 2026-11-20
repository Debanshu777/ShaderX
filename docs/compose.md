---
title: Compose shader modifiers, animation, and error handling
description: Integrate ShaderX with Compose state, handle shader errors, chain ordered effects, scope GPU caches, and choose session animation or controlled playback with live inputs.
---

# Compose integration

Start with the [quickstart](getting-started.md), then choose ordinary animation or explicit playback control below.

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

## Choose an explicit static fallback

The additive `createRenderEffectWithFallback` helper makes recovery from static render creation observable. It attempts the requested effect first, then follows the selected policy:

| Policy | Result after requested creation fails |
| --- | --- |
| `PreserveContent` (default) | Successful `null` filter; show the original content. |
| `Fail` | Original `ShaderResult.Failure`; the caller decides how to present it. |
| `Substitute(effect)` | Attempt that complete replacement once; return its result. |

```kotlin
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderFallbackPolicy
import com.debanshu.shaderlab.shaderx.factory.ShaderRenderOutcome
import com.debanshu.shaderlab.shaderx.factory.createRenderEffectWithFallback

fun staticPreview(
    factory: ShaderFactory,
    requested: ShaderEffect,
    widthPx: Float,
    heightPx: Float,
): ShaderRenderOutcome = factory.createRenderEffectWithFallback(
    effect = requested,
    width = widthPx,
    height = heightPx,
    policy = ShaderFallbackPolicy.Substitute(GrayscaleEffect()),
)
```

Inspect `outcome.result` before applying a filter through `graphicsLayer`. Display or log `outcome.cause` when present so recovery is visible. `fallbackUsed` is true when preservation or substitution was selected, including a failed substitute; it is false for an initial success or the `Fail` policy. If the substitute fails, `result` contains that failure and `cause` retains the requested effect's original error. Replacement never recursively falls back or drops individual chain children. The helper also attempts factories with unknown capabilities and handles compilation errors through the same policy.

This helper creates a **static snapshot**, including for animated effects; it owns no clock or session. Use the existing `shaderEffect` modifier for automatic animated sessions and its `onError` or `shaderEffectWithResult` callback to expose failures. Existing modifier signatures and defaults are unchanged: initial creation failures show original content, while transient session-refresh failures retain the last successful frame as described below. The `Fail` policy returns the reported failure; presentation stays under the caller's control.

## Update parameters

Keep effect values immutable and replace them when state changes. For example, pass `GrayscaleEffect(intensity = intensity)` from a composable whose `intensity` is state. Generic parameter editors can use the [typed parameter API](effects.md#discover-and-edit-parameters).

## Apply custom definitions { #apply-custom-definitions-in-development }

Pass `definition.effect()` or `definition.animatedEffect()` to the same `shaderEffect` modifier.
The [authoring guide](custom-effects.md#editable-static-tint-in-development) provides complete
source, imports and typed update examples. Store the generated instance in state and replace it
with `effect.withValue(key, value)` after edits. Retain definitions and live providers outside
per-frame work; no registry is required.

Generated animated equality excludes time and includes immutable values, definition, playback
flag and provider identity. Sessions own independent native state and replace it on structural
configuration or size changes, retaining the prior session if replacement fails. Color bindings
are always structural; frame providers can supply declared finite scalar floats, images, or explicit `frameData`/`frameNested` inputs,
validated as a complete set before setters run. `withPlayback(false)` leaves live refresh active.
The existing modifier's clock can still restart after configuration, playback or size changes;
use the additive controlled path below when phase must survive those changes.

## Controlled playback { #controlled-playback-in-development }

`rememberShaderPlayback` owns one composition-scoped clock;
`controlledShaderEffect` reads its position and owns only the native session. Retain the clock
outside configuration-dependent composition keys. Parameter edits, resizing, source/provider
replacement and factory replacement can replace native state without resetting its phase.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.debanshu.shaderlab.shaderx.compose.controlledShaderEffect
import com.debanshu.shaderlab.shaderx.compose.rememberShaderPlayback
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect

@Composable
fun ControlledWaveModifier(active: Boolean, reducedMotion: Boolean): Modifier {
    val playback = rememberShaderPlayback(
        initialPositionSeconds = 2.0,
        active = active,
        reducedMotion = reducedMotion,
    )
    return Modifier.controlledShaderEffect(WaveEffect(), playback)
}
```

Set `playback.isPlaying = false` to pause, then true to resume the last sampled phase.
`playback.seekTo(seconds)` accepts finite signed seconds and resets the frame anchor; the next
frame establishes an anchor without advancing. `playback.speed` defaults to 1.0 and accepts finite
nonnegative values; zero settles while preserving the play preference. Negative and nonfinite
speeds are rejected. Initial arguments are read once; later changes use these state operations.
Positions exceeding finite shader Float seconds report a rendering error. Playback itself is
not automatically saved across removal/recreation of its owner.

Share the returned playback between controlled effects for one phase; call the helper separately
for independent clocks. The effect's `time` and `isAnimating` do not drive the controlled path.
Host policy defaults to `active = true`, `reducedMotion = false`; pass the actual lifecycle and
accessibility preference. Either `active = false` or reduced motion freezes progression, retains
the play preference and avoids background catch-up on resume. Seeks and live revisions still work.
Removing the clock owner stops its scheduling.

For live data, retain the same provider, publish a complete validated snapshot, then change
`inputRevision` (a Long, default zero). A paused unchanged input schedules no continuous frames;
a revision triggers refresh independently of time. Images remain borrowed and must not be mutated
concurrently with rendering. Colors remain structural configuration. `visible = false` on the
modifier skips refreshes and retains its last filter; suspend the shared clock's `active` flag
when all its consumers are hidden. Failures retain the last successful filter; change a key or
revision to retry while paused. Disposing a modifier closes its session, not the caller's factory.
Legacy `shaderEffect`, `animate = false` and `frameDataKey` retain the behavior below.

## Compose with image loaders and backdrop surfaces

The [ecosystem guide](ecosystem.md) applies the public modifiers to Coil images and a Haze
source/effect pair. Keep the image loader and playback outside request-state branches, and
keep controls outside the filtered layer when they must remain clear. The Haze recipe captures
an explicitly registered source; `shaderEffect` filters its own downstream content and
`shaderBackground` generates pixels without capturing a backdrop. Modifier order and an outer
clip decide which pixels are recorded and where spatial output can appear.

## Chain effects

Import the `plus` extension to apply effects in order:

```kotlin
import com.debanshu.shaderlab.shaderx.effect.plus
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.VignetteEffect

val filmEffect = GrayscaleEffect() + VignetteEffect(radius = 0.6f)
```

Pass `filmEffect` to `shaderEffect` for a static snapshot. ShaderX supports ordered chains on Android API 33+ and Skia (desktop, iOS, Wasm). Every required child must succeed. Composition uses native filters and does not fuse shader programs. Offline `ImageProcessor` still rejects every composite.

`CompositeEffect.of(...)` is the named equivalent. Composite parameter identifiers use the child index followed by U+001F and the original parameter ID, such as `"0\u001Fintensity"`. This keeps two children's `intensity` parameters distinct.

## Animated effects { #animated-effects-in-development }

`Modifier.shaderEffect` owns a `ShaderSession` and refreshes animated uniforms each frame. Pass the effect directly:

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

The modifier starts its clock at zero when it creates a session. Toggling `animate`, changing structural configuration, or resizing therefore restarts that clock. While paused, the shader uses the supplied effect's `time`; it does not retain the last automatically generated phase. Use the controlled playback path above for a retained pause, resume, or seek timeline, or manage a session manually.

The older `rememberShaderEffect` helper is not needed for this API. It is intended for manually managed render effects; avoid pairing it with the session-owning modifier. In the current implementation, the helper tracks time independently from effect equality, and `rememberRenderEffect` / `rememberRenderEffectResult` include time and factory changes when refreshing their result. The modifier still offers the session path for routine animation.

### Shared-clock pipelines { #shared-clock-pipelines-in-development }

The additive `controlledShaderEffect(CompositeEffect, ShaderPlayback, ...)` overload applies the
same position to every animated child, including nested chains. Use the existing
`rememberShaderPlayback` owner and increment `inputRevision` when publishing live inputs.
Paused unchanged chains request no frames; static chains should use `shaderEffect` so they do
not rebuild as the clock advances. Child `time` and `isAnimating` values do not override the
controlled clock. Independent playback owners remain independent.

Manual integrations can call `factory.createPipelineSession(chain, width, height, timeSeconds)`
(import `com.debanshu.shaderlab.shaderx.factory.createPipelineSession`) and
`session.refresh(chain, width, height, timeSeconds)`. This is an optional `ShaderPipelineProvider`
contract; existing third-party factories need no new methods and return unsupported from the
extension unless they opt in. The existing `createAnimatedSession(AnimatedShaderEffect, ...)`
and standalone modifier declarations are unchanged.

Refresh captures each runtime child's source as an immutable string before invoking its uniform providers, resolves all children before native creation and copies numeric uniform arrays. Later providers cannot change an earlier captured source.
Images remain borrowed and must not be concurrently mutated. Each refresh builds immutable native
snapshots, including fresh Android color state, before replacing the whole chain. A bad child,
source, value or size retains the previous successful whole filter; there is no partial render.
The session closes its retained state on disposal; Compose wrappers retained by a caller continue
to own native references and are released by Skiko's managed lifetime. Temporary Skia composition
wrappers close after native retention. This correctness-first path allocates filter state each
refresh (non-color Android snapshots reuse the source cache; color snapshots, including static children, construct and may compile fresh shaders); no throughput or native
memory-stabilization claim is made. Playback position survives reorder, replacement and resize. A nonpositive Compose target size clears the displayed filter until dimensions become positive; invisible consumers retain it. Manual session refresh with invalid dimensions returns failure and retains its previous snapshot.

### Source-compatible callback overloads

The original `shaderEffect(effect, factory, knownSize, callback)` and result overload remain
visible and keep positional callbacks. The live-input overload requires explicit `frameDataKey`
(e.g. `frameDataKey = revision`); trailing and named callbacks still use the original overload
unless that key is supplied. This avoids silently treating a typed callback as an `Any?` key.

## Live textures and audio { #live-textures-and-audio-in-development }

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

The helper closes the factory automatically. Do not close the process-wide default factory. For manually created factories, close them when done. The default cache size is 50 entries and must be positive. Android animated sessions own a separate shader; Skia sessions own a builder that retains the shared compiled effect. In the current implementation, closing a Skia session releases that builder, and cache eviction or clearing releases the cache's reference without invalidating an active session. Do not refresh a closed session.

## Size and first render

By default, shader dimensions come from `onSizeChanged`, so the initial render may precede the effect. If the content size is already known, pass `knownSize = IntSize(widthPx, heightPx)`. These are **pixels**, not dp; update the supplied size when the content changes. The modifier still creates the render effect asynchronously after composition, so this is not a guarantee of a shader on the first displayed frame.

## Procedural backgrounds { #procedural-backgrounds-in-development }

A procedural background generates its own pixels behind a
composable's foreground. It draws in an otherwise empty Box when the Box has a nonzero size;
it needs no opaque placeholder child. `shaderEffect` retains its content-filtering behavior.
A background does not receive that content and does not capture surrounding UI.

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram
import com.debanshu.shaderlab.shaderx.authoring.proceduralBackground
import com.debanshu.shaderlab.shaderx.compose.shaderBackground

@Composable
fun GeneratedBackground() {
    val background = remember {
        ShaderDefinition(
            id = "generated_background",
            program = ShaderProgram(
                source = """
                    uniform float2 resolution;
                    half4 main(float2 p) {
                        float red = p.x / resolution.x;
                        return half4(red * 0.5, 0.0, 0.25, 0.5);
                    }
                """.trimIndent(),
                bindings = listOf(ShaderBinding.resolution()),
            ),
        ).proceduralBackground()
    }
    Box(Modifier.size(160.dp).shaderBackground(background))
}
```

Declare a source-free `ShaderDefinition` and opt it into the background workflow. Keep the
program/definition outside frame work and reuse immutable parameter values. Bind `resolution`
when the shader needs its actual drawing dimensions. Coordinates and resolution are physical
pixels with origin at the local top-left, not dp or normalized UV. Compose resolves dp through
the host density before drawing; convert any dp-based shader values explicitly. Explicit image
inputs sample image pixels and remain caller-owned.

The modifier fills its own draw bounds before drawing unchanged foreground content. Transparent
shader output reveals whatever is behind the composable; return premultiplied RGB for translucent
pixels. Drawing is clipped to its rectangular bounds; it adds no shape clipping. Put `.clip(shape)` before `.shaderBackground(...)` to clip both
background and children. `.padding(16.dp).shaderBackground(...)` excludes that outer padding;
`.shaderBackground(...).padding(16.dp)` includes the padded area. Changing density or measured
size refreshes the pixel dimensions, without resetting an externally retained playback clock.
Normal Compose layout constraints still determine whether an empty Box occupies any space.

Supply `playback: ShaderPlayback` for time bindings, `frameProvider: ShaderFrameProvider`
exactly when frame bindings are declared, and an explicit `inputRevision` after publishing complete
live inputs. Missing playback/provider requirements report a failure. Paused, unchanged input needs no continuous shader refresh. Native values belong to
each background consumer; sharing a definition does not share a mutable native shader. Repeated
`proceduralBackground()` wrappers for the same definition compare by definition identity, so
recreating that wrapper alone does not refresh a paused instance. Factory and frame-provider
replacement uses reference identity, even if those objects override equality. Removing
the modifier closes its owned Skia snapshot while the caller retains factory, playback and image
ownership. Host lifecycle/accessibility policy follows [controlled playback](#controlled-playback-in-development).

Creation or refresh failure replaces the whole background with the supplied Compose `Brush`, or
transparent output when no fallback is supplied, and reports the error through `onError`.
The optional `onReady` callback reports native snapshot creation for the current candidate; it
does not certify GPU presentation. Unsupported factory/platform routes fail before uniform or frame-provider execution. Foreground
children remain visible. A failed source or missing required input never becomes an
implicit content-filtered or backdrop effect. A successful capability report still does not
validate source, input values, allocation or rendered output.

Skia reuses the factory's compiled program cache and creates a separate builder/snapshot per
consumer. Android creates a fresh runtime shader for each snapshot so structural colors are set
once; animated refresh may therefore compile each frame. This path has no GPU performance or
native-memory stabilization guarantee. The default factory remains caller/process-owned.

General ShaderX brushes for shapes/text are deferred. Compose's ordinary gradient/image brushes
remain suitable for those jobs. The [background validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx08-background-and-brush.json)
explains the remaining shape/text lifetime and invalidation gates.

### Explicit numerical and nested live revisions

With controlled playback, retained providers may declare `frameData` and `frameNested`. Supply immutable
`Rgba8Data` or `ShaderNode` snapshots and advance `inputRevision` after publishing a complete update.
An unchanged paused revision does not refresh; an actual data refresh currently binds a fresh
input. Child snapshots freeze their own explicit time and dimensions. Read an independent
`ShaderPlayback.positionSeconds` when creating a child snapshot if it needs a separate clock;
parent playback never advances it implicitly. Structural values/colors still trigger owned
replacement. See the [input and ownership contract](custom-effects.md#numerical-data-and-nested-inputs-in-development).

For manual rendering, assign the `RenderEffect` returned by successful `refresh()` calls.
Legacy native sessions expose their initial filter through `currentRenderEffect`; definition-backed
sessions expose their latest successful snapshot.

Closed native and definition sessions reject further refresh and `currentRenderEffect` access,
and drop their owned render-snapshot/configuration references. Save a returned `RenderEffect`
before closing if the caller needs that immutable output afterward; it retains its native
input dependencies independently. This is reference ownership cleanup, not a native-memory
stabilization or collection-timing guarantee.


### Android window validation

The independent validation Activity explicitly maps `onPause`/`onResume` to the playback owner's
`active` input. Its window pixel tests exercise paused/live revisions, structural replacements,
resize, failure recovery and session disposal, plus source-free background fallback with unchanged
foreground. Android HWUI `RenderNode` tests and Skia raster tests alone do not establish these
Compose graphics-layer behaviors. See the [window harness commands](contributing.md#actual-android-compose-window-validation)
and the [integration validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx12-final-validation.json). The bounded
tiny-rotation invalidation mechanism is retained; no zero-compilation-per-frame or GPU timing
promise follows from passing correctness pixels.

### README motion recipes

The README's liquid-button capture uses `WaveEffect(amplitude = 5f, frequency = 9f)` with the retained
`rememberShaderPlayback(active = active, reducedMotion = reducedMotion)` shown above. Amplitude
is physical pixels; frequency is spatial, not speed. The original
[foil and scan recipes](custom-effects.md#foil-and-scan-recipes) use the same controlled
modifier with time bindings. An opaque Canvas supplies the membership surface; white text
supplies the glyph mask for the type specimen. Image and surface layers are explicitly clipped
to their intended frames so filter output cannot paint over neighboring UI.
The [noise recipes](custom-effects.md#noise-recipes) contrast a static `shaderEffect` stipple
filter with a time-bound `controlledShaderEffect` surface. Both keep editorial copy outside the
clipped artwork layer; only the layered noise needs playback.
The [applied gallery record](effects.md#applied-ui-recipes) gives capture dimensions, full-period
sampling, GIF timing and platform limits. These previews were rendered through actual Compose
modifiers in a disposable JVM scene; the existing sample apps were not changed for these captures.
