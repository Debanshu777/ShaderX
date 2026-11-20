package com.debanshu.shaderlab.shaderx

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.debanshu.shaderlab.shaderx.factory.createBackgroundSnapshot
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.compose.shaderBackground
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.playback.ShaderPlayback
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.impl.use
import kotlin.test.*
import kotlin.math.abs

/** Real Compose draw ordering, sizing and invalidation; desktop raster, not Android HWUI. */
class BackgroundComposeTest {
    private fun definition(source: String = "half4 main(float2 p) { return half4(0.5,0,0,0.5); }") =
        ShaderDefinition("background_test", program = ShaderProgram(source)).proceduralBackground()

    @Test
    fun emptyBoxGeneratesPixelsAndForegroundStaysUnchanged() {
        ShaderFactory.create().use { factory ->
            val scene = ImageComposeScene(32, 32) {
                Box(Modifier.size(32.dp).shaderBackground(definition(), factory = factory)) {
                    Box(Modifier.size(8.dp).background(Color.Green))
                }
            }
            try {
                val pixels = pixels(scene)
                assertEquals(org.jetbrains.skia.Color.GREEN, pixels[4 * 32 + 4])
                near(128, org.jetbrains.skia.Color.getA(pixels[16 * 32 + 16]))
                near(255, org.jetbrains.skia.Color.getR(pixels[16 * 32 + 16]))
                assertFalse(scene.hasInvalidations())
            } finally { scene.close() }
        }
    }

    @Test
    fun clippingAndPaddingOrderUseDrawBoundsAndDensityPixels() {
        ShaderFactory.create().use { factory ->
            val spatial = ShaderDefinition("background_size", program = ShaderProgram("""
                uniform float2 resolution;
                half4 main(float2 p) { return half4(p.x/resolution.x, 0, 0, 1); }
            """.trimIndent(), listOf(ShaderBinding.resolution()))).proceduralBackground()
            val size = mutableStateOf(16.dp)
            val scene = ImageComposeScene(96, 32, density = Density(2f)) {
                Row {
                    Box(Modifier.size(size.value).shaderBackground(spatial, factory = factory).padding(4.dp))
                    Box(Modifier.size(16.dp).padding(4.dp).shaderBackground(spatial, factory = factory))
                    Box(Modifier.size(16.dp).clip(CircleShape).shaderBackground(spatial, factory = factory))
                }
            }
            try {
                val first = pixels(scene, 96)
                assertTrue(org.jetbrains.skia.Color.getA(first[16 * 96 + 2]) > 250, "Background before padding covers outer bounds")
                assertEquals(0, org.jetbrains.skia.Color.getA(first[16 * 96 + 34]), "Padding before background leaves transparent inset")
                assertEquals(0, org.jetbrains.skia.Color.getA(first[65]), "Outer circle clips generated corner")
                near(132, org.jetbrains.skia.Color.getR(first[16 * 96 + 16]), 5)
                size.value = 8.dp
                val resized = pixels(scene, 96)
                near(135, org.jetbrains.skia.Color.getR(resized[8 * 96 + 8]), 7)
            } finally { scene.close() }
        }
    }

