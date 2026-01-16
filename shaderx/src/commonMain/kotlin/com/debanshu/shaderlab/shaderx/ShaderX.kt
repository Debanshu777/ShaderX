@file:Suppress("unused")

package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effects.ChromaticAberrationEffect
import com.debanshu.shaderlab.shaderx.effects.GradientEffect
import com.debanshu.shaderlab.shaderx.effects.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effects.InvertEffect
import com.debanshu.shaderlab.shaderx.effects.NativeBlurEffect
import com.debanshu.shaderlab.shaderx.effects.PixelateEffect
import com.debanshu.shaderlab.shaderx.effects.SepiaEffect
import com.debanshu.shaderlab.shaderx.effects.VignetteEffect
import com.debanshu.shaderlab.shaderx.effects.WaveEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect as IShaderEffect

/**
 * ShaderX - A Kotlin Multiplatform library for GPU shader effects.
 *
 * ## Quick Start
 *
 * ### Apply a simple effect to an image
 * ```kotlin
 * Image(
 *     painter = painterResource("photo.png"),
 *     modifier = Modifier.shaderEffect(GrayscaleEffect())
 * )
 * ```
 *
 * ### Use animated effects
 * ```kotlin
 * val waveEffect = rememberShaderEffect(WaveEffect(amplitude = 10f))
 * Image(
 *     painter = painterResource("photo.png"),
 *     modifier = Modifier.shaderEffect(waveEffect)
 * )
 * ```
 *
 * ## Supported Platforms
 * - Android (API 33+) using AGSL
 * - iOS using Skia
 * - Desktop/JVM using Skia
 *
 * @see [com.debanshu.shaderlab.shaderx.factory.ShaderFactory] for manual effect creation
 */
public object ShaderX {
    /**
     * Library version.
     */
    public const val VERSION: String = "1.0.0"

    /**
     * Returns all built-in effects.
     */
    public fun builtInEffects(): List<IShaderEffect> =
        listOf(
            GrayscaleEffect(),
            SepiaEffect(),
            GradientEffect(),
            VignetteEffect(),
            PixelateEffect(),
            ChromaticAberrationEffect(),
            InvertEffect,
            WaveEffect(),
            NativeBlurEffect(),
        )
}
