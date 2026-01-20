@file:Suppress("unused")

package com.debanshu.shaderlab.shaderx.effects

// Re-export from new package for backwards compatibility
@Deprecated(
    message = "Moved to com.debanshu.shaderlab.shaderx.effect.impl",
    replaceWith = ReplaceWith(
        "NativeBlurEffect",
        "com.debanshu.shaderlab.shaderx.effect.impl.NativeBlurEffect"
    )
)
public typealias NativeBlurEffect = com.debanshu.shaderlab.shaderx.effect.impl.NativeBlurEffect
