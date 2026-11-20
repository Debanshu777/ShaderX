package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.uniform.*
import org.jetbrains.skia.*
import org.jetbrains.skia.impl.use
import kotlin.test.*

/** Real raster tests shared by JVM, Wasm and iOS; no Android or physical GPU claim. */
class NestedInputRenderTest {
    private val child = ShaderDefinition("child", program = ShaderProgram(
        "uniform float value; uniform float time; half4 main(float2 p) { return half4(value,time,0,1); }",
        listOf(ShaderBinding.frameFloat("value"), ShaderBinding.time()),
    )).proceduralBackground()

    private fun node(value: Float, time: Float = 0f): ShaderNode = ShaderNode.snapshot(
        child, timeSeconds = time, frameProvider = ShaderFrameProvider { listOf(FloatUniform("value", value)) },
    )

    @Test
    fun sameProgramSiblingsKeepIndependentValuesAndExplicitChildTimeAfterCacheEviction() {
        val first = node(.2f, .3f)
        val second = node(.8f, .7f)
        ShaderFactory.create(maxCacheSize = 1).use { factory ->
            val effect = Stage(listOf(NestedUniform("left", first), NestedUniform("right", second)))
            val filter = factory.createRenderEffect(effect, 8f, 8f).getOrThrow().asSkiaImageFilter()
            factory.clearCache()
            assertEquals(0, factory.cacheSize)
            factory.close()
            filter.use { retained ->
                repeat(4) {
                    val pixels = render(retained)
                    near(51, Color.getR(pixels[2]))
                    near(77, Color.getG(pixels[2]))
                    near(204, Color.getR(pixels[6]))
                    near(179, Color.getG(pixels[6]))
                }
            }
        }
    }

    @Test
    fun sessionReplacementKeepsOldSnapshotsAndSurvivesProgramEvictionAndRepeatedDisposal() {
        ShaderFactory.create(maxCacheSize = 1).use { factory ->
            var current = node(.2f, .3f)
            val stage = LiveStage({ current })
            val session = factory.createAnimatedSession(stage, 8f, 8f).getOrThrow()
            val old = session.currentRenderEffect.asSkiaImageFilter()
            try {
                near(51, Color.getR(render(old)[2]))
                current = node(.8f, .7f)
                val updated = session.refresh(stage.withTime(99f), 8f, 8f).getOrThrow().asSkiaImageFilter()
                updated.use {
                    near(204, Color.getR(render(it)[2]))
                    near(179, Color.getG(render(it)[2]), "Parent time does not replace child time")
                    factory.clearCache()
                    near(204, Color.getR(render(it)[2]))
                }
                session.close(); session.close(); factory.close()
                near(51, Color.getR(render(old)[2]))
                assertTrue(session.refresh(stage, 8f, 8f) is ShaderResult.Failure)
            } finally { old.close(); session.close() }
        }
    }

    @Test
    fun nestedSourceReplacementAndCompilerFailurePreserveExistingSnapshots() {
        ShaderFactory.create(maxCacheSize = 1).use { factory ->
            val good = Stage(listOf(NestedUniform("left", node(.2f)), NestedUniform("right", node(.8f))))
            factory.createRenderEffect(good, 8f, 8f).getOrThrow().asSkiaImageFilter().use { filter ->
                val broken = ShaderDefinition("broken", program = ShaderProgram("not valid SkSL")).proceduralBackground()
                val replacement = Stage(listOf(NestedUniform("left", ShaderNode.snapshot(broken)), NestedUniform("right", node(.8f))))
                val failed=assertIs<ShaderResult.Failure<*>>(factory.createRenderEffect(replacement,8f,8f))
                val error=assertIs<ShaderError.CompilationError>(failed.error)
                assertEquals(broken.definition.program.source,error.shaderSource)
                assertTrue(error.message.contains("left(broken)"),error.message)
                near(51, Color.getR(render(filter)[2]))
                val green = ShaderDefinition("green", program = ShaderProgram("half4 main(float2 p) { return half4(0,1,0,1); }")).proceduralBackground()
                val next = Stage(listOf(NestedUniform("left", ShaderNode.snapshot(green)), NestedUniform("right", node(.8f))))
                factory.createRenderEffect(next, 8f, 8f).getOrThrow().asSkiaImageFilter().use { changed ->
                    near(255, Color.getG(render(changed)[2]))
                    near(51, Color.getR(render(filter)[2]))
                }
            }
        }
    }

