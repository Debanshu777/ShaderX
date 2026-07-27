package com.debanshu.shaderlab.shaderx.uniform

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.uniform.MatrixUniform.Companion.rowMajor

/**
 * Represents a uniform value to be passed to a shader program.
 *
 * Uniforms are named values that remain constant across all pixels
 * during a single shader invocation but can change between frames.
 *
 * ## Supported Types
 * - [FloatUniform]: Single or multiple float values (vec2, vec3, vec4)
 * - [IntUniform]: Single or multiple integer values
 * - [ColorUniform]: Color values with proper color space handling
 * - [MatrixUniform]: Column-major float matrices (mat2, mat3, mat4)
 * - [TextureUniform]: An image bound as an additional `uniform shader` input
 *
 * ## Example
 * ```kotlin
 * override fun buildUniforms(width: Float, height: Float): List<Uniform> = listOf(
 *     FloatUniform("resolution", width, height),
 *     FloatUniform("intensity", intensity),
 *     ColorUniform("tintColor", 0xFFFF5733),
 *     IntUniform("mode", mode),
 *     TextureUniform("fftData", fftBitmap),
 * )
 * ```
 */
public sealed class Uniform {
    /**
     * The name of the uniform as declared in the shader code.
     * Must match exactly with the uniform declaration.
     */
    public abstract val name: String
}

/**
 * Uniform containing float values.
 *
 * Supports single floats, vec2, vec3, and vec4 types depending on
 * the number of values provided.
 *
 * @property name The uniform name in shader code
 * @property values Array of float values (1-4 elements)
 */
public data class FloatUniform(
    override val name: String,
    public val values: FloatArray,
) : Uniform() {
    /** Creates a single float uniform */
    public constructor(name: String, v1: Float) : this(name, floatArrayOf(v1))

    /** Creates a vec2 uniform */
    public constructor(name: String, v1: Float, v2: Float) : this(name, floatArrayOf(v1, v2))

    /** Creates a vec3 uniform */
    public constructor(name: String, v1: Float, v2: Float, v3: Float) : this(name, floatArrayOf(v1, v2, v3))

    /** Creates a vec4 uniform */
    public constructor(name: String, v1: Float, v2: Float, v3: Float, v4: Float) : this(name, floatArrayOf(v1, v2, v3, v4))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FloatUniform) return false
        return name == other.name && values.contentEquals(other.values)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }

    override fun toString(): String = "FloatUniform(name='$name', values=${values.contentToString()})"
}

/**
 * Uniform containing integer values.
 *
 * Supports single integers and integer vectors depending on
 * the number of values provided.
 *
 * @property name The uniform name in shader code
 * @property values Array of integer values
 */
public data class IntUniform(
    override val name: String,
    public val values: IntArray,
) : Uniform() {
    /** Creates a single int uniform */
    public constructor(name: String, v1: Int) : this(name, intArrayOf(v1))

    /** Creates an ivec2 uniform */
    public constructor(name: String, v1: Int, v2: Int) : this(name, intArrayOf(v1, v2))

    /** Creates an ivec3 uniform */
    public constructor(name: String, v1: Int, v2: Int, v3: Int) : this(name, intArrayOf(v1, v2, v3))

    /** Creates an ivec4 uniform */
    public constructor(name: String, v1: Int, v2: Int, v3: Int, v4: Int) : this(name, intArrayOf(v1, v2, v3, v4))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntUniform) return false
        return name == other.name && values.contentEquals(other.values)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }

    override fun toString(): String = "IntUniform(name='$name', values=${values.contentToString()})"
}

