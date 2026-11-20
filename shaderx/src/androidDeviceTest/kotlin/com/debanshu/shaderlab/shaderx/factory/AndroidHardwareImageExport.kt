package com.debanshu.shaderlab.shaderx.factory

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import java.io.ByteArrayOutputStream
import java.time.Duration

/**
 * Device-test-only feasibility probe, excluded from the library artifact and deliberately
 * separate from the unsupported legacy processor.
 *
 * One invocation owns its reader, surface, renderer, nodes, decoded/readable/hardware bitmaps,
 * acquired image, buffer and API33 fence. The borrowed factory must belong to the calling worker;
 * this helper neither transfers factory ownership nor makes UI-owned factories thread safe.
 * The deadline/cancellation checkpoints suppress late delivery and clean up after native calls
 * return. They cannot preempt HWUI rendering, bitmap readback, decoding or encoding.
 *
 * PNG only, sRGB RGBA8888, encoded raster orientation, bilinear stretch before filtering,
 * bounded to 4 megapixels and 16 MiB encoded input. Chains remain a separate unproved route.
 */
internal object AndroidHardwareImageExport {
    private const val MAX_PIXELS = 4_194_304L
    private const val MAX_BYTES = 16 * 1024 * 1024
    private val operationLock = Any()

    /**
     * Internal A/B teardown experiment; neither order establishes a public exporter.
     * [CleanupOrder.MIGRATION_RECIPE] is diagnostic only: repeated API36 instrumentation
     * crashed the process in isolated and combined pre-render cancellation runs. Explicit opt-in only.
     */
    internal enum class CleanupOrder { RENDERER_FIRST, MIGRATION_RECIPE }

    internal data class ProbeResult(
        val png: ByteArray,
        val renderNanos: Long,
        val readbackNanos: Long,
        val encodeNanos: Long,
    )

