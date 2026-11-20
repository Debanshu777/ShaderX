package com.debanshu.shaderlab.shaderx.effect.impl

import com.debanshu.shaderlab.shaderx.authoring.DefinedShaderEffect
import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram
import com.debanshu.shaderlab.shaderx.parameter.FloatParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterMetadata
import com.debanshu.shaderlab.shaderx.parameter.ParameterSchema
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue

/**
 * A bounded straight-RGBA affine transform. Twenty finite coefficients in
 * [-8, 8], row-major: each of four output rows contains R, G, B, A multipliers then a bias.
 * Biases use normalized component units, not 0..255. Input and returned arrays are snapshots;
 * equality/hashCode compare values (including signed zero consistently). No native state is held.
 *
 * [effect] evaluates in the destination's working component space, without transfer conversion.
 * Input is unpremultiplied (zero-alpha RGB becomes zero), transformed, clamped to 0..1, then
 * premultiplied by transformed alpha. Alpha biases can deliberately create opaque pixels from
 * transparent input. This is a color filter, not a source-free background.
 */
public class ColorMatrix(coefficients: FloatArray) {
    private val elements = coefficients.copyOf()
    public val coefficients: FloatArray get() = elements.copyOf()

    init {
        require(elements.size == 20) { "ColorMatrix requires exactly 20 row-major coefficients" }
        require(elements.all { it.isFinite() && it in -8f..8f }) {
            "ColorMatrix coefficients must be finite and in -8..8"
        }
    }

    /** Uses the same twenty scalar declarations as controls, bindings, presets and Kotlin export. */
    public fun effect(): DefinedShaderEffect {
        val definition = EffectCollection.colorMatrix
        var values = definition.schema.defaults()
        for (row in 0..3) for (column in 0..4) {
            values = values.withValue("m$row$column", ParameterValue.FloatValue(elements[row * 5 + column]))
        }
        return definition.effect(values)
    }

    override fun equals(other: Any?): Boolean = other is ColorMatrix && elements.contentEquals(other.elements)
    override fun hashCode(): Int = elements.contentHashCode()

    public companion object {
        /** Independent immutable identity value. */
        public fun identity(): ColorMatrix = ColorMatrix(FloatArray(20) { if (it / 5 == it % 5) 1f else 0f })
    }
}

/**
 * Original ShaderX color/artistic implementations for ShaderX, under the repository
 * license; no Paper/gallery/GPUImage source was ported. Bayer rank construction and Rec.709
 * luminance coefficients are mathematical recipes, implemented here without copied shader code.
 * These immutable declarations reuse the public authoring path and acquire no native resources.
 *
 * All four filter premultiplied content in the destination's working component space. No sRGB
 * decoding/encoding or gamut conversion is inserted. On an sRGB raster this is encoded-sRGB
 * component math, not linear-light editing. Color controls and artistic filters preserve alpha;
 * zero-alpha RGB is zero. Matrix deliberately permits alpha changes. Neutral settings return
 * sampled content directly; compare rendered pixels with a 2/255 premultiplied-component raster tolerance.
 */
public object EffectCollection {
    /** Named-argument convenience; validates against [colorAdjustment]'s reusable strict schema. */
    public fun adjustColor(brightness: Float = 0f, contrast: Float = 1f, saturation: Float = 1f): DefinedShaderEffect =
        colorAdjustment.effect(colorAdjustment.schema.defaults()
            .withValue("brightness", ParameterValue.FloatValue(brightness))
            .withValue("contrast", ParameterValue.FloatValue(contrast))
            .withValue("saturation", ParameterValue.FloatValue(saturation)))

    /** Named-argument convenience; fractional [levels] are retained and explicitly rounded at rendering. */
    public fun dither(amount: Float = 0f, cellSize: Float = 1f, levels: Float = 4f): DefinedShaderEffect =
        orderedDither.effect(orderedDither.schema.defaults()
            .withValue("amount", ParameterValue.FloatValue(amount))
            .withValue("cellSize", ParameterValue.FloatValue(cellSize))
            .withValue("levels", ParameterValue.FloatValue(levels)))

