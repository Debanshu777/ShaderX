package shaderx.consumer

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.result.ShaderResult

// Implement explicitly: delegation alone could hide newly required interface members.
class ConsumerFactory(private val delegate: ShaderFactory) : ShaderFactory {
    override val cacheSize: Int get() = delegate.cacheSize
    override fun isSupported(): Boolean = delegate.isSupported()
    override fun createRenderEffect(effect: ShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> =
        delegate.createRenderEffect(effect, width, height)
    override fun clearCache() = delegate.clearCache()
    override fun close() = delegate.close()
}
