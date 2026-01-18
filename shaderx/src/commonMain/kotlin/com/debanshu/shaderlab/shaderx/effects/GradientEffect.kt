@file:Suppress("unused")

package com.debanshu.shaderlab.shaderx.effects

// Re-export from new package for backwards compatibility
@Deprecated(
    message = "Moved to com.debanshu.shaderlab.shaderx.effect.impl",
    replaceWith = ReplaceWith(
        "GradientEffect",
        "com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect"
    )
)
public typealias GradientEffect = com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
