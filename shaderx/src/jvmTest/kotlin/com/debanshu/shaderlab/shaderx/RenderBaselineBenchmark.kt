package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use
import kotlin.test.Test

/** Opt-in CPU/raster diagnostic, not a display/GPU/frame-deadline benchmark. */
class RenderBaselineBenchmark {
    @Test
    fun measureColdWarmAndSessionRasterWorkloads() {
        if (System.getenv("SHADERX_RENDER_BENCHMARK") != "1") return
        println("SX12 raster CPU baseline: os=${System.getProperty("os.name")} ${System.getProperty("os.arch")}, java=${System.getProperty("java.version")}, viewport=64x64, WaveEffect defaults, time=0.25, offscreen raster, no display refresh rate; 20 warmups + 5 rounds x 100 operations")
        ShaderFactory.create().use { factory ->
            val effect = WaveEffect().withTime(.25f)
            Surface.makeRasterN32Premul(64, 64).use { surface ->
                Paint().use { paint ->
                    fun draw(filter: RenderEffect) {
                        surface.canvas.clear(Color.TRANSPARENT)
                        paint.color = Color.WHITE
                        paint.imageFilter = filter.asSkiaImageFilter()
                        surface.canvas.drawRect(Rect.makeWH(64f, 64f), paint)
                    }
                    val session = factory.createAnimatedSession(effect, 64f, 64f).getOrNull() ?: error("session failed")
                    session.use {
                        var slider = 0
                        val workloads = linkedMapOf<String, () -> Unit>(
                            "cold-create-and-raster" to { factory.clearCache(); draw(factory.createRenderEffect(effect, 64f, 64f).getOrNull() ?: error("compile failed")) },
                            "warm-create-and-raster" to { draw(factory.createRenderEffect(effect, 64f, 64f).getOrNull() ?: error("compile failed")) },
                            "session-refresh-and-raster" to { draw(session.refresh(effect, 64f, 64f).getOrNull() ?: error("refresh failed")) },
                            "slider-change-and-raster" to { draw(factory.createRenderEffect(effect.withParameter("amplitude", (slider++ % 20).toFloat()), 64f, 64f).getOrNull() ?: error("update failed")) },
                        )
                        for ((name, operation) in workloads) {
                            repeat(20) { operation() }
                            val rounds = List(5) {
                                val start = System.nanoTime()
                                repeat(100) { operation() }
                                (System.nanoTime() - start) / 100.0
                            }
                            println("$name ns/operation=${rounds.joinToString()} median=${rounds.sorted()[2]}")
                        }
                    }
                }
            }
        }
    }
}
