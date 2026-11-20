package com.debanshu.shaderlab

import com.debanshu.shaderlab.playground.*
import com.debanshu.shaderlab.shaderx.parameter.*
import kotlin.test.*

class CollectionPresetTest {
    @Test fun presetLoadReplacesAlreadySelectedNeutralDefinition() {
        val model = com.debanshu.shaderlab.viewmodel.ShaderLabViewModel()
        val preset = CollectionPresets.all.last()
        model.setActiveEffect(preset.definition.effect())
        assertEquals(0f, model.uiState.value.activeEffect?.getParameterValue("amount"))
        model.loadPreset(preset.effect())
        assertEquals(1f, model.uiState.value.activeEffect?.getParameterValue("amount"))
    }

    @Test fun namedPresetsRetainRichSchemaAndRoundTripExactly() {
        val registry = PlaygroundRegistry.standard()
        assertEquals(4, CollectionPresets.all.size)
        for (preset in CollectionPresets.all) {
            val effect = preset.effect()
            val entry = registry.entry(effect.id)
            assertSame(preset.definition.schema, entry.schema)
            assertEquals(1, preset.schemaVersion)
            val restored = PresetCodec.decode(PresetCodec.encode(effect, entry), registry.entries)
            assertEquals(entry.schema.valuesFrom(effect), entry.schema.valuesFrom(restored))
            val code = KotlinExport.generate(effect)
            assertTrue(code.contains("ShaderDefinition("))
            assertTrue(code.contains("ParameterMetadata("))
            val bounded = entry.schema.definitions.filterIsInstance<FloatParameterDefinition>()
            assertTrue(bounded.all { it.range != null })
        }
        assertEquals("px", registry.entry("ordered_dither").schema.definition("cellSize").let { it as FloatParameterDefinition }.unit)
    }
    @Test fun printStarterKeepsOrderedIndependentValues() {
        val registry = PlaygroundRegistry.standard()
        val pipeline = CollectionPresets.printPipeline(registry)
        assertEquals(listOf("color_adjustment", "ordered_dither"), pipeline.children.map { it.project.effect.id })
        val restored = PipelinePresetCodec.decode(PipelinePresetCodec.encode(pipeline), registry)
        assertEquals(pipeline.children.map { it.project.snapshot() }, restored.children.map { it.project.snapshot() })
        assertEquals(ParameterValue.FloatValue(1f), restored.children[1].project.snapshot()["amount"])
    }
}
