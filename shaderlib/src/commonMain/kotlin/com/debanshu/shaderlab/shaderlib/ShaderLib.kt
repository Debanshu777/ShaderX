@file:Suppress("unused")

package com.debanshu.shaderlab.shaderlib

import com.debanshu.shaderlab.shaderlib.effect.ShaderEffect as IShaderEffect
import com.debanshu.shaderlab.shaderlib.effects.ChromaticAberrationEffect
import com.debanshu.shaderlab.shaderlib.effects.GrayscaleEffect
import com.debanshu.shaderlab.shaderlib.effects.InvertEffect
import com.debanshu.shaderlab.shaderlib.effects.NativeBlurEffect
import com.debanshu.shaderlab.shaderlib.effects.PixelateEffect
import com.debanshu.shaderlab.shaderlib.effects.SepiaEffect
import com.debanshu.shaderlab.shaderlib.effects.VignetteEffect
import com.debanshu.shaderlab.shaderlib.effects.WaveEffect

/**
 * ShaderLib - A Kotlin Multiplatform library for GPU shader effects.
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
 * @see [com.debanshu.shaderlab.shaderlib.factory.ShaderFactory] for manual effect creation
 */
public object ShaderLib {
    /**
     * Library version.
     */
    public const val VERSION: String = "1.0.0"

    /**
     * Returns all built-in effects.
     */
    public fun builtInEffects(): List<IShaderEffect> = listOf(
        GrayscaleEffect(),
        SepiaEffect(),
        VignetteEffect(),
        PixelateEffect(),
        ChromaticAberrationEffect(),
        InvertEffect,
        WaveEffect(),
        NativeBlurEffect(),
    )
}
