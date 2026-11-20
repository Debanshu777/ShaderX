package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.NativeBlurEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.factory.createPipelineSession
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import org.jetbrains.skia.*
import org.jetbrains.skia.impl.use
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** SX05 bounded native experiment; uses CPU raster, not hardware GPU evidence. */
class PortableCompositionExperimentTest {
    @Test
    fun composeOrderIsNoncommutative_andRetainsClosedChildWrappers() {
        val add = runtime("half4 c=content.eval(p); return half4(c.r+.2*c.a,c.gba);")
        val scale = runtime("half4 c=content.eval(p); return half4(c.r*.5,c.gba);")
        val forward = ImageFilter.makeCompose(scale, add)
        val reverse = ImageFilter.makeCompose(add, scale)
        add.close()
        scale.close()
        forward.use { a -> reverse.use { b ->
            val ab = render(a)
            val ba = render(b)
            assertTrue(Color.getR(ab[32*64+32]) in 49..53)
            assertTrue(Color.getR(ba[32*64+32]) in 74..79)
            assertTrue(Color.getR(ba[32*64+32]) > Color.getR(ab[32*64+32])+20)
            assertEquals(0, Color.getA(ab[0]), "Transparent exterior stays transparent")
        } }
        assertTrue(forward.isClosed && reverse.isClosed)
    }

    @Test
    fun activeBuilderSurvivesClosedCompiledEffect_andSnapshotsKeepIndependentUniforms() {
        val program = RuntimeEffect.makeForShader("uniform shader content; uniform float gain; half4 main(float2 p) { half4 c=content.eval(p); return half4(c.rgb*gain,c.a); }")
        val builder = RuntimeShaderBuilder(program)
        program.close() // models the compiled cache entry being evicted
        builder.uniform("gain", .5f)
        val first = ImageFilter.makeRuntimeShader(builder,"content",null)
        builder.uniform("gain", 1f)
        val second = ImageFilter.makeRuntimeShader(builder,"content",null)
        builder.close()
        first.use { a -> second.use { b ->
            val old = Color.getR(render(a)[32*64+32])
            val newer = Color.getR(render(b)[32*64+32])
            assertTrue(old in 24..27 && newer in 49..53, "$old vs $newer")
            repeat(8) { assertEquals(old, Color.getR(render(a)[32*64+32])) }
        } }
        assertTrue(program.isClosed && builder.isClosed)
    }

    @Test
    fun runtimeBlurPreservesAlphaExpansion_andMatchesSequentialReferenceAtTwoSizes() {
        for (size in listOf(64,96)) {
            runtime("half4 c=content.eval(p); return half4(c.r*.5,c.gba);").use { color ->
                ImageFilter.makeBlur(3f,3f,FilterTileMode.CLAMP).use { blur ->
                    ImageFilter.makeCompose(blur,color).use { composed ->
                        val actual=render(composed,size)
                        val reference=sequential(color,blur,size)
                        assertPixels(reference,actual,2)
                        val y=size/2
                        assertTrue(Color.getA(actual[y*size+14]) in 1..254,"Blur expands alpha outside source rect")
                        assertEquals(0, Color.getA(actual[0]))
                    }
                }
            }
        }
    }

    @Test
    fun runtimeDistortionAfterBlur_matchesSequentialReferenceIncludingTransparentEdges() {
        for (size in listOf(64,96)) {
            ImageFilter.makeBlur(2f,2f,FilterTileMode.CLAMP).use { blur ->
                runtime("return content.eval(p+float2(4,0));").use { shift ->
                    ImageFilter.makeCompose(shift,blur).use { composed ->
                        val actual=render(composed,size)
                        val reference=sequential(blur,shift,size)
                        assertPixels(reference,actual,2)
                    }
                }
            }
        }
    }

    @Test
    fun nestedAndIdentityCompositionPreserveOrder() {
        runtime("return content.eval(p);").use { identity ->
            runtime("half4 c=content.eval(p); return half4(c.r*.5,c.gba);").use { color ->
                ImageFilter.makeCompose(identity,color).use { nested ->
                    ImageFilter.makeCompose(nested,identity).use { whole ->
                        assertPixels(render(color), render(whole),0)
                    }
                }
            }
        }
    }

