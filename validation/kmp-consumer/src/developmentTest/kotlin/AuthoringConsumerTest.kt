package shaderx.consumer

import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.parameter.ColorParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterRangePolicy
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.uniform.ColorUniform
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.TextureUniform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class AuthoringConsumerTest {
    @Test
    fun editableTintNeedsNoConsumerDispatchAndPreservesIndependentValues() {
        val original = ConsumerTint.effect()
        val changed = original.withValue(ConsumerTint.COLOR.key, 0x20402010L)
        assertEquals(0x804080C0L, original[ConsumerTint.COLOR.key])
        assertEquals(0x20402010L, changed[ConsumerTint.COLOR.key])
        assertNotEquals(original, changed)
        assertEquals(changed, ConsumerTint.effect(0x20402010L))
        assertEquals(changed.hashCode(), ConsumerTint.effect(0x20402010L).hashCode())
        assertEquals(ParameterValue.ColorValue(0x20402010L), changed.getTypedParameterValue("color"))
        assertEquals(changed, original.withTypedParameter("color", ParameterValue.ColorValue(0x20402010L)))
        assertEquals(listOf(ColorUniform("tintColor", 0x20402010L)), changed.buildUniforms(32f, 16f))
        val foreign = ColorParameterDefinition("consumer_tint", "color", 0x804080C0L)
        assertFailsWith<IllegalArgumentException> { original.withValue(foreign.key, 0xFF000000L) }
    }

    @Test
    fun distortionUsesPixelResolutionAndTimeAliasesWithGeneratedEquality() {
        val original = ConsumerDistortion.effect(8f)
        val advanced = original.withTime(2f)
        assertEquals(original, advanced)
        assertEquals(original.hashCode(), advanced.hashCode())
        assertEquals(8f, original[ConsumerDistortion.AMPLITUDE.key])
        assertEquals(2f, advanced.time)
        val uniforms = advanced.buildUniforms(80f, 40f)
        assertEquals(FloatUniform("viewport", 80f, 40f), uniforms.first { it.name == "viewport" })
        assertEquals(FloatUniform("elapsed", 2f), uniforms.first { it.name == "elapsed" })
        assertEquals(FloatUniform("strength", 8f), uniforms.first { it.name == "strength" })
        assertFailsWith<IllegalArgumentException> { original.withValue(ConsumerDistortion.AMPLITUDE.key, 99f) }
        assertEquals(32f, original.withValue(ConsumerDistortion.AMPLITUDE.key, 99f, ParameterRangePolicy.CLAMP)[ConsumerDistortion.AMPLITUDE.key])
        assertEquals(8f, original.getParameterValue("amplitude"))
    }

    @Test
    fun allThreeExamplesCompileAndOwnIndependentSessionsThroughPublicFactory() {
        val factory = ConsumerFactory(ShaderFactory.create())
        try {
            factory.createRenderEffect(ConsumerTint.effect(), 32f, 16f).getOrThrow()
            factory.createRenderEffect(ConsumerTint.effect(0x40C08020L), 32f, 16f).getOrThrow()
            val bitmap = ImageBitmap(2, 2)
            val image = imageOverlay(bitmap)
            assertEquals(emptyList(), image.parameters)
            assertSame(bitmap, (image.buildUniforms(32f, 16f).single() as TextureUniform).image)
            factory.createRenderEffect(image, 32f, 16f).getOrThrow()

            val first = ConsumerDistortion.effect(4f)
            val second = ConsumerDistortion.effect(12f)
            val firstSession = factory.createAnimatedSession(first, 32f, 16f).getOrThrow()
            try {
                val secondSession = factory.createAnimatedSession(second, 32f, 16f).getOrThrow()
                try {
                    firstSession.refresh(first.withTime(1f), 32f, 16f).getOrThrow()
                    secondSession.refresh(second.withTime(2f), 32f, 16f).getOrThrow()
                    factory.clearCache()
                    firstSession.refresh(first.withTime(3f), 32f, 16f).getOrThrow()
                } finally {
                    secondSession.close()
                }
            } finally {
                firstSession.close()
            }
        } finally {
            factory.close()
        }
    }
}
