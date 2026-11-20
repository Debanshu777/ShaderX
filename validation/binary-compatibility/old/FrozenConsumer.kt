package shaderx.binary

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.effect.*
import com.debanshu.shaderlab.shaderx.effect.impl.GradientEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.*
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform

// Compile this file ONLY against the real Maven 0.1.3 jar, then freeze its class bytes.
class FrozenDirectFactory : ShaderFactory {
    override val cacheSize = 0
    override fun isSupported() = true
    override fun clearCache() {}
    override fun close() {}
    override fun createRenderEffect(effect: ShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> =
        ShaderResult.failure(ShaderError.UnsupportedEffect("old direct implementation", effect.id))
}

class FrozenBaseFactory : BaseShaderFactory(7) {
    override val cacheSize = 0
    override fun isSupported() = true
    override fun clearCache() {}
    override fun close() {}
    override fun createNativeEffect(effect: NativeEffect): ShaderResult<RenderEffect> =
        ShaderResult.failure(ShaderError.UnsupportedEffect("old native implementation", effect.id))
    override fun createRuntimeShaderEffect(effect: RuntimeShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> =
        ShaderResult.failure(ShaderError.UnsupportedEffect("old base implementation", effect.id))
}

object FrozenConsumer {
    @JvmStatic fun exercise(): String {
        val initial = GradientEffect(intensity = 0.5f)
        check(initial.copy(intensity = 0.25f).component3() == 0.25f)
        val edited = initial.withTypedParameter(GradientEffect.PARAM_INTENSITY, ParameterValue.FloatValue(0.75f))
        check(edited.getParameterValue(GradientEffect.PARAM_INTENSITY) == 0.75f)
        check(initial.withParameter(GradientEffect.PARAM_INTENSITY, 999f).getParameterValue(GradientEffect.PARAM_INTENSITY) == 1f)
        val wave = WaveEffect(amplitude = 9f, frequency = 4f, animate = false, time = 2f)
        check(wave.withTime(3f).time == 3f)
        check(wave.buildUniforms(32f, 24f).isNotEmpty())
        val uniform = FloatUniform("amount", 0.5f)
        check(uniform.copy(values = floatArrayOf(0.75f)).values.single() == 0.75f)
        val success = ShaderResult.success(7).map { it + 1 }
        check(success.getOrThrow() == 8)
        for (factory in listOf(FrozenDirectFactory(), FrozenBaseFactory())) {
            val failure = factory.createRenderEffect(initial, 32f, 24f)
            check(failure is ShaderResult.Failure && failure.error is ShaderError.UnsupportedEffect)
            factory.clearCache()
            factory.close()
        }
        // Calls the old Companion/create$default descriptor on the new platform implementation.
        ShaderFactory.create().use { factory ->
            check(factory.createRenderEffect(initial, 32f, 24f) is ShaderResult.Success)
        }
        return "ordinary-effects-parameters-results-and-factories=PASS"
    }
}
