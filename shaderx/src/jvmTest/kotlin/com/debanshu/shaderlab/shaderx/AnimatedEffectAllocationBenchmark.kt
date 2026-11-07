package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.AnimatedGradientEffect
import com.debanshu.shaderlab.shaderx.effect.impl.AudioDistortionEffect
import com.debanshu.shaderlab.shaderx.effect.impl.AudioVisualizerEffect
import com.debanshu.shaderlab.shaderx.effect.impl.PlasmaEffect
import com.debanshu.shaderlab.shaderx.effect.impl.RippleEffect
import com.debanshu.shaderlab.shaderx.effect.impl.VortexEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import java.lang.management.ManagementFactory
import kotlin.test.Test

/** Opt-in allocation diagnostic; intentionally has no hardware-dependent timing threshold. */
class AnimatedEffectAllocationBenchmark {
    @Test
    fun measureWithTimeAllocations() {
        if (System.getenv("SHADERX_ALLOCATION_BENCHMARK") != "1") return
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
            ?: error("This diagnostic requires a JVM with per-thread allocation counters")
        check(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        val threadId = Thread.currentThread().threadId()
        val effects = listOf(
            WaveEffect(), AnimatedGradientEffect(), PlasmaEffect(), RippleEffect(),
            VortexEffect(), AudioVisualizerEffect(), AudioDistortionEffect(),
        )
        for (effect in effects) {
            repeat(10_000) { sink = effect.withTime(it.toFloat()) }
            val before = bean.getThreadAllocatedBytes(threadId)
            repeat(20_000) { sink = effect.withTime(it.toFloat()) }
            val bytes = bean.getThreadAllocatedBytes(threadId) - before
            println("${effect.id}: ${bytes / 20_000.0} JVM bytes/withTime")
        }
        sink = null
    }

    private companion object {
        // Force each result to escape so the JIT cannot remove the construction.
        @Volatile
        var sink: AnimatedShaderEffect? = null
    }
}
