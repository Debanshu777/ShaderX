package com.debanshu.shaderlab.shaderx

import android.graphics.*
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.geometry.Size
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.time.Duration
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.uniform.*
import com.debanshu.shaderlab.shaderx.parameter.*
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.result.ShaderError
import org.junit.Test
import kotlin.math.abs
import kotlin.test.*

/** Actual HWUI AGSL input tests. Software emulator is distinct from physical GPU evidence. */
class ShaderInputsDeviceTest {
    private fun data() = Rgba8Data(2, 1, byteArrayOf(32, 64, 128.toByte(), 0, 224.toByte(), 192.toByte(), 96, 128.toByte()))
    private fun effect(input: Uniform, x: Float = .5f, channels: String = "rgb", y: Float = .5f) = ShaderDefinition("bufferData", program = ShaderProgram("""
        uniform shader content;
        uniform shader bufferData;
        half4 main(float2 p) { half4 v = bufferData.eval(float2($x, $y)); return half4(v.$channels, 1); }
    """.trimIndent(), listOf(ShaderBinding.uniform(input)))).effect()
    private fun near(expected: Int, actual: Int) = assertTrue(abs(expected-actual) <= 2, "Expected $expected, got $actual")
    private fun pixel(factory: ShaderFactory, target: PixelTarget, uniform: Uniform, x: Float = .5f, channels: String = "rgb", y: Float = .5f) =
        target.pixel(factory.createRenderEffect(effect(uniform, x, channels, y), 16f,16f).getOrThrow())

    @Test fun rawChannelsIncludingAlphaZeroSurviveSnapshotsAndCallerMutation() {
        val caller = byteArrayOf(32,64,128.toByte(),0,224.toByte(),192.toByte(),96,128.toByte())
        val data = Rgba8Data(2,1,caller)
        caller.fill(0)
        data.bytes().fill(0)
        ShaderFactory.create().use { factory -> PixelTarget(16,16).use { target ->
            val p = pixel(factory,target,DataUniform("bufferData",data))
            near(32,Color.red(p));near(64,Color.green(p));near(128,Color.blue(p));near(255,Color.alpha(p))
            val alpha = pixel(factory,target,DataUniform("bufferData",data),1.5f,"arg")
            near(128,Color.red(alpha));near(224,Color.green(alpha));near(192,Color.blue(alpha))
        } }
    }

    @Test fun nearestLinearCentersAndTileModesAreExplicit() {
        ShaderFactory.create().use { factory -> PixelTarget(16,16).use { target ->
            val data=data()
            near(32,Color.red(pixel(factory,target,DataUniform("bufferData",data),.5f)))
            near(224,Color.red(pixel(factory,target,DataUniform("bufferData",data),1.5f)))
            near(128,Color.red(pixel(factory,target,DataUniform("bufferData",data,DataSampling.LINEAR),1f)))
            for((mode,positive,negative) in listOf(Triple(ShaderTileMode.CLAMP,224,32), Triple(ShaderTileMode.REPEAT,32,224),Triple(ShaderTileMode.MIRROR,224,32))) {
                val uniform=DataUniform("bufferData",data,tileModeX=mode)
                near(positive,Color.red(pixel(factory,target,uniform,2.5f)))
                near(negative,Color.red(pixel(factory,target,uniform,-.5f)))
                val vertical=DataUniform("bufferData",Rgba8Data(1,2,data.bytes()),tileModeY=mode)
                near(positive,Color.red(pixel(factory,target,vertical,y=2.5f)))
                near(negative,Color.red(pixel(factory,target,vertical,y=-.5f)))
            }
        } }
    }

