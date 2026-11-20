package com.debanshu.shaderlab.playground

import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect

/** Generates reusable scalar-only pipelines. Caller-owned inputs require a deliberate host adapter. */
object PipelineKotlinExport {
    fun generate(project: PipelineProject): String {
        val sources = project.children.map { child ->
            val definition = when (val effect = child.project.effect) {
                is DefinedShaderEffect -> effect.definition
                is DefinedAnimatedShaderEffect -> effect.definition
                else -> null
            }
            require(definition?.program?.bindings.orEmpty().none { it.descriptor.kind in setOf(
                ShaderBindingKind.IMAGE, ShaderBindingKind.DATA, ShaderBindingKind.NESTED, ShaderBindingKind.FRAME_DATA, ShaderBindingKind.FRAME_NESTED, ShaderBindingKind.FRAME_IMAGE, ShaderBindingKind.FRAME_FLOAT) }) {
                "Pipeline export requires scalar-only definitions; rebind live/image/raw/nested inputs explicitly in your app"
            }
            KotlinExport.generate(child.project.effect)
        }
        val imports = sources.flatMap { it.lines().filter { line -> line.startsWith("import ") } }.distinct()
        val bodies = sources.mapIndexed { index, source ->
            "object PipelineChild$index {" + source.substringAfter("object ExportedShader {").substringBefore("\n@Composable")
        }
        return buildString {
            appendLine("// Requires ShaderX 0.2.0. Pipeline image export is unsupported.")
            appendLine("// Android API 33+ (AGSL); Desktop/iOS/Wasm (Skia). Validate each destination backend.")
            appendLine("// Browser success validates Wasm/Skia only, not Android AGSL or physical iOS GPU behavior.")
            appendLine("// The shared clock overrides each child's time/isAnimating; the host supplies lifecycle and reduced motion.")
            imports.forEach { appendLine(it) }
            appendLine("import com.debanshu.shaderlab.shaderx.effect.CompositeEffect")
            bodies.forEach { appendLine(it) }
            appendLine("object ExportedPipeline {")
            appendLine("    fun children(): Map<String, ShaderEffect> = linkedMapOf(")
            project.children.forEachIndexed { index, child -> appendLine("        ${KotlinExport.quote(child.key)} to PipelineChild$index.effect(),") }
            appendLine("    )")
            appendLine("    fun effect(): CompositeEffect = CompositeEffect.of(children().values.toList())")
            appendLine("}")
            appendLine("@Composable")
            appendLine("fun ExportedPipelinePreview(active: Boolean, reducedMotion: Boolean, onError: (ShaderError) -> Unit, content: @Composable (Modifier) -> Unit) {")
            appendLine("    val effect = remember { ExportedPipeline.effect() }")
            if (project.children.any { it.project.effect is AnimatedShaderEffect }) {
                appendLine("    val playback = rememberShaderPlayback(active = active, reducedMotion = reducedMotion)")
                appendLine("    content(Modifier.controlledShaderEffect(effect, playback, onError = onError))")
            } else {
                appendLine("    content(Modifier.shaderEffect(effect, onError = onError))")
            }
            appendLine("}")
        }
    }
}
