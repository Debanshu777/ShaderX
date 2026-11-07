package com.debanshu.shaderlab.audiolib

import java.lang.management.ManagementFactory
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test

/** Opt-in diagnostic comparing the existing one-shot FFT with the capture workspace. */
class SpectrumAllocationBenchmark {
    @Test
    fun compareOneShotAndWorkspace() {
        if (System.getenv("SHADERX_AUDIO_BENCHMARK") != "1") return
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
            ?: error("This diagnostic requires JVM per-thread allocation counters")
        check(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        val threadId = Thread.currentThread().threadId()
        val samples = FloatArray(2048) { sin(2.0 * PI * 440 * it / 44100).toFloat() * 0.4f }
        val analyzer = SpectrumAnalyzer()
        val oneShot = { Fft.magnitudes(samples, 256) }
        val workspace = { analyzer.magnitudes(samples, 256) }
        repeat(2_000) {
            sink = oneShot()
            sink = workspace()
        }
        val timings = mutableMapOf("one-shot" to mutableListOf<Double>(), "workspace" to mutableListOf())
        repeat(5) { round ->
            val cases = if (round % 2 == 0) listOf("one-shot" to oneShot, "workspace" to workspace)
                else listOf("workspace" to workspace, "one-shot" to oneShot)
            for ((name, operation) in cases) {
                val allocatedBefore = bean.getThreadAllocatedBytes(threadId)
                val started = System.nanoTime()
                repeat(3_000) { sink = operation() }
                val nanos = System.nanoTime() - started
                val allocated = bean.getThreadAllocatedBytes(threadId) - allocatedBefore
                timings.getValue(name) += nanos / 3000.0
                println("audio FFT $name round ${round + 1}: ${allocated / 3000.0} JVM bytes/frame, ${nanos / 3000.0} ns/frame")
            }
        }
        for ((name, rounds) in timings) {
            println("audio FFT $name median: ${rounds.sorted()[rounds.size / 2]} ns/frame")
        }
        sink = null
    }

    private companion object {
        @Volatile
        var sink: FloatArray? = null
    }
}
