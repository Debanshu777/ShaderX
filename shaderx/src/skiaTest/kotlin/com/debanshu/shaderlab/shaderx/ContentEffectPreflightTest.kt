package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderFrameProvider
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram
import com.debanshu.shaderlab.shaderx.authoring.proceduralBackground
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.factory.ImageExportOptions
import com.debanshu.shaderlab.shaderx.factory.ImageProcessor
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.factory.createBackgroundSnapshot
import com.debanshu.shaderlab.shaderx.factory.createPipelineSession
import com.debanshu.shaderlab.shaderx.factory.exportImage
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Actual Skia routes shared by JVM, Wasm and iOS; these are not Android device checks. */
class ContentEffectPreflightTest {
    @Test
    fun sourceFreeInstancesRemainConstructibleButContentRenderingRejectsBeforeCaching() {
        val sources = listOf(
            "half4 main(float2 p) { return half4(1,0,0,1); }",
            "// uniform shader content;\n/* uniform shader content; */\nhalf4 main(float2 p) { return half4(1); }",
        )
        ShaderFactory.create().use { factory ->
            sources.forEachIndexed { index, source ->
                val definition = ShaderDefinition("missing_content_$index", program = ShaderProgram(source))
                val static = definition.effect()
                val animated = definition.animatedEffect(isAnimating = false)
                missingContent(factory.createRenderEffect(static, 8f, 8f), definition)
                missingContent(factory.createRenderEffect(animated, 8f, 8f), definition)
                missingContent(factory.createAnimatedSession(animated, 8f, 8f), definition)
                assertEquals(0, factory.cacheSize, "Rejected authored content must not reach the native cache")
            }
        }
    }

    @Test
    fun explicitContentDeclarationWithCommentsAndWhitespaceStillRendersPixels() {
        val definition = ShaderDefinition("spaced_content", program = ShaderProgram("""
            uniform /* input type */ shader
                /* input name */ content
                ;
            half4 main(float2 p) { return content.eval(p) * 0.25; }
        """.trimIndent()))
        ShaderFactory.create().use { factory ->
            val pixel = render(factory.createRenderEffect(definition.effect(), 8f, 8f).getOrThrow())
            assertTrue(Color.getR(pixel) in 253..255, "Quarter-opacity white must retain unpremultiplied white")
            assertTrue(Color.getA(pixel) in 62..65, "The valid declared shader must actually filter its input")
        }
    }

    @Test
    fun invalidDefinitionSessionReplacementRetainsValidNativePixelsAndSkipsItsProvider() {
        var calls = 0
        val valid = liveDefinition("valid_session", hasContent = true)
        val good = valid.animatedEffect(isAnimating = false, frameProvider = ShaderFrameProvider {
            listOf(FloatUniform("gain", 0.25f))
        })
        val invalid = liveDefinition("invalid_session", hasContent = false)
        val bad = invalid.animatedEffect(isAnimating = false, frameProvider = ShaderFrameProvider {
            calls++
            error("Missing content must be diagnosed before this provider")
        })
        ShaderFactory.create().use { factory ->
            missingContent(factory.createAnimatedSession(bad, 8f, 8f), invalid)
            assertEquals(0, calls)
            assertEquals(0, factory.cacheSize)
            factory.createAnimatedSession(good, 8f, 8f).getOrThrow().use { session ->
                val retained = session.currentRenderEffect
                val before = render(retained)
                val cacheBefore = factory.cacheSize
                assertTrue(Color.getR(before) in 62..65)
                missingContent(session.refresh(bad, 16f, 16f), invalid)
                assertSame(retained, session.currentRenderEffect)
                assertEquals(before, render(session.currentRenderEffect))
                assertEquals(cacheBefore, factory.cacheSize)
                assertEquals(0, calls)
                assertTrue(Color.getR(render(session.refresh(good, 8f, 8f).getOrThrow())) in 62..65)
                assertEquals(before, render(retained), "The previously returned native snapshot stays valid")
            }
        }
    }

    @Test
    fun laterMissingContentRejectsEntireCompositeAndPipelineBeforeEarlierLiveInput() {
        var calls = 0
        val earlier = liveDefinition("earlier_live", hasContent = true).animatedEffect(
            isAnimating = false,
            frameProvider = ShaderFrameProvider {
                calls++
                error("Whole-graph rejection must precede earlier live input resolution")
            },
        )
        val invalid = ShaderDefinition("later_missing", program = ShaderProgram("half4 main(float2 p) { return half4(1); }"))
        // Nest the invalid child to exercise the complete graph rather than just direct siblings.
        val graph = CompositeEffect.of(earlier, CompositeEffect.of(invalid.effect()))
        ShaderFactory.create().use { factory ->
            missingContent(factory.createRenderEffect(graph, 8f, 8f), invalid)
            missingContent(factory.createPipelineSession(graph, 8f, 8f, 1f), invalid)
            assertEquals(0, calls)
            assertEquals(0, factory.cacheSize, "Neither an earlier child nor the invalid child may compile")
        }
    }

