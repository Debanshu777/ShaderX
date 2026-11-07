---
title: Troubleshoot ShaderX setup and rendering
description: Resolve ShaderX dependency, import, Android API, shader compilation, animation, Skia composition, and image-export problems with targeted checks.
---

# Troubleshooting

## Gradle cannot find ShaderX 0.1.4

The published version is **0.1.3**. The `0.1.4` version in the source build is under development. Use 0.1.3 from Maven Central or [publish the development version locally](contributing.md#try-the-library-in-another-project). Confirm `mavenCentral()` is in dependency repositories, not just plugin repositories.

## Unresolved import or missing effect

The package prefix is `com.debanshu.shaderlab.shaderx`, even though the Maven group is `io.github.debanshu777`. Import the `shaderEffect` extension from `.compose` and effects from `.effect.impl`.

`AudioVisualizerEffect`, texture uniforms, and the other [development effects](effects.md#added-on-main-014-development) are not part of 0.1.3. Custom effects in external modules should implement `RuntimeShaderEffect`; the abstract base classes require internal handlers.

## Unsupported Android SDK or JVM target

ShaderX requires Android API 33+. Set your app's `minSdk` accordingly and run on a compatible device. Use JDK 21 for Gradle. If the build selects another JDK, check your IDE's Gradle JDK, `JAVA_HOME`, and `gradle/gradle-daemon-jvm.properties`.

The contributor checkout also requires the Android compile SDK specified in `gradle/libs.versions.toml`, even for some desktop tasks, because Android modules are configured in the same build.

## No visible effect

- Confirm the composable draws content and has a nonzero size.
- Attach an `onError` callback; inspect shader compilation errors.
- Confirm the effect's intensity is not zero and the effect itself is not `null`.
- For custom source, match uniform names/types and use pixel coordinates.
- If you supply `knownSize`, keep it aligned with the actual size in pixels.

## Composite effects fail on iOS, desktop, or web

Multi-effect chains are supported only on Android. Skia reports `ShaderError.UnsupportedEffect`. Use one effect or combine its operations into a single shader. See the [platform matrix](platforms.md).

## Animation or audio does not update

On `main`, pass a standalone `AnimatedShaderEffect` directly to `shaderEffect`. Use `animate = true` to advance time. Avoid wrapping it in a composite if you expect the modifier to manage its clock.

For audio, supply a live bitmap source using `withFftDataSource`. Verify microphone permission and that the bitmap contains data with the expected number of bins. Browser microphone capture requires HTTPS or localhost. Pausing time does not pause live uniform refresh on `main`.

## Image export fails on Android

This is a known implementation limitation: Android's offline processor uses a software canvas that cannot render its hardware `RenderNode`. It returns `ProcessingError`. On-screen Compose rendering is a separate path. See [image processing](image-processing.md) for supported uses.

## Report a reproducible problem

Include the library version or commit, Kotlin/Compose versions, platform and OS/device, a minimal code sample, and the full error message. Use the [bug report form](https://github.com/Debanshu777/ShaderX/issues/new?template=bug_report.yml). Remove tokens and personal data before attaching logs or recordings.
