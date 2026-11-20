package shaderx.consumer

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderSession
import com.debanshu.shaderlab.shaderx.result.ShaderResult

// Implement explicitly: delegation alone could hide newly required interface members.
class ConsumerFactory(private val delegate: ShaderFactory) : ShaderFactory {
    override val cacheSize: Int get() = delegate.cacheSize
    override fun isSupported(): Boolean = delegate.isSupported()
    override fun createRenderEffect(effect: ShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> =
        delegate.createRenderEffect(effect, width, height)
    override fun createAnimatedSession(effect: AnimatedShaderEffect, width: Float, height: Float): ShaderResult<ShaderSession> =
        delegate.createAnimatedSession(effect, width, height).map { ConsumerSession(it) }
    override fun clearCache() = delegate.clearCache()
    override fun close() = delegate.close()
}

// A downstream session implementation must also remain source-compatible.
class ConsumerSession(private val delegate: ShaderSession) : ShaderSession {
    override val currentRenderEffect: RenderEffect get() = delegate.currentRenderEffect
    override fun refresh(effect: RuntimeShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> =
        delegate.refresh(effect, width, height)
    override fun close() = delegate.close()
}
