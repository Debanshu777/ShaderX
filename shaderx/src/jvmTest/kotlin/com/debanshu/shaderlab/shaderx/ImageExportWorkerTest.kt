package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.factory.serializedImageExport
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ImageExportWorkerTest {
    @Test fun realExportCreatesWorkerFactoryAndEvaluatesProvidersAwayFromCaller() = runBlocking {
        val caller = Thread.currentThread()
        var providerThread: Thread? = null
        val base = com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect()
        val effect = object : com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect by base {
            override fun buildUniforms(width: Float, height: Float): List<com.debanshu.shaderlab.shaderx.uniform.Uniform> {
                providerThread = Thread.currentThread()
                return base.buildUniforms(width, height)
            }
        }
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(2, 2)
        val source = try {
            surface.canvas.clear(org.jetbrains.skia.Color.RED)
            val image = surface.makeImageSnapshot()
            try {
                val data = image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!
                try { data.bytes } finally { data.close() }
            } finally { image.close() }
        } finally { surface.close() }
        val output = com.debanshu.shaderlab.shaderx.factory.exportImage(source, effect,
            com.debanshu.shaderlab.shaderx.factory.ImageExportOptions(8, 4)).getOrThrow()
        assertNotNull(providerThread)
        assertNotSame(caller, providerThread)
        val image = org.jetbrains.skia.Image.makeFromEncoded(output)
        try { assertEquals(8, image.width); assertEquals(4, image.height) } finally { image.close() }
    }

    @Test fun admissionIsOneActiveOneQueuedAndCancelledQueueStartsNoWork() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var queuedRan = false
        val active = async(Dispatchers.Default) {
            serializedImageExport {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                ShaderResult.success(byteArrayOf(1))
            }
        }
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        try {
            val queued = async(start = CoroutineStart.UNDISPATCHED) {
                serializedImageExport { queuedRan = true; ShaderResult.success(byteArrayOf(2)) }
            }
            val busy = serializedImageExport { error("Excess work must not start") }
            assertTrue(busy is ShaderResult.Failure && busy.error.message.contains("busy"))
            queued.cancelAndJoin()
            assertFalse(queuedRan)
        } finally { release.countDown() }
        assertContentEquals(byteArrayOf(1), active.await().getOrThrow())
        assertTrue(serializedImageExport { ShaderResult.success(byteArrayOf(3)) } is ShaderResult.Success)
    }

    @Test fun cancellationDuringNativeBlockWaitsForCleanupAndSuppressesDelivery() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var closed = false
        val active = async(Dispatchers.Default) {
            serializedImageExport {
                val owner = Thread.currentThread()
                try {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    ShaderResult.success(byteArrayOf(1))
                } finally {
                    assertSame(owner, Thread.currentThread())
                    closed = true
                }
            }
        }
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        active.cancel()
        assertFalse(closed, "Cancellation cannot preempt a blocked native operation")
        release.countDown()
        assertFailsWith<CancellationException> { active.await() }
        assertTrue(closed)
        assertTrue(serializedImageExport { ShaderResult.success(byteArrayOf(3)) } is ShaderResult.Success)
    }

    @Test fun failureAlsoReleasesBothAdmissionAndOwnership() = runBlocking {
        assertFailsWith<IllegalStateException> { serializedImageExport { error("Injected") } }
        assertTrue(serializedImageExport { ShaderResult.success(byteArrayOf(3)) } is ShaderResult.Success)
    }
}
