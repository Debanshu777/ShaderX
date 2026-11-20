package com.debanshu.shaderlab.playground

import com.debanshu.shaderlab.shaderx.ShaderX
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.AudioDistortionEffect
import com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect
import com.debanshu.shaderlab.shaderx.parameter.*
import com.debanshu.shaderlab.shaderx.uniform.*

/** Deterministic Kotlin source, never Kotlin execution. Source compilation remains backend-specific. */
object KotlinExport {
    fun generate(effect: ShaderEffect): String = generate(effect, WorkbenchOutput.CONTENT)

    fun generate(effect: ShaderEffect, output: WorkbenchOutput): String {
        require(effect !is AudioDistortionEffect && effect !is AudioVisualizerEffect) {
            "Audio inputs are nonportable. Rebind the application's FFT bitmap/provider before applying this effect; callbacks and image handles cannot be exported."
        }
        val definition = when (effect) {
            is DefinedShaderEffect -> effect.definition
            is DefinedAnimatedShaderEffect -> effect.definition
            else -> null
        }
        if (output == WorkbenchOutput.BACKGROUND) requireNotNull(definition) {
            "Background export requires an explicit source-free ShaderDefinition"
        }.proceduralBackground()
        val imageBindings = definition?.program?.bindings.orEmpty().map { it.descriptor }
            .filter { it.kind in setOf(ShaderBindingKind.IMAGE, ShaderBindingKind.DATA, ShaderBindingKind.NESTED) }
        val hasFrameInputs = definition?.program?.bindings.orEmpty().any {
            it.descriptor.kind in setOf(ShaderBindingKind.FRAME_FLOAT, ShaderBindingKind.FRAME_IMAGE, ShaderBindingKind.FRAME_DATA, ShaderBindingKind.FRAME_NESTED)
        }
        val imageParameters = imageBindings.mapIndexed { index, input ->
            val type = when (input.kind) {
                ShaderBindingKind.IMAGE -> "ImageBitmap"
                ShaderBindingKind.DATA -> "Rgba8Data"
                ShaderBindingKind.NESTED -> "ShaderNode"
                else -> error("Unexpected structural input")
            }
            "input$index: $type"
        }
        val imageArguments = imageBindings.indices.map { "input$it" }
        val inputParameters = imageParameters + if (hasFrameInputs) listOf("frameProvider: ShaderFrameProvider") else emptyList()
        val inputArguments = imageArguments + if (hasFrameInputs) listOf("frameProvider") else emptyList()
        val previewParameters = inputParameters + if (hasFrameInputs) listOf("inputRevision: Long") else emptyList()
        val schema = parameterSchema(effect)
        val values = schema.valuesFrom(effect)
        val body = if (definition == null) {
            require(ShaderX.builtInEffects().any { it.id == effect.id }) { "Unregistered effect '${effect.id}': register its ShaderDefinition before exporting" }
            buildString {
                appendLine("    fun effect(): ShaderEffect {")
                appendLine("        var effect: ShaderEffect = ShaderX.builtInEffects().first { it.id == ${quote(effect.id)} }")
                for ((id, value) in values.snapshot()) appendLine("        effect = effect.withTypedParameter(${quote(id)}, ${value(value)})")
                appendLine("        return effect")
                appendLine("    }")
            }
        } else {
            buildString {
                val declarations = schema.definitions
                for ((index, parameter) in declarations.withIndex()) {
                    appendLine("    private val parameter$index = ${parameter(parameter)}")
                }
                appendLine(if (imageBindings.isEmpty()) "    val definition = ShaderDefinition(" else
                    "    fun definition(${imageParameters.joinToString()}) = ShaderDefinition(")
                appendLine("        id = ${quote(definition.id)}, displayName = ${quote(definition.displayName)},")
                appendLine("        schema = ParameterSchema(${quote(schema.ownerId)}, listOf(${declarations.indices.joinToString { "parameter$it" }})),")
                appendLine("        program = ShaderProgram(")
                appendLine("            source = ${quote(definition.program.source)},")
                appendLine("            bindings = listOf(")
                for (binding in definition.program.bindings) appendLine("                ${binding(binding.descriptor, declarations, imageBindings)},")
                appendLine("            ),")
                appendLine("        ),")
                appendLine("    )")
                appendLine("    fun effect(${inputParameters.joinToString()}): ShaderEffect {")
                if (imageBindings.isNotEmpty()) appendLine("        val definition = definition(${imageArguments.joinToString()})")
                appendLine("        var values = definition.schema.defaults()")
                for ((id, value) in values.snapshot()) appendLine("        values = values.withValue(${quote(id)}, ${value(value)})")
                appendLine("        return definition.${if (effect is AnimatedShaderEffect) "animatedEffect(values, isAnimating = ${effect.isAnimating}${if (hasFrameInputs) ", frameProvider = frameProvider" else ""})" else "effect(values)"}")
                appendLine("    }")
            }
        }
        val application = if (output == WorkbenchOutput.BACKGROUND) {
            val kind = if (effect is AnimatedShaderEffect) "DefinedAnimatedShaderEffect" else "DefinedShaderEffect"
            val clock = if (effect is AnimatedShaderEffect) """
                val playback = rememberShaderPlayback(initiallyPlaying = (effect as AnimatedShaderEffect).isAnimating, active = active, reducedMotion = reducedMotion)
            """.trimIndent() else ""
            val arguments = listOf("background", "values = values") +
                (if (effect is AnimatedShaderEffect) listOf("playback = playback") else emptyList()) +
                (if (hasFrameInputs) listOf("inputRevision = inputRevision", "frameProvider = frameProvider") else emptyList()) + "onError = onError"
            """
                val background = remember(effect) { (effect as $kind).definition.proceduralBackground() }
                val values = background.definition.schema.valuesFrom(effect)
                $clock
                content(Modifier.shaderBackground(${arguments.joinToString()}))
            """.trimIndent()
        } else """
            if (effect is AnimatedShaderEffect) {
                val playback = rememberShaderPlayback(initiallyPlaying = effect.isAnimating, active = active, reducedMotion = reducedMotion)
                content(Modifier.controlledShaderEffect(effect, playback, ${if (hasFrameInputs) "inputRevision = inputRevision, " else ""}onError = onError))
            } else {
                content(Modifier.shaderEffect(effect, onError = onError))
            }
        """.trimIndent()
        return """
            // Requires ShaderX 0.2.0.
            // Android API 33+ (AGSL); Desktop/iOS/Wasm (Skia). Browser success is not Android validation.
            // Source and bindings must compile on each destination backend; inspect onError diagnostics.
            // The host supplies lifecycle visibility and accessibility reduced-motion preferences.
            // Borrowed image inputs remain owned by the caller; retain them while the effect uses them.
            // Raw inputs require a backend with RAW_DATA_TEXTURE support; Skia raw input is unavailable.
            // Rgba8Data is an immutable RGBA8 snapshot; alpha is data, no color conversion/premultiplication.
            // ShaderNode is an explicit source-free child snapshot; supply its independent time/inputs.
            // No image/data payload, shader node or callback is embedded in this export.
            // Live providers must supply every declared frame binding exactly once, preserving names.
            // Retain provider identity; increment inputRevision after publishing complete live snapshots.
            // Live sampling/tile modes are chosen by the provider's TextureUniform/DataUniform, never inferred here.
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.remember
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.graphics.ImageBitmap
            import com.debanshu.shaderlab.shaderx.ShaderX
            import com.debanshu.shaderlab.shaderx.authoring.*
            import com.debanshu.shaderlab.shaderx.compose.*
            import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
            import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
            import com.debanshu.shaderlab.shaderx.parameter.*
            import com.debanshu.shaderlab.shaderx.uniform.*
            import com.debanshu.shaderlab.shaderx.result.ShaderError

            object ExportedShader {
            ${body.trimEnd().replace("\n", "\n            ")}
            }

            @Composable
            fun ExportedShaderPreview(
                ${previewParameters.joinToString("") { "$it,\n                " }}active: Boolean,
                reducedMotion: Boolean,
                onError: (ShaderError) -> Unit,
                content: @Composable (Modifier) -> Unit,
            ) {
                val effect = remember${if (inputArguments.isEmpty()) "" else "(${inputArguments.joinToString()})"} { ExportedShader.effect(${inputArguments.joinToString()}) }
                ${application.replace("\n", "\n                ")}
            }
        """.trimIndent() + "\n"
    }