    @Test
    fun rawInputRejectionPrecedesRootCompileAndChildAllocations() {
        ShaderFactory.create().use { factory ->
            val data = DataUniform("raw", Rgba8Data(1,1, byteArrayOf(64,128.toByte(),255.toByte(),0)))
            val rawEffect = Stage(listOf(data), "deliberately invalid source")
            val failure = factory.createRenderEffect(rawEffect,8f,8f)
            assertTrue(failure is ShaderResult.Failure)
            assertTrue(failure.toString().contains("Raw data"), failure.toString())
            assertEquals(0,factory.cacheSize,"Unsupported data must fail before compiling even root source")
            val rawChild = ShaderDefinition("rawChild", program = ShaderProgram(
                "uniform shader raw; half4 main(float2 p) { return raw.eval(p); }",
                listOf(ShaderBinding.uniform(data)),
            )).proceduralBackground()
            val effect = Stage(listOf(NestedUniform("left", node(.2f)), NestedUniform("right", ShaderNode.snapshot(rawChild))))
            assertTrue(factory.createRenderEffect(effect,8f,8f) is ShaderResult.Failure)
            assertEquals(0,factory.cacheSize,"A late unsupported descendant rejects before earlier child compilation")
        }
    }

    @Test
    fun nestedBackgroundAndOfflineExportRetainExplicitInputsAfterTemporaryWrappersClose() {
        ShaderFactory.create(maxCacheSize = 1).use { factory ->
            val background = ShaderDefinition("parentBackground", program = ShaderProgram(
                "uniform shader child; half4 main(float2 p) { return child.eval(p); }",
                listOf(ShaderBinding.uniform(NestedUniform("child",node(.4f,.6f)))),
            )).proceduralBackground()
            val snapshot = createBackgroundSnapshot(factory,background,background.definition.schema.defaults(),8f,8f,99f,null).getOrThrow()
            try {
                factory.clearCache()
                Surface.makeRasterN32Premul(8,8).use { surface ->
                    CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,surface.canvas.asComposeCanvas(),Size(8f,8f)) {
                        drawRect(snapshot.brush)
                    }
                    surface.makeImageSnapshot().use { image -> Bitmap.makeFromImage(image).use { pixels ->
                        near(102,Color.getR(pixels.getColor(2,2)));near(153,Color.getG(pixels.getColor(2,2)))
                    } }
                }
            } finally { snapshot.close();snapshot.close() }
            val bytes = Surface.makeRasterN32Premul(8,8).use { surface ->
                surface.canvas.clear(Color.WHITE)
                surface.makeImageSnapshot().use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes } }
            }
            val effect = Stage(listOf(NestedUniform("left",node(.2f)),NestedUniform("right",node(.8f))))
            val exported = ImageProcessor.create(factory).process(bytes,effect).getOrThrow()
            Image.makeFromEncoded(exported).use { image -> Bitmap.makeFromImage(image).use { pixels ->
                near(51,Color.getR(pixels.getColor(2,2)));near(204,Color.getR(pixels.getColor(6,2)))
            } }
        }
    }

    @Test
    fun metadataQueriesAndKnownRawExportNeverResolveProvidersOrCompileSource() {
        ShaderFactory.create().use { factory ->
            var calls = 0
            val definition = ShaderDefinition("knownRaw", program = ShaderProgram(
                "invalid source deliberately never compiled",listOf(ShaderBinding.frameData("raw")),
            ))
            val effect = definition.animatedEffect(frameProvider = ShaderFrameProvider { calls++; error("provider must not run") })
            val support = factory.querySupport(ShaderOperation.CONTENT_RENDER,effect)
            assertEquals(ShaderSupportReason.RAW_DATA_UNAVAILABLE,support.reason)
            assertEquals(ShaderSupportReason.RAW_DATA_UNAVAILABLE,factory.querySupport(ShaderOperation.IMAGE_EXPORT,effect).reason)
            assertTrue(ImageProcessor.create(factory).process(byteArrayOf(),effect) is ShaderResult.Failure)
            assertEquals(0,calls)
            assertEquals(0,factory.cacheSize)
        }
    }

    @Test
    fun nestedCompilerDiagnosticsKeepDeepestSourceAndAliasDefinitionPathAcrossOperations() {
        val invalidSource = "half4 main(float2 p) { return deepest_child_syntax_error; }"
        val broken = ShaderNode.snapshot(ShaderDefinition("brokenGrandchild",program=ShaderProgram(invalidSource)).proceduralBackground())
        val middle = ShaderDefinition("middleChild",program=ShaderProgram(
            "uniform shader grandAlias; half4 main(float2 p) { return grandAlias.eval(p); }",
            listOf(ShaderBinding.uniform(NestedUniform("grandAlias",broken))),
        )).proceduralBackground()
        val nested = ShaderNode.snapshot(middle)
        val effect = Stage(listOf(NestedUniform("left",nested),NestedUniform("right",node(.8f))))
        fun diagnostic(result:ShaderResult<*>) {
            val error=assertIs<ShaderError.CompilationError>(assertIs<ShaderResult.Failure<*>>(result).error)
            assertEquals(invalidSource,error.shaderSource)
            assertTrue(error.message.contains("left(middleChild)/grandAlias(brokenGrandchild)"),error.message)
        }
        ShaderFactory.create(1).use { factory ->
            diagnostic(factory.createRenderEffect(effect,8f,8f))
            val bytes=Surface.makeRasterN32Premul(8,8).use { surface ->
                surface.canvas.clear(Color.WHITE)
                surface.makeImageSnapshot().use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes } }
            }
            diagnostic(ImageProcessor.create(factory).process(bytes,effect))
            val background=ShaderDefinition("rootBackground",program=ShaderProgram(
                "uniform shader left;half4 main(float2 p) { return left.eval(p); }",
                listOf(ShaderBinding.uniform(NestedUniform("left",nested))),
            )).proceduralBackground()
            diagnostic(createBackgroundSnapshot(factory,background,background.definition.schema.defaults(),8f,8f,0f,null))
            var current=node(.2f)
            val live=LiveStage({ current })
            factory.createAnimatedSession(live,8f,8f).getOrThrow().use { session ->
                current=nested
                diagnostic(session.refresh(live,8f,8f))
            }
            current=nested
            diagnostic(factory.createAnimatedSession(live,8f,8f))
        }
    }

    @Test
    fun parentCompilationErrorsKeepParentSourceWithValidNestedInputs() {
        val parentSource="half4 main(float2 p) { return invalid_parent_syntax; }"
        ShaderFactory.create().use { factory ->
            val result=factory.createRenderEffect(Stage(listOf(NestedUniform("wrongAlias",node(.2f))),parentSource),8f,8f)
            val error=assertIs<ShaderError.CompilationError>(assertIs<ShaderResult.Failure<*>>(result).error)
            assertEquals(parentSource,error.shaderSource)
            assertFalse(error.message.contains("Child input"),error.message)
        }
    }

    @Test
    fun unknownNestedParentAliasFailsBeforeCompilationWithParentSource() {
        ShaderFactory.create().use { factory ->
            val effect=Stage(listOf(NestedUniform("missingAlias",node(.2f))))
            val failure=assertIs<ShaderResult.Failure<*>>(factory.createRenderEffect(effect,8f,8f))
            val error=assertIs<ShaderError.CompilationError>(failure.error)
            assertEquals(effect.shaderSource,error.shaderSource)
            assertTrue(error.message.contains("Parent binding 'missingAlias'"),error.message)
            assertFalse(error.message.contains("Child input"),error.message)
            assertEquals(0,factory.cacheSize)
        }
    }

    @Test
    fun manualSessionAliasPreflightAndDiagnosticsUseCapturedNativeSourceWithoutRequeryingGetter() {
        val delegate=LiveStage({ node(.2f) })
        val original=delegate.shaderSource
        var source=original
        var calls=0
        var wrongAlias=false
        val effect=object:AnimatedShaderEffect by delegate {
            override val shaderSource:String get() { calls++; return source }
            override fun buildFrameUniforms():List<Uniform> = if(wrongAlias) listOf(NestedUniform("missingAlias",node(.2f))) else delegate.buildFrameUniforms()
        }
        ShaderFactory.create().use { factory ->
            factory.createAnimatedSession(effect,8f,8f).getOrThrow().use { session ->
                assertEquals(1,calls)
                source="not the native program anymore"
                assertIs<ShaderResult.Success<*>>(session.refresh(effect,8f,8f))
                assertEquals(1,calls,"Refreshing an existing native program must not acquire a new source")
                wrongAlias=true
                val failed=assertIs<ShaderResult.Failure<*>>(session.refresh(effect,8f,8f))
                val error=assertIs<ShaderError.CompilationError>(failed.error)
                assertEquals(original,error.shaderSource)
                assertTrue(error.message.contains("missingAlias"),error.message)
                assertEquals(1,calls,"Failure diagnostics retain the actual compiled source")
            }
        }
    }

    @Test
    fun staticAndExportParentErrorsRetainSourceCapturedBeforeMutatingUniformProvider() {
        val delegate=Stage(emptyList())
        val original=delegate.shaderSource
        var source=original
        var calls=0
        val effect=object:RuntimeShaderEffect by delegate {
            override val shaderSource:String get() { calls++; return source }
            override fun buildUniforms(width:Float,height:Float):List<Uniform> {
                source="changed by provider after source capture"
                return listOf(NestedUniform("missingAlias",node(.2f)))
            }
        }
        fun check(result:ShaderResult<*>) {
            val error=assertIs<ShaderError.CompilationError>(assertIs<ShaderResult.Failure<*>>(result).error)
            assertEquals(original,error.shaderSource)
            assertTrue(error.message.contains("missingAlias"),error.message)
        }
        ShaderFactory.create().use { factory ->
            check(factory.createRenderEffect(effect,8f,8f))
            assertEquals(1,calls)
            source=original
            val bytes=Surface.makeRasterN32Premul(8,8).use { surface ->
                surface.canvas.clear(Color.WHITE)
                surface.makeImageSnapshot().use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { it.bytes } }
            }
            check(ImageProcessor.create(factory).process(bytes,effect))
            assertEquals(2,calls)
            assertEquals(0,factory.cacheSize)
        }
    }

    @Test
    fun closedManualAndDefinedOwnersRejectAccessWhileSavedOutputsSurviveCacheAndOwnerDisposal() {
        val definition=ShaderDefinition("ownedNested",program=ShaderProgram(
            Stage(emptyList()).shaderSource,
            listOf(ShaderBinding.uniform(NestedUniform("left",node(.2f))),ShaderBinding.uniform(NestedUniform("right",node(.8f)))),
        ))
        val effects=listOf(LiveStage({ node(.2f) }),definition.animatedEffect(isAnimating=false))
        for(effect in effects) ShaderFactory.create(1).use { factory ->
            val session=factory.createAnimatedSession(effect,8f,8f).getOrThrow()
            val saved=session.currentRenderEffect
            val filter=saved.asSkiaImageFilter()
            try {
                val before=render(filter)
                session.close();session.close()
                assertFailsWith<IllegalStateException> { session.currentRenderEffect }
                assertIs<ShaderResult.Failure<*>>(session.refresh(effect,8f,8f))
                factory.clearCache();factory.close()
                assertContentEquals(before,render(saved.asSkiaImageFilter()),"Caller-held output remains independent of closed owner")
            } finally { filter.close();session.close() }
        }
    }

    private open class Stage(
        private val uniforms: List<Uniform>,
        override val shaderSource: String = """uniform shader content; uniform shader left; uniform shader right;
            half4 main(float2 p) { return (p.x < 4 ? left.eval(p) : right.eval(p)) * content.eval(p).a; }""",
    ) : RuntimeShaderEffect {
        override val id = "nested-test"
        override val displayName = id
        override val parameters: List<ParameterSpec> = emptyList()
        override fun buildUniforms(width:Float,height:Float):List<Uniform> = uniforms
        override fun withTypedParameter(parameterId:String,value:ParameterValue):RuntimeShaderEffect = this
    }

    private class LiveStage(private val input: () -> ShaderNode, override val time:Float=0f) :
        Stage(emptyList()), AnimatedShaderEffect {
        override fun withTypedParameter(parameterId:String,value:ParameterValue):LiveStage = this
        override val isAnimating = false
        override fun withTime(newTime:Float) = LiveStage(input,newTime)
        override fun buildUniforms(width:Float,height:Float) = buildFrameUniforms()
        override fun buildFrameUniforms():List<Uniform> = listOf(NestedUniform("left",input()),NestedUniform("right",input()))
    }

    private fun render(filter: ImageFilter): IntArray = Surface.makeRasterN32Premul(8,8).use { surface ->
        surface.canvas.clear(Color.TRANSPARENT)
        Paint().use { paint ->
            paint.color = Color.WHITE; paint.imageFilter = filter
            surface.canvas.drawRect(Rect.makeWH(8f,8f),paint)
        }
        surface.makeImageSnapshot().use { image -> Bitmap.makeFromImage(image).use { bitmap ->
            IntArray(64) { bitmap.getColor(it%8,it/8) }
        } }
    }

    private fun near(expected:Int,actual:Int,message:String="") = assertTrue(kotlin.math.abs(expected-actual)<=2,"$expected vs $actual $message")
}
