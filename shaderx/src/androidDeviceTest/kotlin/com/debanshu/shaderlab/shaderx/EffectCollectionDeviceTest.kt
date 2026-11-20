package com.debanshu.shaderlab.shaderx

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.authoring.DefinedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.EffectCollection
import com.debanshu.shaderlab.shaderx.effect.impl.ColorMatrix
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.factory.createPipelineSession
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import org.junit.Test
import kotlin.math.abs
import kotlin.test.*

/** AGSL through actual ShaderX factory filters and HWUI readback, not offline source compilation. */
class EffectCollectionDeviceTest {
    @Test
    fun nativeCompilerAndNeutralFiltersPreserveOpaqueTranslucentAndTransparentInput() {
        ShaderFactory.create().use { factory -> PixelTarget(16,16).use { target ->
            for(input in listOf(Color.TRANSPARENT, Color.argb(128,64,128,192), Color.rgb(64,128,192), Color.WHITE)) {
                val reference=target.pixels(null,input)
                for(definition in listOf(EffectCollection.colorAdjustment,EffectCollection.colorMatrix,
                    EffectCollection.orderedDither,EffectCollection.halftone)) {
                    val filter=factory.createRenderEffect(definition.effect(),16f,16f).getOrThrow()
                    assertPixels(reference,target.pixels(filter,input),2,definition.id)
                }
            }
        } }
    }

    @Test
    fun colorMathAndNormalizedMatrixBiasPreserveAlphaOnHardware() {
        ShaderFactory.create().use { factory -> PixelTarget(16,16).use { target ->
            val input=Color.argb(128,64,128,192)
            val effect=edit(EffectCollection.colorAdjustment.effect(),"saturation" to 0f)
            val gray=target.pixels(factory.createRenderEffect(effect,16f,16f).getOrThrow(),input)[136]
            // .2126*64 + .7152*128 + .0722*192 =119.0144 in encoded sRGB components.
            near(119,Color.red(gray),3);near(119,Color.green(gray),3);near(119,Color.blue(gray),3)
            near(128,Color.alpha(gray),2)
            // Independent row-major reference: R'=B+.1, G'=.5G, B'=R, A'=.5A.
            val coefficients=floatArrayOf(0f,0f,1f,0f,.1f, 0f,.5f,0f,0f,0f,
                1f,0f,0f,0f,0f, 0f,0f,0f,.5f,0f)
            val matrix=ColorMatrix(coefficients).effect()
            val transformed=target.pixels(factory.createRenderEffect(matrix,16f,16f).getOrThrow(),input)[136]
            near(218,Color.red(transformed),5);near(64,Color.green(transformed),5)
            near(64,Color.blue(transformed),5);near(64,Color.alpha(transformed),2)
            val transparent=target.pixels(factory.createRenderEffect(matrix,16f,16f).getOrThrow(),Color.TRANSPARENT)
            assertTrue(transparent.all { Color.alpha(it)==0 },"Zero-alpha input stays transparent when alpha row has no bias")
            val whole=CompositeEffect.of(effect,matrix)
            factory.createPipelineSession(whole,16f,16f).getOrThrow().use { session ->
                val pixel=target.pixels(session.currentRenderEffect,input)[136]
                near(145,Color.red(pixel),5);near(60,Color.green(pixel),5)
                near(119,Color.blue(pixel),5);near(64,Color.alpha(pixel),2)
            }
        } }
    }

    @Test
    fun artisticPixelsHaveIntendedPatternAndFractionalLevelRounding() {
        ShaderFactory.create().use { factory -> PixelTarget(16,16).use { target ->
            val input=Color.argb(128,128,128,128)
            val dither=edit(EffectCollection.orderedDither.effect(),"amount" to 1f,"levels" to 2f)
            fun ditherPixels(effect:DefinedShaderEffect)=target.pixels(factory.createRenderEffect(effect,16f,16f).getOrThrow(),input)
            val actual=ditherPixels(dither)
            val ranks=arrayOf(intArrayOf(0,8,2,10),intArrayOf(12,4,14,6),intArrayOf(3,11,1,9),intArrayOf(15,7,13,5))
            for(y in 0..15) for(x in 0..15) {
                val p=actual[y*16+x]
                near(if(ranks[y%4][x%4]>=8)255 else 0,Color.red(p),2)
                near(128,Color.alpha(p),2)
            }
            assertPixels(actual,ditherPixels(edit(dither,"levels" to 2.49f)),0,"Explicit lower rounding")
            assertPixels(ditherPixels(edit(dither,"levels" to 3f)),ditherPixels(edit(dither,"levels" to 2.5f)),0,"Explicit half tie upward")
            val halftone=edit(EffectCollection.halftone.effect(),"amount" to 1f,"cellSize" to 8f)
            val screen=target.pixels(factory.createRenderEffect(halftone,16f,16f).getOrThrow(),input)
            assertTrue(screen.any { Color.red(it)<3 },"Halftone ink")
            assertTrue(screen.any { Color.red(it)>252 },"Halftone paper")
            assertTrue(screen.all { Color.alpha(it) in 126..130 },"Screen preserves input alpha")
            near(0,Color.red(screen[4*16+4]),2);near(255,Color.red(screen[0]),2)
        } }
    }

