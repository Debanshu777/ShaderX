package shaderx.consumer

import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderInput
import com.debanshu.shaderlab.shaderx.factory.ShaderOperation
import com.debanshu.shaderlab.shaderx.factory.ShaderSupportReason
import com.debanshu.shaderlab.shaderx.factory.ShaderSupportStatus
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.factory.querySupport
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CapabilityConsumerTest {
    @Test
    fun originalExplicitFactoryRemainsCompatibleAndUnknownForBothLegacyBooleanValues() {
        val backend = ShaderFactory.create()
        try {
            val original = ConsumerFactory(backend)
            assertTrue(original.isSupported())
            for (factory in listOf(original, object : ShaderFactory by original {
                override fun isSupported(): Boolean = false
            })) {
                for (operation in ShaderOperation.entries) {
                    val support = factory.querySupport(operation, WaveEffect())
                    assertEquals(ShaderSupportStatus.UNKNOWN, support.status)
                    assertEquals(ShaderSupportReason.CAPABILITIES_NOT_PROVIDED, support.reason)
                }
            }
            assertNotNull(original.createRenderEffect(GrayscaleEffect(), 16f, 16f).getOrNull())
        } finally {
            backend.close()
        }
    }

    @Test
    fun optionalProviderForwardsOnlyCapabilitiesWhoseOperationsItActuallyDelegates() {
        val backend = ShaderFactory.create()
        try {
            val adapter: ShaderFactory = CapabilityConsumerFactory(backend)
            assertEquals(ShaderSupportStatus.SUPPORTED, adapter.querySupport(ShaderOperation.CONTENT_RENDER, GrayscaleEffect()).status)
            assertEquals(ShaderSupportStatus.SUPPORTED, adapter.querySupport(ShaderOperation.ANIMATED_SESSION, WaveEffect()).status)
            assertEquals(ShaderSupportReason.RAW_DATA_UNAVAILABLE,
                adapter.querySupport(ShaderOperation.CONTENT_RENDER, WaveEffect(), setOf(ShaderInput.RAW_DATA_TEXTURE)).reason)
            assertEquals(ShaderSupportStatus.UNKNOWN, adapter.querySupport(ShaderOperation.IMAGE_EXPORT, GrayscaleEffect()).status)
            assertEquals(0, backend.cacheSize, "External queries must not compile shaders")
        } finally {
            backend.close()
        }
    }

    @Test
    fun externalCallSitesSelectObservablePoliciesWithoutDroppingChainChildren() {
        val backend = ShaderFactory.create()
        try {
            val original = ConsumerFactory(backend)
            // A raw requirement is unsupported across backends, but fallback below exercises the
            // actual Skia chain route on JVM and the static helper independently of query metadata.
            val chain = CompositeEffect.of(GrayscaleEffect(), WaveEffect())
            val query = backend.querySupport(ShaderOperation.CONTENT_RENDER, chain)
            if (query.status == ShaderSupportStatus.SUPPORTED) {
                // Android supports the complete chain; a successful request never selects recovery.
                val success = renderWithReplacement(original, chain, GrayscaleEffect())
                assertFalse(success.fallbackUsed)
                assertNull(success.cause)
                assertNotNull(success.result.getOrNull())
            } else {
                val preserved = renderPreservingContent(original, chain)
                assertTrue(preserved.fallbackUsed)
                assertIs<ShaderError.UnsupportedEffect>(preserved.cause)
                assertIs<ShaderResult.Success<*>>(preserved.result)
                assertNull(preserved.result.getOrNull())
                val failed = renderFailingExplicitly(original, chain)
                assertFalse(failed.fallbackUsed)
                assertIs<ShaderResult.Failure<*>>(failed.result)
                val replaced = renderWithReplacement(original, chain, GrayscaleEffect())
                assertTrue(replaced.fallbackUsed)
                assertNotNull(replaced.cause)
                assertNotNull(replaced.result.getOrNull())
            }
        } finally {
            backend.close()
        }
    }
}
