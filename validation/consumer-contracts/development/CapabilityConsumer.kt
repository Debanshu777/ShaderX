package shaderx.consumer

import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderCapabilityProvider
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderFallbackPolicy
import com.debanshu.shaderlab.shaderx.factory.ShaderInput
import com.debanshu.shaderlab.shaderx.factory.ShaderOperation
import com.debanshu.shaderlab.shaderx.factory.ShaderRenderOutcome
import com.debanshu.shaderlab.shaderx.factory.ShaderSupport
import com.debanshu.shaderlab.shaderx.factory.ShaderSupportReason
import com.debanshu.shaderlab.shaderx.factory.ShaderSupportStatus
import com.debanshu.shaderlab.shaderx.factory.createRenderEffectWithFallback
import com.debanshu.shaderlab.shaderx.factory.querySupport

/** An external adapter opts in explicitly; ConsumerFactory still implements only the original API. */
class CapabilityConsumerFactory(private val delegate: ShaderFactory) : ShaderFactory by delegate, ShaderCapabilityProvider {
    override fun querySupport(operation: ShaderOperation, effect: ShaderEffect?, inputs: Set<ShaderInput>): ShaderSupport =
        if (operation == ShaderOperation.IMAGE_EXPORT) {
            // ImageProcessor.create on Skia requires the concrete built-in factory. Delegating a
            // rendering interface alone cannot promise that this wrapper has an export processor.
            ShaderSupport(ShaderSupportStatus.UNKNOWN, ShaderSupportReason.CAPABILITIES_NOT_PROVIDED,
                "This rendering adapter does not provide an image processor capability.")
        } else {
            delegate.querySupport(operation, effect, inputs)
        }
}

// Complete external call sites compile without internal imports or any additional factory member.
fun renderPreservingContent(factory: ShaderFactory, effect: ShaderEffect): ShaderRenderOutcome =
    factory.createRenderEffectWithFallback(effect, 16f, 16f)

fun renderFailingExplicitly(factory: ShaderFactory, effect: ShaderEffect): ShaderRenderOutcome =
    factory.createRenderEffectWithFallback(effect, 16f, 16f, ShaderFallbackPolicy.Fail)

fun renderWithReplacement(factory: ShaderFactory, effect: ShaderEffect, replacement: ShaderEffect): ShaderRenderOutcome =
    factory.createRenderEffectWithFallback(effect, 16f, 16f, ShaderFallbackPolicy.Substitute(replacement))
