package com.debanshu.shaderlab.shaderx.authoring

import com.debanshu.shaderlab.shaderx.parameter.*
import com.debanshu.shaderlab.shaderx.uniform.*

/**
 * Reusable source and binding declarations; never a native shader or renderer. Source is AGSL/SkSL,
 * not arbitrary GLSL. Only `content` is reserved. Built-in content routes perform a bounded
 * preflight requiring an explicit top-level `uniform shader content;` (comments/whitespace allowed;
 * preprocessor, comma/array or aliased declarations are rejected conservatively). This is not
 * general source parsing/reflection; the declaration result is cached once on this immutable program.
 * Backend compilation/setters validate remaining types/names.
 * Reuse this object; a new program is a conservative configuration replacement even for equal text.
 */
public class ShaderProgram(public val source: String, bindings: List<ShaderBinding> = emptyList()) {
    private val declarations = bindings.toList()
    public val bindings: List<ShaderBinding> get() = declarations.toList()

    init {
        require(source.isNotBlank()) { "Shader source must not be blank" }
        val names = mutableSetOf<String>()
        for (binding in declarations) {
            require(binding.name.isNotBlank() && binding.name.all { it == '_' || it.isLetterOrDigit() } &&
                !binding.name.first().isDigit()) { "Invalid binding name '${binding.name}'" }
            require(binding.name != "content") { "Binding 'content' is reserved for rendered content" }
            require(names.add(binding.name)) { "Duplicate binding '${binding.name}'" }
        }
    }

    // Source is immutable: never strip/tokenize it again during a pipeline frame refresh.
    internal val hasExplicitContentInput: Boolean = hasExplicitContentChild(source)
}

/**
 * Shared immutable declaration. The minimal path needs just id and named [program] ([displayName] defaults to id); the editable
 * path supplies [schema]. Every schema key must have a binding using that exact declaration key.
 * No registry/editor/serialization/compiler plugin is needed to apply [effect].
 */
public class ShaderDefinition(
    public val id: String,
    public val displayName: String = id,
    public val program: ShaderProgram,
    public val schema: ParameterSchema = ParameterSchema(id, emptyList()),
) {
    init {
        require(id.isNotBlank() && displayName.isNotBlank()) { "Definition id and name must not be blank" }
        require(schema.ownerId == id) { "Definition '$id' cannot own schema '${schema.ownerId}'" }
        for (binding in program.bindings) binding.key?.let { key -> schema.definition(key) }
        for (definition in schema.definitions) {
            require(program.bindings.any { it.key === definition.key }) {
                "Definition '$id' is missing a binding for '${definition.key}'"
            }
        }
    }

    /**
     * Static content-filter instance. Built-in content routes reject missing/ambiguous reserved
     * content declarations before providers/native work. For source-free pixels use
     * [proceduralBackground] instead. Time/live bindings require [animatedEffect], even when paused.
     */
    public fun effect(values: ParameterValues = schema.defaults()): DefinedShaderEffect {
        require(program.bindings.none { it.frame || it.clock }) { "Definition '$id' requires an animated instance" }
        return DefinedShaderEffect(this, values)
    }

    /**
     * Creates independent content values/playback state; sessions later acquire native state.
     * Content declaration preflight follows [effect]; source-free generation uses [proceduralBackground].
     */
    public fun animatedEffect(
        values: ParameterValues = schema.defaults(),
        isAnimating: Boolean = true,
        frameProvider: ShaderFrameProvider? = null,
    ): DefinedAnimatedShaderEffect {
        require(program.bindings.any { it.frame } == (frameProvider != null)) {
            "Definition '$id' requires a provider exactly when frame bindings are declared"
        }
        return DefinedAnimatedShaderEffect(this, values, isAnimating, 0f, frameProvider)
    }

    internal fun uniforms(values: ParameterValues, width: Float, height: Float, time: Float): List<Uniform> {
        require(width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
            "Definition '$id' requires positive finite pixel dimensions"
        }
        return program.bindings.filter { !it.frame }.map { binding ->
            try { binding.resolve(values, width, height, time) }
            catch (e: Exception) { throw ShaderBindingException(id, binding.name, "binding values", e) }
        }
    }

    internal fun frameUniforms(time: Float, provider: ShaderFrameProvider?): List<Uniform> {
        val declarations = program.bindings.filter { it.frame }
        val supplied = try { provider?.uniforms(time)?.map {
            if (it is FloatUniform) FloatUniform(it.name, it.values.copyOf()) else it
        }.orEmpty() }
        catch (e: Exception) { throw ShaderBindingException(id, "<frame provider>", "provider execution", e) }
        val seen = mutableSetOf<String>()
        for (uniform in supplied) {
            try {
                require(seen.add(uniform.name)) { "duplicate frame binding" }
                val declaration = declarations.singleOrNull { it.name == uniform.name }
                require(declaration != null) { "undeclared frame binding; structural/time bindings cannot be overridden" }
                require(when (declaration.descriptor.kind) {
                    ShaderBindingKind.FRAME_FLOAT -> uniform is FloatUniform && uniform.values.size == 1 && uniform.values.all { it.isFinite() }
                    ShaderBindingKind.FRAME_IMAGE -> uniform is TextureUniform
                    ShaderBindingKind.FRAME_DATA -> uniform is DataUniform
                    ShaderBindingKind.FRAME_NESTED -> uniform is NestedUniform
                    else -> false
                }) { "frame binding requires its declared kind; colors are structural" }
            } catch (e: Exception) { throw ShaderBindingException(id, uniform.name, "frame validation", e) }
        }
        for (binding in declarations) if (binding.name !in seen) {
            throw ShaderBindingException(id, binding.name, "frame validation", IllegalArgumentException("missing frame binding"))
        }
        // Validate the entire provider result before either backend sees a single assignment.
        return program.bindings.filter { it.clock }.map { it.resolve(schema.defaults(), 1f, 1f, time) } + supplied
    }
}
