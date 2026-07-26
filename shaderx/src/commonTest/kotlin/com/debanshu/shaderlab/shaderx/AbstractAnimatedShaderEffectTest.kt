package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.AbstractAnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Contract tests for [AbstractAnimatedShaderEffect] and the animated-effect equality rules.
 */
class AbstractAnimatedShaderEffectTest {
    /** Minimal concrete implementation for base-class contract tests. */
    private class TestAnimatedEffect(
        val speed: Float = 1f,
        override val isAnimating: Boolean = true,
        override val time: Float = 0f,
    ) : AbstractAnimatedShaderEffect() {
        override val id: String = "test_animated"
        override val displayName: String = "Test Animated"
        override val shaderSource: String =
            """
            uniform shader content;
            uniform float time;
            uniform float speed;
            half4 main(float2 fragCoord) {
                return content.eval(fragCoord);
            }
            """.trimIndent()

        override val parameterHandlers: Map<String, com.debanshu.shaderlab.shaderx.effect.ParamHandler<*>> =
            emptyMap()

        override fun buildUniforms(
            width: Float,
            height: Float,
        ): List<Uniform> =
            listOf(
                FloatUniform("time", time),
                FloatUniform("speed", speed),
            )

        override fun copyWithTime(newTime: Float): TestAnimatedEffect = TestAnimatedEffect(speed, isAnimating, newTime)

        override fun structuralEquals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TestAnimatedEffect) return false
            return speed == other.speed && isAnimating == other.isAnimating
        }

        override fun structuralHashCode(): Int {
            var result = speed.hashCode()
            result = 31 * result + isAnimating.hashCode()
            return result
        }
    }

    @Test
    fun abstractAnimated_equalityExcludesTime() {
        val a = TestAnimatedEffect(speed = 1f, time = 0f)
        val b = TestAnimatedEffect(speed = 1f, time = 2.5f)
        assertEquals(a, b, "Different time must not affect equality")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun abstractAnimated_equalityHonorsConfig() {
        val a = TestAnimatedEffect(speed = 1f)
        val b = TestAnimatedEffect(speed = 2f)
        assertNotEquals(a, b)
    }

    @Test
    fun abstractAnimated_withTime_preservesEquality() {
        val original = TestAnimatedEffect(speed = 1f)
        val updated = original.withTime(42f) as TestAnimatedEffect
        assertEquals(original, updated)
        assertEquals(42f, updated.time)
    }

    @Test
    fun abstractAnimated_isAnimatedShaderEffect() {
        val effect: AnimatedShaderEffect = TestAnimatedEffect()
        assertTrue(effect is AbstractAnimatedShaderEffect)
    }

    @Test
    fun waveEffect_stillSatisfiesAnimatedContract() {
        val frame0 = WaveEffect(amplitude = 10f, time = 0f)
        val frame1 = WaveEffect(amplitude = 10f, time = 0.016f)
        assertEquals(frame0, frame1)
    }
}