    @Test fun childInstancesKeepIndependentExplicitTimeValuesAndRetainedSnapshots() {
        val level=FloatParameterDefinition("child","level",.2f,0f..1f)
        val child=ShaderDefinition("child", program=ShaderProgram("""
            uniform float time;
            uniform float level;
            half4 main(float2 p) { return half4(time,level,0,1); }
        """.trimIndent(),listOf(ShaderBinding.time(),ShaderBinding.float("level",level.key))),schema=ParameterSchema("child",listOf(level)))
        val first=ShaderNode.snapshot(child.proceduralBackground(),values=child.schema.defaults().withValue(level.key,.4f),timeSeconds=.25f)
        val second=ShaderNode.snapshot(child.proceduralBackground(),values=child.schema.defaults().withValue(level.key,.8f),timeSeconds=.75f)
        val parent=ShaderDefinition("parent",program=ShaderProgram("""
            uniform shader content;
            uniform shader first;
            uniform shader second;
            half4 main(float2 p) { return half4(first.eval(p).r,second.eval(p).r,first.eval(p).g + second.eval(p).g * .5,1); }
        """.trimIndent(),listOf(ShaderBinding.uniform(NestedUniform("first",first)),ShaderBinding.uniform(NestedUniform("second",second)))))
        val factory=ShaderFactory.create(1)
        PixelTarget(16,16).use { target ->
            val retained=factory.createRenderEffect(parent.effect(),16f,16f).getOrThrow()
            repeat(6) { i ->
                pixel(factory,target,DataUniform("bufferData",data()),i.toFloat())
            }
            factory.close()
            repeat(3) {
                val p=target.pixel(retained)
                near(64,Color.red(p));near(191,Color.green(p));near(204,Color.blue(p))
            }
        }
    }

    @Test fun liveDataReplacementAndRepeatedCloseKeepRetainedFramesValid() {
        val definition=ShaderDefinition("live-data",program=ShaderProgram("""
            uniform shader content;
            uniform shader bufferData;
            half4 main(float2 p) { return half4(bufferData.eval(float2(.5)).rgb,1); }
        """.trimIndent(),listOf(ShaderBinding.frameData("bufferData"))))
        var current=data()
        val provider=ShaderFrameProvider { listOf(DataUniform("bufferData",current)) }
        val effect=definition.animatedEffect(isAnimating=false,frameProvider=provider)
        ShaderFactory.create(1).use { factory -> PixelTarget(16,16).use { target ->
            repeat(4) {
                val session=factory.createAnimatedSession(effect,16f,16f).getOrThrow()
                near(32,Color.red(target.pixel(session.currentRenderEffect)))
                current=Rgba8Data(1,1,byteArrayOf(200.toByte(),0,0,0))
                val retained=session.refresh(effect,16f,16f).getOrThrow()
                near(200,Color.red(target.pixel(retained)))
                val unchanged=session.refresh(effect,16f,16f).getOrThrow()
                near(200,Color.red(target.pixel(unchanged)))
                session.close();session.close()
                assertFailsWith<IllegalStateException> { session.currentRenderEffect }
                assertTrue(session.refresh(effect,16f,16f) is ShaderResult.Failure)
                factory.clearCache()
                near(200,Color.red(target.pixel(retained)))
                current=data()
            }
        } }
        checkRetainedSessionSource()
    }

