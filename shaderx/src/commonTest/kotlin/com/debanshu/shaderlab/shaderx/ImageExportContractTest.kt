package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import kotlin.test.*

class ImageExportContractTest {
    @Test fun dimensionsRejectOverflowAndCoordinatesAreIndependent() {
        for ((w, h) in listOf(0 to 1, -1 to 1, Int.MAX_VALUE to Int.MAX_VALUE, 4097 to 1, 4096 to 4096)) {
            assertFailsWith<IllegalArgumentException> { ImageExportOptions(w, h) }
        }
        for (value in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { ImageExportOptions(2, 3, coordinateWidth = value) }
            assertFailsWith<IllegalArgumentException> { ImageExportOptions(2, 3, coordinateHeight = value) }
        }
        assertEquals(123f, ImageExportOptions(2, 3, coordinateWidth = 123f).coordinateWidth)
        ImageExportOptions(2048, 2048)
    }

    @Test fun thirdPartyProcessorNeedsNoNewMethodsAndCannotAccidentallyClaimBoundedExport() {
        var legacyCalls = 0
        val old = object : ImageProcessor {
            override fun process(imageBytes: ByteArray, effect: ShaderEffect, width: Float, height: Float): ShaderResult<ByteArray> {
                legacyCalls++
                return ShaderResult.success(imageBytes)
            }
        }
        val bytes = byteArrayOf(1, 2, 3)
        assertEquals(ShaderSupportStatus.UNSUPPORTED, old.queryImageExportSupport(GrayscaleEffect()).status)
        assertTrue(old.exportImage(bytes, GrayscaleEffect(), ImageExportOptions(2, 3)) is ShaderResult.Failure)
        assertEquals(0, legacyCalls)
        assertContentEquals(bytes, old.process(bytes, GrayscaleEffect()).getOrThrow())
        assertEquals(1, legacyCalls)
    }
}