    /** Probe/test hook. Throwing from [checkpoint] always unwinds currently owned resources. */
    internal fun renderPng(
        factory: ShaderFactory,
        source: ByteArray,
        effect: ShaderEffect,
        width: Int,
        height: Int,
        timeoutMillis: Long = 5_000,
        cleanupOrder: CleanupOrder = CleanupOrder.RENDERER_FIRST,
        checkpoint: (String) -> Unit = {},
    ): ProbeResult = synchronized(operationLock) {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Hardware export requires a worker thread" }
        require(timeoutMillis in 1..30_000) { "Probe timeout must be 1..30000 milliseconds" }
        require(source.isNotEmpty() && source.size <= MAX_BYTES) { "Encoded input must be 1..16 MiB" }
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS) { "Output exceeds 4 megapixels or has invalid dimensions" }
        require(effect !is CompositeEffect) { "Offline chains need independent export validation" }
        val deadline = SystemClock.elapsedRealtimeNanos() + timeoutMillis * 1_000_000
        fun checkPoint(phase: String) {
            checkpoint(phase)
            check(SystemClock.elapsedRealtimeNanos() < deadline) { "Hardware image export deadline exceeded at $phase" }
        }
        checkPoint("beforeDecode")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= MAX_PIXELS) {
            "Invalid image or decoded input exceeds 4 megapixels"
        }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val input = requireNotNull(BitmapFactory.decodeByteArray(source, 0, source.size, options)) { "Image decode failed" }
        try {
            checkPoint("afterDecode")
            val filter = factory.createRenderEffect(effect, width.toFloat(), height.toFloat()).getOrThrow()
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
            var readerClosed = false
            fun closeReader() {
                if (!readerClosed) {
                    reader.close()
                    readerClosed = true
                }
            }
            try {
                val surface = reader.surface
                try {
                    val renderer = HardwareRenderer()
                    var rendererDestroyed = false
                    try {
                        val child = RenderNode("ShaderX export input")
                        val root = RenderNode("ShaderX export root")
                        try {
                            renderer.setName("ShaderX image export probe")
                            renderer.setOpaque(false)
                            renderer.setSurface(surface)
                            renderer.setContentRoot(root)
                            child.setPosition(0, 0, width, height)
                            root.setPosition(0, 0, width, height)
                            child.setRenderEffect(filter.asAndroidRenderEffect())
                            val canvas = child.beginRecording()
                            try {
                                canvas.drawBitmap(input, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
                            } finally { child.endRecording() }
                            val rootCanvas = root.beginRecording()
                            try { rootCanvas.drawRenderNode(child) } finally { root.endRecording() }
                            checkPoint("beforeRender")
                            val renderStart = SystemClock.elapsedRealtimeNanos()
                            val status = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
                            val renderNanos = SystemClock.elapsedRealtimeNanos() - renderStart
                            val failures = HardwareRenderer.SYNC_CONTEXT_IS_STOPPED or
                                HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND or HardwareRenderer.SYNC_FRAME_DROPPED
                            check(status and failures == 0) { "HardwareRenderer did not present a frame: $status" }
                            checkPoint("afterRender")
                            val image = checkNotNull(reader.acquireNextImage()) { "Renderer produced no image" }
                            image.use {
                                if (Build.VERSION.SDK_INT >= 33) Api33ExportFence.await(image, deadline)
                                checkPoint("afterFence")
                                val readStart = SystemClock.elapsedRealtimeNanos()
                                val buffer = checkNotNull(image.hardwareBuffer) { "Image has no hardware buffer" }
                                buffer.use {
                                    val hardware = checkNotNull(Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))) { "Cannot wrap hardware buffer" }
                                    try {
                                        val readable = checkNotNull(hardware.copy(Bitmap.Config.ARGB_8888, false)) { "Hardware readback failed" }
                                        try {
                                            val readNanos = SystemClock.elapsedRealtimeNanos() - readStart
                                            checkPoint("afterReadback")
                                            val encodeStart = SystemClock.elapsedRealtimeNanos()
                                            val bytes = ByteArrayOutputStream().use { output ->
                                                check(readable.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG encoding failed" }
                                                output.toByteArray()
                                            }
                                            val encodeNanos = SystemClock.elapsedRealtimeNanos() - encodeStart
                                            checkPoint("beforeDelivery")
                                            ProbeResult(bytes, renderNanos, readNanos, encodeNanos)
                                        } finally { readable.recycle() }
                                    } finally { hardware.recycle() }
                                }
                            }
                        } finally {
                            if (cleanupOrder == CleanupOrder.MIGRATION_RECIPE) {
                                // Experimental exact recipe order: buffer/image have unwound above,
                                // then reader, display lists and finally renderer. Keep finally
                                // coverage even if one teardown operation itself throws.
                                try { closeReader() } finally {
                                    try { child.discardDisplayList() } finally {
                                        try { root.discardDisplayList() } finally {
                                            renderer.destroy()
                                            rendererDestroyed = true
                                        }
                                    }
                                }
                            } else {
                                // Original experiment: detach the producer before releasing reader.
                                renderer.destroy()
                                rendererDestroyed = true
                                child.discardDisplayList()
                                root.discardDisplayList()
                            }
                        }
                    } finally {
                        // Also covers failure while constructing either node.
                        if (!rendererDestroyed) renderer.destroy()
                    }
                } finally {
                    // ImageReader.close owns this Surface in the recipe experiment.
                    if (!readerClosed) surface.release()
                }
            } finally { closeReader() }
        } finally { input.recycle() }
    }
}

/** API33-only types/methods stay outside the API31 probe's fields and method signatures. */
@RequiresApi(33)
private object Api33ExportFence {
    @DoNotInline
    fun await(image: Image, deadline: Long) {
        image.fence.use { fence ->
            if (fence.isValid) {
                val remaining = deadline - SystemClock.elapsedRealtimeNanos()
                check(remaining > 0 && fence.await(Duration.ofNanos(remaining))) { "Hardware image fence deadline exceeded" }
            }
        }
    }
}
