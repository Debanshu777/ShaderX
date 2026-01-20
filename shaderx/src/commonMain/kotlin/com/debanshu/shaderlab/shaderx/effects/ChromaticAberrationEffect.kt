@file:Suppress("unused")

package com.debanshu.shaderlab.shaderx.effects

// Re-export from new package for backwards compatibility
@Deprecated(
    message = "Moved to com.debanshu.shaderlab.shaderx.effect.impl",
    replaceWith = ReplaceWith(
        "ChromaticAberrationEffect",
        "com.debanshu.shaderlab.shaderx.effect.impl.ChromaticAberrationEffect"
    )
)
public typealias ChromaticAberrationEffect = com.debanshu.shaderlab.shaderx.effect.impl.ChromaticAberrationEffect
