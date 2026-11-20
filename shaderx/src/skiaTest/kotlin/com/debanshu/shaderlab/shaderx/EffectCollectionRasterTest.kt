package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.authoring.DefinedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.impl.EffectCollection
import com.debanshu.shaderlab.shaderx.effect.impl.ColorMatrix
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.factory.createPipelineSession
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import org.jetbrains.skia.*
import org.jetbrains.skia.impl.use
import kotlin.math.*
import kotlin.test.*

/** Actual native Skia raster across JVM, Wasm and iOS simulator. No GPU throughput claim. */
class EffectCollectionRasterTest {
    @Test
    fun neutralDefaultsPreserveOriginalPixelsIncludingTransparentAndTranslucentEdges() {
        ShaderFactory.create().use { factory ->
            for (input in listOf(Color.TRANSPARENT, Color.makeARGB(1, 64, 128, 192),
                Color.makeARGB(128, 64, 128, 192), Color.makeARGB(255, 64, 128, 192), Color.WHITE, Color.BLACK)) {
                val reference = render(null, input)
                for (definition in listOf(EffectCollection.colorAdjustment, EffectCollection.colorMatrix,
                    EffectCollection.orderedDither, EffectCollection.halftone)) {
                    val actual = filtered(factory, definition.effect(), input)
                    assertPixels(reference, actual, 1, definition.id)
                }
            }
        }
    }

    @Test
    fun nonNeutralFiltersPreserveAntialiasedTranslucentEdgeCoverage() {
        Surface.makeRasterN32Premul(64,64).use { source ->
            source.canvas.clear(Color.TRANSPARENT)
            Paint().use { paint ->
                paint.isAntiAlias=true;paint.color=Color.makeARGB(128,64,128,192)
                source.canvas.drawCircle(32.25f,32.25f,19.7f,paint)
            }
            val reference=pixels(source)
            assertTrue(reference.any { Color.getA(it) in 1..127 },"Fixture contains fractional edge coverage")
            source.makeImageSnapshot().use { image -> ShaderFactory.create().use { factory ->
                val matrix=ColorMatrix.identity().coefficients.apply { this[4]=.1f }
                val effects=listOf(edit(EffectCollection.colorAdjustment.effect(),"brightness" to .1f),
                    edit(EffectCollection.orderedDither.effect(),"amount" to 1f),
                    edit(EffectCollection.halftone.effect(),"amount" to 1f),ColorMatrix(matrix).effect())
                for(effect in effects) {
                    factory.createRenderEffect(effect,64f,64f).getOrThrow().asSkiaImageFilter().use { filter ->
                        Surface.makeRasterN32Premul(64,64).use { target ->
                            target.canvas.clear(Color.TRANSPARENT)
                            Paint().use { paint ->paint.imageFilter=filter;target.canvas.drawImage(image,0f,0f,paint) }
                            val actual=pixels(target)
                            for(i in reference.indices) near(Color.getA(reference[i]),Color.getA(actual[i]),1)
                        }
                    }
                }
            } }
        }
    }

    @Test
    fun adjustmentMathMatchesIndependentReferenceAndPreservesInputAlpha() {
        ShaderFactory.create().use { factory ->
            val input = Color.makeARGB(128, 64, 128, 192)
            val effect = edit(EffectCollection.colorAdjustment.effect(), "brightness" to .12f,
                "contrast" to 1.4f, "saturation" to .35f)
            val original = render(null, input)[32 * 64 + 32]
            val rgb = listOf(Color.getR(original), Color.getG(original), Color.getB(original)).map {
                ((it / 255.0 - .5) * 1.4 + .5 + .12)
            }
            val luminance = rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722
            val expected = rgb.map { ((luminance + (it - luminance) * .35).coerceIn(0.0, 1.0) * 255).roundToInt() }
            val pixel = filtered(factory, effect, input)[32 * 64 + 32]
            near(128, Color.getA(pixel), 1)
            near(expected[0], Color.getR(pixel), 3); near(expected[1], Color.getG(pixel), 3); near(expected[2], Color.getB(pixel), 3)
            for ((brightness, expectedChannel) in listOf(-1f to 0, 1f to 255)) {
                val endpoint = edit(EffectCollection.colorAdjustment.effect(), "brightness" to brightness)
                val color = filtered(factory, endpoint, input)[32 * 64 + 32]
                near(expectedChannel, Color.getR(color), 2); near(expectedChannel, Color.getG(color), 2); near(expectedChannel, Color.getB(color), 2)
                near(128, Color.getA(color), 1)
            }
            val gray = filtered(factory, edit(EffectCollection.colorAdjustment.effect(), "saturation" to 0f), Color.RED)[32 * 64 + 32]
            near(54, Color.getR(gray), 2); near(54, Color.getG(gray), 2); near(54, Color.getB(gray), 2)
            assertEquals(0, Color.getA(filtered(factory, effect, Color.TRANSPARENT)[0]))
        }
    }

