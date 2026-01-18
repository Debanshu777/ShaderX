@file:Suppress("unused")

package com.debanshu.shaderlab.shaderx.effects

// Re-export from new package for backwards compatibility
@Deprecated(
    message = "Moved to com.debanshu.shaderlab.shaderx.effect.impl",
    replaceWith = ReplaceWith(
        "InvertEffect",
        "com.debanshu.shaderlab.shaderx.effect.impl.InvertEffect"
    )
)
public val InvertEffect: com.debanshu.shaderlab.shaderx.effect.impl.InvertEffect =
    com.debanshu.shaderlab.shaderx.effect.impl.InvertEffect
