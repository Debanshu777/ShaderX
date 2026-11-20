package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.uniform.*
import org.jetbrains.skia.*
import org.jetbrains.skia.impl.use
import kotlin.math.*
import kotlin.test.*

/** Numerical uniform sampler correctness on native raster; never calls image.makeShader. */
class Rgba8SamplerRenderTest {
    @Test
    fun all64RgbaTexelsSurviveIncludingNonzeroRgbAtAlphaZeroAcrossDestinations() {
        val bytes = ByteArray(256) { i -> ((i*37+17)%256).toByte() }.also { it[3]=0 }
        val expected = bytes.copyOf()
        val input = ShaderNode.rgba8Sampler(Rgba8Data(8,8,bytes))
        bytes.fill(0)
        ShaderFactory.create(1).use { factory ->
            for (space in listOf(ColorSpace.sRGB,ColorSpace.sRGBLinear)) {
                for (alpha in listOf(false,true)) {
                    val source = """uniform shader content; uniform shader data;
                        half4 main(float2 p) { half4 d=data.eval(p); return half4(${if(alpha) "d.aaa" else "d.rgb"},1)*content.eval(p).a; }"""
                    val filter=factory.createRenderEffect(Stage(source,listOf(NestedUniform("data",input))),8f,8f).getOrThrow().asSkiaImageFilter()
                    factory.clearCache()
                    filter.use { retained ->
                        val actual=render(retained,space)
                        for (i in 0 until 64) for(c in 0..2) {
                            val expectedChannel=expected[i*4+if(alpha) 3 else c].toInt() and 255
                            near(expectedChannel,actual[i*4+c].toInt() and 255,"texel$i channel$c alphaRead$alpha")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun nearestLinearPixelCentersAndEveryAxisTileModeMatchIndependentCpuReference() {
        val red = intArrayOf(0,64,128,255)
        val bytes = ByteArray(16) { i -> if(i%4==0) red[i/4].toByte() else 0 }
        val data = Rgba8Data(2,2,bytes)
        val points = listOf(-1.5f to -.5f, -.5f to .5f, .5f to 1.5f, 1f to 1f, 1.5f to .5f, 2.5f to 3.5f)
        ShaderFactory.create().use { factory ->
            for(sampling in DataSampling.entries) for(tx in ShaderTileMode.entries) for(ty in ShaderTileMode.entries) {
                val input=ShaderNode.rgba8Sampler(data,sampling,tx,ty)
                for((x,y) in points) {
                    val source="""uniform shader content; uniform shader data; uniform float2 coord;
                        half4 main(float2 p) { half4 d=data.eval(coord);return half4(d.rrr,1)*content.eval(p).a; }"""
                    val effect=Stage(source,listOf(NestedUniform("data",input),FloatUniform("coord",x,y)))
                    factory.createRenderEffect(effect,8f,8f).getOrThrow().asSkiaImageFilter().use { filter ->
                        val actual=render(filter,ColorSpace.sRGB)[0].toInt() and 255
                        near(reference(red,x,y,sampling,tx,ty).roundToInt(),actual,"$sampling $tx $ty ($x,$y)")
                    }
                }
            }
        }
    }

    @Test
    fun boundRejectsBeforeCopyOrNativeWorkAndOneTexelMirrorsAndInterpolatesAsConstant() {
        assertFailsWith<IllegalArgumentException> { ShaderNode.rgba8Sampler(Rgba8Data(65,1,ByteArray(260))) }
        ShaderFactory.create().use { factory ->
            for(sampling in DataSampling.entries) for(tile in ShaderTileMode.entries) {
                val input=ShaderNode.rgba8Sampler(Rgba8Data(1,1,byteArrayOf(64,0,0,0)),sampling,tile,tile)
                val source="""uniform shader content;uniform shader data;
                    half4 main(float2 p) { return half4(data.eval(float2(-5.7,8.2)).rrr,1)*content.eval(p).a; }"""
                factory.createRenderEffect(Stage(source,listOf(NestedUniform("data",input))),8f,8f).getOrThrow().asSkiaImageFilter().use {
                    near(64,render(it,ColorSpace.sRGB)[0].toInt() and 255,"$sampling $tile")
                }
            }
        }
    }

    private fun reference(red:IntArray,x:Float,y:Float,sampling:DataSampling,tx:ShaderTileMode,ty:ShaderTileMode):Float {
        fun wrap(i:Int,mode:ShaderTileMode):Int=when(mode) {
            ShaderTileMode.CLAMP -> i.coerceIn(0,1)
            ShaderTileMode.REPEAT -> ((i%2)+2)%2
            ShaderTileMode.MIRROR -> (((i%4)+4)%4).let { if(it<2) it else 3-it }
        }
        fun texel(a:Int,b:Int)=red[wrap(b,ty)*2+wrap(a,tx)].toFloat()
        if(sampling==DataSampling.NEAREST) return texel(floor(x).toInt(),floor(y).toInt())
        val a=floor(x-.5f).toInt();val b=floor(y-.5f).toInt();val u=x-.5f-a;val v=y-.5f-b
        return (texel(a,b)*(1-u)+texel(a+1,b)*u)*(1-v)+(texel(a,b+1)*(1-u)+texel(a+1,b+1)*u)*v
    }

    private class Stage(override val shaderSource:String,val uniforms:List<Uniform>):RuntimeShaderEffect {
        override val id="rgba8-sampler-test"
        override val displayName=id
        override val parameters:List<ParameterSpec> = emptyList()
        override fun buildUniforms(width:Float,height:Float)=uniforms
        override fun withTypedParameter(parameterId:String,value:ParameterValue)=this
    }

    private fun render(filter:ImageFilter,space:ColorSpace):ByteArray = Surface.makeRaster(ImageInfo(8,8,ColorType.RGBA_8888,ColorAlphaType.PREMUL,space)).use { surface ->
        surface.canvas.clear(Color.TRANSPARENT)
        Paint().use { paint -> paint.color=Color.WHITE;paint.imageFilter=filter;surface.canvas.drawRect(Rect.makeWH(8f,8f),paint) }
        surface.makeImageSnapshot().use { image -> Bitmap.makeFromImage(image).use { bitmap ->
            bitmap.readPixels(ImageInfo(8,8,ColorType.RGBA_8888,ColorAlphaType.PREMUL,space))!!
        } }
    }
    private fun near(expected:Int,actual:Int,message:String) = assertTrue(abs(expected-actual)<=2,"$expected vs $actual $message")
}