    @Test
    fun matrixRowsAndNormalizedBiasMatchIndependentDoubleReferenceIncludingAlpha() {
        // A deliberately nonsymmetric transform catches transposition and normalized-vs-255 bias.
        val matrix = floatArrayOf(.4f,.2f,.1f,.15f,.03f, .1f,.6f,.2f,0f,.04f,
            .3f,.1f,.5f,0f,-.02f, 0f,0f,0f,.5f,.1f)
        ShaderFactory.create().use { factory ->
            for (input in listOf(Color.makeARGB(255,51,102,153), Color.makeARGB(128,64,128,192), Color.TRANSPARENT)) {
                val original = render(null, input)[32 * 64 + 32]
                val alpha = Color.getA(original) / 255.0
                val vector = doubleArrayOf(if(alpha == 0.0) 0.0 else Color.getR(original)/255.0,
                    if(alpha == 0.0) 0.0 else Color.getG(original)/255.0,
                    if(alpha == 0.0) 0.0 else Color.getB(original)/255.0, alpha, 1.0)
                val expected = DoubleArray(4) { row -> (0..4).sumOf { col -> matrix[row * 5 + col].toDouble() * vector[col] }.coerceIn(0.0, 1.0) }
                val pixel = filtered(factory, ColorMatrix(matrix).effect(), input)[32 * 64 + 32]
                near((expected[3]*255).roundToInt(), Color.getA(pixel), 2)
                for ((index, actual) in listOf(Color.getR(pixel),Color.getG(pixel),Color.getB(pixel)).withIndex()) {
                    // Compare premultiplied components: near-zero alpha magnifies unpremultiplied quantization.
                    assertTrue(abs(expected[index]*expected[3]*255 - actual*Color.getA(pixel)/255.0) <= 2.0,
                        "Matrix row $index expected ${expected.toList()}, actual $pixel")
                }
            }
        }
    }

    @Test
    fun orderedDitherMatchesIndependentBayerTableAndRoundsLevelsExplicitly() {
        ShaderFactory.create().use { factory ->
            val input = Color.makeARGB(128,128,128,128)
            val effect = edit(EffectCollection.orderedDither.effect(), "amount" to 1f,"levels" to 2f,"cellSize" to 1f)
            val pixels = filtered(factory, effect, input)
            val ranks = arrayOf(intArrayOf(0,8,2,10),intArrayOf(12,4,14,6),intArrayOf(3,11,1,9),intArrayOf(15,7,13,5))
            for(y in 16..31) for(x in 16..31) {
                val expected = if(ranks[y%4][x%4] >= 8) 255 else 0
                val pixel=pixels[y*64+x]
                near(128,Color.getA(pixel),1);near(expected,Color.getR(pixel),2)
                near(expected,Color.getG(pixel),2);near(expected,Color.getB(pixel),2)
            }
            val below=edit(effect,"levels" to 2.49f)
            val above=edit(effect,"levels" to 2.5f)
            assertPixels(pixels,filtered(factory,below,input),0,"2.49 rounds to2")
            assertPixels(filtered(factory,edit(effect,"levels" to 3f),input),filtered(factory,above,input),0,"2.5 rounds to3")
            assertTrue(filtered(factory,above,input).any { Color.getA(it)>0 && Color.getR(it) in 120..135 },"Three-level output has an intermediate tone")
            val coarse=filtered(factory,edit(effect,"cellSize" to 2f),input)
            for(y in 16..31 step 2) for(x in 16..31 step 2) {
                assertEquals(coarse[y*64+x],coarse[y*64+x+1]);assertEquals(coarse[y*64+x],coarse[(y+1)*64+x])
            }
        }
    }

    @Test
    fun halftoneHasBlackInkWhitePaperAndCorrectEndpointsWithoutExpandingAlpha() {
        ShaderFactory.create().use { factory ->
            val effect=edit(EffectCollection.halftone.effect(),"amount" to 1f,"cellSize" to 8f)
            for((input,expected) in listOf(Color.BLACK to 0, Color.WHITE to 255)) {
                val actual=filtered(factory,effect,input)
                for(y in 16..47) for(x in 16..47) near(expected,Color.getR(actual[y*64+x]),2)
            }
            val actual=filtered(factory,effect,Color.makeARGB(128,128,128,128))
            val interior=(16..47).flatMap { y -> (16..47).map { x -> actual[y*64+x] } }
            assertTrue(interior.any { Color.getR(it)<3 },"Contains ink")
            assertTrue(interior.any { Color.getR(it)>252 },"Contains paper")
            assertTrue(interior.all { Color.getA(it) in 127..129 },"Alpha preserved at every ink/paper pixel")
            assertEquals(0,Color.getA(actual[0]),"Transparent exterior remains transparent")
            assertTrue(Color.getR(actual[20*64+20]) < 3,"Cell center contains a black dot")
            assertTrue(Color.getR(actual[16*64+16]) > 252,"Cell corner remains paper for midgray")
        }
    }

