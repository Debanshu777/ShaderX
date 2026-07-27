package com.debanshu.shaderlab.shaderx.uniform

/**
 * Tiling behaviour for a [TextureUniform] when the shader samples outside the
 * bounds of the bound image.
 *
 * Mapped to the platform tile modes in the factories:
 * - Android: `android.graphics.Shader.TileMode`
 * - Skia (iOS / Desktop / Wasm): `org.jetbrains.skia.FilterTileMode`
 */
public enum class ShaderTileMode {
    /** Clamp to the edge pixel. */
    CLAMP,

    /** Repeat the image. */
    REPEAT,

    /** Repeat the image, mirroring on each tile. */
    MIRROR,
}
