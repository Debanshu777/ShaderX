package io.github.debanshu.shaderx.validation

import com.debanshu.shaderlab.shaderx.authoring.DefinedShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.*
import kotlin.test.*

class GeneratedCollectionConsumerTest {
    @Test fun exactGeneratedPresetsCompileWithRichSchemaAndValues() {
        val effects = listOf(
            sx09.generated.case0.ExportedShader.effect(), sx09.generated.case1.ExportedShader.effect(),
            sx09.generated.case2.ExportedShader.effect(), sx09.generated.case3.ExportedShader.effect(),
        ).map { it as DefinedShaderEffect }
        assertEquals(listOf("color_adjustment", "color_matrix", "ordered_dither", "halftone"), effects.map { it.id })
        assertEquals(ParameterValue.FloatValue(.05f), effects[0].getTypedParameterValue("brightness"))
        assertEquals(ParameterValue.FloatValue(1f), effects[1].getTypedParameterValue("m02"))
        assertEquals(ParameterValue.FloatValue(4f), effects[2].getTypedParameterValue("levels"))
        assertEquals(ParameterValue.FloatValue(8f), effects[3].getTypedParameterValue("cellSize"))
        val size = effects[2].definition.schema.definition("cellSize") as FloatParameterDefinition
        assertEquals("px", size.unit)
        assertNotNull(size.metadata.semanticRole)
        assertEquals(1f..64f, size.range)
        effects.forEach { assertTrue(it.buildUniforms(32f, 32f).isNotEmpty()) }
    }
}