    @Test
    fun publicFactoryPreservesOrderNestedIdentity_independentValuesAndEvictedFilters() {
        ShaderFactory.create(maxCacheSize=1).use { factory ->
            val add=Stage("half4 c=content.eval(p); return half4(c.r+gain*c.a,c.gba);",.2f)
            val scale=Stage("half4 c=content.eval(p); return half4(c.r*gain,c.gba);",.5f)
            val identity=Stage("return content.eval(p);",0f)
            val forward=factory.createRenderEffect(CompositeEffect.of(add,scale),64f,64f).getOrNull()!!
            val reverse=factory.createRenderEffect(CompositeEffect.of(scale,add),64f,64f).getOrNull()!!
            val nested=factory.createRenderEffect(CompositeEffect.of(identity,CompositeEffect.of(add,scale)),64f,64f).getOrNull()!!
            val separate=factory.createRenderEffect(CompositeEffect.of(add.copy(gain=.4f),scale),64f,64f).getOrNull()!!
            factory.clearCache()
            assertEquals(0,factory.cacheSize)
            forward.asSkiaImageFilter().use { a -> reverse.asSkiaImageFilter().use { b ->
                nested.asSkiaImageFilter().use { n -> separate.asSkiaImageFilter().use { other ->
                    val first=render(a)
                    assertPixels(first,render(n),0)
                    assertTrue(Color.getR(render(b)[32*64+32])>Color.getR(first[32*64+32])+20)
                    assertTrue(Color.getR(render(other)[32*64+32])>Color.getR(first[32*64+32])+20)
                    repeat(8) { assertPixels(first,render(a),0) }
                } }
            } }
        }
    }

    @Test
    fun publicFactoryInvalidChildFailsWholePipeline_withoutPoisoningExistingResult() {
        ShaderFactory.create(maxCacheSize=1).use { factory ->
            val valid=Stage("return content.eval(p);",0f)
            val good=factory.createRenderEffect(CompositeEffect.of(valid,valid),64f,64f).getOrNull()!!
            good.asSkiaImageFilter().use { retained ->
                val before=render(retained)
                val bad=Stage("not valid shader syntax",0f)
                assertTrue(factory.createRenderEffect(CompositeEffect.of(valid,bad,valid),64f,64f) is ShaderResult.Failure)
                assertPixels(before,render(retained),0)
            }
        }
    }

    @Test
    fun publicFactoryNativeBlurDistortionResize_matchesExplicitSequentialPixels() {
        ShaderFactory.create().use { factory ->
            for(size in listOf(64,96)) {
                val stages=listOf(NativeBlurEffect(2f),Stage("return content.eval(p+float2(4,0));",0f))
                val whole=factory.createRenderEffect(CompositeEffect(stages),size.toFloat(),size.toFloat()).getOrNull()!!
                val first=factory.createRenderEffect(stages[0],size.toFloat(),size.toFloat()).getOrNull()!!
                val second=factory.createRenderEffect(stages[1],size.toFloat(),size.toFloat()).getOrNull()!!
                whole.asSkiaImageFilter().use { composed -> first.asSkiaImageFilter().use { inner -> second.asSkiaImageFilter().use { outer ->
                    assertPixels(sequential(inner,outer,size),render(composed,size),2)
                } } }
            }
        }
    }

    @Test
    fun pipelineSnapshotsShareTimestamp_updateLiveWhilePaused_andKeepIndependentInstances() {
        ShaderFactory.create(maxCacheSize=1).use { factory ->
            var live=.2f
            val animated=ClockStage(99f) { live }
            val chain=CompositeEffect.of(animated,animated)
            factory.createPipelineSession(chain,64f,64f,1f).getOrNull()!!.use { session ->
                factory.createPipelineSession(chain,64f,64f,0f).getOrNull()!!.use { independent ->
                    val initial=session.currentRenderEffect
                    val first=render(initial.asSkiaImageFilter())
                    assertTrue(Color.getR(first[32*64+32]) in 100..104,"Both children receive1.0 seconds, ignoring stored99 seconds")
                    assertTrue(Color.getR(render(independent.currentRenderEffect.asSkiaImageFilter())[32*64+32]) in 49..53)
                    live=.5f
                    val updated=session.refresh(chain,64f,64f,1f).getOrNull()!!
                    val later=render(updated.asSkiaImageFilter())
                    assertEquals(Color.getR(first[32*64+32]),Color.getR(later[32*64+32]),"Explicit live refresh preserves paused clock")
                    assertTrue(Color.getG(later[32*64+32])>Color.getG(first[32*64+32])+10)
                    factory.clearCache()
                    assertPixels(first,render(initial.asSkiaImageFilter()),0)
                    assertPixels(later,render(updated.asSkiaImageFilter()),0)
                }
            }
        }
    }

