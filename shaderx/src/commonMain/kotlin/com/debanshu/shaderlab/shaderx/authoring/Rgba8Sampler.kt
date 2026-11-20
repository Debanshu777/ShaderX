package com.debanshu.shaderlab.shaderx.authoring

import com.debanshu.shaderlab.shaderx.uniform.*

/**
 * Bounded numerical child implemented with ordinary float uniforms, not an image or raw texture.
 * Accepts at most 64 RGBA8 texels (256 float components, 1024 uniform bytes). Channels including
 * alpha are independent UNORM byte/255 values, without color conversion or premultiplication.
 * The consuming parent must convert data into valid premultiplied output colors.
 *
 * Samples in pixel coordinates, centers (x+.5,y+.5). NEAREST selects floor(coord); LINEAR
 * interpolates four neighbors around those centers. Tiling applies independently to each neighbor.
 * No mipmaps. Reuse the immutable snapshot until bytes/sampling change. Rebinding makes a fresh
 * child shader on refresh. Skia caches compiled programs; Android creates a fresh mutable shader
 * per occurrence. No upload/performance
 * improvement is claimed. Unlike native DataUniform, this requires only nested shader support.
 */
public fun ShaderNode.Companion.rgba8Sampler(
    data: Rgba8Data,
    sampling: DataSampling = DataSampling.NEAREST,
    tileModeX: ShaderTileMode = ShaderTileMode.CLAMP,
    tileModeY: ShaderTileMode = ShaderTileMode.CLAMP,
): ShaderNode {
    val texels = data.width.toLong() * data.height
    require(texels <= 64) { "Uniform RGBA8 sampler accepts at most 64 texels" }
    val floats = data.bytes().map { (it.toInt() and 255) / 255f }.toFloatArray()
    fun wrap(name: String, size: Int, mode: ShaderTileMode): String = when (mode) {
        ShaderTileMode.CLAMP -> "float $name(float v) { return clamp(v,0.0,${size - 1}.0); }"
        ShaderTileMode.REPEAT -> "float $name(float v) { return v-floor(v/$size.0)*$size.0; }"
        ShaderTileMode.MIRROR -> "float $name(float v) { float q=v-floor(v/${size * 2}.0)*${size * 2}.0; return min(q,${size * 2 - 1}.0-q); }"
    }
    val source = buildString {
        appendLine("uniform float4 sxData[$texels];")
        appendLine(wrap("sxX", data.width, tileModeX))
        appendLine(wrap("sxY", data.height, tileModeY))
        appendLine("float4 sxRead(float2 p) { float i=sxY(p.y)*${data.width}.0+sxX(p.x);")
        // RuntimeEffect does not permit dynamic uniform array indices. Keep dispatch bounded.
        for (i in 0 until texels.toInt() - 1) appendLine("if (i < ${i + 1}.0) return sxData[$i];")
        appendLine("return sxData[${texels - 1}]; }")
        if (sampling == DataSampling.NEAREST) {
            appendLine("half4 main(float2 p) { return half4(sxRead(floor(p))); }")
        } else {
            appendLine("""half4 main(float2 p) {
                float2 q=p-float2(.5); float2 b=floor(q); float2 t=q-b;
                return half4(mix(mix(sxRead(b),sxRead(b+float2(1,0)),t.x),
                    mix(sxRead(b+float2(0,1)),sxRead(b+float2(1,1)),t.x),t.y));
            }""")
        }
    }
    val background = ShaderDefinition("rgba8-uniform-sampler", program = ShaderProgram(
        source, listOf(ShaderBinding.uniform(FloatUniform("sxData",floats))),
    )).proceduralBackground()
    return ShaderNode.snapshot(background)
}
