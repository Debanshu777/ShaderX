package io.github.debanshu.shaderx.validation

import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import sx05.generated.ExportedPipeline
import kotlin.test.*

/** Invokes actual sample generator output compiled in a separate, non-friend consumer. */
class GeneratedPipelineConsumerTest {
    @Test fun generatedPipelinePreservesOrderedRepeatedInstancesAndTimeBindings() {
        val children = ExportedPipeline.children()
        assertEquals(listOf("tint-one", "distortion", "tint-two"), children.keys.toList())
        assertEquals(listOf("custom_tint", "custom_distortion", "custom_tint"), children.values.map { it.id })
        val composite = ExportedPipeline.effect()
        assertEquals(3, composite.size)
        assertTrue(composite.effects[1] is AnimatedShaderEffect)
        assertEquals(children.values.map { it.id }, composite.effects.map { it.id })
        assertEquals(composite.effects[0].getTypedParameterValue("tint"), composite.getTypedParameterValue("0\u001Ftint"))
    }
}