    @Test
    fun pipelineSourceProviderAndSizeFailuresRetainLastWholeNativeSnapshot_thenRecover() {
        ShaderFactory.create(maxCacheSize=1).use { factory ->
            var fails=false
            val animated=ClockStage(0f) { if(fails) error("Frame not ready") else .2f }
            val chain=CompositeEffect.of(animated,Stage("return content.eval(p);",0f))
            val session=factory.createPipelineSession(chain,64f,64f,.5f).getOrNull()!!
            val initial=session.currentRenderEffect
            val before=render(initial.asSkiaImageFilter())
            fails=true
            assertTrue(session.refresh(chain,96f,96f,2f) is ShaderResult.Failure)
            assertTrue(session.currentRenderEffect===initial)
            fails=false
            val invalid=CompositeEffect.of(animated,Stage("this is not SkSL",0f))
            assertTrue(session.refresh(invalid,96f,96f,2f) is ShaderResult.Failure)
            assertTrue(session.currentRenderEffect===initial)
            assertTrue(session.refresh(chain,0f,96f,2f) is ShaderResult.Failure)
            assertTrue(session.currentRenderEffect===initial)
            assertPixels(before,render(session.currentRenderEffect.asSkiaImageFilter()),0)
            val replacement=session.refresh(chain,96f,96f,2f).getOrNull()!!
            assertTrue(Color.getR(render(replacement.asSkiaImageFilter(),96)[48*96+48])>Color.getR(before[32*64+32])+20)
            assertPixels(before,render(initial.asSkiaImageFilter()),0)
            session.close()
            session.close()
            assertTrue(session.refresh(chain,64f,64f,1f) is ShaderResult.Failure)
            // Caller-held Compose snapshots remain usable after the owner and cache are closed.
            factory.close()
            assertPixels(before,render(initial.asSkiaImageFilter()),0)
        }
    }

    @Test
    fun sourceIsCapturedOnceBeforeUniformProvidersAndNativeCreation() {
        ShaderFactory.create().use { factory ->
            val events = mutableListOf<String>()
            val delegate = Stage("half4 c=content.eval(p); return half4(c.rgb*gain,c.a);", .5f)
            var source = delegate.shaderSource
            val first = object : RuntimeShaderEffect by delegate {
                override val shaderSource: String get() { events += "source"; return source }
                override fun buildUniforms(width: Float, height: Float): List<com.debanshu.shaderlab.shaderx.uniform.Uniform> {
                    events += "uniforms"
                    // Even this child's provider must not change the source already acquired.
                    source = "invalid source after provider"
                    return delegate.buildUniforms(width, height)
                }
            }
            val later = object : RuntimeShaderEffect by Stage("return content.eval(p);", 1f) {
                override fun buildUniforms(width: Float, height: Float): List<com.debanshu.shaderlab.shaderx.uniform.Uniform> {
                    assertEquals(listOf("source", "uniforms"), events)
                    assertEquals(0, factory.cacheSize, "No earlier runtime child may be created before later providers finish")
                    events += "later"
                    return listOf(FloatUniform("gain", 1f))
                }
            }
            factory.createPipelineSession(CompositeEffect.of(first, later), 64f, 64f).getOrNull()!!.use { session ->
                assertEquals(listOf("source", "uniforms", "later"), events, "Backend must read only captured source, never original getter")
                val value = Color.getR(render(session.currentRenderEffect.asSkiaImageFilter())[32 * 64 + 32])
                assertTrue(value in 24..27, "Frozen valid source still renders after provider mutation: $value")
            }
        }
    }

    @Test
    fun throwingLaterSourceFailsBeforeAnyNativeChildCreation() {
        ShaderFactory.create().use { factory ->
            var laterProviderCalls = 0
            val first = Stage("return content.eval(p) * gain;", 1f)
            val badSource = object : RuntimeShaderEffect by first {
                override val shaderSource: String get() = throw IllegalStateException("Source unavailable")
                override fun buildUniforms(width: Float, height: Float): List<com.debanshu.shaderlab.shaderx.uniform.Uniform> {
                    laterProviderCalls++
                    return first.buildUniforms(width, height)
                }
            }
            assertTrue(factory.createPipelineSession(CompositeEffect.of(first, badSource), 64f, 64f) is ShaderResult.Failure)
            assertEquals(0, factory.cacheSize, "An earlier child must not compile before every source is acquired")
            assertEquals(0, laterProviderCalls, "A failed source acquisition precedes its uniform provider")
        }
    }

