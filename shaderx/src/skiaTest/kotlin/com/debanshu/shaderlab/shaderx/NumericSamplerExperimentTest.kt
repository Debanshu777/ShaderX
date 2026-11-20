package com.debanshu.shaderlab.shaderx

import org.jetbrains.skia.*
import org.jetbrains.skia.impl.use
import kotlin.test.*

/** Bounded feasibility experiment, independent of image.makeShader or public support claims. */
class NumericSamplerExperimentTest {
    @Test
    fun scalarUniformArrayChildPreservesRgbAtZeroAlphaInSrgbAndLinearDestinations() {
        for (space in listOf(ColorSpace.sRGB,ColorSpace.sRGBLinear)) {
            RuntimeEffect.makeForShader("""uniform float4 bytes[2];
                half4 main(float2 p) { return p.x < 1 ? half4(bytes[0]) : half4(bytes[1]); }""").use { program ->
                RuntimeShaderBuilder(program).use { child ->
                    child.uniform("bytes",floatArrayOf(64f/255,128f/255,192f/255,0f, 1f,0f,0f,1f))
                    child.makeShader().use { data ->
                        RuntimeEffect.makeForShader("""uniform shader data;
                            half4 main(float2 p) { half4 d=data.eval(float2(.5,.5)); return half4(d.rgb,1); }""").use { parentProgram ->
                            RuntimeShaderBuilder(parentProgram).use { parent ->
                                parent.child("data",data)
                                parent.makeShader().use { shader ->
                                    Surface.makeRaster(ImageInfo(1,1,ColorType.RGBA_8888,ColorAlphaType.PREMUL,space)).use { surface ->
                                        Paint().use { paint -> paint.shader=shader;surface.canvas.drawRect(Rect.makeWH(1f,1f),paint) }
                                        surface.makeImageSnapshot().use { image -> Bitmap.makeFromImage(image).use { bitmap ->
                                            // Destination bytes are inspected in their own encoding, avoiding getColor conversion.
                                            val bytes = bitmap.readPixels(ImageInfo(1,1,ColorType.RGBA_8888,ColorAlphaType.PREMUL,space))!!
                                            assertTrue(kotlin.math.abs(64-(bytes[0].toInt() and 255))<=1)
                                            assertTrue(kotlin.math.abs(128-(bytes[1].toInt() and 255))<=1)
                                            assertTrue(kotlin.math.abs(192-(bytes[2].toInt() and 255))<=1)
                                        } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