    @Test
    fun independentClocksLiveRevisionAndWholeFallbackSettle() {
        ShaderFactory.create().use { factory ->
            var live = .1f
            var fail = false
            var calls = 0
            val provider = ShaderFrameProvider {
                calls++
                check(!fail) { "Live input unavailable" }
                listOf(FloatUniform("live", live))
            }
            val bg = ShaderDefinition("background_clock", program = ShaderProgram("""
                uniform float time; uniform float live;
                half4 main(float2 p) { return half4(time, live, 0, 1); }
            """.trimIndent(), listOf(ShaderBinding.time(), ShaderBinding.frameFloat("live")))).proceduralBackground()
            val first = ShaderPlayback(.25, false)
            val second = ShaderPlayback(.75, false)
            val revision = mutableLongStateOf(0)
            var errors = 0
            val scene = ImageComposeScene(64, 32) {
                Row {
                    Box(Modifier.size(32.dp).shaderBackground(bg, playback = first, inputRevision = revision.longValue,
                        frameProvider = provider, factory = factory, fallback = SolidColor(Color.Blue), onError = { errors++ }))
                    Box(Modifier.size(32.dp).shaderBackground(bg, playback = second, inputRevision = revision.longValue,
                        frameProvider = provider, factory = factory))
                }
            }
            try {
                var p = pixels(scene,64)
                near(64, org.jetbrains.skia.Color.getR(p[16 * 64 + 16]))
                near(191, org.jetbrains.skia.Color.getR(p[16 * 64 + 48]))
                assertFalse(scene.hasInvalidations())
                val initialCalls = calls
                pixels(scene,64)
                assertEquals(initialCalls,calls, "Paused inputs must not poll")
                live=.6f; revision.longValue++
                p=pixels(scene,64)
                near(153,org.jetbrains.skia.Color.getG(p[16*64+16]))
                near(191,org.jetbrains.skia.Color.getR(p[16*64+48]))
                first.seekTo(.5)
                p=pixels(scene,64)
                near(128,org.jetbrains.skia.Color.getR(p[16*64+16]))
                fail=true; revision.longValue++
                p=pixels(scene,64)
                assertEquals(org.jetbrains.skia.Color.BLUE,p[16*64+16],"Failed whole background uses explicit fallback")
                assertEquals(0,org.jetbrains.skia.Color.getA(p[16*64+48]),"Default failure is transparent")
                assertTrue(errors>0)
                fail=false; revision.longValue++
                p=pixels(scene,64)
                near(128,org.jetbrains.skia.Color.getR(p[16*64+16]))
                assertFalse(scene.hasInvalidations())
            } finally { scene.close() }
        }
    }

    @Test
    fun boundedTextBrushExperimentDrawsWithRememberedSnapshotOwnership() {
        ShaderFactory.create().use { factory ->
            val bg=definition("half4 main(float2 p) { return half4(0,1,0,1); }")
            var disposed=false
            val scene=ImageComposeScene(32,32) {
                val snapshot=remember {
                    createBackgroundSnapshot(factory,bg,bg.definition.schema.defaults(),32f,32f,0f,null).getOrNull()!!
                }
                DisposableEffect(snapshot) { onDispose { snapshot.close();disposed=true } }
                BasicText("X",style=TextStyle(brush=snapshot.brush,fontSize=24.sp))
            }
            try {
                val actual=pixels(scene)
                assertTrue(actual.any { org.jetbrains.skia.Color.getG(it)>240 && org.jetbrains.skia.Color.getA(it)>200 },
                    "The internal adapter can shade text on desktop raster")
                assertTrue(actual.any { org.jetbrains.skia.Color.getA(it)==0 },"Only glyph coverage is painted")
            } finally { scene.close() }
            assertTrue(disposed,"Removing the composition closes its remembered text shader")
        }
    }

    @Test
    fun missingClockUsesWholeFallbackAndPreservesForeground() {
        ShaderFactory.create().use { factory ->
            val bg=ShaderDefinition("requires_clock",program=ShaderProgram("""
                uniform float time;
                half4 main(float2 p) { return half4(time,0,0,1); }
            """.trimIndent(),listOf(ShaderBinding.time()))).proceduralBackground()
            var errors=0
            val scene=ImageComposeScene(32,32) {
                Box(Modifier.size(32.dp).shaderBackground(bg,factory=factory,
                    fallback=SolidColor(Color.Blue),onError={ errors++ })) {
                    Box(Modifier.size(8.dp).background(Color.Green))
                }
            }
            try {
                val p=pixels(scene)
                assertEquals(org.jetbrains.skia.Color.GREEN,p[4*32+4])
                assertEquals(org.jetbrains.skia.Color.BLUE,p[16*32+16])
                assertEquals(1,errors)
                assertEquals(0,factory.cacheSize)
                assertFalse(scene.hasInvalidations())
            } finally { scene.close() }
        }
    }

