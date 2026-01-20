package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.Color
import com.debanshu.shaderlab.shaderx.uniform.ColorUniform
import com.debanshu.shaderlab.shaderx.uniform.FloatUniform
import com.debanshu.shaderlab.shaderx.uniform.IntUniform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UniformTest {

    @Test
    fun floatUniform_singleValue_createsCorrectArray() {
        val uniform = FloatUniform("intensity", 0.5f)
        assertEquals("intensity", uniform.name)
        assertEquals(1, uniform.values.size)
        assertEquals(0.5f, uniform.values[0])
    }

    @Test
    fun floatUniform_vec2_createsCorrectArray() {
        val uniform = FloatUniform("resolution", 1920f, 1080f)
        assertEquals("resolution", uniform.name)
        assertEquals(2, uniform.values.size)
        assertEquals(1920f, uniform.values[0])
        assertEquals(1080f, uniform.values[1])
    }

    @Test
    fun floatUniform_vec3_createsCorrectArray() {
        val uniform = FloatUniform("color", 1f, 0.5f, 0.25f)
        assertEquals(3, uniform.values.size)
        assertEquals(1f, uniform.values[0])
        assertEquals(0.5f, uniform.values[1])
        assertEquals(0.25f, uniform.values[2])
    }

    @Test
    fun floatUniform_vec4_createsCorrectArray() {
        val uniform = FloatUniform("color", 1f, 0.5f, 0.25f, 1f)
        assertEquals(4, uniform.values.size)
    }

    @Test
    fun floatUniform_equality_worksWithArrays() {
        val uniform1 = FloatUniform("test", 1f, 2f)
        val uniform2 = FloatUniform("test", 1f, 2f)
        val uniform3 = FloatUniform("test", 1f, 3f)

        assertEquals(uniform1, uniform2)
        assertEquals(uniform1.hashCode(), uniform2.hashCode())
        assertNotEquals(uniform1, uniform3)
    }

    @Test
    fun intUniform_singleValue_createsCorrectArray() {
        val uniform = IntUniform("mode", 1)
        assertEquals("mode", uniform.name)
        assertEquals(1, uniform.values.size)
        assertEquals(1, uniform.values[0])
    }

    @Test
    fun intUniform_equality_worksWithArrays() {
        val uniform1 = IntUniform("test", 1, 2)
        val uniform2 = IntUniform("test", 1, 2)
        val uniform3 = IntUniform("test", 1, 3)

        assertEquals(uniform1, uniform2)
        assertEquals(uniform1.hashCode(), uniform2.hashCode())
        assertNotEquals(uniform1, uniform3)
    }

    @Test
    fun floatUniform_toString_includesNameAndValues() {
        val uniform = FloatUniform("test", 1f, 2f)
        val str = uniform.toString()
        assertTrue(str.contains("test"))
        assertTrue(str.contains("1.0"))
        assertTrue(str.contains("2.0"))
    }

    // ColorUniform tests

    @Test
    fun colorUniform_componentConstructor_storesValues() {
        val uniform = ColorUniform("tint", 1f, 0.5f, 0.25f, 0.8f)
        assertEquals("tint", uniform.name)
        assertEquals(1f, uniform.red)
        assertEquals(0.5f, uniform.green)
        assertEquals(0.25f, uniform.blue)
        assertEquals(0.8f, uniform.alpha)
    }

    @Test
    fun colorUniform_defaultAlpha_isOne() {
        val uniform = ColorUniform("color", 1f, 0.5f, 0.25f)
        assertEquals(1f, uniform.alpha)
    }

    @Test
    fun colorUniform_longConstructor_extractsComponents() {
        // Test with 0xFFFF5733 (Coral color)
        // A=FF (255), R=FF (255), G=57 (87), B=33 (51)
        val uniform = ColorUniform("coral", 0xFFFF5733)
        assertEquals("coral", uniform.name)
        assertEquals(1f, uniform.red, 0.01f)  // FF = 255 -> 1.0
        assertEquals(87f / 255f, uniform.green, 0.01f)  // 57 = 87
        assertEquals(51f / 255f, uniform.blue, 0.01f)  // 33 = 51
        assertEquals(1f, uniform.alpha, 0.01f)  // FF = 255 -> 1.0
    }

    @Test
    fun colorUniform_longConstructor_handlesTransparentColors() {
        // Test with 0x80FF0000 (Semi-transparent red)
        val uniform = ColorUniform("semiRed", 0x80FF0000)
        assertEquals(1f, uniform.red, 0.01f)
        assertEquals(0f, uniform.green, 0.01f)
        assertEquals(0f, uniform.blue, 0.01f)
        assertEquals(128f / 255f, uniform.alpha, 0.01f)  // 80 = 128 -> ~0.5
    }

    @Test
    fun colorUniform_toFloatArray_returnsCorrectOrder() {
        val uniform = ColorUniform("test", 0.1f, 0.2f, 0.3f, 0.4f)
        val array = uniform.toFloatArray()
        assertEquals(4, array.size)
        assertEquals(0.1f, array[0])  // red
        assertEquals(0.2f, array[1])  // green
        assertEquals(0.3f, array[2])  // blue
        assertEquals(0.4f, array[3])  // alpha
    }

    @Test
    fun colorUniform_equality_works() {
        val uniform1 = ColorUniform("test", 1f, 0.5f, 0.25f, 1f)
        val uniform2 = ColorUniform("test", 1f, 0.5f, 0.25f, 1f)
        val uniform3 = ColorUniform("test", 1f, 0.5f, 0.5f, 1f)

        assertEquals(uniform1, uniform2)
        assertEquals(uniform1.hashCode(), uniform2.hashCode())
        assertNotEquals(uniform1, uniform3)
    }

    @Test
    fun colorUniform_toString_includesComponents() {
        val uniform = ColorUniform("myColor", 1f, 0.5f, 0.25f, 0.8f)
        val str = uniform.toString()
        assertTrue(str.contains("myColor"))
        assertTrue(str.contains("1.0") || str.contains("1"))  // red
        assertTrue(str.contains("0.5"))  // green
    }

    // Compose Color integration tests

    @Test
    fun colorUniform_fromColor_extractsComponents() {
        val composeColor = Color(red = 1f, green = 0.5f, blue = 0.25f, alpha = 0.8f)
        val uniform = ColorUniform.fromColor("test", composeColor)

        assertEquals("test", uniform.name)
        assertEquals(1f, uniform.red, 0.01f)
        assertEquals(0.5f, uniform.green, 0.01f)
        assertEquals(0.25f, uniform.blue, 0.01f)
        assertEquals(0.8f, uniform.alpha, 0.01f)
    }

    @Test
    fun colorUniform_fromColor_handlesRed() {
        val uniform = ColorUniform.fromColor("red", Color.Red)
        assertEquals(1f, uniform.red, 0.01f)
        assertEquals(0f, uniform.green, 0.01f)
        assertEquals(0f, uniform.blue, 0.01f)
        assertEquals(1f, uniform.alpha, 0.01f)
    }

    @Test
    fun colorUniform_fromColor_handlesGreen() {
        val uniform = ColorUniform.fromColor("green", Color.Green)
        assertEquals(0f, uniform.red, 0.01f)
        assertEquals(1f, uniform.green, 0.01f)
        assertEquals(0f, uniform.blue, 0.01f)
        assertEquals(1f, uniform.alpha, 0.01f)
    }

    @Test
    fun colorUniform_fromColor_handlesBlue() {
        val uniform = ColorUniform.fromColor("blue", Color.Blue)
        assertEquals(0f, uniform.red, 0.01f)
        assertEquals(0f, uniform.green, 0.01f)
        assertEquals(1f, uniform.blue, 0.01f)
        assertEquals(1f, uniform.alpha, 0.01f)
    }

    @Test
    fun colorUniform_fromColor_handlesTransparent() {
        val uniform = ColorUniform.fromColor("transparent", Color.Transparent)
        assertEquals(0f, uniform.alpha, 0.01f)
    }

    @Test
    fun colorUniform_fromColor_handlesWhite() {
        val uniform = ColorUniform.fromColor("white", Color.White)
        assertEquals(1f, uniform.red, 0.01f)
        assertEquals(1f, uniform.green, 0.01f)
        assertEquals(1f, uniform.blue, 0.01f)
        assertEquals(1f, uniform.alpha, 0.01f)
    }
}

