package com.debanshu.shaderlab.playground

import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.effect.impl.EffectCollection
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue

/** Named settings, separate from neutral SDK defaults. Stable recipe IDs; exchanged presets persist definition ID, schema version and exact values. */
class CollectionPreset(val id: String, val label: String, val definition: ShaderDefinition, edits: Map<String, Float>) {
    val schemaVersion = 1
    private val values = edits.entries.fold(definition.schema.defaults()) { values, (id, value) ->
        values.withValue(id, ParameterValue.FloatValue(value))
    }
    fun effect() = definition.effect(values)
}

object CollectionPresets {
    val all = listOf(
        CollectionPreset("color-punch-v1", "Color punch", EffectCollection.colorAdjustment,
            mapOf("brightness" to .05f, "contrast" to 1.2f, "saturation" to 1.25f)),
        CollectionPreset("matrix-red-blue-v1", "Swap red / blue", EffectCollection.colorMatrix,
            mapOf("m00" to 0f, "m02" to 1f, "m20" to 1f, "m22" to 0f)),
        CollectionPreset("dither-print-v1", "Four-tone print", EffectCollection.orderedDither,
            mapOf("amount" to 1f, "levels" to 4f, "cellSize" to 2f)),
        CollectionPreset("halftone-news-v1", "Newsprint dots", EffectCollection.halftone,
            mapOf("amount" to 1f, "cellSize" to 8f)),
    )
    fun printPipeline(registry: PlaygroundRegistry): PipelineProject = PipelineProject(
        listOf(all[0], all[2]).mapIndexed { index, preset ->
            PipelineChild("print-${index + 1}", PlaygroundProject(registry.entry(preset.definition.id), preset.effect()))
        },
    )
}