    @Test
    fun staticAndPipelineCollectionOrderMatchesSequentialRasterIncludingEdges() {
        ShaderFactory.create().use { factory ->
            val adjustment=edit(EffectCollection.colorAdjustment.effect(),"brightness" to .15f,"saturation" to .5f)
            val art=edit(EffectCollection.orderedDither.effect(),"amount" to 1f,"levels" to 3f)
            val composite=CompositeEffect.of(adjustment,art)
            val input=Color.makeARGB(128,51,102,153)
            val first=factory.createRenderEffect(adjustment,64f,64f).getOrThrow().asSkiaImageFilter()
            val second=factory.createRenderEffect(art,64f,64f).getOrThrow().asSkiaImageFilter()
            first.use { a -> second.use { b ->
                val sequential=Surface.makeRasterN32Premul(64,64).use { surface ->
                    draw(surface,a,input)
                    surface.makeImageSnapshot().use { image -> Surface.makeRasterN32Premul(64,64).use { target ->
                        target.canvas.clear(Color.TRANSPARENT)
                        Paint().use { paint ->paint.imageFilter=b;target.canvas.drawImage(image,0f,0f,paint) }
                        pixels(target)
                    } }
                }
                assertPixels(sequential,filtered(factory,composite,input),2,"Static composed effect")
                factory.createPipelineSession(composite,64f,64f).getOrThrow().use { session ->
                    session.currentRenderEffect.asSkiaImageFilter().use { filter ->
                        assertPixels(sequential,render(filter,input),2,"Owned pipeline snapshot")
                    }
                }
            } }
        }
    }

    @Test
    fun halftoneFractionalCellsHaveSolidBlackAndWhiteEndpoints() {
        ShaderFactory.create().use { factory ->
            for (size in listOf(2.5f, 3.5f, 8f, 63.5f)) {
                val effect = edit(EffectCollection.halftone.effect(), "amount" to 1f, "cellSize" to size)
                for (component in listOf(0, 255)) {
                    val result = filtered(factory, effect, Color.makeARGB(255, component, component, component))
                    // Interior avoids center samples outside content; includes exact cell vertices
                    // at pixel centers for fractional 2.5px / 3.5px periods.
                    for (y in 24..39) for (x in 24..39) {
                        near(component, Color.getR(result[y * 64 + x]), 1)
                    }
                }
            }
        }
    }

    private fun edit(effect:DefinedShaderEffect,vararg values:Pair<String,Float>):DefinedShaderEffect =
        values.fold(effect) { current,(id,value) -> current.withTypedParameter(id,ParameterValue.FloatValue(value)) }

    private fun filtered(factory:ShaderFactory,effect:ShaderEffect,input:Int):IntArray =
        factory.createRenderEffect(effect,64f,64f).getOrThrow().asSkiaImageFilter().use { render(it,input) }

    private fun render(filter:ImageFilter?,input:Int):IntArray = Surface.makeRasterN32Premul(64,64).use { surface ->
        draw(surface,filter,input);pixels(surface)
    }
    private fun draw(surface:Surface,filter:ImageFilter?,input:Int) {
        surface.canvas.clear(Color.TRANSPARENT)
        Paint().use { paint ->
            paint.imageFilter=filter;paint.color=input
            // Transparent margin plus a hard translucent edge test content alpha, not only an opaque fill.
            surface.canvas.drawRect(Rect.makeLTRB(16f,16f,48f,48f),paint)
        }
    }
    private fun pixels(surface:Surface):IntArray=surface.makeImageSnapshot().use { image ->
        Bitmap.makeFromImage(image).use { bitmap ->IntArray(4096) { bitmap.getColor(it%64,it/64) } }
    }
    private fun near(expected:Int,actual:Int,tolerance:Int)=assertTrue(abs(expected-actual)<=tolerance,"Expected $expected ±$tolerance, got $actual")
    private fun assertPixels(expected:IntArray,actual:IntArray,tolerance:Int,context:String) {
        for(i in expected.indices) {
            val e=expected[i];val a=actual[i]
            near(Color.getA(e),Color.getA(a),tolerance)
            for((ec,ac) in listOf(Color.getR(e) to Color.getR(a),Color.getG(e) to Color.getG(a),Color.getB(e) to Color.getB(a))) {
                assertTrue(abs(ec*Color.getA(e)-ac*Color.getA(a))/255.0<=tolerance,"$context at ${i%64},${i/64}: $e vs $a")
            }
        }
    }
}
