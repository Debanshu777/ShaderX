package com.debanshu.shaderlab.shaderx.authoring

import com.debanshu.shaderlab.shaderx.parameter.ParameterValues
import com.debanshu.shaderlab.shaderx.uniform.*

/**
 * Immutable source-free child snapshot. Shares its definition, never mutable native state or a
 * clock/provider. Explicit [timeSeconds] freezes child time; parent playback does not advance it.
 * Resolution is explicitly supplied at snapshot (default 1×1), never inherited.
 * Recreate and supply through frameNested for independently controlled child playback/live data.
 * Graphs are limited to depth 4 and 32 nodes (sibling occurrences count independently).
 */
public class ShaderNode private constructor(
    public val background: ShaderBackground,
    public val timeSeconds: Float,
    internal val uniforms: List<Uniform>,
) {
    internal val requiresRawData: Boolean get() = uniforms.any { it is DataUniform || (it is NestedUniform && it.input.requiresRawData) }
    public companion object {
        /** Resolves and validates all providers now, before any backend work. */
        public fun snapshot(
            background: ShaderBackground,
            values: ParameterValues = background.definition.schema.defaults(),
            timeSeconds: Float = 0f,
            frameProvider: ShaderFrameProvider? = null,
            width: Float = 1f,
            height: Float = 1f,
        ): ShaderNode {
            require(timeSeconds.isFinite()) { "Child time must be finite" }
            val definition = background.definition
            val uniforms = definition.animatedEffect(values, false, frameProvider).withTime(timeSeconds)
                .buildUniforms(width, height).map { it.inputSnapshot() }
            // Require explicit binding coverage for shader inputs; never infer content/backdrop.
            val names = declaredChildInputs(definition.program.source)
            val supplied = uniforms.filter { it is TextureUniform || it is DataUniform || it is NestedUniform }.map { it.name }.toSet()
            require(names == supplied) { "Nested input '${definition.id}' missing or undeclared shader inputs: required=$names supplied=$supplied" }
            val result = ShaderNode(background, timeSeconds, uniforms)
            validateInputTree(listOf(NestedUniform("<root>", result)))
            return result
        }
    }
}

internal fun Uniform.inputSnapshot(): Uniform = when (this) {
    is FloatUniform -> FloatUniform(name, values.copyOf())
    is IntUniform -> IntUniform(name, values.copyOf())
    is MatrixUniform -> MatrixUniform(name, values.copyOf())
    is ColorUniform, is TextureUniform, is DataUniform, is NestedUniform -> this
}

/** Whole-tree validation must run before compilation or image/native allocation. */
internal fun validateInputTree(uniforms: List<Uniform>, rawSupported: Boolean = true, nestedSupported: Boolean = true) {
    var count = 0
    val path = mutableListOf<ShaderNode>()
    fun visit(values: List<Uniform>, depth: Int) {
        require(depth <= 4) { "Nested input depth exceeds 4" }
        for (value in values) when (value) {
            is DataUniform -> require(rawSupported) { "Raw data input unavailable on this backend" }
            is NestedUniform -> {
                require(nestedSupported) { "Nested input unavailable on this backend" }
                require(++count <= 32) { "Nested input node count exceeds 32" }
                require(path.none { it === value.input }) { "Nested input cycle at '${value.name}'" }
                path.add(value.input)
                visit(value.input.uniforms, depth + 1)
                path.removeAt(path.lastIndex)
            }
            else -> Unit
        }
    }
    visit(uniforms, 0)
}

/** Narrow fail-closed input coverage check, not shader reflection or a general source parser. */
internal fun declaredChildInputs(source: String): Set<String> {
    val clean = StringBuilder()
    var i = 0
    while (i < source.length) {
        when {
            source.startsWith("//", i) -> { while (i < source.length && source[i] != '\n') i++ }
            source.startsWith("/*", i) -> {
                val end = source.indexOf("*/", i + 2)
                require(end >= 0) { "Nested shader has an unterminated comment" }
                clean.append(' '); i = end + 2
            }
            else -> clean.append(source[i++])
        }
    }
    require('#' !in clean && '\"' !in clean && '\'' !in clean) {
        "Nested shader input coverage does not support preprocessor directives, macros or quoted tokens"
    }
    val tokens = Regex("[A-Za-z_][A-Za-z_0-9]*|[^\\s]").findAll(clean).map { it.value }.toList()
    val names = mutableSetOf<String>()
    for (index in tokens.indices) if (tokens[index] == "shader") {
        require(index > 0 && tokens[index - 1] == "uniform" &&
            tokens.getOrNull(index + 1)?.matches(Regex("[A-Za-z_][A-Za-z_0-9]*")) == true &&
            tokens.getOrNull(index + 2) == ";") {
            "Nested shader inputs require one 'uniform shader name;' declaration each; arrays, aliases and comma declarations are unsupported"
        }
        require(names.add(tokens[index + 1])) { "Duplicate nested shader input '${tokens[index + 1]}'" }
    }
    return names
}

/** New child kinds use narrow alias preflight; legacy image/scalar setter behavior is unchanged. */
internal fun validateInputAliases(source: String, uniforms: List<Uniform>) {
    val inputs = uniforms.filter { it is DataUniform || it is NestedUniform }
    if (inputs.isEmpty()) return
    val declarations = declaredChildInputs(source)
    for (input in inputs) require(input.name in declarations) {
        "Parent binding '${input.name}' requires an explicit 'uniform shader ${input.name};' declaration"
    }
}
