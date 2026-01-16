package com.debanshu.shaderlab.shaderlib

import com.debanshu.shaderlab.shaderlib.uniform.FloatUniform
import com.debanshu.shaderlab.shaderlib.uniform.IntUniform
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
}

