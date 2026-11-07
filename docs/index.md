---
title: GPU shader effects for Compose Multiplatform
description: Add GPU shader effects to Jetpack Compose and Compose Multiplatform with ShaderX. Start with Kotlin examples for Android, iOS, desktop, and WebAssembly.
---

# ShaderX: GPU shader effects for Compose Multiplatform

![ShaderX — a blue halftone sunflower, GPU shader effects for Kotlin and Compose Multiplatform](assets/shaderx-banner.png){ .shaderx-banner }

ShaderX is a Kotlin Multiplatform library for applying GPU shader effects to Compose UI. Use the same Kotlin API for Android's AGSL backend and the Skia backend on iOS, JVM desktop, and WebAssembly.

Start with grayscale, blur, gradients, or pixelation, then write a custom AGSL/SkSL shader when you need a different effect.

[Get started](getting-started.md){ .md-button .md-button--primary }
[Explore the effects](effects.md){ .md-button }

## See ShaderX in action

![Animated ShaderLab gallery running in a browser with live effect controls](https://raw.githubusercontent.com/Debanshu777/ShaderX/main/assets/shader_lab_web.gif){ .shaderx-demo }

The [sample gallery](samples.md) includes Android and WebAssembly ShaderLab recordings, a live ASCII camera, and a scroll-driven movie carousel, with source code and run commands for each.

## Start building

| I want to… | Start here |
| --- | --- |
| Add ShaderX to an app | [Installation and quickstart](getting-started.md) |
| Check my target platforms | [Platform support](platforms.md) |
| Choose an effect | [Effect catalog](effects.md) |
| Animate effects or handle errors | [Compose integration](compose.md) |
| Write an AGSL/SkSL shader | [Custom effects](custom-effects.md) |
| Process image bytes | [Image processing](image-processing.md) |
| Run an example | [Sample applications](samples.md) |
| Fix a setup problem | [Troubleshooting](troubleshooting.md) |
| Contribute a change | [Contributor guide](contributing.md) |

## Releases and development docs

The published Maven Central version is **0.1.3**. The repository's `main` branch is developing **0.1.4**. The installation and static quickstart examples work with 0.1.3; session animation, texture inputs, and audio effects are marked as development features throughout these guides.

Check the [release notes](https://github.com/Debanshu777/ShaderX/releases) before using a newer API. Do not change your dependency to `0.1.4` until that version is published, or [build the development version locally](contributing.md#try-the-library-in-another-project).

## Source and API reference

ShaderX is [open source on GitHub](https://github.com/Debanshu777/ShaderX) under the [Apache 2.0 license](https://github.com/Debanshu777/ShaderX/blob/main/LICENSE). Kotlin packages start with `com.debanshu.shaderlab.shaderx`; the Maven coordinates are `io.github.debanshu777:shaderx`.

Generate the full KDoc reference from your checkout:

```shell
./gradlew :shaderx:generateDocs
```

Open `shaderx/build/dokka/html/index.html`. See [contributor setup](contributing.md) for the required toolchain.
