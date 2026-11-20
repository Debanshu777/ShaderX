package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.NativeBlurEffect
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.NativeEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderCapabilityProvider
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderInput
import com.debanshu.shaderlab.shaderx.factory.ShaderOperation
import com.debanshu.shaderlab.shaderx.factory.ShaderSession
import com.debanshu.shaderlab.shaderx.factory.ShaderSupport
import com.debanshu.shaderlab.shaderx.factory.ShaderSupportReason
import com.debanshu.shaderlab.shaderx.factory.ShaderSupportStatus
import com.debanshu.shaderlab.shaderx.factory.builtInSupport
import com.debanshu.shaderlab.shaderx.factory.querySupport
import com.debanshu.shaderlab.shaderx.factory.createPipelineSession
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Metadata checks run without shader compilation, texture allocation or Android runtime calls. */
class ShaderCapabilitiesTest {
    @Test
    fun allBuiltInsHaveStaticRoutesButAndroidExportIsIndependentlyUnavailable() {
        for (effect in ShaderX.builtInEffects()) {
            supported(null, ShaderOperation.CONTENT_RENDER, effect)
            supported(33, ShaderOperation.CONTENT_RENDER, effect)
            supported(37, ShaderOperation.CONTENT_RENDER, effect)
            supported(null, ShaderOperation.IMAGE_EXPORT, effect)
            if (effect is NativeBlurEffect) supported(32, ShaderOperation.CONTENT_RENDER, effect)
            else unsupported(32, ShaderOperation.CONTENT_RENDER, effect, ShaderSupportReason.ANDROID_API_33_REQUIRED)
            unsupported(33, ShaderOperation.IMAGE_EXPORT, effect, ShaderSupportReason.ANDROID_EXPORT_UNAVAILABLE)
            unsupported(37, ShaderOperation.IMAGE_EXPORT, effect, ShaderSupportReason.ANDROID_EXPORT_UNAVAILABLE)
            if (effect is AnimatedShaderEffect) {
                supported(null, ShaderOperation.ANIMATED_SESSION, effect)
                supported(33, ShaderOperation.ANIMATED_SESSION, effect)
            } else {
                unsupported(null, ShaderOperation.ANIMATED_SESSION, effect, ShaderSupportReason.ANIMATED_EFFECT_REQUIRED)
            }
        }
        assertEquals("Android API 31", builtInSupport(32, ShaderOperation.CONTENT_RENDER, NativeBlurEffect()).minimumRequirement)
    }

    @Test
    fun compositeRulesDistinguishScreenExportNestedAndAutomaticAnimation() {
        val single = CompositeEffect.of(GrayscaleEffect())
        val multi = CompositeEffect.of(GrayscaleEffect(), NativeBlurEffect())
        val nestedSingle = CompositeEffect.of(single)
        val nestedMulti = CompositeEffect.of(multi)
        for (effect in listOf(single, nestedSingle)) supported(null, ShaderOperation.CONTENT_RENDER, effect)
        for (effect in listOf(multi, nestedMulti)) {
            supported(null, ShaderOperation.CONTENT_RENDER, effect)
        }
        for (effect in listOf(single, multi, nestedSingle, nestedMulti)) {
            supported(33, ShaderOperation.CONTENT_RENDER, effect)
            unsupported(null, ShaderOperation.IMAGE_EXPORT, effect, ShaderSupportReason.COMPOSITE_EXPORT_UNAVAILABLE)
        }
        val animatedChild = CompositeEffect.of(WaveEffect())
        supported(null, ShaderOperation.CONTENT_RENDER, animatedChild)
        supported(33, ShaderOperation.CONTENT_RENDER, animatedChild)
        for (api in listOf(null, 33)) {
            supported(api, ShaderOperation.ANIMATED_SESSION, animatedChild)
        }
        assertTrue(builtInSupport(33, ShaderOperation.CONTENT_RENDER, animatedChild).message.contains("no automatic"))
    }

    @Test
    fun unsupportedInputsAndBackgroundsStayExplicitWhileImageTexturesUseExistingRoutes() {
        for (api in listOf(null, 33, 37)) {
            for (operation in listOf(ShaderOperation.CONTENT_RENDER, ShaderOperation.ANIMATED_SESSION)) {
                supported(api, operation, WaveEffect(), setOf(ShaderInput.IMAGE_TEXTURE))
                if (api == null) unsupported(api, operation, WaveEffect(), ShaderSupportReason.RAW_DATA_UNAVAILABLE,
                    setOf(ShaderInput.RAW_DATA_TEXTURE))
                else supported(api, operation, WaveEffect(), setOf(ShaderInput.RAW_DATA_TEXTURE))
                supported(api, operation, WaveEffect(), setOf(ShaderInput.NESTED_SHADER))
            }
            unsupported(api, ShaderOperation.BACKGROUND_RENDER, WaveEffect(), ShaderSupportReason.BACKGROUND_UNAVAILABLE)
        }
        supported(null, ShaderOperation.IMAGE_EXPORT, WaveEffect(), setOf(ShaderInput.IMAGE_TEXTURE))
        unsupported(null, ShaderOperation.IMAGE_EXPORT, WaveEffect(), ShaderSupportReason.RAW_DATA_UNAVAILABLE,
            setOf(ShaderInput.RAW_DATA_TEXTURE))
    }

    @Test
    fun unsupportedNativeEffectsAreRejectedIncludingInsideComposites() {
        for (api in listOf(null, 33)) {
            unsupported(api, ShaderOperation.CONTENT_RENDER, UnknownNative, ShaderSupportReason.NATIVE_EFFECT_UNAVAILABLE)
            unsupported(api, ShaderOperation.CONTENT_RENDER, CompositeEffect.of(UnknownNative), ShaderSupportReason.NATIVE_EFFECT_UNAVAILABLE)
        }
        unsupported(null, ShaderOperation.IMAGE_EXPORT, UnknownNative, ShaderSupportReason.NATIVE_EFFECT_UNAVAILABLE)
    }

