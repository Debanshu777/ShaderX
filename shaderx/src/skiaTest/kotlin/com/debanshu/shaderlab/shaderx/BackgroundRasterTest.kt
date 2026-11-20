package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Color as ComposeColor
import com.debanshu.shaderlab.shaderx.parameter.*
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.TextureUniform
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import com.debanshu.shaderlab.shaderx.uniform.ColorUniform
import org.jetbrains.skia.*
import org.jetbrains.skia.impl.use
import kotlin.test.*

/** Same source-free Compose brush route on JVM, browser Wasm and iOS simulator raster. */
class BackgroundRasterTest {
    @Test
    fun independentNativeSnapshotsSurviveCacheEvictionAndHavePremultipliedAlpha() {
        ShaderFactory.create(1).use { factory ->
            fun background(red:Float) = ShaderDefinition("raster_background", program=ShaderProgram("""
                layout(color) uniform half4 tint;
                half4 main(float2 p) { return half4(tint.rgb*tint.a,tint.a); }
            """.trimIndent(),listOf(ShaderBinding.uniform(ColorUniform("tint",red,.2f,.1f,.5f))))).proceduralBackground()
            val a=background(.8f);val b=background(.2f)
            val first=createBackgroundSnapshot(factory,a,a.definition.schema.defaults(),32f,32f,0f,null).getOrNull()!!
            val second=createBackgroundSnapshot(factory,b,b.definition.schema.defaults(),32f,32f,0f,null).getOrNull()!!
            try {
                factory.clearCache()
                val old=render(first)
                val other=render(second)
                assertTrue(Color.getR(old[16*32+16]) in 200..208)
                assertTrue(Color.getR(other[16*32+16]) in 48..54)
                assertTrue(Color.getA(old[16*32+16]) in 126..130)
                repeat(5) { assertContentEquals(old,render(first));assertContentEquals(other,render(second)) }
            } finally { first.close();second.close();first.close();second.close() }
        }
    }

    @Test
    fun declarationRejectsContentAndInvalidDimensionsFailBeforeNativeAllocation() {
        assertFailsWith<IllegalArgumentException> {
            ShaderDefinition("content_background",program=ShaderProgram("uniform shader content; half4 main(float2 p) { return content.eval(p); }")).proceduralBackground()
        }
        ShaderFactory.create().use { factory ->
            val bg=ShaderDefinition("invalid_background",program=ShaderProgram("half4 main(float2 p) { return half4(1); }")).proceduralBackground()
            assertTrue(createBackgroundSnapshot(factory,bg,bg.definition.schema.defaults(),0f,32f,0f,null) is ShaderResult.Failure)
            assertEquals(0,factory.cacheSize)
            val invalid=ShaderDefinition("invalid_source_background",program=ShaderProgram("invalid source")).proceduralBackground()
            assertTrue(createBackgroundSnapshot(factory,invalid,invalid.definition.schema.defaults(),32f,32f,0f,null) is ShaderResult.Failure)
        }
    }

    @Test
    fun boundedShapeAdapterExperimentHasIndependentShaderPaints() {
        ShaderFactory.create().use { factory ->
            val bg=ShaderDefinition("circle_background",program=ShaderProgram("half4 main(float2 p) { return half4(0,1,0,1); }")).proceduralBackground()
            val snapshot=createBackgroundSnapshot(factory,bg,bg.definition.schema.defaults(),32f,32f,0f,null).getOrNull()!!
            try {
                val actual=render(snapshot,true)
                assertEquals(0,Color.getA(actual[0]))
                assertEquals(Color.GREEN,actual[16*32+16])
            } finally { snapshot.close() }
        }
    }

