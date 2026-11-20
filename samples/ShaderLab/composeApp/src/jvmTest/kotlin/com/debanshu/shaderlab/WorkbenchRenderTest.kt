package com.debanshu.shaderlab

import androidx.compose.ui.graphics.asSkiaBitmap
import com.debanshu.shaderlab.imagelib.decodeImageBytes
import com.debanshu.shaderlab.playground.*
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ImageProcessor
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use
import kotlin.test.*

/** Real Skia raster rendering through the same public SDK used by the Wasm workbench.
 * This does not establish Android AGSL compilation or browser/GPU responsiveness. */
class WorkbenchRenderTest {
    @Test fun bundledWorkbenchPngHasCompleteDecodedPixels() {
        val bytes = java.io.File("src/commonMain/composeResources/files/workbench.png").readBytes()
        val image = decodeWorkbenchImage(bytes)
        val pixels = IntArray(256 * 256)
        image.readPixels(pixels)
        assertEquals(256, image.width)
        assertEquals(256, image.height)
        assertEquals(0xFF647832.toInt(), pixels[120 * 256 + 100])
        assertEquals(0xFF000032.toInt(), pixels[0])
    }

    @Test
    fun warmTintValuesRenderBeforeAtomicPromotionAndBrokenSourceRetainsLastGoodExport() {
        val state = WorkbenchState(WorkbenchStarters.tint.copy(
            currentValues = mapOf("intensity" to ParameterValue.FloatValue(1f)),
        ))
        val run = state.beginRun()
        val project = run.build()
        val input = pattern()
        ShaderFactory.create(WorkbenchLimits.CACHE_SIZE).use { factory ->
            val processor = ImageProcessor.create(factory)
            val unchanged = project.effect.withTypedParameter("intensity", ParameterValue.FloatValue(0f))
            val warm = project.effect.withTypedParameter("intensity", ParameterValue.FloatValue(1f))
            val before = pixels(input)
            val zero = pixels(processor.process(input, unchanged).getOrThrow())
            val full = pixels(processor.process(input, warm).getOrThrow())
            assertContentEquals(before, zero)
            assertTrue(full.indices.any { Color.getB(full[it]) < Color.getB(before[it]) })
            assertTrue(full.indices.all { Color.getA(full[it]) == Color.getA(before[it]) })
            processor.process(input, project.effect).getOrThrow()
            assertTrue(state.promote(run, project))
            val lastGood = assertNotNull(state.lastGood)
            val exported = KotlinExport.generate(lastGood.project.effect)
            assertTrue(exported.contains("1.0f"))

            state.edit(state.draft.copy(source = state.draft.source.replace("return half4(", "return missingFunction(")))
            val badRun = state.beginRun()
            val broken = badRun.build() // Supported declarations are not source compilation.
            val failure = assertIs<ShaderResult.Failure<*>>(processor.process(input, broken.effect))
            val compilation = assertIs<ShaderError.CompilationError>(failure.error)
            assertEquals(badRun.draft.source, compilation.shaderSource)
            assertTrue(compilation.message.contains("missingFunction"), compilation.message)
            assertTrue(state.fail(badRun, compilation.message))
            assertSame(lastGood, state.lastGood)
            assertTrue(state.stale)
            assertEquals(exported, KotlinExport.generate(assertNotNull(state.lastGood).project.effect))
            assertEquals(compilation.message, state.error)

            state.edit(WorkbenchStarters.tint)
            val recovery = state.beginRun()
            val fixed = recovery.build()
            processor.process(input, fixed.effect).getOrThrow()
            assertTrue(state.promote(recovery, fixed))
            assertFalse(state.stale)
            assertNull(state.error)
        }
    }