    /** Named-argument convenience; [cellSize] is physical pixels and [amount] defaults to neutral. */
    public fun dots(amount: Float = 0f, cellSize: Float = 8f): DefinedShaderEffect =
        halftone.effect(halftone.schema.defaults()
            .withValue("amount", ParameterValue.FloatValue(amount))
            .withValue("cellSize", ParameterValue.FloatValue(cellSize)))

    private fun scalar(
        owner: String, id: String, label: String, default: Float, range: ClosedFloatingPointRange<Float>,
        description: String, order: Int, unit: String? = null, step: Float = .01f,
    ): FloatParameterDefinition = FloatParameterDefinition(
        owner, id, default, range,
        ParameterMetadata(label, description, order, if (unit == "px") "physical-size" else "color-adjustment"),
        unit = unit, step = step, decimalPlaces = 2,
    )

    private fun definition(id: String, name: String, parameters: List<FloatParameterDefinition>, body: String): ShaderDefinition =
        ShaderDefinition(id, name, ShaderProgram(
            "uniform shader content;\n" + parameters.joinToString("\n") { "uniform float ${it.key.id};" } + "\n" + body,
            parameters.map { ShaderBinding.float(it.key.id, it.key) },
        ), ParameterSchema(id, parameters))

    /**
     * Brightness [-1,1], contrast [0,2], saturation [0,2]; defaults 0,1,1 are neutral.
     * Apply contrast around .5 plus brightness, then mix Rec.709 gray with that RGB by saturation,
     * finally clamp and premultiply. Gray coefficients operate on working components.
     */
    public val colorAdjustment: ShaderDefinition = run {
        val id = "color_adjustment"
        definition(id, "Color adjustment", listOf(
            scalar(id, "brightness", "Brightness", 0f, -1f..1f, "Additive normalized brightness after contrast", 0),
            scalar(id, "contrast", "Contrast", 1f, 0f..2f, "Contrast around component value 0.5", 1),
            scalar(id, "saturation", "Saturation", 1f, 0f..2f, "Mix from working-component Rec.709 gray", 2),
        ), """
            half4 main(float2 p) {
                half4 sampled = content.eval(p);
                if (brightness == 0.0 && contrast == 1.0 && saturation == 1.0) return sampled;
                float4 c = float4(sampled);
                float3 rgb = c.a > 0.0 ? c.rgb / c.a : float3(0.0);
                rgb = (rgb - 0.5) * contrast + 0.5 + brightness;
                float gray = dot(rgb, float3(0.2126, 0.7152, 0.0722));
                rgb = clamp(mix(float3(gray), rgb, saturation), 0.0, 1.0);
                return half4(rgb * c.a, c.a);
            }
        """.trimIndent())
    }

    /**
     * Twenty editable m00..m34 coefficients; column 4 is a normalized bias. See [ColorMatrix].
     * Defaults form an identity. Every coefficient is finite and bounded to [-8,8].
     */
    public val colorMatrix: ShaderDefinition = run {
        val id = "color_matrix"
        val parameters = (0..3).flatMap { row -> (0..4).map { column ->
            scalar(id, "m$row$column", "${"RGBA"[row]} ${if (column == 4) "bias" else "RGBA"[column]}",
                if (row == column) 1f else 0f, -8f..8f,
                "Row $row column $column: ${if (column == 4) "normalized bias" else "straight RGBA multiplier"}", row * 5 + column)
        } }
        val neutral = parameters.joinToString(" && ") { "${it.key.id} == ${it.defaultValue}" }
        val rows = (0..3).joinToString(",\n") { row ->
            "dot(c, float4(m${row}0, m${row}1, m${row}2, m${row}3)) + m${row}4"
        }
        definition(id, "Color matrix", parameters, """
            half4 main(float2 p) {
                half4 sampled = content.eval(p);
                if ($neutral) return sampled;
                float4 c = float4(sampled);
                c.rgb = c.a > 0.0 ? c.rgb / c.a : float3(0.0);
                float4 result = clamp(float4($rows), 0.0, 1.0);
                return half4(result.rgb * result.a, result.a);
            }
        """.trimIndent())
    }