    private fun parameter(parameter: ParameterDefinition<*>): String {
        val prefix = "${quote(parameter.key.ownerId)}, ${quote(parameter.key.id)}, "
        val metadata = parameter.metadata.let {
            "ParameterMetadata(${quote(it.label)}, ${nullable(it.description)}, ${it.order}, ${nullable(it.semanticRole)})"
        }
        return when (parameter) {
            is FloatParameterDefinition -> "FloatParameterDefinition($prefix${number(parameter.defaultValue)}, " +
                "${parameter.range?.let { "${number(it.start)}..${number(it.endInclusive)}" } ?: "null"}, $metadata, " +
                "unit = ${nullable(parameter.unit)}, step = ${parameter.step?.let(::number) ?: "null"}, decimalPlaces = ${parameter.decimalPlaces})"
            is ColorParameterDefinition -> "ColorParameterDefinition($prefix${color(parameter.defaultValue)}, $metadata)"
            is BooleanParameterDefinition -> "BooleanParameterDefinition($prefix${parameter.defaultValue}, $metadata)"
        }
    }

    private fun binding(binding: ShaderBindingDescriptor, parameters: List<ParameterDefinition<*>>, images: List<ShaderBindingDescriptor>): String {
        val name = quote(binding.name)
        val key = binding.parameterId?.let { id ->
            val index = parameters.indexOfFirst { it.key.id == id }
            require(index >= 0) { "Binding '${binding.name}' refers to missing schema parameter '$id'" }
            "parameter$index.key"
        }
        return when (binding.kind) {
            ShaderBindingKind.RESOLUTION -> "ShaderBinding.resolution($name)"
            ShaderBindingKind.TIME -> "ShaderBinding.time($name)"
            ShaderBindingKind.FLOAT_PARAMETER -> "ShaderBinding.float($name, ${requireNotNull(key)})"
            ShaderBindingKind.COLOR_PARAMETER -> "ShaderBinding.color($name, ${requireNotNull(key)})"
            ShaderBindingKind.BOOLEAN_PARAMETER -> "ShaderBinding.boolean($name, ${requireNotNull(key)})"
            ShaderBindingKind.CONSTANT -> "ShaderBinding.uniform(${uniform(requireNotNull(binding.constant))})"
            ShaderBindingKind.IMAGE -> {
                val index = images.indexOfFirst { it.name == binding.name }
                require(index >= 0) { "Missing caller image input '${binding.name}'" }
                "ShaderBinding.image($name, input$index, ShaderTileMode.${requireNotNull(binding.tileModeX).name}, ShaderTileMode.${requireNotNull(binding.tileModeY).name})"
            }
            ShaderBindingKind.DATA -> {
                val index = images.indexOfFirst { it.name == binding.name }
                require(index >= 0) { "Missing caller data input '${binding.name}'" }
                "ShaderBinding.data($name, input$index, DataSampling.${requireNotNull(binding.dataSampling).name}, ShaderTileMode.${requireNotNull(binding.tileModeX).name}, ShaderTileMode.${requireNotNull(binding.tileModeY).name})"
            }
            ShaderBindingKind.NESTED -> {
                val index = images.indexOfFirst { it.name == binding.name }
                require(index >= 0) { "Missing caller nested input '${binding.name}'" }
                "ShaderBinding.nested($name, input$index)"
            }
            ShaderBindingKind.FRAME_DATA -> "ShaderBinding.frameData($name)"
            ShaderBindingKind.FRAME_NESTED -> "ShaderBinding.frameNested($name)"
            ShaderBindingKind.FRAME_FLOAT -> "ShaderBinding.frameFloat($name)"
            ShaderBindingKind.FRAME_IMAGE -> "ShaderBinding.frameImage($name)"
        }
    }