    @Test
    fun wrongSchemaAndFrameInputFailBeforeCompilationWhileExplicitImageRenders() {
        ShaderFactory.create().use { factory ->
            val gain=FloatParameterDefinition("owned_background","gain",.5f)
            val schema=ParameterSchema("owned_background",listOf(gain))
            val bg=ShaderDefinition("owned_background",program=ShaderProgram("""
                uniform float gain;
                half4 main(float2 p) { return half4(gain,0,0,1); }
            """.trimIndent(),listOf(ShaderBinding.float("gain",gain.key))),schema=schema).proceduralBackground()
            val other=ParameterSchema("wrong_owner",emptyList()).defaults()
            assertTrue(createBackgroundSnapshot(factory,bg,other,32f,32f,0f,null) is ShaderResult.Failure)
            val image=ImageBitmap(2,2)
            Canvas(image).drawRect(androidx.compose.ui.geometry.Rect(0f,0f,2f,2f),Paint().apply { color=ComposeColor.Blue })
            val external=ShaderDefinition("external_background",program=ShaderProgram("""
                uniform shader extra;
                half4 main(float2 p) { return extra.eval(p); }
            """.trimIndent(),listOf(ShaderBinding.frameImage("extra")))).proceduralBackground()
            assertTrue(createBackgroundSnapshot(factory,external,external.definition.schema.defaults(),32f,32f,0f,null) is ShaderResult.Failure)
            assertTrue(createBackgroundSnapshot(factory,external,external.definition.schema.defaults(),32f,32f,0f,
                ShaderFrameProvider { listOf(FloatUniform("extra",1f)) }) is ShaderResult.Failure)
            assertEquals(0,factory.cacheSize,"All wrong-input failures precede native compilation")
            val snapshot=createBackgroundSnapshot(factory,external,external.definition.schema.defaults(),32f,32f,0f,
                ShaderFrameProvider { listOf(TextureUniform("extra",image)) }).getOrNull()!!
            try { assertEquals(Color.BLUE,render(snapshot)[16*32+16]) } finally { snapshot.close() }
        }
    }

    @Test
    fun unsupportedFactoryPreflightNeverResolvesPoisonInputs() {
        ShaderFactory.create().use { builtIn ->
            // Delegation does not opt a third-party factory into the built-in native route.
            val unsupported = object : ShaderFactory by builtIn {}
            var calls = 0
            val definition = ShaderDefinition("poison_background", program = ShaderProgram("""
                uniform float live;
                half4 main(float2 p) { return half4(live,0,0,1); }
            """.trimIndent(), listOf(ShaderBinding.frameFloat("live"))))
            val bg = definition.proceduralBackground()
            val provider = ShaderFrameProvider { calls++; error("Must not resolve unsupported-route input") }
            val result = createBackgroundSnapshot(unsupported,bg,definition.schema.defaults(),32f,32f,0f,provider)
            assertTrue(result is ShaderResult.Failure)
            assertTrue(result.error is com.debanshu.shaderlab.shaderx.result.ShaderError.UnsupportedEffect,
                "Route rejection must win over a throwing frame provider")
            assertEquals(0,calls)
            // Rejection also precedes value/dimension/time validation and structural uniform resolution.
            val invalidValues = ParameterSchema("unrelated_values",emptyList()).defaults()
            val invalid = createBackgroundSnapshot(unsupported,bg,invalidValues,-1f,0f,Float.NaN,provider)
            assertTrue(invalid is ShaderResult.Failure)
            assertTrue(invalid.error is com.debanshu.shaderlab.shaderx.result.ShaderError.UnsupportedEffect)
            assertEquals(0,calls)
            assertEquals(0,builtIn.cacheSize)
        }
    }

    private fun render(snapshot:BackgroundSnapshot,circle:Boolean=false):IntArray = Surface.makeRasterN32Premul(32,32).use { surface ->
        surface.canvas.clear(Color.TRANSPARENT)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,surface.canvas.asComposeCanvas(),Size(32f,32f)) {
            if(circle) drawCircle(snapshot.brush) else drawRect(snapshot.brush)
        }
        surface.makeImageSnapshot().use { image -> Bitmap.makeFromImage(image).use { bitmap -> IntArray(32*32) { bitmap.getColor(it%32,it/32) } } }
    }
}
