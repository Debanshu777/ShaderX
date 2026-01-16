# ShaderLib

A Kotlin Multiplatform library for GPU shader effects in Jetpack Compose.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-1.9.3-green.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## Features

- **Cross-platform**: Works on Android, iOS, and Desktop (JVM)
- **Type-safe API**: Strongly typed parameters and error handling
- **Built-in effects**: Grayscale, Sepia, Vignette, Blur, Pixelate, and more
- **Custom shaders**: Create your own effects with AGSL/SkSL
- **Animation support**: Built-in support for animated effects
- **Compose integration**: Easy-to-use Modifier extensions

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.debanshu:shaderlib:1.0.0")
}
```

## Quick Start

### Apply a simple effect

```kotlin
import com.debanshu.shaderlab.shaderlib.compose.shaderEffect
import com.debanshu.shaderlab.shaderlib.effects.GrayscaleEffect

@Composable
fun GrayscaleImage() {
    Image(
        painter = painterResource("photo.png"),
        contentDescription = null,
        modifier = Modifier.shaderEffect(GrayscaleEffect())
    )
}
```

### Use animated effects

```kotlin
import com.debanshu.shaderlab.shaderlib.compose.rememberShaderEffect
import com.debanshu.shaderlab.shaderlib.compose.shaderEffect
import com.debanshu.shaderlab.shaderlib.effects.WaveEffect

@Composable
fun AnimatedImage() {
    val waveEffect = rememberShaderEffect(WaveEffect(amplitude = 10f))
    
    Image(
        painter = painterResource("photo.png"),
        contentDescription = null,
        modifier = Modifier.shaderEffect(waveEffect)
    )
}
```

### Adjust parameters

```kotlin
val effect = GrayscaleEffect(intensity = 0.5f)
val updatedEffect = effect.withParameter("intensity", 0.8f)
```

## Built-in Effects

| Effect | Description |
|--------|-------------|
| `GrayscaleEffect` | Converts to grayscale using luminance weights |
| `SepiaEffect` | Applies vintage sepia tone |
| `VignetteEffect` | Darkens image edges |
| `NativeBlurEffect` | Hardware-accelerated Gaussian blur |
| `PixelateEffect` | Creates retro pixelation |
| `ChromaticAberrationEffect` | Simulates lens color separation |
| `InvertEffect` | Inverts all colors |
| `WaveEffect` | Animated wave distortion |

## Creating Custom Effects

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

## Error Handling

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

## Platform Support

| Platform | Shader Backend | Min Version |
|----------|---------------|-------------|
| Android | AGSL (RuntimeShader) | API 33 |
| iOS | Skia | iOS 14+ |
| Desktop | Skia | JDK 11+ |

## Architecture

```
shaderlib/
├── effect/           # Core effect interfaces
│   ├── ShaderEffect
│   ├── RuntimeShaderEffect
│   ├── AnimatedShaderEffect
│   └── NativeEffect
├── effects/          # Built-in effect implementations
├── factory/          # Platform-specific factories
├── parameter/        # Parameter types and formatting
├── uniform/          # Shader uniform types
├── result/           # Error handling
└── compose/          # Compose integration
```

## License

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