    private fun uniform(uniform: Uniform): String {
        val name = quote(uniform.name)
        return when (uniform) {
            is FloatUniform -> "FloatUniform($name, floatArrayOf(${uniform.values.joinToString { number(it) }}))"
            is IntUniform -> "IntUniform($name, intArrayOf(${uniform.values.joinToString { if (it == Int.MIN_VALUE) "Int.MIN_VALUE" else it.toString() }}))"
            is MatrixUniform -> "MatrixUniform($name, floatArrayOf(${uniform.values.joinToString { number(it) }}))"
            is ColorUniform -> "ColorUniform($name, ${number(uniform.red)}, ${number(uniform.green)}, ${number(uniform.blue)}, ${number(uniform.alpha)})"
            is DataUniform, is NestedUniform -> error("Input '${uniform.name}' requires explicit rebinding")
            is TextureUniform -> error("Image '${uniform.name}' requires explicit rebinding")
        }
    }

    private fun value(value: ParameterValue): String = when (value) {
        is ParameterValue.FloatValue -> "ParameterValue.FloatValue(${number(value.value)})"
        is ParameterValue.ColorValue -> "ParameterValue.ColorValue(${color(value.color)})"
        is ParameterValue.BooleanValue -> "ParameterValue.BooleanValue(${value.enabled})"
    }
    private fun number(value: Float): String { require(value.isFinite()); return "${value}f" }
    private fun color(value: Long): String = "0x${value.toString(16).padStart(8, '0')}L"
    private fun nullable(value: String?): String = value?.let(::quote) ?: "null"

    /** Regular strings avoid raw-string delimiter hazards; indices never incorporate user IDs. */
    internal fun quote(value: String): String = buildString {
        append('"')
        for (char in value) when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '$' -> append("\\$")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 32 || char.code > 126) append("\\u${char.code.toString(16).padStart(4, '0')}") else append(char)
        }
        append('"')
    }
}
