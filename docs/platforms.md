---
title: Android, iOS, desktop, and WebAssembly support
description: Check ShaderX platform targets, Android API requirements, Skia composition limits, browser requirements, and offline image-processing support before integrating.
---

# Platform support

ShaderX shares effect definitions in Kotlin and uses a platform backend to render them. This matrix describes published **0.1.4**, which includes the 15 built-in effects in the [catalog](effects.md).

| Capability | Android | iOS | JVM desktop | WebAssembly |
| --- | --- | --- | --- | --- |
| Kotlin target | Android | `iosArm64`, `iosSimulatorArm64` | `jvm` | `wasmJs` |
| Backend | AGSL `RuntimeShader` | Skia `RuntimeEffect` | Skia `RuntimeEffect` | Skia `RuntimeEffect` |
| Single runtime shader | Yes, API 33+ | Yes | Yes | Yes |
| Native blur | Yes, API 33+ through ShaderX | Yes | Yes | Yes |
| Multiple effects in a `CompositeEffect` | Yes | Unsupported | Unsupported | Unsupported |
| Offline `ImageProcessor` | Currently fails; see below | Single effects | Single effects | Single effects |
| Session animation and texture uniforms | Yes, API 33+ | Yes | Yes | Yes |

“Yes” describes an implemented backend, not a guarantee for every device or arbitrary shader source. Handle compilation and processing failures in your app.

## Android

The library's minimum Android SDK is **33 (Android 13)**. Although Android introduced some `RenderEffect` APIs earlier, ShaderX's factory requires API 33 for its rendering paths, including blur and composite effects.

Android's offline `ImageProcessor` remains unsupported: a software bitmap canvas cannot render a hardware `RenderNode`. In 0.1.4, it returns `ShaderError.ProcessingError` immediately, without decoding the image, compiling its shader, or allocating a destination bitmap.

On-screen Compose rendering supports ordered chains such as `GrayscaleEffect() + VignetteEffect()`. Use `Modifier.shaderEffect` for this rendering path.

## iOS and desktop

The library builds for physical arm64 iOS devices and arm64 iOS simulators. It does not currently declare `iosX64`. Use the deployment target required by your Compose Multiplatform app and Xcode project; this repository does not establish a separately tested minimum iOS version.

Desktop uses the JVM target on Windows, macOS, and Linux. Use JDK 21 for this repository. This is not a Kotlin/Native macOS, Linux, or Windows library.

Skia factories and processors are not thread-safe. Scope a factory to a Compose window or a single processing thread. For background exports, create a separate factory on the worker and close it there after processing. If sharing a factory across threads, serialize all factory, processor, and session operations, including cache clearing and disposal; synchronizing only cache lookups is insufficient.

In 0.1.4, Skia cache eviction closes compiled-effect wrappers immediately, and failed compilation leaves existing cache entries intact. Sessions retain their own native references until closed. Texture binding releases temporary image and shader wrappers after the builder retains the child shader, limiting native memory waiting for garbage collection during live updates.

## WebAssembly

Use a browser with support for the Wasm features required by Compose Multiplatform, including Wasm GC. There is no standalone `js` target. See [Kotlin's browser compatibility documentation](https://kotlinlang.org/docs/wasm-overview.html#browser-support) for current browser requirements.

The ShaderLab microphone demo requires a secure context (HTTPS or localhost) and browser permission. Microphone capture is sample code, not part of the published shader library.

## Composition limitations

On Skia platforms, a `CompositeEffect` with multiple children returns `ShaderError.UnsupportedEffect`; it does not silently render only the last effect. Single-child composites can render through the factory, but Skia's offline processor rejects `CompositeEffect` altogether. Pass a single effect directly for offline processing.

Animated children inside a `CompositeEffect` do not gain an automatic session clock: the modifier's session path applies to `AnimatedShaderEffect` itself. Use a standalone animated effect for the automatic animation API.

See [Compose error handling](compose.md#handle-rendering-errors) and [image processing](image-processing.md) for examples.