    /**
     * Original Bayer 4x4 ordered color quantization. amount=0 is neutral; amount=1 is full effect.
     * cellSize [1,64] is physical pixels per Bayer entry (the pattern spans four entries).
     * levels [2,8] is stored exactly but explicitly rounded in the shader by floor(levels+.5),
     * with positive half ties upward. Step is only a UI hint, never implicit value quantization.
     */
    public val orderedDither: ShaderDefinition = run {
        val id = "ordered_dither"
        definition(id, "Ordered dither", listOf(
            scalar(id, "amount", "Amount", 0f, 0f..1f, "Blend original and quantized working RGB", 0),
            scalar(id, "cellSize", "Entry size", 1f, 1f..64f, "Physical pixels per Bayer entry; four entries per repeat", 1, "px", .25f),
            scalar(id, "levels", "Levels", 4f, 2f..8f, "Per-channel levels; floor(value + 0.5), ties upward", 2, "levels", 1f),
        ), """
            float parity(float n) { return n - 2.0 * floor(n / 2.0); }
            float rank2(float2 q) { return 2.0 * parity(q.x + q.y) + parity(q.y); }
            half4 main(float2 p) {
                half4 sampled = content.eval(p);
                if (amount == 0.0) return sampled;
                float4 c = float4(sampled);
                float3 rgb = c.a > 0.0 ? c.rgb / c.a : float3(0.0);
                float2 q = floor(p / cellSize);
                float rank = 4.0 * rank2(q) + rank2(floor(q / 2.0));
                float threshold = (rank + 0.5) / 16.0;
                float scale = floor(levels + 0.5) - 1.0;
                float3 quantized = clamp(floor(clamp(rgb, 0.0, 1.0) * scale + threshold) / scale, 0.0, 1.0);
                return half4(mix(rgb, quantized, amount) * c.a, c.a);
            }
        """.trimIndent())
    }

    /**
     * Center-sampled monochrome dot screen; amount=0 is neutral. cellSize [2,64] is the physical
     * pixel repeat. Dots use working-component Rec.709 luminance and radius sqrt(1-luma)/sqrt(2)
     * of a cell; this is a graphic screen, not an area-calibrated print simulation. Original
     * per-pixel alpha is preserved even when the cell-center sample has different alpha.
     */
    public val halftone: ShaderDefinition = run {
        val id = "halftone"
        definition(id, "Halftone", listOf(
            scalar(id, "amount", "Amount", 0f, 0f..1f, "Blend original and black/white dot screen", 0),
            scalar(id, "cellSize", "Dot spacing", 8f, 2f..64f, "Physical pixels per square screen cell", 1, "px", .25f),
        ), """
            half4 main(float2 p) {
                half4 sampled = content.eval(p);
                if (amount == 0.0) return sampled;
                float4 c = float4(sampled);
                float3 rgb = c.a > 0.0 ? c.rgb / c.a : float3(0.0);
                float2 cell = floor(p / cellSize);
                float4 center = float4(content.eval((cell + 0.5) * cellSize));
                float3 centerRgb = center.a > 0.0 ? center.rgb / center.a : float3(0.0);
                float gray = clamp(dot(centerRgb, float3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
                float2 local = p / cellSize - cell - 0.5;
                float paper = gray <= 0.0 ? 0.0 : (gray >= 1.0 ? 1.0 : step(1.0 - gray, 2.0 * dot(local, local)));
                return half4(mix(rgb, float3(paper), amount) * c.a, c.a);
            }
        """.trimIndent())
    }
}
