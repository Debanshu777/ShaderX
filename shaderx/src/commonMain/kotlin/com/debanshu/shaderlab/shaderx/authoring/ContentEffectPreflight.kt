package com.debanshu.shaderlab.shaderx.authoring

import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderError

/**
 * Check the entire authored content graph before providers, cache lookup or native work.
 * Manual RuntimeShaderEffect declarations retain their legacy native-compiler handling.
 * This is a bounded declaration guard, not source validation or macro expansion.
 */
internal fun definedContentError(effect: ShaderEffect): ShaderError.CompilationError? {
    if (effect is CompositeEffect) {
        for (child in effect.effects) definedContentError(child)?.let { return it }
        return null
    }
    val definition = when (effect) {
        is DefinedShaderEffect -> effect.definition
        is DefinedAnimatedShaderEffect -> effect.definition
        else -> return null
    }
    if (definition.program.hasExplicitContentInput) return null
    return ShaderError.CompilationError(
        "Effect '${definition.id}' content rendering requires an explicit top-level 'uniform shader content;' " +
            "declaration (comments/whitespace allowed; no preprocessor aliases or comma/array declarations). " +
            "For source-free generation use definition.proceduralBackground() with Modifier.shaderBackground instead.",
        definition.program.source,
    )
}

/** Accept only a literal scalar child, so comments/local names/inactive macros cannot fake it. */
internal fun hasExplicitContentChild(source: String): Boolean {
    val code = StringBuilder()
    var index = 0
    while (index < source.length) {
        when {
            source.startsWith("//", index) -> {
                while (index < source.length && source[index] != '\n') index++
                code.append(' ')
            }
            source.startsWith("/*", index) -> {
                val end = source.indexOf("*/", index + 2)
                if (end < 0) return false
                code.append(' ')
                index = end + 2
            }
            else -> code.append(source[index++])
        }
    }
    // Macro/conditional expansion could remove or rename the child. Do not guess at it.
    if (code.any { it == '#' || it == '"' || it == '\'' }) return false
    val tokens = Regex("[A-Za-z_][A-Za-z_0-9]*|[^\\s]").findAll(code).map { it.value }.toList()
    var depth = 0
    for (i in tokens.indices) {
        when (tokens[i]) {
            "{" -> depth++
            "}" -> depth--
            "uniform" -> if (depth == 0 && (i == 0 || tokens[i - 1] == ";" || tokens[i - 1] == "}") &&
                tokens.getOrNull(i + 1) == "shader" && tokens.getOrNull(i + 2) == "content" &&
                tokens.getOrNull(i + 3) == ";") return true
        }
    }
    return false
}