    @Test
    fun renamedScalarCannotReachSilentNativeSetterThroughWorkbench() {
        val draft = WorkbenchStarters.tint.copy(
            source = WorkbenchStarters.tint.source.replace("intensity", "renamedIntensity"),
        )
        val error = assertFailsWith<IllegalArgumentException> { draft.build() }
        assertTrue(error.message.orEmpty().contains("intensity"))
        assertTrue(error.message.orEmpty().contains("renamedIntensity"))
        val fixed = draft.copy(parameters = draft.parameters.map { it.copy(uniformName = "renamedIntensity") })
        ShaderFactory.create(1).use { factory ->
            val result = ImageProcessor.create(factory).process(pattern(), fixed.build().effect).getOrThrow()
            assertEquals(16 * 16, pixels(result).size)
        }
    }

    @Test
    fun distortionAnimatedAndImageStartersUseRealSdkRenderingAndRetainImageModes() {
        val input = pattern()
        val image = assertNotNull(decodeImageBytes(input, 16))
        try {
            ShaderFactory.create(WorkbenchLimits.CACHE_SIZE).use { factory ->
                val processor = ImageProcessor.create(factory)
                val distortion = WorkbenchStarters.distortion.build().effect
                val original = pixels(input)
                val distorted = pixels(processor.process(input, distortion).getOrThrow())
                assertTrue(original.indices.any { original[it] != distorted[it] })

                val animated = WorkbenchStarters.animated.build().effect as AnimatedShaderEffect
                val start = pixels(processor.process(input, animated.withTime(0f) as ShaderEffect).getOrThrow())
                val next = pixels(processor.process(input, animated.withTime(1f) as ShaderEffect).getOrThrow())
                assertTrue(start.indices.any { start[it] != next[it] })

                val imageDraft = WorkbenchStarters.image.build(image)
                val copiedImage = imageDraft.effect.withTypedParameter("intensity", ParameterValue.FloatValue(1f))
                assertContentEquals(original, pixels(processor.process(input, copiedImage).getOrThrow()))
                val code = KotlinExport.generate(copiedImage)
                assertTrue(code.contains("ShaderTileMode.REPEAT"))
                assertTrue(code.contains("ShaderTileMode.MIRROR"))
            }
        } finally {
            image.asSkiaBitmap().close()
        }
    }

    @Test
    fun nativeSessionFailureRecoversAndRepeatedCloseDoesNotRetainUsableSession() {
        val good = WorkbenchStarters.animated.build().effect as AnimatedShaderEffect
        val broken = WorkbenchStarters.animated.copy(source = WorkbenchStarters.animated.source
            .replace("return content.eval", "return unknownCall")).build().effect as AnimatedShaderEffect
        ShaderFactory.create(1).use { factory ->
            repeat(12) {
                val session = factory.createAnimatedSession(good, 16f, 16f).getOrThrow()
                val failure = assertIs<ShaderResult.Failure<*>>(session.refresh(broken, 16f, 16f))
                assertIs<ShaderError.CompilationError>(failure.error)
                session.refresh(good, 16f, 16f).getOrThrow()
                session.close()
                session.close()
                assertIs<ShaderResult.Failure<*>>(session.refresh(good, 16f, 16f))
                factory.clearCache()
            }
            val recovered = ImageProcessor.create(factory).process(pattern(), good).getOrThrow()
            assertEquals(256, pixels(recovered).size)
        }
    }

    private fun pattern(): ByteArray = Surface.makeRasterN32Premul(16, 16).use { surface ->
        Paint().use { paint ->
            for (x in 0 until 16) {
                paint.color = Color.makeRGB(48 + x * 12, 192 - x * 6, 64 + x * 10)
                surface.canvas.drawRect(Rect.makeXYWH(x.toFloat(), 0f, 1f, 16f), paint)
            }
        }
        surface.makeImageSnapshot().use { it.encodeToData(EncodedImageFormat.PNG)!!.use { data -> data.bytes } }
    }

    private fun pixels(bytes: ByteArray): IntArray = Image.makeFromEncoded(bytes).use { image ->
        Bitmap.makeFromImage(image).use { bitmap ->
            IntArray(image.width * image.height) { index -> bitmap.getColor(index % image.width, index / image.width) }
        }
    }
}
