package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** Source and behavior contracts exposed by the published 0.1.3 data class. */
class WaveCompatibilityTest {
    @Test
    fun copyAndDestructuringKeepPublishedConstructorOrderAndDefaults() {
        val original = WaveEffect(12f, 7f, false, 2f)
        val unchanged: WaveEffect = original.copy()
        assertNotSame(original, unchanged)

        val (amplitude, frequency, animate, time) = unchanged
        assertEquals(12f, amplitude)
        assertEquals(7f, frequency)
        assertEquals(false, animate)
        assertEquals(2f, time)

        val changed: WaveEffect = original.copy(frequency = 9f, time = 4f)
        assertEquals(12f, changed.amplitude)
        assertEquals(9f, changed.frequency)
        assertEquals(false, changed.animate)
        assertEquals(4f, changed.time)
        assertEquals("WaveEffect(amplitude=12.0, frequency=7.0, animate=false, time=2.0)", original.toString())
    }

    @Test
    fun timeUpdatesKeepConcreteReturnTypeAndAllocateEvenForUnchangedTime() {
        val original = WaveEffect(12f, 7f, false, 2f)
        val unchanged: WaveEffect = original.withTime(2f)
        val changed: WaveEffect = original.withTime(4f)

        assertNotSame(original, unchanged)
        assertNotSame(original, changed)
        assertEquals(4f, changed.time)
        assertEquals(original, changed)
        assertEquals(original.hashCode(), changed.hashCode())
        assertSame(original.shaderSource, changed.shaderSource)
        assertSame(original.parameterHandlers, changed.parameterHandlers)
    }

    @Test
    fun typedUpdatesKeepConcreteReturnTypeAndUnchangedCopyBehavior() {
        val original = WaveEffect(12f, 7f, false, 2f)
        val unchanged: WaveEffect = original.withTypedParameter(
            WaveEffect.PARAM_AMPLITUDE, ParameterValue.FloatValue(12f),
        )
        val changed: WaveEffect = original.withTypedParameter(
            WaveEffect.PARAM_AMPLITUDE, ParameterValue.FloatValue(20f),
        )
        val unknown: WaveEffect = original.withTypedParameter(
            "unknown", ParameterValue.FloatValue(1f),
        )

        assertNotSame(original, unchanged)
        assertEquals(20f, changed.amplitude)
        assertEquals(7f, changed.frequency)
        assertEquals(false, changed.animate)
        assertEquals(2f, changed.time)
        assertSame(original, unknown)
        assertSame(original.parameterHandlers, changed.parameterHandlers)
    }
}