    private fun edit(effect:DefinedShaderEffect,vararg values:Pair<String,Float>):DefinedShaderEffect =
        values.fold(effect) { current,(id,value) -> current.withTypedParameter(id,ParameterValue.FloatValue(value)) }
    private fun near(expected:Int,actual:Int,tolerance:Int)=assertTrue(abs(expected-actual)<=tolerance,"Expected $expected ±$tolerance, got $actual")
    private fun assertPixels(expected:IntArray,actual:IntArray,tolerance:Int,context:String) {
        for(i in expected.indices) {
            val e=expected[i];val a=actual[i]
            near(Color.alpha(e),Color.alpha(a),tolerance)
            for((ec,ac) in listOf(Color.red(e) to Color.red(a),Color.green(e) to Color.green(a),Color.blue(e) to Color.blue(a))) {
                assertTrue(abs(ec*Color.alpha(e)-ac*Color.alpha(a))/255.0<=tolerance,"$context pixel $i: $e vs $a")
            }
        }
    }
    /** Based on Android's HardwareRenderer/ImageReader migration recipe; owns every native wrapper. */
    private class PixelTarget(private val width: Int, private val height: Int) : AutoCloseable {
        private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
        private val node = RenderNode("ShaderX pixels").apply { setPosition(0, 0, this@PixelTarget.width, this@PixelTarget.height) }
        private val root = RenderNode("ShaderX root").apply { setPosition(0, 0, this@PixelTarget.width, this@PixelTarget.height) }
        private val renderer = HardwareRenderer().apply {
            setName("ShaderX validation")
            setOpaque(false)
            setSurface(reader.surface)
            setContentRoot(root)
        }

        init {
            Log.i("ShaderXValidation", "HWUI ${Build.MANUFACTURER}/${Build.MODEL}, API ${Build.VERSION.SDK_INT}, ${Build.FINGERPRINT}; viewport=${width}x$height; RGBA8888 sRGB; drawingEnabled=${HardwareRenderer.isDrawingEnabled()}; debug instrumentation")
        }

        fun pixels(effect: RenderEffect?, input: Int = Color.WHITE, patterned: Boolean = false): IntArray {
            node.setRenderEffect(effect?.asAndroidRenderEffect())
            node.beginRecording().apply {
                drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = input })
                if (patterned) {
                    drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = Color.RED })
                    drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
                }
            }
            node.endRecording()
            root.beginRecording().drawRenderNode(node)
            root.endRecording()
            var status = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            // HWUI may decline a second request in the same vsync. Wait for a new frame slot;
            // pixel assertions are never retried and surface/context failures still fail below.
            var droppedFrames = 0
            while (status and HardwareRenderer.SYNC_FRAME_DROPPED != 0 && droppedFrames++ < 3) {
                Thread.sleep(20)
                status = renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            }
            if (droppedFrames > 0) Log.i("ShaderXValidation", "HWUI frame scheduling resubmissions=$droppedFrames, status=$status")
            assertEquals(0, status and (HardwareRenderer.SYNC_CONTEXT_IS_STOPPED or HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND or HardwareRenderer.SYNC_FRAME_DROPPED), "HardwareRenderer failed: $status")
            val image = reader.acquireNextImage() ?: error("HardwareRenderer produced no image")
            return image.use {
                image.fence.use { fence ->
                    if (fence.isValid) check(fence.await(java.time.Duration.ofSeconds(5))) { "GPU fence timeout" }
                }
                val buffer = image.hardwareBuffer ?: error("Missing hardware buffer")
                buffer.use {
                    val hardware = Bitmap.wrapHardwareBuffer(buffer, null) ?: error("Cannot wrap hardware buffer")
                    try {
                        val readable = hardware.copy(Bitmap.Config.ARGB_8888, false) ?: error("Cannot read hardware pixels")
                        try { IntArray(width * height).also { readable.getPixels(it,0,width,0,0,width,height) } } finally { readable.recycle() }
                    } finally { hardware.recycle() }
                }
            }
        }

        override fun close() {
            renderer.destroy()
            node.discardDisplayList()
            root.discardDisplayList()
            reader.close()
        }
    }
}
