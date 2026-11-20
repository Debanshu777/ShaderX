package com.debanshu.shaderlab.playground

import com.debanshu.shaderlab.authoring.CustomDefinitions
import com.debanshu.shaderlab.shaderx.ShaderX
import com.debanshu.shaderlab.shaderx.authoring.DefinedAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.authoring.DefinedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.parameter.*

/** Editor truth: generated declarations retain nullable bounds and all presentation metadata. */
fun parameterSchema(effect: ShaderEffect): ParameterSchema = when (effect) {
    is DefinedShaderEffect -> effect.definition.schema
    is DefinedAnimatedShaderEffect -> effect.definition.schema
    is GradientEffect -> GradientEffect.SCHEMA
    is WaveEffect -> WaveEffect.SCHEMA
    else -> ParameterSchema.fromLegacy(effect.id, effect.parameters)
}

/** Sample-local portable identity; instances and runtime resources never form part of the ID. */
class PlaygroundEntry(val starter: ShaderEffect, val category: String, val schemaVersion: Int = 1) {
    val id: String get() = starter.id
    val schema: ParameterSchema = parameterSchema(starter)
    init { require(schemaVersion > 0) { "Schema version must be positive" } }
    fun restore(values: Map<String, ParameterValue>): ShaderEffect {
        require(values.keys == schema.definitions.map { it.key.id }.toSet()) { "Preset parameters must exactly match definition '$id'; reset or choose a compatible preset" }
        var checked = schema.defaults()
        for ((key, value) in values) checked = checked.withValue(key, value)
        var effect = starter
        for ((key, value) in checked.snapshot()) effect = effect.withTypedParameter(key, value)
        return effect
    }
}

/** Explicit local registration, reusable by the source workbench without a global SDK registry. */
class PlaygroundRegistry(entries: List<PlaygroundEntry>) {
    val entries: List<PlaygroundEntry> = entries.toList()
    init { require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate playground definition ID" } }
    fun entry(id: String): PlaygroundEntry = entries.singleOrNull { it.id == id }
        ?: throw IllegalArgumentException("Unknown definition '$id'; register it before importing or select another preset")

    companion object {
        fun standard(): PlaygroundRegistry = PlaygroundRegistry(
            ShaderX.builtInEffects().map { PlaygroundEntry(it, if (it is AnimatedShaderEffect) "Animated" else "Built-in") } +
                CustomDefinitions.effects().map { PlaygroundEntry(it, "Custom starters") },
        )
    }
}

/** Configuration only. Playback owners and borrowed image/live inputs remain with the host. */
data class PlaygroundProject(val entry: PlaygroundEntry, val effect: ShaderEffect = entry.starter) {
    init {
        require(effect.id == entry.id) { "Project definition mismatch" }
        entry.schema.valuesFrom(effect)
    }
    fun snapshot(): Map<String, ParameterValue> = entry.schema.valuesFrom(effect).snapshot()
}
