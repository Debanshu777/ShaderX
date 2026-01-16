package com.debanshu.shaderlab.shaderlib.uniform

/**
 * Represents a uniform value to be passed to a shader program.
 *
 * Uniforms are named values that remain constant across all pixels
 * during a single shader invocation but can change between frames.
 *
 * ## Supported Types
 * - [FloatUniform]: Single or multiple float values (vec2, vec3, vec4)
 * - [IntUniform]: Single or multiple integer values
 *
 * ## Example
 * ```kotlin
 * override fun buildUniforms(width: Float, height: Float): List<Uniform> = listOf(
 *     FloatUniform("resolution", width, height),
 *     FloatUniform("intensity", intensity),
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
