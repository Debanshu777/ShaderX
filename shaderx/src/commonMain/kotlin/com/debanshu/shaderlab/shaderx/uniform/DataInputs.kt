package com.debanshu.shaderlab.shaderx.uniform

/** Explicit numerical sampling. Pixel centers are (x + .5, y + .5); no mipmaps. */
public enum class DataSampling { NEAREST, LINEAR }

/**
 * Immutable, tightly packed row-major RGBA8 UNORM data. Each channel, including alpha, is
 * independent byte/255 data. Never premultiplied or color converted. Maximum 4096 per axis
 * and 16 MiB per value. Validates before copying. Reuse this value until data changes.
 */
public class Rgba8Data(public val width: Int, public val height: Int, bytes: ByteArray) {
    private val storage: ByteArray
    init {
        require(width in 1..4096 && height in 1..4096) { "Data dimensions must be 1..4096" }
        val count = width.toLong() * height * 4
        require(count <= 16L * 1024 * 1024 && count == bytes.size.toLong()) { "Data requires exact RGBA byte count, at most 16 MiB" }
        storage = bytes.copyOf()
    }
    /** Defensive snapshot; no caller can mutate the stored data. */
    public fun bytes(): ByteArray = storage.copyOf()
}

/** Raw data input. Existing TextureUniform continues to mean a color-managed picture. */
public data class DataUniform(
    override val name: String,
    public val data: Rgba8Data,
    public val sampling: DataSampling = DataSampling.NEAREST,
    public val tileModeX: ShaderTileMode = ShaderTileMode.CLAMP,
    public val tileModeY: ShaderTileMode = ShaderTileMode.CLAMP,
) : Uniform()

/** Explicit immutable source-free shader child; independent siblings may share a definition. */
public data class NestedUniform(
    override val name: String,
    public val input: com.debanshu.shaderlab.shaderx.authoring.ShaderNode,
) : Uniform()