    @Test
    fun queryNeverReadsSourceMetadataOrLiveUniforms() {
        for (api in listOf(null, 33)) {
            supported(api, ShaderOperation.CONTENT_RENDER, PoisonAnimated)
            supported(api, ShaderOperation.ANIMATED_SESSION, PoisonAnimated)
            unsupported(api, ShaderOperation.IMAGE_EXPORT, PoisonAnimated,
                if (api == null) null else ShaderSupportReason.ANDROID_EXPORT_UNAVAILABLE)
        }
    }

    @Test
    fun pipelineQueriesAreStructuralAndNonProviderSessionDoesNoRendering() {
        val child = object : AnimatedShaderEffect by PoisonAnimated {
            override val id = "query_child"
            override val displayName = "Query child"
            override val parameters: List<ParameterSpec> = emptyList()
        }
        val chain = CompositeEffect.of(child, CompositeEffect.of(child))
        for (api in listOf(null, 33)) {
            supported(api, ShaderOperation.CONTENT_RENDER, chain)
            supported(api, ShaderOperation.ANIMATED_SESSION, chain)
        }
        val factory = UnusedFactory()
        assertEquals(ShaderSupportStatus.UNKNOWN, factory.querySupport(ShaderOperation.ANIMATED_SESSION, chain).status)
        assertTrue(factory.createPipelineSession(chain, 16f, 16f) is ShaderResult.Failure)
    }

    @Test
    fun thirdPartyFactoryWithoutOptionalProviderIsUnknownWithoutCallingLegacyBoolean() {
        val factory = UnusedFactory()
        for (operation in ShaderOperation.entries) {
            val report = factory.querySupport(operation, PoisonAnimated, setOf(ShaderInput.RAW_DATA_TEXTURE))
            assertEquals(ShaderSupportStatus.UNKNOWN, report.status)
            assertEquals(ShaderSupportReason.CAPABILITIES_NOT_PROVIDED, report.reason)
        }
    }

    @Test
    fun optionalProviderReceivesExactRequirementsAndMayReturnUnknown() {
        var invocations = 0
        val factory: ShaderFactory = object : UnusedFactory(), ShaderCapabilityProvider {
            override fun querySupport(operation: ShaderOperation, effect: ShaderEffect?, inputs: Set<ShaderInput>): ShaderSupport {
                invocations++
                assertEquals(ShaderOperation.IMAGE_EXPORT, operation)
                assertTrue(effect === PoisonAnimated)
                assertEquals(setOf(ShaderInput.NESTED_SHADER), inputs)
                return ShaderSupport(ShaderSupportStatus.UNKNOWN, message = "Custom backend needs an external check")
            }
        }
        assertEquals(ShaderSupportStatus.UNKNOWN,
            factory.querySupport(ShaderOperation.IMAGE_EXPORT, PoisonAnimated, setOf(ShaderInput.NESTED_SHADER)).status)
        assertEquals(1, invocations)
    }

    private fun supported(api: Int?, operation: ShaderOperation, effect: ShaderEffect, inputs: Set<ShaderInput> = emptySet()) {
        val report = builtInSupport(api, operation, effect, inputs)
        assertEquals(ShaderSupportStatus.SUPPORTED, report.status, report.message)
        assertEquals(null, report.reason)
    }

    private fun unsupported(api: Int?, operation: ShaderOperation, effect: ShaderEffect, reason: ShaderSupportReason?, inputs: Set<ShaderInput> = emptySet()) {
        if (reason == null) {
            supported(api, operation, effect, inputs)
            return
        }
        val report = builtInSupport(api, operation, effect, inputs)
        assertEquals(ShaderSupportStatus.UNSUPPORTED, report.status, report.message)
        assertEquals(reason, report.reason)
        assertTrue(report.message.isNotBlank())
    }

    private object UnknownNative : NativeEffect {
        override val id = "unknown_native"
        override val displayName = "Unknown native"
        override val parameters: List<ParameterSpec> = emptyList()
        override fun withTypedParameter(parameterId: String, value: ParameterValue): NativeEffect = this
    }

    private object PoisonAnimated : AnimatedShaderEffect {
        override val id: String get() = error("Query must not read effect metadata")
        override val displayName: String get() = error("Query must not read effect metadata")
        override val parameters: List<ParameterSpec> get() = error("Query must not read parameters")
        override val shaderSource: String get() = error("Query must not read source")
        override val isAnimating: Boolean get() = error("Query must not read live playback")
        override val time: Float get() = error("Query must not read live time")
        override fun withTime(newTime: Float): AnimatedShaderEffect = error("Query must not advance playback")
        override fun buildUniforms(width: Float, height: Float): List<Uniform> = error("Query must not invoke live uniform providers")
        override fun buildFrameUniforms(): List<Uniform> = error("Query must not invoke live frame providers")
        override fun withTypedParameter(parameterId: String, value: ParameterValue): AnimatedShaderEffect = this
    }

    private open class UnusedFactory : ShaderFactory {
        override val cacheSize: Int get() = error("Query must not inspect cache")
        override fun isSupported(): Boolean = error("Legacy Boolean cannot determine unknown operation support")
        override fun createRenderEffect(effect: ShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> = error("Query must not render")
        override fun createAnimatedSession(effect: AnimatedShaderEffect, width: Float, height: Float): ShaderResult<ShaderSession> = error("Query must not create sessions")
        override fun clearCache(): Unit = error("Query must not mutate cache")
        override fun close(): Unit = error("Query must not close factory")
    }
}
