package com.debanshu.shaderlab.shaderx

import android.os.Build
import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.effect.CompositeEffect
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.uniform.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in native creation/upload diagnostic; no drawing, frame deadline or GPU timing claim. */
class AndroidSnapshotMetricsTest {
    @Test fun boundedCreationAndUploadSamples() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        assumeTrue(InstrumentationRegistry.getArguments().getString("shaderxMetrics") == "true")
        println("SX12_METRICS model=${Build.MODEL} api=${Build.VERSION.SDK_INT} viewport=16x16 debug instrumentation; native creation only, no GPU presentation; 5 warmups + 3 rounds x 20 operations")
        ShaderFactory.create(4).use { factory ->
            var step = 0
            fun colorEffect() = ShaderDefinition("metrics-color", program = ShaderProgram(
                "uniform shader content; layout(color) uniform half4 tint; half4 main(float2 p) { return half4(tint.rgb*tint.a,tint.a)*content.eval(p).a; }",
                listOf(ShaderBinding.uniform(ColorUniform("tint", (++step % 20) / 20f, .2f, .4f, 1f))))).effect()
            fun background() = ShaderDefinition("metrics-background", program = ShaderProgram(
                "layout(color) uniform half4 tint; half4 main(float2 p) { return half4(tint.rgb*tint.a,tint.a); }",
                listOf(ShaderBinding.uniform(ColorUniform("tint", (++step % 20) / 20f, .2f, .4f, 1f))))).proceduralBackground()
            val dataDefinition = ShaderDefinition("metrics-data", program = ShaderProgram(
                "uniform shader content; uniform shader values; half4 main(float2 p) { return half4(values.eval(float2(.5)).rgb,1)*content.eval(p).a; }",
                listOf(ShaderBinding.frameData("values"))))
            val provider = ShaderFrameProvider { listOf(DataUniform("values", Rgba8Data(1, 1,
                byteArrayOf((++step % 255).toByte(), 32, 64, 0)))) }
            val dataEffect = dataDefinition.animatedEffect(isAnimating = false, frameProvider = provider)
            factory.createAnimatedSession(dataEffect, 16f, 16f).getOrThrow().use { session ->
                val workloads = linkedMapOf<String, () -> Unit>(
                    "structural-color" to { factory.createRenderEffect(colorEffect(), 16f, 16f).getOrThrow(); Unit },
                    "source-free-background" to {
                        val bg = background()
                        createBackgroundSnapshot(factory, bg, bg.definition.schema.defaults(), 16f, 16f, 0f, null).getOrThrow().close()
                    },
                    "two-child-chain" to { factory.createRenderEffect(CompositeEffect.of(colorEffect(), GrayscaleEffect()), 16f, 16f).getOrThrow(); Unit },
                    "numerical-data-upload" to { session.refresh(dataEffect, 16f, 16f).getOrThrow(); Unit },
                )
                for ((name, operation) in workloads) {
                    repeat(5) { operation() }
                    val heapBefore = Debug.getNativeHeapAllocatedSize()
                    val fdsBefore = File("/proc/self/fd").list()?.size
                    val rounds = List(3) {
                        val start = System.nanoTime()
                        repeat(20) { operation() }
                        (System.nanoTime() - start) / 20.0
                    }
                    println("SX12_METRICS name=$name nsPerOperation=$rounds nativeHeapBefore=$heapBefore nativeHeapAfter=${Debug.getNativeHeapAllocatedSize()} fdsBefore=$fdsBefore fdsAfter=${File("/proc/self/fd").list()?.size}")
                }
            }
        }
        // Samples intentionally have no universal timing/memory threshold or forced-GC claim.
    }
}
