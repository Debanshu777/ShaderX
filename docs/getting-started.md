---
title: How to add shaders to Compose Multiplatform
description: Add GPU shaders to a Compose Multiplatform project with ShaderX. Configure Gradle, apply a shader modifier, and separate background effects from foreground content.
---

# How to add shaders to Compose Multiplatform

To add a GPU shader to your Compose Multiplatform project, add ShaderX to `commonMain`, import `shaderEffect`, and apply `Modifier.shaderEffect(...)` to the composable you want to transform. ShaderX uses Android's AGSL runtime on Android API 33+ and Skia on iOS, desktop, and WebAssembly.

The steps below start with an existing Compose project and a built-in grayscale effect. You can then implement a [custom AGSL/SkSL shader](custom-effects.md) using the same modifier. For an Android-only Jetpack Compose project, use the Android dependency block below.

This guide targets **ShaderX 0.2.0**. See [publication status](index.md#releases-and-development-docs) before resolving the dependency.

## Requirements

- An existing Jetpack Compose or Compose Multiplatform app.
- JDK 21 for repository builds. ShaderX Android artifacts target Java 17 bytecode.
- Android minSdk 31 and compileSdk 35 or newer. Native blur works on API 31–32; runtime shaders, animation sessions and procedural backgrounds require API 33. See [Android requirements](platforms.md#android-requirements) for transitive dependency and toolchain constraints.
- Kotlin 2.3.21 and Compose Multiplatform 1.10.3, matching the library build. Other version combinations are not covered by a compatibility guarantee here.
- A supported [Kotlin target](platforms.md). ShaderX publishes `wasmJs`, not a separate Kotlin/JS artifact.

## Gradle dependency

In `settings.gradle.kts`, ensure your existing repositories include Maven Central:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

In the shared module's `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.debanshu777:shaderx:0.2.0")
        }
    }
}
```

For an Android-only app, add it to the module's regular `dependencies` block instead:

```kotlin
dependencies {
    implementation("io.github.debanshu777:shaderx:0.2.0")
}
```

If your shared module includes unsupported targets, put ShaderX in an intermediate source set shared only by the supported targets. No ShaderX application initialization call is required.

### Version catalog

As an alternative to the literal dependency, add to `gradle/libs.versions.toml`:

```toml
[versions]
shaderx = "0.2.0"

[libraries]
shaderx = { module = "io.github.debanshu777:shaderx", version.ref = "shaderx" }
```

Then use `implementation(libs.shaderx)` in the same dependency block. Merge these entries into existing sections rather than duplicating the `[versions]` and `[libraries]` headers.

## Apply a grayscale effect

Place this composable in `commonMain` (or your Android app). Pass a `Painter` from your existing resource or image-loading code:

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect

@Composable
fun GrayscalePhoto(painter: Painter, modifier: Modifier = Modifier) {
    Image(
        painter = painter,
        contentDescription = "Photo with a grayscale effect",
        modifier = modifier.shaderEffect(GrayscaleEffect(intensity = 1f)),
    )
}
```

The effect transforms the content drawn by the composable. Make sure that content has a nonzero layout size. Set `intensity = 0.5f` to blend the original colors with grayscale, or pass `null` as the effect to disable it.

For animated built-ins, the same modifier owns and closes its animation session. For example, replace `GrayscaleEffect(...)` with `WaveEffect(amplitude = 16f, frequency = 9f)` from the same package. Pass the effect directly; use a [scoped factory](compose.md#scope-the-shader-cache) when a screen should release its compiled cache on disposal.

For phase-preserving pause/resume, seeking, shared time or explicit live-input revisions, use [`rememberShaderPlayback` and `controlledShaderEffect`](compose.md#controlled-playback-in-development).
The host supplies visibility/lifecycle and reduced-motion policy. This additive path keeps time
independent of native session replacement; existing `shaderEffect` timing remains unchanged.

## Add an existing image loader or backdrop

Apply ShaderX to the pixels drawn by your existing image composable. For asynchronous loading,
retain loader state and playback above loading/success/error presentation. The
[Coil and Haze guide](ecosystem.md) links complete consumer recipes with exact optional
dependency pins, source/foreground boundaries and ownership.

## Apply a shader to the background only

`shaderEffect` transforms the content inside its drawing layer. Applying it to a container also affects that container's text and child UI. To keep the foreground unchanged, draw an affected background as a separate child, then place the foreground above it:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect

@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .shaderEffect(GradientEffect(intensity = 1f))
                .background(Color.White),
        )
        content()
    }
}
```

This recipe filters an existing content layer. For source-free pixel generation, use the [dedicated procedural background modifier](compose.md#procedural-backgrounds-in-development). It works in an empty sized Box and leaves foreground children unchanged. A content-dependent effect such as `GradientEffect` cannot be converted to a source-free background by omitting its input.

Give the outer container a size through its modifier or foreground content. The white background supplies opaque pixels for `GradientEffect` to tint; an empty transparent layer has no visible pixels to transform. Keep `.shaderEffect(...)` before `.background(...)` so the background is drawn inside the affected layer. For an ordinary static gradient, Compose's built-in `Brush` is sufficient; use a shader when its pixel calculations or distortion are needed.

## Next steps

Choose another effect in the [catalog](effects.md), learn about [animation and error handling](compose.md), or run the [ShaderLab gallery](samples.md#shaderlab). Check [operation-specific capabilities](platforms.md#operation-queries-in-development) and choose an [explicit static fallback](compose.md#choose-an-explicit-static-fallback).

## Try custom authoring { #try-authoring-in-development }

Start with the [migration and onboarding guide](migration.md) for complete minimal/editable
effects, strict versus legacy semantics, controlled playback and caller-rebound saved inputs.
For changes beyond the release, [build a local artifact](contributing.md#try-the-library-in-another-project)
under a unique validation coordinate.

The local ShaderLab sample includes a custom shader workbench: open it from the gallery,
choose Warm tint and use **Run shader**. Source, typed schema fields and bindings stay editable
after failures; preview and SDK code identify the last successful rendered revision. Generated
Kotlin uses the same documented API. See [the sample journey](samples.md#author-a-custom-shader-in-development).

ShaderX also supports on-screen ordered chains on Skia. Use the [Compose pipeline guide](compose.md#chain-effects) for static chains and shared playback; offline chain export remains unsupported.
