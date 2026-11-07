---
title: Install ShaderX and apply your first Compose shader
description: Install ShaderX from Maven Central in Kotlin Multiplatform or Android, configure a Gradle version catalog, and apply a grayscale shader with a complete Kotlin example.
---

# Installation and quickstart

These examples use the published **0.1.3** release. See [release status](index.md#releases-and-development-docs) for APIs that currently require a checkout of `main`.

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
            implementation("io.github.debanshu777:shaderx:0.1.3")
        }
    }
}
```

For an Android-only app, add it to the module's regular `dependencies` block instead:

```kotlin
dependencies {
    implementation("io.github.debanshu777:shaderx:0.1.3")
}
```

If your shared module includes unsupported targets, put ShaderX in an intermediate source set shared only by the supported targets. No ShaderX application initialization call is required.

### Version catalog

As an alternative to the literal dependency, add to `gradle/libs.versions.toml`:

```toml
[versions]
shaderx = "0.1.3"

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

## Next steps

Choose another effect in the [catalog](effects.md), learn about [animation and error handling](compose.md), or run the [ShaderLab gallery](samples.md#shaderlab).