    @Test
    fun invalidPipelineReplacementRetainsWholeSnapshotWithoutResamplingEarlierChild() {
        var calls = 0
        val earlier = liveDefinition("pipeline_live", hasContent = true).animatedEffect(
            isAnimating = false,
            frameProvider = ShaderFrameProvider { calls++; listOf(FloatUniform("gain", 0.5f)) },
        )
        val valid = ShaderDefinition("pipeline_tail", program = ShaderProgram(
            "uniform shader content; half4 main(float2 p) { half4 c = content.eval(p); return half4(c.rgb*0.5,c.a); }",
        ))
        val invalid = ShaderDefinition("invalid_pipeline_tail", program = ShaderProgram("half4 main(float2 p) { return half4(1); }"))
        val good = CompositeEffect.of(earlier, valid.effect())
        val bad = CompositeEffect.of(earlier, invalid.effect())
        ShaderFactory.create().use { factory ->
            factory.createPipelineSession(good, 8f, 8f, 0f).getOrThrow().use { session ->
                val retained = session.currentRenderEffect
                val before = render(retained)
                val callsBefore = calls
                val cacheBefore = factory.cacheSize
                assertTrue(callsBefore > 0, "The valid graph must have resolved real live input")
                assertTrue(Color.getR(before) in 62..65)
                missingContent(session.refresh(bad, 16f, 16f, 2f), invalid)
                assertEquals(callsBefore, calls, "Invalid replacement must not resample an earlier live child")
                assertEquals(cacheBefore, factory.cacheSize)
                assertSame(retained, session.currentRenderEffect)
                assertEquals(before, render(session.currentRenderEffect))
                session.refresh(good, 8f, 8f, 3f).getOrThrow()
                assertTrue(calls > callsBefore, "A subsequent valid refresh still resolves live input")
                assertEquals(before, render(retained))
            }
        }
    }

    @Test
    fun imageRoutesReportMissingContentBeforeInvalidBytesOrLiveProviders() = runTest {
        var calls = 0
        val definition = liveDefinition("missing_export_content", hasContent = false)
        val effect = definition.animatedEffect(isAnimating = false, frameProvider = ShaderFrameProvider {
            calls++
            error("Export must reject missing content before provider execution")
        })
        val static = ShaderDefinition("missing_static_export_content", program = ShaderProgram(
            "half4 main(float2 p) { return half4(1,0,0,1); }",
        ))
        val exports = listOf(definition to effect, static to static.effect())
        val options = ImageExportOptions(8, 8)
        ShaderFactory.create().use { factory ->
            val processor = ImageProcessor.create(factory)
            for ((owner, instance) in exports) for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 2, 3))) {
                missingContent(processor.process(bytes, instance), owner)
                missingContent(processor.exportImage(bytes, instance, options), owner)
            }
            assertEquals(0, factory.cacheSize)
        }
        for ((owner, instance) in exports) for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 2, 3))) {
            missingContent(exportImage(bytes, instance, options), owner)
        }
        assertEquals(0, calls)
    }

    @Test
    fun sameSourceFreeDefinitionStillRendersThroughExplicitProceduralBackgroundRoute() {
        val definition = ShaderDefinition("explicit_background", program = ShaderProgram(
            "half4 main(float2 p) { return half4(1,0,0,1); }",
        ))
        ShaderFactory.create().use { factory ->
            missingContent(factory.createRenderEffect(definition.effect(), 8f, 8f), definition)
            val background = definition.proceduralBackground()
            createBackgroundSnapshot(factory, background, definition.schema.defaults(), 8f, 8f, 0f, null).getOrThrow().use { snapshot ->
                Surface.makeRasterN32Premul(8, 8).use { surface ->
                    surface.canvas.clear(Color.TRANSPARENT)
                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, surface.canvas.asComposeCanvas(), Size(8f, 8f)) {
                        drawRect(snapshot.brush)
                    }
                    surface.makeImageSnapshot().use { image -> Bitmap.makeFromImage(image).use { pixels ->
                        assertEquals(Color.RED, pixels.getColor(4, 4))
                    } }
                }
            }
        }
    }

    private fun liveDefinition(id: String, hasContent: Boolean): ShaderDefinition = ShaderDefinition(id, program = ShaderProgram(
        (if (hasContent) "uniform shader content;\n" else "") +
            "uniform float gain; half4 main(float2 p) { " +
            (if (hasContent) "half4 c=content.eval(p); return half4(c.rgb*gain,c.a);" else "return half4(gain,0,0,1);") + " }",
        listOf(ShaderBinding.frameFloat("gain")),
    ))

    private fun missingContent(result: ShaderResult<*>, definition: ShaderDefinition) {
        val error = assertIs<ShaderError.CompilationError>(assertIs<ShaderResult.Failure<*>>(result).error)
        assertEquals(definition.program.source, error.shaderSource)
        assertTrue(error.message.contains(definition.id), error.message)
        assertTrue(error.message.contains("content"), error.message)
        assertTrue(error.message.contains("proceduralBackground"), error.message)
    }

    private fun render(effect: RenderEffect): Int = Surface.makeRasterN32Premul(8, 8).use { surface ->
        surface.canvas.clear(Color.TRANSPARENT)
        Paint().use { paint ->
            paint.color = Color.WHITE
            paint.imageFilter = effect.asSkiaImageFilter()
            surface.canvas.drawRect(Rect.makeWH(8f, 8f), paint)
        }
        surface.makeImageSnapshot().use { image -> Bitmap.makeFromImage(image).use { it.getColor(4, 4) } }
    }
}
