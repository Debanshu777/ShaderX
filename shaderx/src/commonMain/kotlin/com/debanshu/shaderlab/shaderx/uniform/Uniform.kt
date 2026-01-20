package com.debanshu.shaderlab.shaderx.uniform

import androidx.compose.ui.graphics.Color

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
 *
 * ## Example
 * ```kotlin
 * override fun buildUniforms(width: Float, height: Float): List<Uniform> = listOf(
 *     FloatUniform("resolution", width, height),
 *     FloatUniform("intensity", intensity),
 *     ColorUniform("tintColor", 0xFFFF5733),
 *     IntUniform("mode", mode)
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

    override fun toString(): String =
        "ColorUniform(name='$name', rgba=[$red, $green, $blue, $alpha])"

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
        public fun fromColor(name: String, color: Color): ColorUniform = ColorUniform(
            name = name,
            red = color.red,
            green = color.green,
            blue = color.blue,
            alpha = color.alpha,
        )
    }
}
