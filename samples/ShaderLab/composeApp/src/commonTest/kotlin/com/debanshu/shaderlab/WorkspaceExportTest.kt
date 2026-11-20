package com.debanshu.shaderlab

import com.debanshu.shaderlab.playground.CollectionPresets
import com.debanshu.shaderlab.playground.PipelineKotlinExport
import com.debanshu.shaderlab.playground.PlaygroundRegistry
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.ui.PipelineWorkspace
import kotlin.test.*

/** A retained workspace must never present generated Kotlin for a superseded pipeline. */
class WorkspaceExportTest {
    @Test fun retainedPipelineInvalidatesExportForValueAndOrderChanges() {
        val registry = PlaygroundRegistry.standard()
        val workspace = PipelineWorkspace(registry)
        workspace.project = CollectionPresets.printPipeline(registry)
        val firstKey = workspace.project.children.first().key
        workspace.code = PipelineKotlinExport.generate(workspace.project)
        workspace.project = workspace.project.update(firstKey, "brightness", ParameterValue.FloatValue(0.3f))
        assertNull(workspace.code)
        assertEquals(firstKey, workspace.project.children.first().key)
        assertEquals(0.3f, workspace.project.children.first().project.effect.getParameterValue("brightness"))
        workspace.code = PipelineKotlinExport.generate(workspace.project)
        workspace.project = workspace.project.move(firstKey, 1)
        assertNull(workspace.code)
        assertEquals(firstKey, workspace.project.children.last().key)
        assertEquals(0.3f, workspace.project.children.last().project.effect.getParameterValue("brightness"))
    }
}
