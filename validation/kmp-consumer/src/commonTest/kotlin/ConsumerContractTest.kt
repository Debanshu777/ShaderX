package shaderx.consumer

import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConsumerContractTest {
    @Test
    fun editableFixtureRetainsItsCurrentValue() {
        val updated = EditableEffect().withTypedParameter("amount", ParameterValue.FloatValue(0.75f))
        assertEquals(0.75f, updated.getParameterValue("amount"))
        assertEquals(ParameterValue.FloatValue(0.75f), updated.getTypedParameterValue("amount"))
        assertEquals(listOf(FloatUniform("amount", 0.75f)), updated.buildUniforms(16f, 16f))
    }

    @Test
    fun publicFactoryAcceptsExternalEffects() {
        val factory = ConsumerFactory(ShaderFactory.create())
        try {
            assertNotNull(factory.createRenderEffect(MinimalEffect(), 16f, 16f).getOrNull())
            assertNotNull(factory.createRenderEffect(EditableEffect(), 16f, 16f).getOrNull())
            assertEquals(AnimatedEffect(), AnimatedEffect().withTime(1f))
        } finally {
            factory.close()
        }
    }
}
