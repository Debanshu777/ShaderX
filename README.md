# ShaderX 🎨

A **Kotlin Multiplatform** library for GPU shader effects in **Jetpack Compose**, supporting **Android**, **iOS**, and **Desktop** (Windows, macOS, Linux). Apply stunning visual effects with a simple, type-safe API.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-1.10.0-green.svg?logo=jetpackcompose)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-33+-brightgreen.svg)](https://android-arsenal.com/api?level=33)

## 🎯 What is ShaderX?

ShaderX is a cross-platform shader effects library that brings GPU-powered visual effects to Compose Multiplatform applications. It provides an intuitive Modifier-based API for applying effects like grayscale, blur, pixelation, and wave animations to any composable. Built using modern Kotlin Multiplatform development practices, it offers native performance across Android, iOS, and Desktop platforms.

## 📱 Demo

<!-- Add screenshots/gifs here when available -->
| Android | iOS | Desktop |
|---------|-----|---------|
| Coming Soon | Coming Soon | Coming Soon |

## ✨ Key Features

- **Cross-Platform Support**
  - 🤖 Android (API 33+ with AGSL)
  - 🍎 iOS (arm64, Simulator) via Skia
  - 🖥️ Desktop (Windows, macOS, Linux) via Skia
  - 🌐 Web (WASM) via Skia

- **Rich Effects Library**
  - 🌫️ **Blur** - Hardware-accelerated Gaussian blur
  - 🎨 **Grayscale** - Convert to grayscale using luminance weights
  - 📸 **Sepia** - Apply vintage sepia tone
  - 🔲 **Pixelate** - Create retro pixelation effects
  - 🌀 **Vignette** - Darken image edges
  - 🌈 **Chromatic Aberration** - Simulate lens color separation
  - 🔄 **Invert** - Invert all colors
  - 🌊 **Wave** - Animated wave distortion
  - 🎭 **Gradient** - Custom gradient overlays

- **Developer Experience**
  - 🔒 Type-safe API with strongly typed parameters
  - ⚡ Compose Modifier extensions for easy integration
  - 🎬 Built-in animation support
  - 🛠️ Custom shader support (AGSL/SkSL)
  - ❌ Comprehensive error handling with `ShaderResult<T>`
  - 🔗 Effect composition with `CompositeEffect` (see limitations below)

## 🚀 Getting Started

### Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// build.gradle.kts
dependencies {
    implementation("io.github.debanshu777:shaderx:0.1.2")
}
```

### Quick Start

**Apply a simple effect:**

```kotlin
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect

@Composable
fun GrayscaleImage() {
    Image(
        painter = painterResource("photo.png"),
        contentDescription = null,
        modifier = Modifier.shaderEffect(GrayscaleEffect())
    )
}
```

**Use animated effects:**

```kotlin
import com.debanshu.shaderlab.shaderx.compose.rememberShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect

@Composable
fun AnimatedWaveImage() {
    val waveEffect = rememberShaderEffect(WaveEffect(amplitude = 10f))
    
    Image(
        painter = painterResource("photo.png"),
        contentDescription = null,
        modifier = Modifier.shaderEffect(waveEffect)
    )
}
```

**Adjust parameters dynamically:**

```kotlin
val effect = GrayscaleEffect(intensity = 0.5f)
val updatedEffect = effect.withParameter("intensity", 0.8f)
```

## 🎨 Built-in Effects

| Effect | Description | Animated |
|--------|-------------|----------|
| `GrayscaleEffect` | Converts to grayscale using luminance weights | ❌ |
| `SepiaEffect` | Applies vintage sepia tone | ❌ |
| `VignetteEffect` | Darkens image edges with customizable radius | ❌ |
| `NativeBlurEffect` | Hardware-accelerated Gaussian blur | ❌ |
| `PixelateEffect` | Creates retro pixelation with adjustable block size | ❌ |
| `ChromaticAberrationEffect` | Simulates lens color separation | ❌ |
| `InvertEffect` | Inverts all color channels | ❌ |
| `WaveEffect` | Animated wave distortion effect | ✅ |
| `GradientEffect` | Custom gradient overlay | ❌ |

## 🛠️ Creating Custom Effects

```kotlin
data class MyEffect(
    private val intensity: Float = 1f
) : RuntimeShaderEffect {

    override val id = "my_effect"
    override val displayName = "My Effect"

    override val shaderSource = """
        uniform shader content;
        uniform float intensity;
        
        half4 main(float2 fragCoord) {
            half4 color = content.eval(fragCoord);
            return half4(color.rgb * intensity, color.a);
        }
    """

    override val parameters = listOf(
        PercentageParameter("intensity", "Intensity", intensity)
    )

    override fun buildUniforms(width: Float, height: Float) = listOf(
        FloatUniform("intensity", intensity)
    )

    override fun withParameter(id: String, value: Float) = when (id) {
        "intensity" -> copy(intensity = value)
        else -> this
    }
}
```

## ⚠️ Known Limitations

### CompositeEffect Chaining

Effect chaining is supported on **Android (API 31+)**. On iOS, Desktop, and Web, only the last effect in a composite is applied due to platform API limitations.

### ShaderFactory Lifecycle

Each `Modifier.shaderEffect()` call creates its own factory by default, so shader caches are not shared. For better cache reuse across many composables, create a shared factory and pass it:

```kotlin
val factory = remember { ShaderFactory.create() }
Image(
    modifier = Modifier.shaderEffect(effect, factory = factory)
)
```

Use `ShaderFactory.create(maxCacheSize = 25)` for memory-constrained environments.

## ❌ Error Handling

The library uses `ShaderResult<T>` for operations that may fail:

```kotlin
val factory = ShaderFactory.create()
val result = factory.createRenderEffect(effect, width, height)

result
    .onSuccess { renderEffect ->
        // Apply the effect
    }
    .onFailure { error ->
        when (error) {
            is ShaderError.CompilationError -> log("Shader compile failed: ${error.message}")
            is ShaderError.UnsupportedEffect -> log("Effect not supported: ${error.effectId}")
            is ShaderError.PlatformNotSupported -> log("Platform limitation: ${error.message}")
            else -> log("Unknown error: ${error.message}")
        }
    }
```

## 🏗️ Architecture & Technical Stack

### **Technology Stack**

| Category | Technology | Version |
|----------|------------|---------|
| **Language** | Kotlin | 2.3.0 |
| **UI Framework** | Compose Multiplatform | 1.10.0 |
| **Build System** | Gradle (AGP) | 9.0.0 |
| **Android SDK** | Compile/Target SDK | 36 |
| **Coroutines** | kotlinx-coroutines | 1.10.2 |
| **Serialization** | kotlinx-serialization | 1.9.0 |

### **Platform-Specific Backends**

| Platform | Shader Backend | Min Version |
|----------|---------------|-------------|
| Android | AGSL (RuntimeShader) | API 33 |
| iOS | Skia | iOS 14+ |
| Desktop (JVM) | Skia | JDK 11+ |
| Web | Skia (Skiko WASM) | Modern browsers |

### **Project Structure**

```
ShaderLab/
├── 📁 shaderx/                         # Core library module
│   └── src/
│       ├── 📁 commonMain/              # Shared Kotlin code
│       │   └── kotlin/com/debanshu/shaderlab/shaderx/
│       │       ├── ShaderX.kt          # Main entry point
│       │       ├── ShaderConstants.kt  # Shared constants
│       │       ├── compose/            # Compose Modifier integration
│       │       ├── effect/             # Core effect interfaces
│       │       │   ├── ShaderEffect.kt
│       │       │   ├── RuntimeShaderEffect.kt
│       │       │   ├── AnimatedShaderEffect.kt
│       │       │   ├── NativeEffect.kt
│       │       │   ├── CompositeEffect.kt
│       │       │   └── impl/           # Built-in effect implementations
│       │       ├── effects/            # Public effect APIs
│       │       ├── factory/            # Platform-specific shader factories
│       │       ├── parameter/          # Parameter types & formatting
│       │       ├── uniform/            # Shader uniform types
│       │       └── result/             # Error handling (ShaderResult)
│       ├── 📁 androidMain/             # Android-specific (AGSL)
│       ├── 📁 iosMain/                 # iOS-specific (Skia)
│       ├── 📁 jvmMain/                 # Desktop-specific (Skia)
│       └── 📁 skiaMain/                # Shared Skia implementation
├── 📁 samples/
│   ├── 📁 ShaderLab/                   # Demo application
│   │   ├── composeApp/                 # Shared compose code
│   │   ├── androidApp/                 # Android launcher
│   │   ├── iosApp/                     # iOS Xcode project
│   │   └── imagelib/                   # Image loading utilities
│   └── 📁 VerticalCarousel/            # Additional sample
└── 📁 gradle/
    └── libs.versions.toml              # Version catalog
```

### **Core Abstractions**

| Interface | Purpose |
|-----------|---------|
| `ShaderEffect` | Base interface for all shader effects |
| `RuntimeShaderEffect` | Effects using custom AGSL/SkSL shaders |
| `AnimatedShaderEffect` | Effects with time-based animations |
| `NativeEffect` | Platform-native effects (e.g., hardware blur) |
| `CompositeEffect` | Chain multiple effects (Android; other platforms apply last only) |

## 📦 Build Commands

### **Library**

```bash
# Build library for all platforms
./gradlew :shaderx:build

# Publish to local Maven
./gradlew :shaderx:publishToMavenLocal
```

### **Sample App - Android**

```bash
# Build debug APK
./gradlew :samples:ShaderLab:androidApp:assembleDebug

# Install on connected device
./gradlew :samples:ShaderLab:androidApp:installDebug
```

### **Sample App - iOS (macOS only)**

```bash
# Build framework for simulator
./gradlew :samples:ShaderLab:composeApp:linkDebugFrameworkIosSimulatorArm64
```
Then open `samples/ShaderLab/iosApp/iosApp.xcodeproj` in Xcode and run.

### **Sample App - Desktop**

```bash
# Run directly
./gradlew :samples:ShaderLab:composeApp:run

# Package for distribution
./gradlew :samples:ShaderLab:composeApp:packageDmg    # macOS
./gradlew :samples:ShaderLab:composeApp:packageMsi    # Windows
./gradlew :samples:ShaderLab:composeApp:packageDeb    # Linux
```

## 🔮 Roadmap

- [ ] Additional effects (Glitch, Noise, Film Grain)
- [ ] Effect presets and themes
- [ ] Shader parameter animation curves
- [ ] Compose preview support
- [ ] Effect export/import (JSON)
- [ ] Shader hot-reload for development
- [ ] Web (WASM) support
- [ ] Video/camera effect support

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

### Development Guidelines

- Follow Kotlin coding conventions
- Use Compose best practices
- Maintain clean architecture principles
- Add appropriate test coverage

## 📄 License

```
Copyright 2024 Debanshu

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 🙏 Acknowledgments

- Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- UI powered by [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- Android shaders using [AGSL (Android Graphics Shading Language)](https://developer.android.com/develop/ui/views/graphics/agsl)
- Cross-platform rendering with [Skia](https://skia.org/)

---

**ShaderX** - GPU-powered visual effects across platforms 🎨✨
