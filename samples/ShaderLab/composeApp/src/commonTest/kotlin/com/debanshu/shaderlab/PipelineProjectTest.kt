package com.debanshu.shaderlab

import com.debanshu.shaderlab.authoring.CustomDefinitions
import com.debanshu.shaderlab.playground.*
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import kotlin.test.*

class PipelineProjectTest {
    private val entry = PlaygroundEntry(GradientEffect(), "Built-in")
    private val registry = PlaygroundRegistry(listOf(entry, PlaygroundEntry(CustomDefinitions.distortion.animatedEffect(), "Custom")))
    private fun initial() = PipelineProject(listOf(
        PipelineChild("first-gradient", PlaygroundProject(entry)),
        PipelineChild("second-gradient", PlaygroundProject(entry)),
    ))

    @Test fun customAnimateBooleanRemainsEditableWhileKnownPlaybackFlagsAreHidden() {
        val toggle = com.debanshu.shaderlab.shaderx.parameter.BooleanParameterDefinition("custom_animate", "animate", true)
        val schema = com.debanshu.shaderlab.shaderx.parameter.ParameterSchema("custom_animate", listOf(toggle))
        val definition = ShaderDefinition("custom_animate", schema = schema, program = ShaderProgram(
            "uniform shader content; uniform float animate; half4 main(float2 p) { return content.eval(p) * animate; }",
            listOf(ShaderBinding.boolean("animate", toggle.key))))
        for (effect in listOf(definition.effect(), definition.animatedEffect())) {
            val project = PlaygroundProject(PlaygroundEntry(effect, "Custom"))
            assertEquals(listOf("animate"), pipelineControlDefinitions(project).map { it.key.id })
        }
        val wave = PlaygroundProject(PlaygroundEntry(com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect(), "Built-in"))
        assertFalse(pipelineControlDefinitions(wave).any { it.key.id == "animate" })
    }

    @Test fun repeatedDefinitionsKeepValuesAndAdaptLegacyAddressesAfterReorder() {
        val parameter = entry.schema.definitions.first().key.id
        val original = initial()
        val updated = original.update("second-gradient", parameter, ParameterValue.ColorValue(0x80402010L))
        assertNotEquals(updated.children[0].project.snapshot(), updated.children[1].project.snapshot())
        val moved = updated.move("second-gradient", 0)
        assertEquals("0\u001F$parameter", moved.address("second-gradient", parameter))
        assertEquals("1\u001F$parameter", moved.address("first-gradient", parameter))
        assertEquals(ParameterValue.ColorValue(0x80402010L), moved.effect().getTypedParameterValue(moved.address("second-gradient", parameter)))
        assertEquals(original.children[0].project.snapshot(), original.children[1].project.snapshot())
        assertFailsWith<IllegalArgumentException> { moved.address("missing", parameter) }
        assertFailsWith<IllegalArgumentException> { moved.address("first-gradient", "missing") }
    }

    @Test fun versionedPresetPreservesStableKeysOrderExactValuesAndRejectsWholeInvalidInput() {
        val project = initial().move("second-gradient", 0)
        val text = PipelinePresetCodec.encode(project)
        val loaded = PipelinePresetCodec.decode(text, registry)
        assertEquals(project.children.map { it.key }, loaded.children.map { it.key })
        assertEquals(project.children.map { it.project.snapshot() }, loaded.children.map { it.project.snapshot() })
        assertEquals(text, PipelinePresetCodec.encode(loaded))
        assertFailsWith<IllegalArgumentException> { PipelinePresetCodec.decode(text.replace("Pipeline 1", "Pipeline 2"), registry) }
        assertFailsWith<IllegalArgumentException> { PipelinePresetCodec.decode(text.replace("first-gradient", "second-gradient"), registry) }
        assertFailsWith<IllegalArgumentException> { PipelinePresetCodec.decode(text + "broken\nzz\n", registry) }
        assertFailsWith<IllegalArgumentException> { PipelinePresetCodec.decode(PresetCodec.encode(entry.starter, entry), registry) }
        assertFailsWith<IllegalArgumentException> { PipelinePresetCodec.decode(text, PlaygroundRegistry(emptyList())) }
        assertFailsWith<IllegalArgumentException> { PipelineChild("0\u001Fcolor", PlaygroundProject(entry)) }
        assertFailsWith<IllegalArgumentException> { PipelineProject(emptyList()) }
        assertFailsWith<IllegalArgumentException> { PipelineProject(List(17) { PipelineChild("key$it", PlaygroundProject(entry)) }) }
        assertFailsWith<IllegalArgumentException> { PipelinePresetCodec.decode("x".repeat(PipelinePresetCodec.MAX_BYTES + 1), registry) }
        assertEquals(project.children.map { it.key }, listOf("second-gradient", "first-gradient"))
    }

    @Test fun generatorPreservesSourceAndDisplayTextWhileRenamingOnlyGeneratedObjects() {
        val source = "// object ExportedShader { should survive\nuniform shader content; half4 main(float2 p) { return content.eval(p); }"
        val definition = ShaderDefinition("named", displayName = "ExportedShader", program = ShaderProgram(source))
        val named = PlaygroundEntry(definition.effect(), "Custom")
        val output = PipelineKotlinExport.generate(PipelineProject(listOf(PipelineChild("named", PlaygroundProject(named)))))
        assertTrue(output.contains(KotlinExport.quote(source)))
        assertTrue(output.contains("displayName = \"ExportedShader\""))
        assertTrue(output.contains("object PipelineChild0 {"))
        assertFalse(output.contains("rememberShaderPlayback(active ="), "Static exported previews own no clock")
        assertTrue(output.contains("Modifier.shaderEffect(effect"))
    }

    @Test fun generatorKeepsKeysAndEmitsSharedClockWhileRejectingImplicitLiveInput() {
        val project = PipelineProject(initial().children + PipelineChild("animated", PlaygroundProject(registry.entry("custom_distortion"))))
        val source = PipelineKotlinExport.generate(project)
        assertTrue(source.contains("\"second-gradient\" to PipelineChild1.effect()"))
        assertTrue(source.contains("CompositeEffect.of(children().values.toList())"))
        assertTrue(source.contains("controlledShaderEffect(effect, playback"))
        assertEquals(1, source.lines().count { it.startsWith("import androidx.compose.runtime.Composable") })
        val definition = ShaderDefinition("live", program = ShaderProgram("uniform shader content; uniform float live; half4 main(float2 p) { return content.eval(p) * live; }", listOf(ShaderBinding.frameFloat("live"))))
        val liveEntry = PlaygroundEntry(definition.animatedEffect(frameProvider = com.debanshu.shaderlab.shaderx.authoring.ShaderFrameProvider { listOf(com.debanshu.shaderlab.shaderx.uniform.FloatUniform("live", 1f)) }), "Custom")
        assertFailsWith<IllegalArgumentException> { PipelineKotlinExport.generate(PipelineProject(listOf(PipelineChild("live", PlaygroundProject(liveEntry))))) }
    }
}