    @Test
    fun redundantDeclarationWrappingSettlesWhileEqualDistinctProviderReplacesByIdentity() {
        ShaderFactory.create().use { factory ->
            val definition = ShaderDefinition("provider_identity", program = ShaderProgram("""
                uniform float live;
                half4 main(float2 p) { return half4(live,0,0,1); }
            """.trimIndent(),listOf(ShaderBinding.frameFloat("live"))))
            assertEquals(definition.proceduralBackground(),definition.proceduralBackground(),
                "Redundant wrappers of the same immutable definition have stable equality")
            var calls = 0
            class EqualProvider(val value:Float):ShaderFrameProvider {
                override fun uniforms(timeSeconds:Float) = listOf(FloatUniform("live",value)).also { calls++ }
                override fun equals(other:Any?) = other is EqualProvider
                override fun hashCode() = 42
            }
            val provider = mutableStateOf<ShaderFrameProvider>(EqualProvider(.25f),referentialEqualityPolicy())
            val unrelated = mutableIntStateOf(0)
            var ready = 0
            val scene = ImageComposeScene(32,32) {
                // Read unrelated host state to recompose and wrap the immutable declaration again.
                val padding = if(unrelated.intValue % 2 == 0) 0.dp else 0.dp
                Box(Modifier.size(32.dp).padding(padding).shaderBackground(definition.proceduralBackground(),
                    frameProvider=provider.value,factory=factory,onReady={ ready++ }))
            }
            try {
                near(64,org.jetbrains.skia.Color.getR(pixels(scene)[16*32+16]))
                val settledCalls = calls
                val settledReady = ready
                unrelated.intValue++
                near(64,org.jetbrains.skia.Color.getR(pixels(scene)[16*32+16]))
                assertEquals(settledCalls,calls,"Unrelated recomposition must not resolve providers again")
                assertEquals(settledReady,ready,"Redundant background wrapping must not replace native snapshots")
                assertFalse(scene.hasInvalidations())
                provider.value = EqualProvider(.75f)
                near(191,org.jetbrains.skia.Color.getR(pixels(scene)[16*32+16]))
                assertEquals(settledCalls+1,calls,"Equal-but-distinct providers replace by reference identity")
                assertEquals(settledReady+1,ready)
                assertFalse(scene.hasInvalidations())
            } finally { scene.close() }
        }
    }

    @Test
    fun attachingAndRemovingPausedZeroPlaybackRetriesClockRequirementWithoutPhaseChange() {
        ShaderFactory.create().use { factory ->
            val bg = ShaderDefinition("attach_zero_clock",program=ShaderProgram("""
                uniform float time;
                half4 main(float2 p) { return half4(time+1,0,0,1); }
            """.trimIndent(),listOf(ShaderBinding.time()))).proceduralBackground()
            val playback = mutableStateOf<ShaderPlayback?>(null,referentialEqualityPolicy())
            var errors = 0
            var ready = 0
            val scene = ImageComposeScene(32,32) {
                Box(Modifier.size(32.dp).shaderBackground(bg,playback=playback.value,factory=factory,
                    fallback=SolidColor(Color.Blue),onError={ errors++ },onReady={ ready++ })) {
                    Box(Modifier.size(8.dp).background(Color.Green))
                }
            }
            try {
                var p = pixels(scene)
                assertEquals(org.jetbrains.skia.Color.BLUE,p[16*32+16])
                assertEquals(org.jetbrains.skia.Color.GREEN,p[4*32+4])
                assertEquals(1,errors)
                assertEquals(0,ready)
                assertFalse(scene.hasInvalidations())
                playback.value = ShaderPlayback(0.0,false)
                p = pixels(scene)
                assertEquals(org.jetbrains.skia.Color.RED,p[16*32+16],
                    "Attaching a paused zero-position owner must retry a previously missing clock")
                assertEquals(org.jetbrains.skia.Color.GREEN,p[4*32+4])
                assertEquals(1,ready)
                assertEquals(1,errors)
                assertFalse(scene.hasInvalidations())
                playback.value = null
                p = pixels(scene)
                assertEquals(org.jetbrains.skia.Color.BLUE,p[16*32+16],
                    "Removing the owner must restore whole fallback even though sampled position remains zero")
                assertEquals(org.jetbrains.skia.Color.GREEN,p[4*32+4])
                assertEquals(2,errors)
                assertEquals(1,ready)
                assertFalse(scene.hasInvalidations())
            } finally { scene.close() }
        }
    }

    private fun near(expected:Int, actual:Int, tolerance:Int=3) = assertTrue(abs(expected-actual)<=tolerance,"$expected vs $actual")
    private fun pixels(scene:ImageComposeScene,width:Int=32):IntArray {
        Snapshot.sendApplyNotifications()
        repeat(3) { scene.render(1_000_000_000L).close() }
        return scene.render(1_000_000_000L).use { image -> Bitmap.makeFromImage(image).use { b ->
            IntArray(width*32) { b.getColor(it%width,it/width) }
        } }
    }
}
