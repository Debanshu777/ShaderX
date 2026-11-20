package com.debanshu.shaderlab.shaderx

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.util.Log
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.uniform.ColorUniform
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import org.junit.Test
import kotlin.test.*

/** Actual source-free ShaderX snapshot drawing on HWUI. This does not test Compose lifecycle. */
class BackgroundDeviceTest {
    @Test
    fun sourceFreeSnapshotsGenerateAlphaBehindUnchangedForegroundAndRemainIndependent() {
        ShaderFactory.create(1).use { factory ->
            fun background(red:Float) = ShaderDefinition("device_background",program=ShaderProgram("""
                layout(color) uniform half4 tint;
                half4 main(float2 p) { return half4(tint.rgb*tint.a,tint.a); }
            """.trimIndent(),listOf(ShaderBinding.uniform(ColorUniform("tint",red,.2f,.1f,.5f))))).proceduralBackground()
            val a=background(.8f);val b=background(.2f)
            val first=createBackgroundSnapshot(factory,a,a.definition.schema.defaults(),32f,32f,0f,null).getOrNull()!!
            val second=createBackgroundSnapshot(factory,b,b.definition.schema.defaults(),32f,32f,0f,null).getOrNull()!!
            try {
                PixelTarget(32,32).use { target ->
                    assertEquals(0,Color.alpha(target.pixel(null)),"Empty native drawing starts transparent")
                    val old=target.pixel(first)
                    assertTrue(Color.red(old) in 198..210,"Structural color renders on AGSL: $old")
                    assertTrue(Color.alpha(old) in 125..131)
                    assertTrue(Color.red(target.pixel(second)) in 46..56)
                    assertEquals(Color.GREEN,target.pixel(first,true),"Foreground must stay unfiltered")
                    factory.clearCache()
                    repeat(8) { assertEquals(old,target.pixel(first));assertTrue(Color.red(target.pixel(second)) in 46..56) }
                }
            } finally { first.close();second.close() }
        }
    }

    @Test
    fun explicitPlaybackLiveValuesAndResizeProduceIndependentHardwareSnapshots() {
        ShaderFactory.create(1).use { factory ->
            val bg = ShaderDefinition("device_background_clock",program=ShaderProgram("""
                uniform float time;
                uniform float live;
                uniform float2 resolution;
                half4 main(float2 p) { return half4(time,live,p.x/resolution.x,1); }
            """.trimIndent(),listOf(ShaderBinding.time(),ShaderBinding.frameFloat("live"),
                ShaderBinding.resolution()))).proceduralBackground()
            var live = .1f
            var fail = false
            var calls = 0
            val provider = ShaderFrameProvider {
                calls++
                check(!fail) { "Device live input unavailable" }
                listOf(FloatUniform("live",live))
            }
            fun snapshot(time:Float,size:Float=32f) = createBackgroundSnapshot(factory,bg,
                bg.definition.schema.defaults(),size,size,time,provider)
            val first = snapshot(.25f).getOrNull()!!
            val independent = snapshot(.75f).getOrNull()!!
            try {
                PixelTarget(32,32).use { target ->
                    val initial = target.pixel(first)
                    assertTrue(Color.red(initial) in 61..67,"Explicit quarter-second phase")
                    assertTrue(Color.green(initial) in 23..29,"Initial live value")
                    assertTrue(Color.blue(initial) in 129..135,"32px resolution at pixel center")
                    val other = target.pixel(independent)
                    assertTrue(Color.red(other) in 188..194,"Independent three-quarter phase")
                    val settledCalls = calls
                    repeat(4) { assertEquals(initial,target.pixel(first));assertEquals(other,target.pixel(independent)) }
                    assertEquals(settledCalls,calls,"Drawing immutable snapshots never polls providers")
                    live = .6f
                    val updated = snapshot(.25f).getOrNull()!!
                    try {
                        val current = target.pixel(updated)
                        assertTrue(Color.red(current) in 61..67,"Live update preserves explicit paused phase")
                        assertTrue(Color.green(current) in 150..156)
                        assertEquals(initial,target.pixel(first),"Later live input cannot mutate an earlier native value")
                        assertEquals(other,target.pixel(independent))
                        val resized = snapshot(.25f,64f).getOrNull()!!
                        try {
                            assertTrue(Color.blue(target.pixel(resized)) in 63..69,
                                "New 64px resolution changes coordinate normalization on the original target")
                            PixelTarget(64,64).use { larger ->
                                val large = larger.pixel(resized)
                                assertTrue(Color.red(large) in 61..67)
                                assertTrue(Color.green(large) in 150..156)
                                assertTrue(Color.blue(large) in 126..132,"Resized shader normalizes against its 64px target")
                            }
                            fail = true
                            assertTrue(snapshot(.75f,64f) is ShaderResult.Failure,
                                "A missing live snapshot fails as a whole")
                            assertEquals(current,target.pixel(updated),"Failed replacement does not mutate existing snapshot")
                            assertEquals(initial,target.pixel(first))
                        } finally { resized.close() }
                    } finally { updated.close() }
                }
            } finally { first.close();independent.close() }
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

        fun pixel(snapshot: BackgroundSnapshot?, foreground: Boolean = false): Int {
            val native = node.beginRecording()
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(native), Size(width.toFloat(), height.toFloat())) {
                snapshot?.let { drawRect(it.brush) }
                if (foreground) drawRect(androidx.compose.ui.graphics.Color.Green,
                    topLeft = androidx.compose.ui.geometry.Offset(width / 4f, height / 4f),
                    size = Size(width / 2f, height / 2f))
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
                        try { readable.getPixel(width / 2, height / 2) } finally { readable.recycle() }
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