/**
 * Uniform containing color values.
 *
 * Uses `layout(color)` annotation in AGSL shaders for proper color space handling.
 * On Android, this uses `setColorUniform()` which handles color space conversions.
 * On Skia platforms, this is passed as a vec4 (r, g, b, a).
 *
 * ## Color space behaviour
 * The two platforms handle colors differently:
 * - **Android (AGSL):** `setColorUniform()` with `layout(color)` applies the sRGB ↔ linear
 *   color space conversion required by AGSL. Declare the uniform with `layout(color)` in the
 *   shader source to get correct results on Android.
 * - **Skia (iOS / Desktop / Wasm):** The components are passed as a raw `float4` with no
 *   color space conversion. `layout(color)` is parsed but has no semantic effect in SkSL.
 *   Results on these platforms assume sRGB components, which matches Compose's standard
 *   rendering pipeline for most devices. Wide-gamut (Display P3) displays may show subtle
 *   color differences between Android and Skia targets for highly saturated colors.
 *
 * ## Shader Declaration
 * ```glsl
 * layout(color) uniform half4 tintColor;
 * ```
 *
 * @property name The uniform name in shader code
 * @property red Red component (0.0 to 1.0)
 * @property green Green component (0.0 to 1.0)
 * @property blue Blue component (0.0 to 1.0)
 * @property alpha Alpha component (0.0 to 1.0), defaults to 1.0
 */
public data class ColorUniform(
    override val name: String,
    public val red: Float,
    public val green: Float,
    public val blue: Float,
    public val alpha: Float = 1f,
) : Uniform() {
    /**
     * Creates a color uniform from an ARGB color Long.
     *
     * @param name The uniform name in shader code
     * @param color Color in ARGB format (e.g., 0xFFFF5733)
     */
    public constructor(name: String, color: Long) : this(
        name = name,
        red = ((color shr 16) and 0xFF) / 255f,
        green = ((color shr 8) and 0xFF) / 255f,
        blue = (color and 0xFF) / 255f,
        alpha = ((color shr 24) and 0xFF) / 255f,
    )

    /**
     * Returns the color components as a float array [r, g, b, a].
     * Useful for platforms that don't have native color uniform support.
     */
    public fun toFloatArray(): FloatArray = floatArrayOf(red, green, blue, alpha)

    override fun toString(): String = "ColorUniform(name='$name', rgba=[$red, $green, $blue, $alpha])"

    public companion object {
        /**
         * Creates a color uniform from a Compose Color.
         *
         * This is the recommended way to create ColorUniform in Compose UI code.
         *
         * @param name The uniform name in shader code
         * @param color Compose Color value
         * @return A new ColorUniform with the color's components
         */
        public fun fromColor(
            name: String,
            color: Color,
        ): ColorUniform =
            ColorUniform(
                name = name,
                red = color.red,
                green = color.green,
                blue = color.blue,
                alpha = color.alpha,
            )
    }
}

/**
 * Uniform containing a column-major float matrix.
 *
 * Corresponds to the AGSL/SkSL matrix types:
 * - 4 elements → `float2x2` / `mat2`
 * - 9 elements → `float3x3` / `mat3`
 * - 16 elements → `float4x4` / `mat4`
 *
 * ## Column-major layout
 * AGSL and SkSL use **column-major** storage. A rotation matrix that you would write
 * in row-major form as:
 * ```
 * [cos  -sin  0]
 * [sin   cos  0]
 * [0     0    1]
 * ```
 * must be stored as `[cos, sin, 0, -sin, cos, 0, 0, 0, 1]` (columns packed left-to-right).
 * Use [rowMajor] to supply values in the natural mathematical order and have the
 * transposition applied automatically.
 *
 * ## Shader Declaration
 * ```glsl
 * uniform float3x3 transform;
 * ```
 *
 * ## Usage
 * ```kotlin
 * // Column-major: identity mat3
 * MatrixUniform("transform", floatArrayOf(1f,0f,0f, 0f,1f,0f, 0f,0f,1f))
 *
 * // Row-major convenience:
 * MatrixUniform.rowMajor("transform",
 *     1f, 0f, 0f,
 *     0f, 1f, 0f,
 *     0f, 0f, 1f,
 * )
 * ```
 *
 * @property name The uniform name as declared in the shader source.
 * @property values Column-major flat float array. Must have 4, 9, or 16 elements.
 */