    private fun checkRetainedSessionSource() {
        val actualSource="uniform shader content; uniform shader bufferData; half4 main(float2 p) { return half4(bufferData.eval(float2(.5)).rgb,1); }"
        var staticSource=actualSource
        var staticReads=0
        val staticEffect=object:com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect {
            override val id="mutating-static-source"
            override val displayName=id
            override val parameters:List<ParameterSpec> = emptyList()
            override val shaderSource:String get() { staticReads++; return staticSource }
            override fun withTypedParameter(parameterId:String,value:ParameterValue)=this
            override fun buildUniforms(width:Float,height:Float):List<Uniform> {
                staticSource="provider changed the source"
                return listOf(DataUniform("missingAlias",data()))
            }
        }
        ShaderFactory.create().use { factory ->
            val error=assertIs<ShaderError.CompilationError>(assertIs<ShaderResult.Failure<*>>(factory.createRenderEffect(staticEffect,16f,16f)).error)
            assertEquals(actualSource,error.shaderSource)
            assertEquals(1,staticReads,"Static failure diagnostics retain pre-provider source")
        }
        var sourceReads=0
        var mayReadSource=true
        var alias="bufferData"
        val effect=object:com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect {
            override val id="manual-source"
            override val displayName="manual-source"
            override val parameters:List<ParameterSpec> = emptyList()
            override val time=0f
            override val isAnimating=false
            override val shaderSource:String get() { sourceReads++; check(mayReadSource); return actualSource }
            override fun withTime(newTime:Float)=this
            override fun withTypedParameter(parameterId:String,value:ParameterValue)=this
            override fun buildUniforms(width:Float,height:Float):List<Uniform> = listOf(DataUniform("bufferData",data()))
            override fun buildFrameUniforms():List<Uniform> = listOf(DataUniform(alias,data()))
        }
        ShaderFactory.create().use { factory -> PixelTarget(16,16).use { target ->
            factory.createAnimatedSession(effect,16f,16f).getOrThrow().use { session ->
                assertEquals(1,sourceReads)
                mayReadSource=false
                near(32,Color.red(target.pixel(session.refresh(effect,16f,16f).getOrThrow())))
                alias="missingAlias"
                val error=assertIs<ShaderError.CompilationError>(assertIs<ShaderResult.Failure<*>>(session.refresh(effect,16f,16f)).error)
                assertEquals(actualSource,error.shaderSource)
                assertEquals(1,sourceReads,"Refresh and its diagnostics retain actual compiled source")
            }
        } }
    }

    @Test fun portableUniformSamplerRetainsAll64TexelsAndExplicitSampling() {
        val bytes=ByteArray(256)
        repeat(64) { i -> bytes[i*4]=(32+i*3).toByte();bytes[i*4+1]=(64+i*2).toByte();bytes[i*4+2]=(128-i).toByte();bytes[i*4+3]=if(i%2==0)0 else 192.toByte() }
        val sampler=NestedUniform("bufferData",ShaderNode.rgba8Sampler(Rgba8Data(64,1,bytes)))
        ShaderFactory.create().use { factory -> PixelTarget(16,16).use { target ->
            repeat(64) { i ->
                val rgb=pixel(factory,target,sampler,i+.5f)
                near(32+i*3,Color.red(rgb));near(64+i*2,Color.green(rgb));near(128-i,Color.blue(rgb))
                val alpha=pixel(factory,target,sampler,i+.5f,"arg")
                near(if(i%2==0)0 else 192,Color.red(alpha))
            }
            val linear=NestedUniform("bufferData",ShaderNode.rgba8Sampler(data(),DataSampling.LINEAR))
            near(128,Color.red(pixel(factory,target,linear,1f)))
            val repeated=NestedUniform("bufferData",ShaderNode.rgba8Sampler(data(),tileModeX=ShaderTileMode.REPEAT))
            near(32,Color.red(pixel(factory,target,repeated,2.5f)))
            val mirrored=NestedUniform("bufferData",ShaderNode.rgba8Sampler(data(),tileModeX=ShaderTileMode.MIRROR))
            near(224,Color.red(pixel(factory,target,mirrored,2.5f)))
        } }
    }

    @Test fun liveNestedReplacementKeepsOldFrameAfterChildCompilerFailure() {
        fun child(seconds:Float):ShaderNode {
            val definition=ShaderDefinition("nested-live",program=ShaderProgram("""
                uniform float time;
                half4 main(float2 p) { return half4(time,0,0,1); }
            """.trimIndent(),listOf(ShaderBinding.time())))
            return ShaderNode.snapshot(definition.proceduralBackground(),timeSeconds=seconds)
        }
        var node=child(.25f)
        val provider=ShaderFrameProvider { listOf(NestedUniform("childData",node)) }
        val parent=ShaderDefinition("live-parent",program=ShaderProgram("""
            uniform shader content;
            uniform shader childData;
            half4 main(float2 p) { return childData.eval(p); }
        """.trimIndent(),listOf(ShaderBinding.frameNested("childData")))).animatedEffect(isAnimating=false,frameProvider=provider)
        ShaderFactory.create(1).use { factory -> PixelTarget(16,16).use { target ->
            factory.createAnimatedSession(parent,16f,16f).getOrThrow().use { session ->
                val initial=session.currentRenderEffect
                near(64,Color.red(target.pixel(initial)))
                node=child(.75f)
                val replacement=session.refresh(parent,16f,16f).getOrThrow()
                near(191,Color.red(target.pixel(replacement)))
                near(64,Color.red(target.pixel(initial)))
                val broken=ShaderDefinition("broken-child",program=ShaderProgram("half4 main(float2 p) { return half4(1) BROKEN; }")).proceduralBackground()
                node=ShaderNode.snapshot(broken)
                assertTrue(session.refresh(parent,16f,16f) is ShaderResult.Failure)
                near(191,Color.red(target.pixel(replacement)))
                node=child(.5f)
                near(128,Color.red(target.pixel(session.refresh(parent,16f,16f).getOrThrow())))
            }
        } }
    }

