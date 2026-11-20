package com.debanshu.shaderlab.playground

import com.debanshu.shaderlab.shaderx.parameter.valuesFrom
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.impl.*
import com.debanshu.shaderlab.shaderx.parameter.ParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue

/** Stable editor instance identity, separate from a definition ID and runtime index address. */
data class PipelineChild(val key: String, val project: PlaygroundProject) {
    init { require(key.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Pipeline keys require 1..64 letters, digits, underscores or hyphens" } }
}

/** Sample-only ordered configuration. No time, providers, images or native resources are persisted. */
class PipelineProject(children: List<PipelineChild>) {
    val children = children.toList()
    init {
        require(this.children.size in 1..16) { "A pipeline requires 1..16 children" }
        require(this.children.map { it.key }.distinct().size == this.children.size) { "Duplicate pipeline instance key" }
    }
    fun effect(): CompositeEffect = CompositeEffect.of(children.map { it.project.effect })
    fun address(key: String, parameter: String): String {
        val index = children.indexOfFirst { it.key == key }
        require(index >= 0) { "Unknown pipeline instance '$key'" }
        require(children[index].project.entry.schema.definitions.any { it.key.id == parameter }) { "Unknown child parameter '$parameter'" }
        return "$index${CompositeEffect.DELIMITER}$parameter"
    }
    fun move(key: String, destination: Int): PipelineProject {
        require(destination in children.indices) { "Invalid pipeline destination" }
        val child = children.single { it.key == key }
        return PipelineProject(children.toMutableList().apply { remove(child); add(destination, child) })
    }
    fun update(key: String, parameter: String, value: ParameterValue): PipelineProject {
        require(children.any { it.key == key }) { "Unknown pipeline instance '$key'" }
        return PipelineProject(children.map { child ->
            if (child.key != key) child else {
                val values = child.project.entry.schema.valuesFrom(child.project.effect).withValue(parameter, value)
                child.copy(project = PlaygroundProject(child.project.entry, child.project.entry.restore(values.snapshot())))
            }
        })
    }
}

/** Separate pipeline format; scalar ShaderLab-Preset 1 remains unchanged and never inferred. */
object PipelinePresetCodec {
    const val MAX_BYTES = 262144
    fun encode(project: PipelineProject): String = buildString {
        appendLine("ShaderLab-Pipeline 1")
        project.children.forEach { child ->
            appendLine(child.key)
            val scalar = PresetCodec.encode(child.project.effect, child.project.entry)
            appendLine(scalar.encodeToByteArray().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') })
        }
    }.also { require(it.encodeToByteArray().size <= MAX_BYTES) { "Pipeline preset exceeds size limit" } }

    /** Validate every child before returning a replacement; reject unknown versions instead of guessing. */
    fun decode(text: String, registry: PlaygroundRegistry): PipelineProject {
        require(text.length <= MAX_BYTES && text.encodeToByteArray().size <= MAX_BYTES) { "Pipeline preset exceeds size limit" }
        val lines = text.removeSuffix("\n").split('\n')
        require(lines.firstOrNull() == "ShaderLab-Pipeline 1" && lines.size in 3..33 && lines.size % 2 == 1) { "Expected a version 1 ShaderLab pipeline" }
        return PipelineProject(lines.drop(1).chunked(2).map { (key, encoded) ->
            require(encoded.length <= PresetCodec.MAX_BYTES * 2 && encoded.length % 2 == 0 && encoded.all { it in "0123456789abcdef" }) { "Malformed pipeline child" }
            val scalar = ByteArray(encoded.length / 2) { encoded.substring(it * 2, it * 2 + 2).toInt(16).toByte() }.decodeToString(throwOnInvalidSequence = true)
            val effect = PresetCodec.decode(scalar, registry.entries)
            PipelineChild(key, PlaygroundProject(registry.entry(effect.id), effect))
        })
    }
}

/** Only known built-in playback flags are overridden; a custom GPU Boolean named animate is editable. */
fun pipelineControlDefinitions(project: PlaygroundProject): List<ParameterDefinition<*>> {
    val playbackId = when (project.effect) {
        is WaveEffect -> WaveEffect.PARAM_ANIMATE
        is AnimatedGradientEffect -> AnimatedGradientEffect.PARAM_ANIMATE
        is PlasmaEffect -> PlasmaEffect.PARAM_ANIMATE
        is RippleEffect -> RippleEffect.PARAM_ANIMATE
        is VortexEffect -> VortexEffect.PARAM_ANIMATE
        is AudioDistortionEffect -> AudioDistortionEffect.PARAM_ANIMATE
        is AudioVisualizerEffect -> AudioVisualizerEffect.PARAM_ANIMATE
        else -> null
    }
    return project.entry.schema.definitions.filterNot { it.key.id == playbackId }
}
