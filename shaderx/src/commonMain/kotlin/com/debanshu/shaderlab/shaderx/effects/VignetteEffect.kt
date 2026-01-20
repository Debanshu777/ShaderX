@file:Suppress("unused")

package com.debanshu.shaderlab.shaderx.effects

// Re-export from new package for backwards compatibility
@Deprecated(
    message = "Moved to com.debanshu.shaderlab.shaderx.effect.impl",
    replaceWith = ReplaceWith(
        "VignetteEffect",
        "com.debanshu.shaderlab.shaderx.effect.impl.VignetteEffect"
    )
)
public typealias VignetteEffect = com.debanshu.shaderlab.shaderx.effect.impl.VignetteEffect
