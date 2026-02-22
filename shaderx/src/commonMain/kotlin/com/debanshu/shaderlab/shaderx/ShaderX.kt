@file:Suppress("unused")

package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.ChromaticAberrationEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.InvertEffect
import com.debanshu.shaderlab.shaderx.effect.impl.NativeBlurEffect
import com.debanshu.shaderlab.shaderx.effect.impl.PixelateEffect
import com.debanshu.shaderlab.shaderx.effect.impl.SepiaEffect
import com.debanshu.shaderlab.shaderx.effect.impl.VignetteEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect

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
     * Cached list of built-in effects.
     *
     * Effects are instantiated once and reused to avoid repeated allocations.
     * For effects that need fresh instances (e.g., animated effects with different
     * initial states), create them directly instead of using this list.
     */
    private val cachedBuiltInEffects: List<ShaderEffect> by lazy {
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

    /**
     * Returns all built-in effects.
     *
     * This returns a cached list of effect instances. For effects that need
     * fresh instances with specific parameters, create them directly.
     */
    public fun builtInEffects(): List<ShaderEffect> = cachedBuiltInEffects
}
