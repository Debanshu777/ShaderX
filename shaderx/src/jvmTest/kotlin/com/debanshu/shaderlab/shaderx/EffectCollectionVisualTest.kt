package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.authoring.DefinedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.impl.EffectCollection
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import org.jetbrains.skia.*
import org.jetbrains.skia.impl.use
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/** Review artifacts from the same public factory and actual Skia pixels tested elsewhere. */
class EffectCollectionVisualTest {
    @Test
    fun renderOriginalAndUsefulCollectionSettingsForVisualReview() {
        Surface.makeRasterN32Premul(256,256).use { source ->
            source.canvas.clear(Color.TRANSPARENT)
            Paint().use { paint ->
                for(y in 0..255) {
                    val t=y/255f
                    paint.color=Color.makeRGB((220-120*t).roundToInt(),(235-90*t).roundToInt(),(250-40*t).roundToInt())
                    source.canvas.drawRect(Rect.makeLTRB(0f,y.toFloat(),256f,y+1f),paint)
                }
                // Original procedural test card: gradients, flat hues, details and translucent edges.
                paint.isAntiAlias=true
                paint.color=Color.makeARGB(255,240,130,55)
                source.canvas.drawCircle(176f,72f,42f,paint)
                paint.color=Color.makeARGB(255,35,110,140)
                source.canvas.drawOval(Rect.makeLTRB(-30f,115f,210f,295f),paint)
                paint.color=Color.makeARGB(190,75,165,125)
                source.canvas.drawOval(Rect.makeLTRB(70f,145f,310f,320f),paint)
                paint.color=Color.makeARGB(180,240,225,170)
                source.canvas.drawCircle(80f,140f,43f,paint)
                paint.color=Color.makeARGB(255,55,55,80)
                for(x in 20..230 step 14) source.canvas.drawRect(Rect.makeLTRB(x.toFloat(),216f,x+4f,240f),paint)
            }
            source.makeImageSnapshot().use { image ->
                write("/tmp/sx09-original.png",image)
                val effects=listOf(
                    "adjusted" to edit(EffectCollection.colorAdjustment.effect(),"brightness" to .05f,"contrast" to 1.2f,"saturation" to 1.25f),
                    "dither" to edit(EffectCollection.orderedDither.effect(),"amount" to 1f,"cellSize" to 2f,"levels" to 3f),
                    "halftone" to edit(EffectCollection.halftone.effect(),"amount" to 1f,"cellSize" to 6f))
                val diagnostics=mutableListOf<String>()
                ShaderFactory.create().use { factory ->
                    Surface.makeRasterN32Premul(1024,256).use { comparison ->
                        comparison.canvas.drawImage(image,0f,0f)
                        effects.forEachIndexed { index,(name,effect) ->
                            factory.createRenderEffect(effect,256f,256f).getOrThrow().asSkiaImageFilter().use { filter ->
                                Surface.makeRasterN32Premul(256,256).use { rendered ->
                                    Paint().use { paint -> paint.imageFilter=filter;rendered.canvas.drawImage(image,0f,0f,paint) }
                                    // Five warmed CPU-raster rounds; includes drawing and snapshot/readback.
                                    // Reused compiled filter: excludes compilation and UI/GPU scheduling.
                                    repeat(3) { Paint().use { p -> p.imageFilter=filter;rendered.canvas.drawImage(image,0f,0f,p) } }
                                    val rounds=List(5) {
                                        val start=System.nanoTime()
                                        repeat(10) {
                                            Paint().use { p ->p.imageFilter=filter;rendered.canvas.drawImage(image,0f,0f,p) }
                                            rendered.makeImageSnapshot().use { snapshot -> Bitmap.makeFromImage(snapshot).use { it.getColor(128,128) } }
                                        }
                                        (System.nanoTime()-start)/10
                                    }
                                    diagnostics += "\"$name\": [${rounds.joinToString()}]"
                                    rendered.makeImageSnapshot().use { output ->
                                        write("/tmp/sx09-$name.png",output)
                                        comparison.canvas.drawImage(output,(index+1)*256f,0f)
                                    }
                                }
                            }
                        }
                        comparison.makeImageSnapshot().use { write("/tmp/sx09-effects.png",it) }
                    }
                }
                File("/tmp/sx09-raster-diagnostic.json").writeText("""
                    {"scope":"JVM Skia CPU raster,256x256,reused compiled filter,warmup3,5 rounds of10 draws+snapshot+bitmap readback; ns per operation, native output caches may be reused; no shader-execution/GPU/frame-rate/memory claim", "rounds_ns":{${diagnostics.joinToString()}}}
                """.trimIndent())
            }
        }
    }
    private fun edit(effect:DefinedShaderEffect,vararg values:Pair<String,Float>):DefinedShaderEffect =
        values.fold(effect) { current,(id,value) -> current.withTypedParameter(id,ParameterValue.FloatValue(value)) }
    private fun write(path:String,image:Image) {
        image.encodeToData()!!.use { data -> File(path).writeBytes(data.bytes) }
        assertTrue(File(path).length()>100,"Actual PNG artifact $path")
    }
}
