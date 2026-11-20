package io.github.debanshu.shaderx.validation

import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.factory.ImageExportOptions
import com.debanshu.shaderlab.shaderx.factory.ImageProcessor
import com.debanshu.shaderlab.shaderx.factory.exportImage
import com.debanshu.shaderlab.shaderx.factory.queryImageExportSupport
import com.debanshu.shaderlab.shaderx.factory.ShaderSupportStatus
import com.debanshu.shaderlab.shaderx.result.ShaderResult

/** Compiled unchanged by independent Android and KMP consumer modules. */
public fun exportWithOwnedProcessor(processor: ImageProcessor, bytes: ByteArray): ShaderResult<ByteArray>? =
    if (processor.queryImageExportSupport(GrayscaleEffect()).status == ShaderSupportStatus.SUPPORTED) {
        processor.exportImage(bytes, GrayscaleEffect(), ImageExportOptions(40, 24, coordinateWidth = 80f))
    } else null

/** Creates its own worker factory; no borrowed UI factory crosses dispatchers. */
public suspend fun exportOnWorker(bytes: ByteArray): ShaderResult<ByteArray> =
    exportImage(bytes, GrayscaleEffect(), ImageExportOptions(40, 24))
