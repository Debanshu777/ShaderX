---
title: How to add shaders to Compose Multiplatform
description: Add GPU shaders to a Compose Multiplatform project with ShaderX. Configure Gradle, apply a shader modifier, and separate background effects from foreground content.
---

# How to add shaders to Compose Multiplatform

To add a GPU shader to your Compose Multiplatform project, add ShaderX to `commonMain`, import `shaderEffect`, and apply `Modifier.shaderEffect(...)` to the composable you want to transform. ShaderX uses Android's AGSL runtime on Android API 33+ and Skia on iOS, desktop, and WebAssembly.

The steps below start with an existing Compose project and a built-in grayscale effect. You can then implement a [custom AGSL/SkSL shader](custom-effects.md) using the same modifier. For an Android-only Jetpack Compose project, use the Android dependency block below.

These examples use the published **0.1.4** release. It includes the animation sessions, texture inputs, and audio effects covered by these guides. See [release status](index.md#release-status) for version details.

## Requirements

- An existing Jetpack Compose or Compose Multiplatform app.
- JDK 21. ShaderX's Android library targets JVM 21.
- Kotlin 2.3.21 and Compose Multiplatform 1.10.3, matching the published artifact's dependencies. Other version combinations are not covered by a compatibility guarantee here.
- Android `minSdk = 33` when targeting Android.
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
            implementation("io.github.debanshu777:shaderx:0.1.4")
        }
    }
}
```

For an Android-only app, add it to the module's regular `dependencies` block instead:

```kotlin
dependencies {
    implementation("io.github.debanshu777:shaderx:0.1.4")
}
```

If your shared module includes unsupported targets, put ShaderX in an intermediate source set shared only by the supported targets. No ShaderX application initialization call is required.

### Version catalog

As an alternative to the literal dependency, add to `gradle/libs.versions.toml`:

```toml
[versions]
shaderx = "0.1.4"

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

For animated effects in 0.1.4, the same modifier owns and closes its animation session. Pass the effect directly; use a [scoped factory](compose.md#scope-the-shader-cache) when a screen should release its compiled cache on disposal.

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

Give the outer container a size through its modifier or foreground content. The white background supplies opaque pixels for `GradientEffect` to tint; an empty transparent layer has no visible pixels to transform. Keep `.shaderEffect(...)` before `.background(...)` so the background is drawn inside the affected layer. For an ordinary static gradient, Compose's built-in `Brush` is sufficient; use a shader when its pixel calculations or distortion are needed.

## Next steps

Choose another effect in the [catalog](effects.md), learn about [animation and error handling](compose.md), or run the [ShaderLab gallery](samples.md#shaderlab).