public data class MatrixUniform(
    override val name: String,
    public val values: FloatArray,
) : Uniform() {
    init {
        require(values.size == 4 || values.size == 9 || values.size == 16) {
            "MatrixUniform '$name' requires 4 (mat2), 9 (mat3), or 16 (mat4) elements; " +
                "got ${values.size}."
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MatrixUniform) return false
        return name == other.name && values.contentEquals(other.values)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }

    override fun toString(): String = "MatrixUniform(name='$name', size=${values.size}, values=${values.contentToString()})"

    public companion object {
        /**
         * Creates a [MatrixUniform] from values supplied in **row-major** order.
         *
         * The values are transposed into column-major storage automatically.
         *
         * @param name The uniform name in shader code.
         * @param values Row-major flat float array (4, 9, or 16 elements).
         */
        public fun rowMajor(
            name: String,
            vararg values: Float,
        ): MatrixUniform {
            require(values.size == 4 || values.size == 9 || values.size == 16) {
                "MatrixUniform '$name' requires 4, 9, or 16 elements; got ${values.size}."
            }
            val dim =
                when (values.size) {
                    4 -> 2
                    9 -> 3
                    else -> 4
                }
            val colMajor =
                FloatArray(values.size) { i ->
                    val col = i / dim
                    val row = i % dim
                    values[row * dim + col]
                }
            return MatrixUniform(name, colMajor)
        }

        /** 2×2 identity matrix (column-major). */
        public fun identity2x2(name: String): MatrixUniform = MatrixUniform(name, floatArrayOf(1f, 0f, 0f, 1f))

        /** 3×3 identity matrix (column-major). */
        public fun identity3x3(name: String): MatrixUniform = MatrixUniform(name, floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))

        /** 4×4 identity matrix (column-major). */
        public fun identity4x4(name: String): MatrixUniform =
            MatrixUniform(
                name,
                floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
            )
    }
}

/**
 * Uniform binding an [ImageBitmap] as an additional shader input, declared in shader code as
 * `uniform shader <name>;` and sampled with `<name>.eval(coord)`.
 *
 * This is distinct from the mandatory `content` input (the composable's own rendered layer). Use it
 * to feed arbitrary image data into a shader — a lookup table, a mask, or per-frame data such as
 * audio FFT magnitudes packed into a bitmap.
 *
 * ## Platform binding
 * - **Android (AGSL):** `RuntimeShader.setInputShader(name, BitmapShader(bitmap, tileX, tileY))`
 * - **Skia (iOS / Desktop / Wasm):** `RuntimeShaderBuilder.child(name, image.makeShader(tileX, tileY))`
 *
 * ## Coordinate space
 * AGSL/SkSL child shaders sample in **pixel** coordinates, not normalised UV. To read a texel at
 * normalised `(u, v)` multiply by the image dimensions, e.g. `fftData.eval(float2(u * texWidth, 0))`.
 * Pass the texture size as a separate [FloatUniform] if the shader needs it.
 *
 * ## Per-frame use (live data)
 * For animated effects that update the texture every frame, prefer a **stable** effect instance
 * that resolves the image inside [com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect.buildFrameUniforms]
 * (see [com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect.withFftDataSource]). Emitting
 * this uniform from `buildFrameUniforms` and excluding the image from `structuralEquals` /
 * `structuralHashCode` keeps the animation session alive. Avoid creating a new effect identity
 * every frame solely to change the texture — Compose may skip `shaderEffect` when structural
 * [equals] matches, leaving a stale image bound.
 *
 * ## Shader Declaration
 * ```glsl
 * uniform shader content;
 * uniform shader fftData;
 * ```
 *
 * @property name The uniform name as declared in the shader source.
 * @property image The image to bind as this shader input.
 * @property tileModeX Horizontal tiling when sampling outside the image bounds.
 * @property tileModeY Vertical tiling when sampling outside the image bounds.
 */
public data class TextureUniform(
    override val name: String,
    public val image: ImageBitmap,
    public val tileModeX: ShaderTileMode = ShaderTileMode.CLAMP,
    public val tileModeY: ShaderTileMode = ShaderTileMode.CLAMP,
) : Uniform() {
    // ImageBitmap has no content equality; compare by reference identity. Two TextureUniforms are
    // equal only when they wrap the same image instance with the same name and tile modes.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextureUniform) return false
        return name == other.name &&
            image === other.image &&
            tileModeX == other.tileModeX &&
            tileModeY == other.tileModeY
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + image.hashCode()
        result = 31 * result + tileModeX.hashCode()
        result = 31 * result + tileModeY.hashCode()
        return result
    }

    override fun toString(): String =
        "TextureUniform(name='$name', image=${image.width}x${image.height}, tileX=$tileModeX, tileY=$tileModeY)"
}
