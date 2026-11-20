package com.debanshu.shaderlab.shaderx.factory

import com.debanshu.shaderlab.shaderx.authoring.definedContentError
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Bounded still-image export options.
 *
 * [pixelWidth]/[pixelHeight] are actual output pixels, independently of the optional positive,
 * finite shader coordinate dimensions. Coordinates default to output pixels. The source is stretched
 * to the output rectangle using linear sampling BEFORE filtering; filter output is clipped there.
 *
 * Built-in export accepts single-frame PNG/JPEG input with normal (top-left) encoded orientation;
 * rotated/mirrored EXIF is rejected, never silently ignored. Output is always 8-bit sRGB PNG with
 * alpha, rendered on a premultiplied raster surface. Tagged source colors are converted by Skia;
 * untagged sources use its sRGB assumption. Metadata/EXIF is not copied. JPEG OUTPUT, quality,
 * flattening, contain/cover fitting, animated input and automatic orientation are not offered.
 *
 * Input is limited to 8 MiB encoded and 4,194,304 decoded pixels; each source/output edge is at most
 * 4096 pixels and output is at most 4,194,304 pixels. These are admission limits, not a bound on
 * codec/filter native scratch memory or a security sandbox. Native allocation can still fail.
 * Legacy [ImageProcessor.process] retains its original uniform-only size overrides and limits.
 */
public data class ImageExportOptions(
    public val pixelWidth: Int,
    public val pixelHeight: Int,
    public val coordinateWidth: Float? = null,
    public val coordinateHeight: Float? = null,
) {
    init {
        require(validExportDimensions(pixelWidth, pixelHeight)) { "Output must have positive edges <=4096 and <=4194304 pixels" }
        require(coordinateWidth == null || coordinateWidth.isFinite() && coordinateWidth > 0f) { "coordinateWidth must be finite and positive" }
        require(coordinateHeight == null || coordinateHeight.isFinite() && coordinateHeight > 0f) { "coordinateHeight must be finite and positive" }
    }
}

internal const val MAX_EXPORT_INPUT_BYTES = 8 * 1024 * 1024
internal fun validExportDimensions(width: Int, height: Int): Boolean =
    width in 1..4096 && height in 1..4096 && width.toLong() * height <= 4_194_304L

/**
 * Optional processor contract for bounded PNG export. Existing [ImageProcessor] implementors need
 * no new members. A provider must report support for THIS route and release temporary resources
 * before returning; on-screen support alone never establishes offline support.
 */
public interface ImageExportProvider {
    /** Metadata only; does not decode, compile or invoke effect providers. */
    public fun queryImageExportSupport(effect: ShaderEffect): ShaderSupport

    /** Synchronous; use one caller-owned thread/factory, or use worker-owned [exportImage]. */
    public fun exportImage(imageBytes: ByteArray, effect: ShaderEffect, options: ImageExportOptions): ShaderResult<ByteArray>
}

/** Queries the optional bounded route. Legacy IMAGE_EXPORT remains tied to [ImageProcessor.process]. */
public fun ImageProcessor.queryImageExportSupport(effect: ShaderEffect): ShaderSupport =
    (this as? ImageExportProvider)?.queryImageExportSupport(effect) ?: ShaderSupport(
        ShaderSupportStatus.UNSUPPORTED,
        message = "This processor does not provide bounded PNG export; legacy process support is independent.",
    )

/**
 * Bounded synchronous PNG export. Does not mutate [imageBytes] or preserve its encoding/metadata.
 * Requires an optional provider; never falls back to legacy processing or unfiltered input bytes.
 * Caller must keep the factory/borrowed effect inputs alive and serialize all factory access.
 */
public fun ImageProcessor.exportImage(
    imageBytes: ByteArray,
    effect: ShaderEffect,
    options: ImageExportOptions,
): ShaderResult<ByteArray> = (this as? ImageExportProvider)?.exportImage(imageBytes, effect, options)
    ?: ShaderResult.failure(ShaderError.ProcessingError("Bounded PNG export is unavailable for this processor"))

private val exportMutex = Mutex()
private val exportAdmission = Semaphore(2)

/**
 * Serialized worker-owned PNG export; creates, uses and closes a private factory in one synchronous
 * worker block. No UI-owned factory/session is shared. At most one built-in suspend export executes
 * at a time with at most one queued request; excess requests fail immediately. Queued requests
 * borrow caller bytes/effects without copying. Do not mutate bytes or dispose/mutate
 * borrowed image/uniform inputs until completion. Input bytes are copied after admission.
 *
 * Cancellation while queued starts no work; cancellation during native work suppresses delivery
 * after cleanup. It cannot preempt a decoder, compiler, renderer or encoder, and no hard timeout is
 * promised. JVM/iOS use Dispatchers.Default; Wasm Default runs bounded synchronous work on the
 * browser thread and can block it. Effect/uniform providers run in the worker block and must be
 * safe there; captured UI state/resources are caller responsibility. Android remains unsupported.
 */
public suspend fun exportImage(
    imageBytes: ByteArray,
    effect: ShaderEffect,
    options: ImageExportOptions,
): ShaderResult<ByteArray> = serializedImageExport {
    val contentError = definedContentError(effect)
    if (contentError != null) {
        ShaderResult.failure(contentError)
    } else if (imageBytes.isEmpty() || imageBytes.size > MAX_EXPORT_INPUT_BYTES) {
        ShaderResult.failure(ShaderError.ProcessingError("Encoded source must be 1..8388608 bytes"))
    } else {
        val bytes = imageBytes.copyOf()
        val factory = ShaderFactory.create(maxCacheSize = 8)
        try {
            ImageProcessor.create(factory).exportImage(bytes, effect, options)
        } finally {
            factory.close()
        }
    }
}

internal suspend fun serializedImageExport(operation: () -> ShaderResult<ByteArray>): ShaderResult<ByteArray> {
    currentCoroutineContext().ensureActive()
    if (!exportAdmission.tryAcquire()) {
        return ShaderResult.failure(ShaderError.ProcessingError("Image exporter busy: one active and one queued request allowed"))
    }
    return try {
        exportMutex.withLock {
            withContext(Dispatchers.Default) {
                currentCoroutineContext().ensureActive()
                val result = operation()
                currentCoroutineContext().ensureActive()
                result
            }
        }
    } finally {
        exportAdmission.release()
    }
}
