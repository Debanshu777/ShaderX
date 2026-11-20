package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.factory.create
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.uniform.ColorUniform
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use
import kotlin.test.Test
import kotlin.test.assertTrue

/** Exercises the public factory, including Skia's destination color transform and alpha. */
class ColorUniformRenderTest {
    @Test
    fun layoutColorTransformsSrgbToLinearDestination_andRequiresPremultipliedOutput() {
        val color = ColorUniform("tint", 0.5f, 0.25f, 0.75f, 0.5f)
        for (destination in listOf(ColorSpace.sRGB, ColorSpace.sRGBLinear)) {
            ShaderFactory.create().use { factory ->
                for (annotated in listOf(true, false)) {
                    val effect = Tint(color, annotated)
                    val filter = factory.createRenderEffect(effect, 8f, 8f).getOrNull()
                        ?: error("Tint compilation failed")
                    Surface.makeRaster(ImageInfo(8, 8, ColorType.N32, ColorAlphaType.PREMUL, destination)).use { surface ->
                        Paint().use { paint ->
                            paint.color = Color.WHITE
                            paint.imageFilter = filter.asSkiaImageFilter()
                            surface.canvas.drawRect(Rect.makeWH(8f, 8f), paint)
                        }
                        surface.makeImageSnapshot().use { image ->
                            Bitmap.makeFromImage(image).use { bitmap ->
                                val actual = bitmap.getColor(4, 4)
                                // getColor returns unpremultiplied channel values in the bitmap's space.
                                val expected = if (annotated) ColorSpace.sRGB.convert(destination, Color4f(.5f, .25f, .75f, .5f))
                                    else Color4f(.5f, .25f, .75f, .5f)
                                for ((value, target) in listOf(Color.getR(actual) to expected.r, Color.getG(actual) to expected.g, Color.getB(actual) to expected.b, Color.getA(actual) to .5f)) {
                                    assertTrue(kotlin.math.abs(value / 255f - target) < .025f,
                                        "layout=$annotated linear=${destination.isGammaLinear}: $value vs $target")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private data class Tint(val tint: ColorUniform, val annotated: Boolean) : RuntimeShaderEffect {
        override val id = "color_audit"
        override val displayName = "Color audit"
        override val parameters: List<ParameterSpec> = emptyList()
        override val shaderSource = """
            uniform shader content;
            ${if (annotated) "layout(color)" else ""} uniform half4 tint;
            half4 main(float2 p) { return half4(tint.rgb * tint.a, tint.a) * content.eval(p).a; }
        """.trimIndent()
        override fun buildUniforms(width: Float, height: Float) = listOf(tint)
        override fun withTypedParameter(parameterId: String, value: ParameterValue) = this
    }
}
