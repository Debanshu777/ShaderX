package com.debanshu.shaderlab.playground

/** Deliberately narrow tooling validation, not shader reflection or a GLSL parser. Native compilation
 * is still required. We support one uniform per declaration, scalar float/half/int, float2,
 * layout(color) half4/float4 and shader. Unsupported/ambiguous declarations fail closed. */
object WorkbenchDeclarations {
    data class Declaration(val name: String, val type: String, val color: Boolean, val line: Int)
    private data class Token(val text: String, val line: Int)

    fun validate(source: String, expected: Map<String, String>): List<Declaration> {
        val tokens = tokenize(source)
        require(tokens.none { it.text == "#" }) { "Preprocessor declarations are unsupported by the workbench; use the manual SDK API" }
        val found = linkedMapOf<String, Declaration>()
        for (i in tokens.indices) {
            if (tokens[i].text != "uniform") continue
            val type = tokens.getOrNull(i + 1)?.text
            val name = tokens.getOrNull(i + 2)?.text.orEmpty()
            require(type in setOf("float", "half", "int", "float2", "half4", "float4", "shader") &&
                name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && tokens.getOrNull(i + 3)?.text == ";") {
                "Line ${tokens[i].line}: unsupported or ambiguous uniform declaration. Use one supported uniform per declaration; arrays, structs and qualifiers are unsupported."
            }
            val color = i >= 4 && tokens.subList(i - 4, i).map { it.text } == listOf("layout", "(", "color", ")")
            if (i > 0 && tokens[i - 1].text == ")") require(color) {
                "Line ${tokens[i].line}: unsupported uniform layout; only layout(color) is supported"
            }
            require((type == "half4" || type == "float4") == color) {
                "Line ${tokens[i].line}: color uniforms require layout(color) half4/float4; data vectors are unsupported"
            }
            require(found.put(name, Declaration(name, requireNotNull(type), color, tokens[i].line)) == null) { "Duplicate uniform '$name'" }
        }
        require(found.keys == expected.keys) {
            "Uniform bindings differ: missing source declaration ${expected.keys - found.keys}; missing form binding ${found.keys - expected.keys}. Edit the named uniform binding or source, then Run."
        }
        for ((name, kind) in expected) {
            val actual = found.getValue(name)
            val matches = when (kind) {
                "float" -> actual.type in setOf("float", "half")
                "color" -> actual.color
                else -> actual.type == kind
            }
            require(matches) { "Line ${actual.line}: binding '$name' expects $kind, source declares ${actual.type}" }
        }
        return found.values.toList()
    }

    private fun tokenize(source: String): List<Token> {
        val result = mutableListOf<Token>()
        var i = 0
        var line = 1
        while (i < source.length) {
            val c = source[i]
            when {
                c == '\n' -> { line++; i++ }
                c.isWhitespace() -> i++
                source.startsWith("//", i) -> { while (i < source.length && source[i] != '\n') i++ }
                source.startsWith("/*", i) -> {
                    i += 2
                    while (i < source.length && !source.startsWith("*/", i)) { if (source[i] == '\n') line++; i++ }
                    require(i < source.length) { "Unterminated block comment; native compiler log is unavailable until the draft passes declaration validation" }
                    i += 2
                }
                c == '"' || c == '\'' -> {
                    val quote = c
                    i++
                    var closed = false
                    while (i < source.length) {
                        if (source[i] == '\n') line++
                        if (source[i] == '\\') { if (source.getOrNull(i + 1) == '\n') line++; i += 2; continue }
                        if (source[i++] == quote) { closed = true; break }
                    }
                    require(closed) { "Unterminated quoted token" }
                }
                c.isLetterOrDigit() || c == '_' -> {
                    val start = i++
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '_')) i++
                    result += Token(source.substring(start, i), line)
                }
                else -> { result += Token(c.toString(), line); i++ }
            }
        }
        return result
    }
}
