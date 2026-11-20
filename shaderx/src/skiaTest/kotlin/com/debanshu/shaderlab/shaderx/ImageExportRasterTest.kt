package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderFrameProvider
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.NativeBlurEffect
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.Uniform
import org.jetbrains.skia.*
import org.jetbrains.skia.impl.use
import kotlin.math.abs
import kotlin.test.*

/** Actual exported PNG decoding and pixels on the shared JVM/Wasm/iOS Skia raster backend. */
class ImageExportRasterTest {
    @Test
    fun outputPixelsAreFilteredResizedAndPreserveAlphaWithoutMutatingSourceBytes() {
        val source = fixture(4, 3) { x, _ -> if (x < 2) Color.makeARGB(128, 240, 40, 20) else Color.TRANSPARENT }
        val unchanged = source.copyOf()
        ShaderFactory.create().use { factory ->
            val processor = ImageProcessor.create(factory)
            assertEquals(ShaderSupportStatus.SUPPORTED, processor.queryImageExportSupport(GrayscaleEffect()).status)
            val output = processor.exportImage(source, GrayscaleEffect(), ImageExportOptions(12, 6)).getOrThrow()
            assertFalse(source.contentEquals(output), "Success contains rendered output, not original source encoding")
            decoded(output) { image, bitmap ->
                assertEquals(12, image.width); assertEquals(6, image.height)
                val filtered = bitmap.getColor(1, 3)
                near(128, Color.getA(filtered))
                near(Color.getR(filtered), Color.getG(filtered)); near(Color.getG(filtered), Color.getB(filtered))
                assertTrue(Color.getR(filtered) in 92..103, "Grayscale must change the red source: $filtered")
                assertEquals(0, Color.getA(bitmap.getColor(11, 3)))
            }
        }
        assertContentEquals(unchanged, source)
    }

    @Test
    fun outputDimensionsAndShaderCoordinateOverridesAreIndependentOfLegacyUniformSizes() {
        val source = fixture(4, 2) { _, _ -> Color.RED }
        val observed = mutableListOf<Pair<Float, Float>>()
        val effect = Stage("""
            uniform shader content; uniform float cutoff;
            half4 main(float2 p) { return p.x < cutoff ? content.eval(p) : half4(0); }
        """.trimIndent()) { width, height ->
            observed += width to height
            listOf(FloatUniform("cutoff", width / 2f))
        }
        ShaderFactory.create().use { factory ->
            val processor = ImageProcessor.create(factory)
            decoded(processor.exportImage(source, effect, ImageExportOptions(12, 6)).getOrThrow()) { image, bitmap ->
                assertEquals(12, image.width); assertEquals(6, image.height)
                assertEquals(255, Color.getA(bitmap.getColor(5, 3)))
                assertEquals(0, Color.getA(bitmap.getColor(6, 3)))
            }
            decoded(processor.exportImage(source, effect, ImageExportOptions(12, 6, 4f, 8f)).getOrThrow()) { image, bitmap ->
                assertEquals(12, image.width); assertEquals(6, image.height)
                assertEquals(255, Color.getA(bitmap.getColor(1, 3)))
                assertEquals(0, Color.getA(bitmap.getColor(2, 3)))
            }
            decoded(processor.process(source, effect, width = 20f, height = 10f).getOrThrow()) { image, bitmap ->
                assertEquals(4, image.width); assertEquals(2, image.height)
                assertEquals(255, Color.getA(bitmap.getColor(3, 1)))
            }
        }
        assertEquals(listOf(12f to 6f, 4f to 8f, 20f to 10f), observed)
    }

    @Test
    fun stretchUsesLinearSamplingBeforeFilteringInDestinationCoordinates() {
        val source = fixture(2, 1) { x, _ -> if (x == 0) Color.BLACK else Color.WHITE }
        ShaderFactory.create().use { factory ->
            val output = ImageProcessor.create(factory).exportImage(source, GrayscaleEffect(0f), ImageExportOptions(4, 1)).getOrThrow()
            decoded(output) { _, bitmap ->
                val actual = IntArray(4) { Color.getR(bitmap.getColor(it, 0)) }
                listOf(0, 64, 191, 255).forEachIndexed { index, expected -> near(expected, actual[index]) }
            }
        }
    }

