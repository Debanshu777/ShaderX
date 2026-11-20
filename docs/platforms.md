---
title: Android, iOS, desktop, and WebAssembly support
description: Check ShaderX platform targets, Android API requirements, Skia composition limits, browser requirements, and offline image-processing support before integrating.
---

# Platform support

ShaderX shares effect definitions in Kotlin and uses a platform backend to render them. The matrix below describes the documented API. For older artifacts and the next release, see [release status](index.md#releases-and-development-docs) and the [migration guide](migration.md).

[Custom definitions](custom-effects.md) use these existing backends. Android generated
colors remain structural: static color effects and changed animated configurations get fresh
RuntimeShader instances, trading shared-cache reuse for conservative color-update ownership.
Skia shares compiled source with independent builders. Pixel 9/API 37 tests verify translucent
`layout(color)` output in explicit FP16 sRGB, linear-sRGB and Display-P3 destinations; this is
device-specific correctness evidence. New color tests need API 34+ HardwareBufferRenderer and
skip unsupported FP16 targets. Native blur works from API 31; AGSL rendering still requires API 33.

| Capability | Android | iOS | JVM desktop | WebAssembly |
| --- | --- | --- | --- | --- |
| Kotlin target | Android | `iosArm64`, `iosSimulatorArm64` | `jvm` | `wasmJs` |
| Backend | AGSL `RuntimeShader` | Skia `RuntimeEffect` | Skia `RuntimeEffect` | Skia `RuntimeEffect` |
| Single runtime shader | Yes, API 33+ | Yes | Yes | Yes |
| Native blur | API 31+ | Yes | Yes | Yes |
| Multiple effects in a `CompositeEffect` | Yes | Yes | Yes | Yes |
| Offline `ImageProcessor` | Currently fails; see below | Single effects | Single effects | Single effects |
| Ordinary session animation and texture uniforms | Yes | Yes | Yes | Yes |
| Explicit source-free child snapshots | API 33+ | Yes | Yes | Yes |
| Numerical RGBA8 inputs | Native raw + 64-texel uniform sampler | 64-texel uniform sampler | 64-texel uniform sampler | 64-texel uniform sampler |

“Yes” describes an implemented backend, not a guarantee for every device or arbitrary shader source. Handle compilation and processing failures in your app.

## Operation queries { #operation-queries-in-development }

`factory.querySupport(...)` reports a specific operation and its known effect structure before rendering. Query the factory that will perform the operation:

```kotlin
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderInput
import com.debanshu.shaderlab.shaderx.factory.ShaderOperation
import com.debanshu.shaderlab.shaderx.factory.ShaderSupport
import com.debanshu.shaderlab.shaderx.factory.querySupport

fun waveSupport(factory: ShaderFactory): ShaderSupport = factory.querySupport(
    operation = ShaderOperation.ANIMATED_SESSION,
    effect = WaveEffect(),
    inputs = setOf(ShaderInput.IMAGE_TEXTURE),
)
```

A `SUPPORTED` result means a backend route exists for the known and supplied requirements. It does not validate arbitrary source, uniform names or values, image bytes, target dimensions, available memory, or device-specific rendering. Invalid source can still return `CompilationError`. Queries do not compile shaders, inspect source, populate caches, allocate graphics resources, or call uniform/live-data providers.

| Operation or requirement | Built-in Android factory | Built-in Skia factory |
| --- | --- | --- |
| `CONTENT_RENDER`: runtime effect | API 33+ | Supported |
| `CONTENT_RENDER`: native blur | API 31+ | Supported |
| `CONTENT_RENDER`: single-child composite | API 31+ for blur; 33+ for runtime child | Supported if its child is supported |
| `CONTENT_RENDER`: multiple children | API 31+ for blur-only; 33+ if any runtime child | Supported; every child must be supported |
| `ANIMATED_SESSION`: standalone animated effect | API 33+ | Supported |
| `ANIMATED_SESSION`: composite via optional pipeline session | API 33+ | Supported |
| `ANIMATED_SESSION`: static effect | Unsupported: `ANIMATED_EFFECT_REQUIRED` | Unsupported: `ANIMATED_EFFECT_REQUIRED` |
| `IMAGE_EXPORT`: runtime effect or native blur | Unsupported: `ANDROID_EXPORT_UNAVAILABLE` | Supported |
| `IMAGE_EXPORT`: any composite | Unsupported: `ANDROID_EXPORT_UNAVAILABLE` | Unsupported: `COMPOSITE_EXPORT_UNAVAILABLE` |
| `BACKGROUND_RENDER`, no content effect | API 33+; source-free background route | Supported; source-free background route |
| `BACKGROUND_RENDER`, supplied content effect | Unsupported: `BACKGROUND_UNAVAILABLE` | Unsupported: `BACKGROUND_UNAVAILABLE` |
| `IMAGE_TEXTURE` input | Existing color-image binding, API 33+ | Existing color-image binding |
| `RAW_DATA_TEXTURE` input | API 33+ native RGBA8 data | Unsupported: `RAW_DATA_UNAVAILABLE` |
| `NESTED_SHADER` input | API 33+ source-free snapshots | Supported; source-free snapshots |
| Unrecognized `NativeEffect` | Unsupported: `NATIVE_EFFECT_UNAVAILABLE` | Unsupported: `NATIVE_EFFECT_UNAVAILABLE` |

API 31–32 runtime/input/background/session requests report `ANDROID_API_33_REQUIRED`; below 31 the report is `ANDROID_API_31_REQUIRED`. Blur and blur-only content chains work from 31. Mixed chains fail as a whole before any child renders. `isSupported()` means some rendering is available; query the actual operation/effect. For requests with several unmet requirements, the report identifies the first applicable reason rather than every possible issue. `effect = null` asks about the operation in general; supply the actual effect to check child structure and native implementation. A supported static composite receives no automatic animation clock. For composites, `ANIMATED_SESSION` reports the optional `createPipelineSession` route; the existing `createAnimatedSession` still accepts only `AnimatedShaderEffect`. A third-party factory must opt into `ShaderPipelineProvider` separately to offer pipeline sessions.

For the background route, use `factory.queryBackgroundSupport(background, inputs)` or query
`BACKGROUND_RENDER` with `effect = null` and the required `inputs`. The additive helper accepts
the explicit background declaration and forwards a metadata-only route query. A non-null `ShaderEffect` represents content filtering and is rejected for background
rendering. Opt a source-free `ShaderDefinition` into `proceduralBackground()` separately; the
capability query does not inspect or certify its source. The actual modifier also requires the
built-in factory route; an arbitrary factory wrapper is not a background implementation merely
because it forwards capability metadata. Rendering preflight rejects an unavailable platform or
factory route before invoking any uniform/frame provider.

`inputs` declares additional semantics the caller needs. `IMAGE_TEXTURE` covers the current color-image `TextureUniform` path; it does not establish exact numeric sampling. ShaderX supports bounded source-free `ShaderNode` child snapshots and an explicit RGBA8 `DataUniform` contract. Android uses `setInputBuffer`; Skiko 0.9.37.4 has no native raw-image shader API. Queries do not infer hidden custom requirements by executing providers or parsing source; omitted requirements remain unchecked.

`ShaderSupport` includes `status`, a stable `reason`, a display `message`, and an optional `minimumRequirement` when a known alternative exists. Display the message and use the reason for application decisions. `isSupported()` remains the legacy Boolean for general on-screen availability; it says nothing about export, chaining, or a particular source.

### Third-party factories

Capability reporting is the separate, optional `ShaderCapabilityProvider` contract; `ShaderFactory` has no new required members. An existing third-party factory, including a wrapper around a built-in factory, reports `UNKNOWN` with `CAPABILITIES_NOT_PROVIDED` unless it deliberately implements the provider. Its `isSupported()` Boolean is not used to invent an operation result. Continue by attempting the operation and handling its `ShaderResult`, or use application knowledge. A provider wrapper must report its own supported operations rather than forward capabilities for routes it changes. Skia's built-in `ImageProcessor` still requires the concrete factory returned by `ShaderFactory.create()`; a wrapper's content-render support does not make it compatible with that processor.

See [explicit static fallback](compose.md#choose-an-explicit-static-fallback) for preserving content, selecting a replacement, or retaining a failure.

## Android requirements

The documented library requires **API 31**: native blur and blur-only chains use API 31 RenderEffect; AGSL, images/data/nested inputs, backgrounds and sessions require API 33. The API 31 factory has no RuntimeShader fields/signatures or initializers; the default creation route and background helper enter API 33 implementations only after a version guard. No manifest override is needed. Static PreserveContent returns successful null with the original PlatformNotSupported cause; Substitute attempts one whole replacement (such as blur). Android software image export remains ProcessingError on every API.

| Independent requirement | Documented requirement |
| --- | --- |
| Runtime minSdk | 31; API 23 dependency floor is not a tested ShaderX runtime |
| AAR minCompileSdk | 35; produced from library compileSdk 35 |
| Transitive AAR minAGP | AndroidX UI/graphics/foundation 1.10.5 declare 8.6.0; not an independently tested minimum plugin version |
| ShaderX Android classfiles | Java 17 (major 61) |
| Contributor JDK | 21; repository AGP/Gradle/catalog edits preserved |

Compose Multiplatform 1.10.3 resolves Android UI graphics to **AndroidX ui-graphics-android 1.10.5**, not AndroidX 1.10.3. Its AAR declares minSdk 23, minCompileSdk 35, minAGP 8.6.0 and Java 8 bytecode. These dependency facts do not prove ShaderX works at API 23. Separate Android/KMP compileSdk-35 Java-17 consumers, an optimized release app, API 31 software HWUI and current Android regressions provide the bounded evidence. API 32, physical old devices, independent minimum-AGP builds and Compose lifecycle proof remain separate gates. See the [Android adoption validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx11-android-adoption.json) for exact evidence.

Android's offline `ImageProcessor` remains unsupported. The implementation returns `ProcessingError` immediately, without decoding the image or compiling its shader. This does not affect on-screen rendering through Compose.

Android supports ordered chains such as `GrayscaleEffect() + VignetteEffect()`. The current offline image processor creates a software bitmap canvas; that canvas cannot render the hardware `RenderNode`. It returns `ShaderError.ProcessingError`. Use `Modifier.shaderEffect` for on-screen rendering instead.

## iOS and desktop

The library builds for physical arm64 iOS devices and arm64 iOS simulators. It does not currently declare `iosX64`. Use the deployment target required by your Compose Multiplatform app and Xcode project; this repository does not establish a separately tested minimum iOS version.

Desktop uses the JVM target on Windows, macOS, and Linux. Use JDK 21 for this repository. This is not a Kotlin/Native macOS, Linux, or Windows library.

Skia factories and processors are not thread-safe. Scope a factory to a Compose window or a single processing thread. For background exports, create a separate factory on the worker and close it there after processing. If sharing a factory across threads, serialize all factory, processor, and session operations, including cache clearing and disposal; synchronizing only cache lookups is insufficient.

In the current implementation, Skia cache eviction closes compiled-effect wrappers immediately, and failed compilation leaves existing cache entries intact. Sessions retain their own native references until closed. Texture binding releases temporary image and shader wrappers after the builder retains the child shader, limiting native memory waiting for garbage collection during live updates.

## WebAssembly

Use a browser with support for the Wasm features required by Compose Multiplatform, including Wasm GC. There is no standalone `js` target. See [Kotlin's browser compatibility documentation](https://kotlinlang.org/docs/wasm-overview.html#browser-support) for current browser requirements.

The ShaderLab microphone demo requires a secure context (HTTPS or localhost) and browser permission. Microphone capture is sample code, not part of the published shader library.

## Composition limitations

Native Skia filter composition applies children left to right and fails the whole request if any required child fails. Skia's offline processor still rejects `CompositeEffect` altogether, including one child; use a single effect directly for offline processing.

`shaderEffect` renders composites as static snapshots. For animated/live chains, use the controlled composite overload and optional pipeline sessions with one shared timestamp. See [Compose](compose.md#shared-clock-pipelines-in-development). JVM raster, Chrome/Wasm and iOS simulator raster validate the bounded filter cases; these are not physical iOS GPU or general spatial-bounds guarantees. Pixel 9 HWUI checks are recorded separately in the [pipeline validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx05-portable-composition.json).

See [Compose error handling](compose.md#handle-rendering-errors) and [image processing](image-processing.md) for examples.


## External image and backdrop integration

The [Coil 3.4.0 and Haze 1.7.2 recipes](ecosystem.md) are consumer-only examples against the
checkout's existing Compose/Skiko graph. ShaderX does not inherit Haze's broader Android
fallback matrix: runtime image/source filters require API 33+. SurfaceView and external platform
surfaces are not captured by this Compose source-layer recipe. The camera example uses an
embedded preview path; camera permission, hardware output and live performance need their own
device checks. Exact compiled and rendered targets are recorded in
[ecosystem validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx14-ecosystem-integration.json).

## Validation coverage

The [consumer and ABI gates](contributing.md#consumer-and-abi-validation) cover public API
compilation separately from rendering. JVM tests render through Skia's raster backend; Android
host tests do not execute AGSL. The opt-in Android device suite renders factory filters through
HWUI with pixel readback on API 33+. Record whether a run used physical hardware or an emulator.
A browser pass establishes Wasm/Skia behavior only. Native compile/ABI checks do not establish
iOS concurrent rendering safety or resource stabilization; those require native runtime evidence.

## Procedural background adapters { #procedural-background-adapters-in-development }

ShaderX's source-free backgrounds use the existing Android AGSL and Skia backends,
with independently owned native state per modifier. Android requires API 33+. They do not use
Android's unsupported offline ImageProcessor route and do not capture a backdrop. The same
explicit color-managed image-binding requirements apply as for custom effects.

Compose 1.10.3's resolved desktop, Wasm and UIKit graphics sources still alias their shader type
to Skiko's shader. That is an implementation detail: no ShaderX public contract exposes a raw
Skia/native wrapper. The audit uses the resolved versioned sources, not a dependency upgrade or
Compose 1.11's adapter assumptions. General shape/text brush support remains deferred; see
[background validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx08-background-and-brush.json).


## Color and artistic collection { #color-and-artistic-collection-in-development }

The collection uses static content filters through the existing AGSL Android
API 33+ and Skia iOS/JVM/Wasm paths. A single filter can use Skia ImageProcessor; ordered
on-screen chains use the on-screen composition route. Android offline image processing and
Skia offline composite export retain their documented limitations.

Color arithmetic uses destination working components after unpremultiplication, with no
implicit transfer-function conversion. sRGB and linear-sRGB destinations are distinct color
math cases. Transparent input recovers zero straight RGB, and output is premultiplied after
clamping. Matrix alpha changes are explicit; other collection filters preserve source alpha.
`cellSize` is physical pixels on every target. See the [effect equations](effects.md#color-math-and-transparent-edges)
and the [collection validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx09-effect-collection.json) for the actual rendered environments and
remaining hardware/performance gates. A compile or browser preview does not establish physical
Android or iOS rendering behavior.

## Numerical and nested input backend gates

Android numerical inputs use
[`RuntimeShader.setInputBuffer`](https://developer.android.com/reference/android/graphics/RuntimeShader#setInputBuffer(java.lang.String,android.graphics.BitmapShader))
with nonpremultiplied RGBA8 bytes and explicit nearest/linear filtering. This API bypasses color
conversion and alpha premultiplication for data sampling. Final shader output still follows
normal layer/destination color conversion; a content effect may use an intermediate sRGB layer.
Direct source-free FP16 readback tests distinguish input sampling from that final conversion.
Ordinary `TextureUniform` remains a
color-managed image with its existing signatures and behavior.

The resolved Skiko **0.9.37.4**
[`Image` API](https://raw.githubusercontent.com/JetBrains/skiko/v0.9.37.4/skiko/src/commonMain/kotlin/org/jetbrains/skia/Image.kt)
exposes `makeShader`, not a native raw-image equivalent; ShaderX does not substitute it for data.
Bounded child snapshots use `RuntimeShaderBuilder.child`/`makeShader` on Skia and independently
owned `RuntimeShader` inputs on Android. Source-free backgrounds and content filtering use the
same explicit input rules; neither captures backdrop content. Skia supports image export with
child snapshots; Android image export remains unavailable.

Queries inspect declarative input metadata without calling providers or allocating native
resources. Manual runtime implementations must declare hidden input requirements explicitly.
Known unsupported declarations reject before compilation (and before decode on the export route).
Manual ImageProcessor implementations with default output dimensions still decode the input to
learn those dimensions before their uniform provider can be checked; native shader compilation
and binding happen after full-tree validation. This limited escape-path behavior is not a
provider-free metadata query. See the [data-input validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx10-data-and-shader-inputs.json) for
actual tested targets, procedural fallback experiment and remaining physical-device gates.

`ShaderNode.rgba8Sampler` provides a separate portable procedural numerical sampler limited to
64 RGBA8 texels / 1024 float-uniform bytes. It uses ordinary uniforms and explicit interpolation/
wrapping math. It is a nested child contract, not native raw-image support or an implicit
replacement for `DataUniform`. Large data remains unavailable on the Skia raw route. Source
compilation and rendered backend evidence remain distinct; see SX10 results for the exact gates.

## Bounded still-image export { #bounded-still-image-export-in-development }

The optional processor query `queryImageExportSupport(effect)` describes the additive bounded PNG
route, while factory `IMAGE_EXPORT` still describes legacy `process`. Skia JVM, iOS and Wasm use
sRGB raster output with explicit pixel dimensions; native blur and single runtime effects are
supported, offline chains are not. Wasm suspend dispatch still runs on the browser thread. Android
API 31 blur and API 33 runtime readback have an internal hardware probe, but neither public export
route is enabled while the graphics-resource gate is unresolved. On-screen backgrounds and native
blur support remain independent. See the [output and worker contracts](image-processing.md#bounded-png-export-in-development).