    @Test fun nestedCompilerDiagnosticsRetainDeepestSourceAndAliasPath() {
        val badSource="half4 main(float2 p) { return half4(1) BROKEN; }"
        val bad=ShaderNode.snapshot(ShaderDefinition("bad-child",program=ShaderProgram(badSource)).proceduralBackground())
        val midSource="uniform shader deep; half4 main(float2 p) { return deep.eval(p); }"
        val midDefinition=ShaderDefinition("middle",program=ShaderProgram(midSource,listOf(ShaderBinding.nested("deep",bad))))
        val middle=ShaderNode.snapshot(midDefinition.proceduralBackground())
        fun parent(node:ShaderNode)=ShaderDefinition("diagnostic-parent",program=ShaderProgram(
            "uniform shader content; uniform shader childData; half4 main(float2 p) { return childData.eval(p); }",
            listOf(ShaderBinding.nested("childData",node))))
        fun check(result:ShaderResult<*>,path:String) {
            val error=assertIs<ShaderError.CompilationError>(assertIs<ShaderResult.Failure<*>>(result).error)
            assertEquals(badSource,error.shaderSource)
            assertTrue(error.message.contains(path),error.message)
        }
        ShaderFactory.create().use { factory ->
            check(factory.createRenderEffect(parent(bad).effect(),16f,16f),"childData(bad-child)")
            check(factory.createRenderEffect(parent(middle).effect(),16f,16f),"childData(middle)/deep(bad-child)")
            check(factory.createAnimatedSession(parent(middle).animatedEffect(),16f,16f),"childData(middle)/deep(bad-child)")
            val bg=midDefinition.proceduralBackground()
            check(createBackgroundSnapshot(factory,bg,midDefinition.schema.defaults(),16f,16f,0f,null),"deep(bad-child)")
            val valid=ShaderNode.snapshot(ShaderDefinition("valid-child",program=ShaderProgram("half4 main(float2 p) { return half4(1); }")).proceduralBackground())
            val parentSource="uniform shader content; half4 main(float2 p) { return content.eval(p); }"
            val aliasFailure=ShaderDefinition("bad-alias",program=ShaderProgram(parentSource,listOf(ShaderBinding.nested("missingAlias",valid))))
            val error=assertIs<ShaderError.CompilationError>(assertIs<ShaderResult.Failure<*>>(factory.createRenderEffect(aliasFailure.effect(),16f,16f)).error)
            assertEquals(parentSource,error.shaderSource)
            assertTrue(error.message.contains("missingAlias"),error.message)
        }
    }