    @Test
    fun taggedLinearSourceConvertsToSrgbAndOutputPngCarriesSrgbMetadata() {
        val info = ImageInfo(1, 1, ColorType.RGBA_8888, ColorAlphaType.PREMUL, ColorSpace.sRGBLinear)
        val source = Image.makeRaster(info, byteArrayOf(64, 0, 0, -1), 4).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes }
        }
        Data.makeFromBytes(source).use { data -> Codec.makeFromData(data).use { codec ->
            val space = codec.imageInfo.colorSpace
            try { assertTrue(space?.isGammaLinear == true, "Fixture must contain a tagged linear profile") }
            finally { space?.close() }
        } }
        ShaderFactory.create().use { factory ->
            val output = ImageProcessor.create(factory).exportImage(source, GrayscaleEffect(0f), ImageExportOptions(3, 2)).getOrThrow()
            decoded(output) { _, bitmap ->
                near(137, Color.getR(bitmap.getColor(1, 1)), 3)
                assertEquals(0, Color.getG(bitmap.getColor(1, 1)))
                assertEquals(255, Color.getA(bitmap.getColor(1, 1)))
            }
            Data.makeFromBytes(output).use { data -> Codec.makeFromData(data).use { codec ->
                assertEquals(EncodedImageFormat.PNG, codec.encodedImageFormat)
                assertEquals(EncodedOrigin.TOP_LEFT, codec.encodedOrigin)
                val space = codec.imageInfo.colorSpace
                try { assertTrue(space?.isSRGB == true, "Output must be tagged sRGB") }
                finally { space?.close() }
            } }
        }
    }

    @Test
    fun invalidTruncatedAndOversizedHeaderInputsFailBeforeUniformProvidersAndRecover() {
        var calls = 0
        val effect = Stage { _, _ -> calls++; emptyList() }
        val valid = fixture(2, 2) { _, _ -> Color.RED }
        val tooWide = withPngDimensions(valid, 5000, 2)
        val tooManyPixels = withPngDimensions(valid, 4096, 4096)
        val truncated = valid.copyOf(40)
        ShaderFactory.create().use { factory ->
            val processor = ImageProcessor.create(factory)
            for (source in listOf(byteArrayOf(), byteArrayOf(1, 2, 3), truncated, tooWide, tooManyPixels, ByteArray(8 * 1024 * 1024 + 1))) {
                repeat(3) {
                    val failed = assertIs<ShaderResult.Failure<*>>(processor.exportImage(source, effect, ImageExportOptions(2, 2)))
                    assertIs<ShaderError.ProcessingError>(failed.error)
                    assertEquals(0, calls, "Invalid input must fail before effect providers")
                    assertEquals(0, factory.cacheSize)
                }
            }
            decoded(processor.exportImage(valid, effect, ImageExportOptions(2, 2)).getOrThrow()) { _, bitmap ->
                assertEquals(Color.RED, bitmap.getColor(0, 0))
            }
            assertEquals(1, calls)
        }
    }

    @Test
    fun nonNormalJpegExifOrientationIsRejectedRatherThanSilentlyStripped() {
        val jpeg = fixture(3, 2, EncodedImageFormat.JPEG) { x, _ -> if (x == 0) Color.RED else Color.BLUE }
        val rotated = jpeg.copyOfRange(0, 2) + byteArrayOf(-1, -31, 0, 34) + byteArrayOf(
            69, 120, 105, 102, 0, 0, 73, 73, 42, 0, 8, 0, 0, 0,
            1, 0, 18, 1, 3, 0, 1, 0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0,
        ) + jpeg.copyOfRange(2, jpeg.size)
        Data.makeFromBytes(rotated).use { data -> Codec.makeFromData(data).use { codec ->
            assertEquals(EncodedOrigin.RIGHT_TOP, codec.encodedOrigin, "Fixture has EXIF orientation 6")
        } }
        var calls = 0
        val effect = Stage { _, _ -> calls++; emptyList() }
        ShaderFactory.create().use { factory ->
            val processor = ImageProcessor.create(factory)
            val error = assertIs<ShaderResult.Failure<*>>(processor.exportImage(rotated, effect, ImageExportOptions(3, 2))).error
            assertTrue(error.message.contains("orientation"), error.message)
            assertEquals(0, calls)
            decoded(processor.exportImage(jpeg, effect, ImageExportOptions(6, 4)).getOrThrow()) { image, _ ->
                assertEquals(6, image.width); assertEquals(4, image.height)
            }
            assertEquals(1, calls)
        }
    }

    @Test
    fun twoFrameApngIsRejectedBeforeProvidersEvenWhenCodecOnlyReportsItsDefaultImage() {
        // Valid 1x1 RGBA APNG: acTL declares two frames; fcTL/IDAT red then fcTL/fdAT green.
        // Every chunk has a valid CRC and both frames contain complete zlib streams.
        val hex = "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489" +
            "000000086163544c0000000200000000f38d93700000001a6663544c0000000000000001" +
            "0000000100000000000000000001000a00005a7f30d00000000d49444154789c63f8cfc0f01f" +
            "00050001ff89993d1d0000001a6663544c0000000100000001000000010000000000000000" +
            "0001000a0000c10cda04000000116664415400000002789c6360f8cff01f00040101ff62e7e99c" +
            "0000000049454e44ae426082"
        val source = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val codecFrames = Data.makeFromBytes(source).use { data -> Codec.makeFromData(data).use { codec ->
            assertEquals(EncodedImageFormat.PNG, codec.encodedImageFormat)
            assertEquals(1, codec.size.x); assertEquals(1, codec.size.y)
            codec.frameCount
        } }
        var calls = 0
        val effect = Stage { _, _ -> calls++; emptyList() }
        ShaderFactory.create().use { factory ->
            val processor = ImageProcessor.create(factory)
            val error = assertIs<ShaderResult.Failure<*>>(
                processor.exportImage(source, effect, ImageExportOptions(1, 1)),
                "A two-frame APNG must reject even if this codec reports $codecFrames frame(s)",
            ).error
            assertIs<ShaderError.ProcessingError>(error)
            assertTrue(error.message.contains("Animated", ignoreCase = true), error.message)
            assertEquals(0, calls)
            assertEquals(0, factory.cacheSize)
            assertIs<ShaderResult.Success<*>>(processor.exportImage(
                fixture(1, 1) { _, _ -> Color.RED }, effect, ImageExportOptions(1, 1),
            ))
            assertEquals(1, calls)
        }
    }

    @Test
    fun repeatedCompileAndBindingFailuresDoNotPoisonSubsequentExportsOrCachedPrograms() {
        val source = fixture(4, 3) { _, _ -> Color.RED }
        val brokenSource = Stage("not a shader")
        // Manual runtime setters retain their legacy behavior for undeclared scalar names.
        // The declarative provider has an explicit exact-binding contract and must reject this.
        val brokenBindings = ShaderDefinition("export-broken-binding", program = ShaderProgram("""
            uniform shader content; uniform float gain;
            half4 main(float2 p) { return content.eval(p) * gain; }
        """.trimIndent(), listOf(ShaderBinding.frameFloat("gain")))).animatedEffect(
            isAnimating = false,
            frameProvider = ShaderFrameProvider { listOf(FloatUniform("notDeclared", 1f)) },
        )
        ShaderFactory.create(maxCacheSize = 1).use { factory ->
            val processor = ImageProcessor.create(factory)
            repeat(5) {
                assertIs<ShaderResult.Failure<*>>(processor.exportImage(source, brokenSource, ImageExportOptions(4, 3)))
                val bindingError = assertIs<ShaderResult.Failure<*>>(processor.exportImage(source, brokenBindings, ImageExportOptions(4, 3))).error
                assertIs<ShaderError.Unknown>(bindingError)
                assertTrue(bindingError.message.contains("notDeclared"), bindingError.message)
                decoded(processor.exportImage(source, GrayscaleEffect(), ImageExportOptions(4, 3)).getOrThrow()) { _, bitmap ->
                    val color = bitmap.getColor(2, 1)
                    near(76, Color.getR(color)); near(76, Color.getG(color)); near(76, Color.getB(color))
                }
                assertTrue(factory.cacheSize <= 1)
                factory.clearCache()
                assertEquals(0, factory.cacheSize)
            }
        }
    }

    @Test
    fun nativeBlurProducesActualSoftAlphaEdgesAndClipsToRequestedOutputBounds() {
        val source = fixture(32, 32) { x, y -> if (x in 12..19 && y in 12..19) Color.RED else Color.TRANSPARENT }
        ShaderFactory.create().use { factory ->
            val output = ImageProcessor.create(factory).exportImage(source, NativeBlurEffect(2f), ImageExportOptions(32, 32)).getOrThrow()
            decoded(output) { image, bitmap ->
                assertEquals(32, image.width); assertEquals(32, image.height)
                assertEquals(0, Color.getA(bitmap.getColor(0, 0)))
                assertTrue(Color.getA(bitmap.getColor(10, 16)) in 1..100, "Blur expands visible alpha beyond original shape")
                assertTrue(Color.getA(bitmap.getColor(16, 16)) in 150..254)
            }
        }
    }

    @Test
    fun compositesRemainUnsupportedForTheExactOfflineEntryPointBeforeDecode() {
        ShaderFactory.create().use { factory ->
            val processor = ImageProcessor.create(factory)
            val effect = CompositeEffect.of(GrayscaleEffect(), NativeBlurEffect(2f))
            assertEquals(ShaderSupportStatus.UNSUPPORTED, processor.queryImageExportSupport(effect).status)
            assertIs<ShaderError.UnsupportedEffect>(assertIs<ShaderResult.Failure<*>>(
                processor.exportImage(byteArrayOf(1, 2, 3), effect, ImageExportOptions(2, 2)),
            ).error)
            assertEquals(0, factory.cacheSize)
        }
    }

    private class Stage(
        override val shaderSource: String = "uniform shader content; half4 main(float2 p) { return content.eval(p); }",
        private val uniforms: (Float, Float) -> List<Uniform> = { _, _ -> emptyList() },
    ) : RuntimeShaderEffect {
        override val id = "export-raster-test"
        override val displayName = id
        override val parameters: List<ParameterSpec> = emptyList()
        override fun buildUniforms(width: Float, height: Float): List<Uniform> = uniforms(width, height)
        override fun withTypedParameter(parameterId: String, value: ParameterValue): RuntimeShaderEffect = this
    }

    private fun fixture(width: Int, height: Int, format: EncodedImageFormat = EncodedImageFormat.PNG, color: (Int, Int) -> Int): ByteArray =
        Surface.makeRaster(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL, ColorSpace.sRGB)).use { surface ->
            surface.canvas.clear(Color.TRANSPARENT)
            Paint().use { paint ->
                for (y in 0 until height) for (x in 0 until width) {
                    paint.color = color(x, y)
                    surface.canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), 1f, 1f), paint)
                }
            }
            surface.makeImageSnapshot().use { image -> image.encodeToData(format)!!.use { it.bytes } }
        }

    private fun decoded(bytes: ByteArray, verify: (Image, Bitmap) -> Unit) = Image.makeFromEncoded(bytes).use { image ->
        Bitmap.makeFromImage(image).use { bitmap -> verify(image, bitmap) }
    }

    /** Valid CRC permits header preflight, while the deliberately mismatched pixel data must never decode. */
    private fun withPngDimensions(bytes: ByteArray, width: Int, height: Int): ByteArray = bytes.copyOf().also { changed ->
        fun writeInt(offset: Int, value: Int) {
            repeat(4) { index -> changed[offset + index] = (value ushr (24 - index * 8)).toByte() }
        }
        writeInt(16, width); writeInt(20, height)
        var crc = -1
        for (offset in 12 until 29) {
            crc = crc xor (changed[offset].toInt() and 255)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xedb88320.toInt() else crc ushr 1 }
        }
        writeInt(29, crc.inv())
    }

    private fun near(expected: Int, actual: Int, tolerance: Int = 2) =
        assertTrue(abs(expected - actual) <= tolerance, "Expected $expected ± $tolerance, got $actual")
}
