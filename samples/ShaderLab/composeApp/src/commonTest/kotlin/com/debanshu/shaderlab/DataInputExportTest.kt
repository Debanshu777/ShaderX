package com.debanshu.shaderlab

import com.debanshu.shaderlab.playground.*
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.uniform.*
import kotlin.test.*

/** Actual output is also compiled by separate Android/KMP consumers. */
object DataInputExportFixture {
    fun structural() = ShaderDefinition("inputs", program = ShaderProgram(
        "uniform shader content; uniform shader raw; uniform shader child; half4 main(float2 p) { return child.eval(p); }",
        listOf(ShaderBinding.data("raw", Rgba8Data(1, 1, byteArrayOf(19, 37, 71, 0)), DataSampling.LINEAR, ShaderTileMode.MIRROR, ShaderTileMode.REPEAT),
            ShaderBinding.nested("child", ShaderNode.snapshot(ShaderDefinition("child", program = ShaderProgram("half4 main(float2 p) { return half4(0.2, 0.3, 0.4, 1.0); }")).proceduralBackground()))))).effect()

    fun portable() = ShaderDefinition("portable-data", program = ShaderProgram(
        "uniform shader content; uniform shader spectrum; half4 main(float2 p) { half4 d = spectrum.eval(float2(0.5, 0.5)); return half4(d.r, 0.0, 0.0, 1.0); }",
        listOf(ShaderBinding.nested("spectrum", ShaderNode.rgba8Sampler(DataRecipes.fftBins(floatArrayOf(.25f, .75f))))))).effect()

    fun live() = ShaderDefinition("live-inputs", program = ShaderProgram(
        "uniform shader content; uniform shader raw; uniform shader child; half4 main(float2 p) { return child.eval(p); }",
        listOf(ShaderBinding.frameData("raw"), ShaderBinding.frameNested("child")))).animatedEffect(
        isAnimating = false, frameProvider = { error("Export must not invoke providers") })
}

class DataInputExportTest {
    @Test fun exporterDescribesCallerRequirementsWithoutPayloadsOrProviderCalls() {
        val code = KotlinExport.generate(DataInputExportFixture.structural())
        assertTrue(code.contains("input0: Rgba8Data"))
        assertTrue(code.contains("input1: ShaderNode"))
        assertTrue(code.contains("DataSampling.LINEAR, ShaderTileMode.MIRROR, ShaderTileMode.REPEAT"))
        assertFalse(code.contains("byteArrayOf"))
        assertFalse(code.contains("ShaderNode.snapshot("))
        val live = KotlinExport.generate(DataInputExportFixture.live())
        assertTrue(live.contains("frameProvider: ShaderFrameProvider"))
        assertTrue(live.contains("inputRevision: Long"))
        assertTrue(live.contains("ShaderBinding.frameData(\"raw\")"))
        assertTrue(live.contains("ShaderBinding.frameNested(\"child\")"))
    }

    @Test fun inputDescriptionsRoundTripButRequireExplicitRebinding() {
        val draft = WorkbenchDraft("uniform shader raw; half4 main(float2 p) { return half4(raw.eval(p).rgb, 1.0); }",
            output = WorkbenchOutput.BACKGROUND,
            inputs = listOf(WorkbenchInputRequirement("raw", WorkbenchInputKind.DATA, DataSampling.LINEAR, ShaderTileMode.REPEAT)))
        val text = WorkbenchProjectCodec.encode(draft)
        assertEquals(draft, WorkbenchProjectCodec.decode(text))
        assertFalse(text.contains("byteArrayOf"))
        assertFails { WorkbenchProjectCodec.decode(text.replace("DATA LINEAR", "UNKNOWN LINEAR")) }
        assertFails { WorkbenchProjectCodec.decode(text.replace("DATA LINEAR", "DATA CUBIC")) }
        assertFails { draft.copy(inputs = List(33) { WorkbenchInputRequirement("data$it", WorkbenchInputKind.DATA) }).checkBounds() }
        assertFails { draft.build(reboundInputs = mapOf("raw" to FloatUniform("raw", .5f))) }
        assertFailsWith<IllegalArgumentException> { draft.build() }
        val project = draft.build(reboundInputs = mapOf("raw" to DataUniform("raw", DataRecipes.fftBins(floatArrayOf(.5f)))))
        assertEquals("workbench", project.effect.id)
        val state = WorkbenchState(draft)
        val run = state.beginRun()
        val candidate = run.build(reboundInputs = mapOf("raw" to DataUniform("raw", DataRecipes.fftBins(floatArrayOf(.5f)))))
        state.edit(draft.copy(source = draft.source + "\n// next"))
        assertFalse(state.promote(run, candidate))
        assertNull(state.lastGood)
    }

    @Test fun draftStateCopiesCallerRequirementLists() {
        val requirements = mutableListOf(WorkbenchInputRequirement("raw", WorkbenchInputKind.DATA))
        val state = WorkbenchState(WorkbenchStarters.background.copy(inputs = requirements))
        requirements.clear()
        assertEquals("raw", state.draft.inputs.single().name)
        val next = mutableListOf(WorkbenchInputRequirement("child", WorkbenchInputKind.NESTED))
        state.edit(state.draft.copy(inputs = next))
        next.clear()
        assertEquals("child", state.beginRun().draft.inputs.single().name)
    }

    @Test fun fftRecipeIsExplicitQuantizationWithNoCoreCapture() {
        assertContentEquals(byteArrayOf(0, 0, 0, -1, -128, 0, 0, -1, -1, 0, 0, -1), DataRecipes.fftBins(floatArrayOf(0f, .5f, 1f)).bytes())
        assertFails { DataRecipes.fftBins(floatArrayOf(Float.NaN)) }
        assertFails { DataRecipes.fftBins(floatArrayOf(1.01f)) }
        assertFails { DataRecipes.fftBins(FloatArray(4097)) }
    }
}
