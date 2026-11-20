package com.debanshu.shaderlab

import com.debanshu.shaderlab.playground.*
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import kotlin.test.*

class WorkbenchModelTest {
    @Test fun supportedStartersHaveMatchingDeclarations() {
        for (draft in listOf(WorkbenchStarters.tint, WorkbenchStarters.distortion, WorkbenchStarters.animated)) {
            val project = draft.build()
            assertEquals("workbench", project.effect.id)
            assertTrue(project.entry.schema.definitions.isNotEmpty())
        }
    }
    @Test fun proceduralOutputRejectsContentAndPreservesOutputAcrossDraftFiles() {
        val background = WorkbenchStarters.background.copy(currentValues = mapOf("intensity" to ParameterValue.FloatValue(.375f)))
        val project = background.build()
        assertEquals(ParameterValue.FloatValue(.375f), project.snapshot()["intensity"])
        assertEquals(background, WorkbenchProjectCodec.decode(WorkbenchProjectCodec.encode(background)))
        assertFails { WorkbenchStarters.tint.copy(output = WorkbenchOutput.BACKGROUND).build() }
        assertFails { background.copy(output = WorkbenchOutput.CONTENT).build() }
        val state = WorkbenchState(background)
        val run = state.beginRun()
        val candidate = run.build()
        state.edit(WorkbenchStarters.tint)
        assertFalse(state.promote(run, candidate))
        assertNull(state.lastGood)
    }
    @Test fun previousDraftFormatRestoresContentWithoutCertifyingSuccess() {
        val v3 = WorkbenchProjectCodec.encode(WorkbenchStarters.tint).lines().toMutableList()
        v3[0] = "ShaderLab-Workbench 2"
        v3.removeAt(5) // v3 adds the input-requirement count after the legacy input-name row.
        val v2 = v3.joinToString("\n")
        assertEquals(WorkbenchStarters.tint, WorkbenchProjectCodec.decode(v2))
        val v1 = v2.replace("ShaderLab-Workbench 2\nCONTENT\n", "ShaderLab-Workbench 1\n")
        assertEquals(WorkbenchStarters.tint, WorkbenchProjectCodec.decode(v1))
    }
    @Test fun renamedOrWrongTypedUniformFailsBeforeSilentSkiaSetter() {
        assertFailsWith<IllegalArgumentException> { WorkbenchStarters.tint.copy(source = WorkbenchStarters.tint.source.replace("uniform float intensity", "uniform float amount")).build() }
        assertFailsWith<IllegalArgumentException> { WorkbenchStarters.tint.copy(source = WorkbenchStarters.tint.source.replace("uniform float intensity", "uniform int intensity")).build() }
        assertFailsWith<IllegalArgumentException> { WorkbenchStarters.tint.copy(parameters = emptyList()).build() }
    }
    @Test fun tokenValidationIgnoresCommentsAndStringsButRejectsAmbiguousDeclarations() {
        val valid = "/* uniform float fake; */\n// uniform shader absent;\n\"uniform int hidden;\"\n" + WorkbenchStarters.tint.source
        assertEquals(2, WorkbenchDeclarations.validate(valid, mapOf("content" to "shader", "intensity" to "float")).size)
        for (declaration in listOf("uniform float intensity[2];", "uniform float intensity, other;", "uniform vec3 intensity;", "layout(foo) uniform float intensity;", "uniform highp float intensity;", "uniform half4 intensity;", "#define X 1\nuniform float intensity;")) {
            assertFailsWith<IllegalArgumentException>(declaration) { WorkbenchDeclarations.validate("uniform shader content;\n$declaration", mapOf("content" to "shader", "intensity" to "float")) }
        }
        assertFailsWith<IllegalArgumentException> { WorkbenchDeclarations.validate("/* unterminated", emptyMap()) }
    }
    @Test fun runsAndDelayedImageResultsCannotPromoteAfterAnyEditOrReset() {
        val state = WorkbenchState()
        val initial = state.beginRun()
        val project = initial.build()
        assertFalse(state.promote(initial, initial.draft.build()))
        assertTrue(state.promote(initial, project))
        assertFalse(state.stale)
        state.edit(state.draft.copy(source = state.draft.source + "\n// draft"))
        assertTrue(state.stale)
        assertFalse(state.promote(initial, project))
        assertFalse(state.fail(initial, "late compiler error"))
        assertNull(state.error)
        val broken = state.beginRun()
        assertTrue(state.fail(broken, "unrecognized raw diagnostic: backend X"))
        assertEquals("unrecognized raw diagnostic: backend X", state.error)
        assertSame(project, state.lastGood!!.project)
        state.reset()
        assertFalse(state.promote(broken, project))
        assertNull(state.lastGood)
    }
    @Test fun valuesRemainDraftUntilSuccessfulAtomicRenderPromotion() {
        val state = WorkbenchState()
        val first = state.beginRun()
        state.promote(first, first.build())
        val previous = state.lastGood
        state.setValue("intensity", ParameterValue.FloatValue(.9f))
        assertTrue(state.stale)
        assertSame(previous, state.lastGood)
        val next = state.beginRun()
        val project = next.build()
        assertEquals(ParameterValue.FloatValue(.9f), project.snapshot()["intensity"])
        assertTrue(state.promote(next, project))
        assertFalse(state.stale)
        assertEquals(ParameterValue.FloatValue(.9f), state.lastGood!!.project.snapshot()["intensity"])
        assertFailsWith<IllegalArgumentException> { next.draft.copy(currentValues = mapOf("intensity" to ParameterValue.FloatValue(2f))).build() }
    }
    @Test fun projectRoundTripPreservesBrokenEditableDraftButNotLastGoodCertification() {
        val draft = WorkbenchStarters.tint.copy(source = "broken \"\"\" \$x \\ \n 你好", parameters = listOf(WorkbenchParameter(defaultValue = "bad", minimum = "", maximum = "")), currentValues = mapOf("intensity" to ParameterValue.FloatValue(.25f)))
        val decoded = WorkbenchProjectCodec.decode(WorkbenchProjectCodec.encode(draft))
        assertEquals(draft, decoded)
        val state = WorkbenchState(decoded)
        assertTrue(state.stale)
        assertNull(state.lastGood)
        assertFailsWith<IllegalArgumentException> { decoded.build() }
    }
    @Test fun structuralFormEditsPruneOnlyAffectedOverrideAndRetainOtherExactValues() {
        val other = WorkbenchParameter(id = "other", label = "Other", uniformName = "other")
        val draft = WorkbenchStarters.tint.copy(
            source = "uniform float other;\n" + WorkbenchStarters.tint.source,
            parameters = WorkbenchStarters.tint.parameters + other,
            currentValues = mapOf("intensity" to ParameterValue.FloatValue(.9f), "other" to ParameterValue.FloatValue(.12345679f)),
        )
        for (next in listOf(draft.parameters[0].copy(id = "renamed"), draft.parameters[0].copy(defaultValue = "0.2"),
            draft.parameters[0].copy(type = WorkbenchParameterType.BOOLEAN, defaultValue = "true"))) {
            val state = WorkbenchState(draft)
            state.editParameter(0, next)
            assertFalse(state.draft.currentValues.containsKey("intensity"))
            assertEquals(ParameterValue.FloatValue(.12345679f), state.draft.currentValues["other"])
            if (next.type == WorkbenchParameterType.BOOLEAN) state.edit(state.draft.copy(source = state.draft.source.replace("uniform float intensity;", "uniform int intensity;")))
            assertEquals(ParameterValue.FloatValue(.12345679f), state.draft.build().snapshot()["other"])
        }
        val state = WorkbenchState(draft)
        state.editParameter(0, draft.parameters[0].copy(label = "Adjusted label"))
        assertEquals(ParameterValue.FloatValue(.9f), state.draft.currentValues["intensity"])
        state.removeParameter(0)
        assertFalse(state.draft.currentValues.containsKey("intensity"))
        state.edit(state.draft.copy(source = state.draft.source.replace("uniform float intensity;\n", "").replace("warm, intensity)", "warm, 0.5)")))
        assertEquals(ParameterValue.FloatValue(.12345679f), state.draft.build().snapshot()["other"])
    }
    @Test fun explicitDefaultsRecoverAfterNarrowingRangeWithoutReplacingSourceOrForm() {
        val state = WorkbenchState(WorkbenchStarters.tint)
        state.setValue("intensity", ParameterValue.FloatValue(.875f))
        state.editParameter(0, state.draft.parameters[0].copy(maximum = "0.5"))
        assertFails { state.draft.build() }
        val before = state.draft
        state.edit(state.draft.copy(currentValues = emptyMap()))
        assertEquals(before.source, state.draft.source)
        assertEquals(before.parameters, state.draft.parameters)
        assertEquals(ParameterValue.FloatValue(.5f), state.draft.build().snapshot()["intensity"])
    }
    @Test fun pngPreflightBoundsBeforeNativeDecode() {
        fun header(width: Int, height: Int): ByteArray {
            val bytes = ByteArray(33)
            byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10).copyInto(bytes)
            "IHDR".encodeToByteArray().copyInto(bytes, 12)
            for ((offset, value) in listOf(16 to width, 20 to height)) for (i in 0..3) bytes[offset + i] = (value ushr ((3 - i) * 8)).toByte()
            return bytes
        }
        validateWorkbenchPng(header(1, 256))
        for (bytes in listOf(ByteArray(32), header(0, 1), header(257, 1), header(Int.MAX_VALUE, 1), header(-1, 1), ByteArray(WorkbenchLimits.IMAGE_BYTES + 1), header(1, 1).also { it[12] = 0 })) {
            assertFails { validateWorkbenchPng(bytes) }
        }
        // A header pass is only intake validation; actual native decode remains required.
    }
    @Test fun projectBoundsVersionsMalformedRowsAndImagesFailClosed() {
        val text = WorkbenchProjectCodec.encode(WorkbenchStarters.tint)
        for (bad in listOf(text.replace("Workbench 3", "Workbench 99"), text + "unexpected\n", text.take(20), text.replaceFirst("756e69666f726d", "zzzz"), "x".repeat(WorkbenchLimits.PROJECT_BYTES + 1))) {
            assertFails { WorkbenchProjectCodec.decode(bad) }
        }
        assertFails { WorkbenchStarters.tint.copy(source = "x".repeat(WorkbenchLimits.SOURCE_BYTES + 1)).build() }
        assertFails { WorkbenchStarters.tint.copy(parameters = List(33) { WorkbenchParameter() }).build() }
        assertFails { WorkbenchLimits.image(WorkbenchLimits.IMAGE_BYTES + 1, 1, 1) }
        assertFails { WorkbenchLimits.image(1, 2049, 1) }
        assertFails { WorkbenchStarters.image.build() }
    }
}