    private class ClockStage(override val time:Float,val live:()->Float):AnimatedShaderEffect {
        override val id="clock_stage"
        override val displayName="Clock stage"
        override val parameters:List<ParameterSpec> = emptyList()
        override val isAnimating=false
        override val shaderSource="""uniform shader content; uniform float time; uniform float live;
            half4 main(float2 p) { half4 c=content.eval(p); return half4(c.r+.1*time*c.a,c.g+.1*live*c.a,c.ba); }"""
        override fun withTime(newTime:Float)=ClockStage(newTime,live)
        override fun buildUniforms(width:Float,height:Float)=listOf(FloatUniform("time",time),FloatUniform("live",live()))
        override fun buildFrameUniforms()=listOf(FloatUniform("time",time),FloatUniform("live",live()))
        override fun withTypedParameter(parameterId:String,value:ParameterValue)=this
        override fun equals(other:Any?)=other is ClockStage && other.live===live
        override fun hashCode()=live.hashCode()
    }

    private data class Stage(val body:String,val gain:Float):RuntimeShaderEffect {
        override val id="experiment_stage"
        override val displayName="Experiment stage"
        override val parameters:List<ParameterSpec> = emptyList()
        override val shaderSource="uniform shader content; uniform float gain; half4 main(float2 p) { $body }"
        override fun buildUniforms(width:Float,height:Float)=listOf(FloatUniform("gain",gain))
        override fun withTypedParameter(parameterId:String,value:ParameterValue)=this
    }

    private fun runtime(body:String):ImageFilter =
        RuntimeEffect.makeForShader("uniform shader content; half4 main(float2 p) { $body }").use { effect ->
            RuntimeShaderBuilder(effect).use { builder -> ImageFilter.makeRuntimeShader(builder,"content",null) }
        }

    private fun render(filter:ImageFilter?, size:Int=64):IntArray = Surface.makeRasterN32Premul(size,size).use { surface ->
        surface.canvas.clear(Color.TRANSPARENT)
        Paint().use { paint ->
            paint.color=Color.makeARGB(255,51,102,153)
            paint.imageFilter=filter
            surface.canvas.drawRect(Rect.makeLTRB(16f,16f,(size-16).toFloat(),(size-16).toFloat()),paint)
        }
        pixels(surface,size)
    }

    private fun sequential(inner:ImageFilter,outer:ImageFilter,size:Int):IntArray =
        Surface.makeRasterN32Premul(size,size).use { first ->
            first.canvas.clear(Color.TRANSPARENT)
            Paint().use { paint ->
                paint.color=Color.makeARGB(255,51,102,153);paint.imageFilter=inner
                first.canvas.drawRect(Rect.makeLTRB(16f,16f,(size-16).toFloat(),(size-16).toFloat()),paint)
            }
            first.makeImageSnapshot().use { image ->
                Surface.makeRasterN32Premul(size,size).use { second ->
                    second.canvas.clear(Color.TRANSPARENT)
                    Paint().use { paint ->
                        paint.imageFilter=outer
                        second.canvas.drawImage(image,0f,0f,paint)
                    }
                    pixels(second,size)
                }
            }
        }

    private fun pixels(surface:Surface,size:Int):IntArray=surface.makeImageSnapshot().use { image ->
        Bitmap.makeFromImage(image).use { bitmap -> IntArray(size*size) { bitmap.getColor(it%size,it/size) } }
    }

    private fun assertPixels(expected:IntArray,actual:IntArray,tolerance:Int) {
        var mismatches=0
        var maximum=0
        for(i in expected.indices) {
            // RGB at alpha 0 is irrelevant; compare premultiplied channels and alpha.
            val e=expected[i];val a=actual[i]
            val alphaError=abs(Color.getA(e)-Color.getA(a))
            var error=alphaError
            for((ec,ac) in listOf(Color.getR(e) to Color.getR(a),Color.getG(e) to Color.getG(a),Color.getB(e) to Color.getB(a))) {
                error=maxOf(error,abs(ec*Color.getA(e)-ac*Color.getA(a))/255)
            }
            if(error>tolerance) mismatches++
            maximum=maxOf(maximum,error)
        }
        assertEquals(0,mismatches,"Raster reference mismatch: $mismatches pixels, maximum premultiplied channel error $maximum")
    }
}
