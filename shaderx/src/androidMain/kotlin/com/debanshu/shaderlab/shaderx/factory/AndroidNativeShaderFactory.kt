package com.debanshu.shaderlab.shaderx.factory

import android.os.Build
import android.graphics.Shader
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.debanshu.shaderlab.shaderx.ShaderConstants
import com.debanshu.shaderlab.shaderx.effect.*
import com.debanshu.shaderlab.shaderx.result.*

/** API 31–32 route. No RuntimeShader fields, signatures, constructors or static initializers. */
internal class AndroidNativeShaderFactory(maxCacheSize: Int) : BaseShaderFactory(maxCacheSize), ShaderCapabilityProvider, ShaderPipelineProvider {
    override fun querySupport(operation: ShaderOperation, effect: ShaderEffect?, inputs: Set<ShaderInput>): ShaderSupport =
        builtInSupport(Build.VERSION.SDK_INT, operation, effect, inputs)
    override fun createPipelineSession(effect: CompositeEffect, width: Float, height: Float, timeSeconds: Float): ShaderResult<ShaderPipelineSession> =
        ShaderResult.failure(querySupport(ShaderOperation.ANIMATED_SESSION, effect, emptySet()).asError(ShaderOperation.ANIMATED_SESSION, effect))
    override fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 31
    override val supportsChaining: Boolean get() = isSupported()
    override val cacheSize: Int get() = 0
    override fun clearCache() {}
    override fun close() {}
    override fun platformNotSupportedError(): ShaderError = ShaderError.PlatformNotSupported("ShaderX requires Android API 31+")
    override fun createRuntimeShaderEffect(effect: RuntimeShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> =
        ShaderResult.failure(ShaderError.PlatformNotSupported("Runtime shaders require Android API 33+"))
    override fun createAnimatedSession(effect: AnimatedShaderEffect, width: Float, height: Float): ShaderResult<ShaderSession> =
        ShaderResult.failure(querySupport(ShaderOperation.ANIMATED_SESSION, effect, emptySet()).asError(ShaderOperation.ANIMATED_SESSION, effect))
    override fun createNativeEffect(effect: NativeEffect): ShaderResult<RenderEffect> = when (effect) {
        is BlurEffect -> ShaderResult.runCatching {
            val radius = effect.radius.coerceAtLeast(ShaderConstants.MIN_BLUR_RADIUS)
            android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP).asComposeRenderEffect()
        }
        else -> ShaderResult.failure(ShaderError.UnsupportedEffect("Unsupported native effect", effect.id))
    }
    override fun chainEffects(inner: RenderEffect, outer: RenderEffect): RenderEffect =
        android.graphics.RenderEffect.createChainEffect(outer.asAndroidRenderEffect(), inner.asAndroidRenderEffect()).asComposeRenderEffect()
}
