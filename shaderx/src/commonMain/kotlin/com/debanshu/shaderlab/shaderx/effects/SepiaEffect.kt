@file:Suppress("unused")

package com.debanshu.shaderlab.shaderx.effects

// Re-export from new package for backwards compatibility
@Deprecated(
    message = "Moved to com.debanshu.shaderlab.shaderx.effect.impl",
    replaceWith = ReplaceWith(
        "SepiaEffect",
        "com.debanshu.shaderlab.shaderx.effect.impl.SepiaEffect"
    )
)
public typealias SepiaEffect = com.debanshu.shaderlab.shaderx.effect.impl.SepiaEffect
