<p align="center">
  <img src="docs/assets/shaderx-banner.png" width="100%" alt="ShaderX — GPU shader effects for Kotlin and Compose Multiplatform, illustrated by a blue halftone sunflower" />
</p>

# ShaderX — GPU shader effects for Compose Multiplatform

ShaderX is a **Kotlin Multiplatform shader library** for **Jetpack Compose and Compose Multiplatform**. Apply grayscale, blur, gradients, pixelation, and custom AGSL/SkSL effects to your UI on **Android, iOS, desktop, and WebAssembly** with `Modifier.shaderEffect`.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.debanshu777/shaderx?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.debanshu777/shaderx)
[![CI](https://github.com/Debanshu777/ShaderX/actions/workflows/ci.yml/badge.svg)](https://github.com/Debanshu777/ShaderX/actions/workflows/ci.yml)
[![Apache 2.0 license](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

[Documentation](https://debanshu777.github.io/ShaderX/) · [Quickstart](#quickstart) · [Effects](https://debanshu777.github.io/ShaderX/effects/) · [Samples](https://debanshu777.github.io/ShaderX/samples/) · [Contributing](CONTRIBUTING.md)

## See it in motion

Explore live effect controls, transform a camera preview into ASCII art, or make a movie carousel respond to every scroll. These recordings show the sample apps built with ShaderX.

<table>
  <tr>
    <th width="50%">ShaderLab · Android</th>
    <th width="50%">ShaderLab · WebAssembly</th>
  </tr>
  <tr>
    <td align="center"><img src="assets/shader_lab.gif" width="320" alt="Animated Android ShaderLab demo with adjustable GPU shader effects" /></td>
    <td align="center"><img src="assets/shader_lab_web.gif" width="420" alt="Animated ShaderLab browser demo running Compose Multiplatform on WebAssembly" /></td>
  </tr>
  <tr>
    <td align="center">An effect playground with live parameter controls.<br /><a href="samples/ShaderLab">Explore ShaderLab</a></td>
    <td align="center">The same shared Compose UI in the browser.<br /><a href="https://debanshu777.github.io/ShaderX/samples/#shaderlab">Run the web sample</a></td>
  </tr>
  <tr>
    <th>ASCIICamera</th>
    <th>VerticalCarousel</th>
  </tr>
  <tr>
    <td align="center"><img src="assets/ASCIICamera.gif" width="320" alt="Animated CameraX preview transformed into ASCII characters with a custom ShaderX shader" /></td>
    <td align="center"><img src="assets/vertical_carousal.gif" width="320" alt="Animated movie carousel applying shader effects as cards scroll" /></td>
  </tr>
  <tr>
    <td align="center">Custom shaders on a live Android camera feed.<br /><a href="samples/ASCIICamera">Explore ASCIICamera</a></td>
    <td align="center">Scroll-driven effects on movie cards.<br /><a href="samples/VerticalCarousel">Explore VerticalCarousel</a></td>
  </tr>
</table>

[Run the samples →](https://debanshu777.github.io/ShaderX/samples/)

## Installation

Add Maven Central to your repositories in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Add ShaderX to your shared module's `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.debanshu777:shaderx:0.1.3")
        }
    }
}
```

For an Android-only Compose app, use the same dependency inside `dependencies { ... }`.
Use **JDK 21**, **Kotlin 2.3.21**, and **Compose Multiplatform 1.10.3** as the starting toolchain. Android requires **minSdk 33**. See [installation and version catalogs](docs/getting-started.md).

> **Release status:** `0.1.3` is the published Maven Central release. This branch develops `0.1.4`; session-driven animation, texture uniforms, and the new audio effects are development features. Guides identify those features explicitly. The quickstart below works with `0.1.3`.

## Quickstart

This complete composable takes your app's existing `Painter`, so it works with Compose resources or an image loader without relying on a particular resource name.

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

Replace `GrayscaleEffect()` with `SepiaEffect()`, `NativeBlurEffect(radius = 8f)`, or `PixelateEffect(pixelSize = 12f)` from the same `effect.impl` package. The modifier can also transform text, cards, and other Compose content.

## Why use ShaderX?

- **Shared Kotlin API:** AGSL `RuntimeShader` on Android and Skia `RuntimeEffect` on iOS, JVM desktop, and Wasm.
- **Configurable effects:** immutable effect values, typed parameters, and reusable shader compilation caches.
- **Custom shaders:** implement the public `RuntimeShaderEffect` interface to bring your own AGSL/SkSL source.
- **Explicit failures:** handle shader compilation and platform limitations with `ShaderResult` or the modifier's `onError` callback.
- **Examples to explore:** an effect gallery, CameraX ASCII preview, and scroll-driven carousel.

## Platform support

| Platform | Kotlin target / requirement | Backend | Multiple effects with `+` |
| --- | --- | --- | --- |
| Android | API 33+ | AGSL / `RuntimeShader` | Supported |
| iOS | `iosArm64`, `iosSimulatorArm64` | Skia / `RuntimeEffect` | Unsupported |
| Desktop | JVM; Windows, macOS, Linux | Skia / `RuntimeEffect` | Unsupported |
| Web | `wasmJs`; browser with Wasm GC support | Skia / `RuntimeEffect` | Unsupported |

Multi-effect chains on Skia return `ShaderError.UnsupportedEffect`. Android's current offline `ImageProcessor` path returns a processing failure; use the Compose modifier for Android rendering. See the [platform and feature matrix](docs/platforms.md) before choosing an API.

## Built-in shader effects

| Available in | Effects |
| --- | --- |
| **0.1.3** | `GrayscaleEffect`, `SepiaEffect`, `GradientEffect`, `VignetteEffect`, `PixelateEffect`, `ChromaticAberrationEffect`, `InvertEffect`, `NativeBlurEffect`, `WaveEffect` |
| **main / 0.1.4 development** | All of the above, plus `AnimatedGradientEffect`, `PlasmaEffect`, `RippleEffect`, `VortexEffect`, `AudioVisualizerEffect`, `AudioDistortionEffect` |

The [effect catalog](docs/effects.md) covers parameters and examples. The [Compose guide](docs/compose.md) explains animation, Android effect chaining, and cache ownership.

## Samples

| Sample | What it demonstrates | Run / preview |
| --- | --- | --- |
| [ShaderLab](samples/ShaderLab) | Effect gallery, live controls, image loading, and development audio effects | [Setup](docs/samples.md#shaderlab) · [Android recording](assets/shader_lab.gif) · [Web recording](assets/shader_lab_web.gif) |
| [ASCIICamera](samples/ASCIICamera) | Custom shaders on an Android CameraX preview | [Setup](docs/samples.md#asciicamera) · [Recording](assets/ASCIICamera.gif) |
| [VerticalCarousel](samples/VerticalCarousel) | Scroll-driven shader parameters on movie cards | [Setup and TMDB token](docs/samples.md#verticalcarousel) · [Recording](assets/vertical_carousal.gif) |

Run the desktop gallery from a checkout with JDK 21 and the [Android SDK configured](docs/contributing.md#local-setup):

```shell
./gradlew :samples:ShaderLab:composeApp:run
```

## Documentation and contribution

- [Installation and quickstart](docs/getting-started.md)
- [Platform support and limitations](docs/platforms.md)
- [Custom AGSL/SkSL shaders](docs/custom-effects.md)
- [Offline image processing](docs/image-processing.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Contributor setup and test commands](docs/contributing.md)
- [Release history](CHANGELOG.md)

Bug reports, documentation fixes, and new effects are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md), then [open an issue](https://github.com/Debanshu777/ShaderX/issues/new/choose) with a reproducible example.

## License

ShaderX is released under the [Apache License 2.0](LICENSE).