    @Test fun rawDataBypassesColorConversionWhileImagesRemainColorManaged() {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 34)
        val usage=HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        org.junit.Assume.assumeTrue(HardwareBuffer.isSupported(16,16,HardwareBuffer.RGBA_FP16,1,usage))
        val image=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(64,128,192)) }
        try {
            ShaderFactory.create().use { factory ->
                fun snapshot(uniform:Uniform):BackgroundSnapshot {
                    val definition=ShaderDefinition("destination",program=ShaderProgram("""
                        uniform shader bufferData;
                        half4 main(float2 p) { return half4(bufferData.eval(float2(.5)).rgb,1); }
                    """.trimIndent(),listOf(ShaderBinding.uniform(uniform))))
                    return createBackgroundSnapshot(factory,definition.proceduralBackground(),definition.schema.defaults(),16f,16f,0f,null).getOrThrow()
                }
                val raw=snapshot(DataUniform("bufferData",Rgba8Data(1,1,byteArrayOf(64,128.toByte(),192.toByte(),0))))
                val picture=snapshot(TextureUniform("bufferData",image.asImageBitmap()))
                try { for(named in listOf(ColorSpace.Named.SRGB,ColorSpace.Named.LINEAR_SRGB,ColorSpace.Named.DISPLAY_P3)) {
                    val space=ColorSpace.get(named)
                    ColorTarget(space).use { target ->
                        val actual=target.color(null,raw)
                        assertTrue(abs(actual.red()-64f/255)<.015f,"Raw red unchanged in $named: $actual")
                        assertTrue(abs(actual.green()-128f/255)<.015f,"Raw green unchanged in $named: $actual")
                        assertTrue(abs(actual.blue()-192f/255)<.015f,"Raw blue unchanged in $named: $actual")
                        val color=target.color(null,picture)
                        val expected=ColorSpace.connect(ColorSpace.get(ColorSpace.Named.SRGB),space).transform(64f/255,128f/255,192f/255)
                        assertTrue(abs(color.red()-expected[0])<.015f,"Image converts to $named: $color")
                        assertTrue(abs(color.green()-expected[1])<.015f)
                        assertTrue(abs(color.blue()-expected[2])<.015f)
                    }
                } } finally { raw.close(); picture.close() }
            }
        } finally { image.recycle() }
    }

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

        fun pixel(effect: RenderEffect?, input: Int = Color.WHITE, patterned: Boolean = false): Int {
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
    private class ColorTarget(private val space: ColorSpace) : AutoCloseable {
        private val buffer = HardwareBuffer.create(16, 16, HardwareBuffer.RGBA_FP16, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
        private val node = RenderNode("Defined effect pixels").apply { setPosition(0, 0, 16, 16) }
        private val root = RenderNode("Defined effect root").apply { setPosition(0, 0, 16, 16) }
        private val renderer = HardwareBufferRenderer(buffer).apply { setContentRoot(root) }

        fun color(effect: RenderEffect?, background: BackgroundSnapshot? = null): Color {
            node.setRenderEffect(effect?.asAndroidRenderEffect())
            node.beginRecording().apply {
                drawRect(0f, 0f, 16f, 16f, Paint().apply {
                    color = Color.WHITE
                    if (background != null) shader=(background.brush as ShaderBrush).createShader(Size(16f,16f))
                })
            }
            node.endRecording()
            root.beginRecording().apply {
                drawColor(Color.TRANSPARENT, android.graphics.BlendMode.CLEAR)
                drawRenderNode(node)
            }
            root.endRecording()
            val done = CountDownLatch(1)
            var result: HardwareBufferRenderer.RenderResult? = null
            renderer.obtainRenderRequest().setColorSpace(space).draw({ it.run() }) { rendered -> result = rendered; done.countDown() }
            check(done.await(5, TimeUnit.SECONDS)) { "HardwareBufferRenderer callback timeout" }
            val rendered = checkNotNull(result)
            assertEquals(HardwareBufferRenderer.RenderResult.SUCCESS, rendered.status)
            rendered.fence.use { if (it.isValid) check(it.await(Duration.ofSeconds(5))) { "GPU fence timeout" } }
            val hardware = checkNotNull(Bitmap.wrapHardwareBuffer(buffer, space))
            try {
                val readable = checkNotNull(hardware.copy(Bitmap.Config.RGBA_F16, false))
                try {
                    // Android may promote F16 readback to extended sRGB/scRGB. Preserve the
                    // readback's declared space and convert numerically; never relabel pixels.
                    return readable.getColor(8, 8).convert(space)
                } finally { readable.recycle() }
            } finally { hardware.recycle() }
        }

        override fun close() {
            renderer.close()
            node.discardDisplayList(); root.discardDisplayList()
            buffer.close()
        }
    }
}
