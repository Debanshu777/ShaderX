package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.parameter.*
import com.debanshu.shaderlab.shaderx.uniform.*
import kotlin.test.*

class ShaderBindingDescriptorTest {
    @Test fun metadataDescribesTheActualDeclarationAndDefensivelySnapshotsArrays() {
        val floats = floatArrayOf(1f, 2f)
        val binding = ShaderBinding.uniform(FloatUniform("vector", floats))
        floats[0] = 99f
        assertEquals(ShaderBindingKind.CONSTANT, binding.descriptor.kind)
        assertEquals("vector", binding.descriptor.name)
        val first = binding.descriptor.constant as FloatUniform
        assertContentEquals(floatArrayOf(1f, 2f), first.values)
        first.values[0] = 42f
        assertContentEquals(floatArrayOf(1f, 2f), (binding.descriptor.constant as FloatUniform).values)
        val key = FloatParameterDefinition("owner", "when", 1f).key
        val parameter = ShaderBinding.float("actualUniformName", key).descriptor
        assertEquals("when", parameter.parameterId)
        assertEquals("actualUniformName", parameter.name)
        assertEquals(ShaderBindingKind.FLOAT_PARAMETER, parameter.kind)
        assertNull(parameter.constant)
    }

    @Test fun kindsDoNotNeedRuntimeProviderEvaluation() {
        val color = ColorParameterDefinition("owner", "color", 0x12345678L)
        val bool = BooleanParameterDefinition("owner", "bool", true)
        assertEquals(ShaderBindingKind.COLOR_PARAMETER, ShaderBinding.color("tint", color.key).descriptor.kind)
        assertEquals(ShaderBindingKind.BOOLEAN_PARAMETER, ShaderBinding.boolean("flag", bool.key).descriptor.kind)
        assertEquals(ShaderBindingKind.RESOLUTION, ShaderBinding.resolution().descriptor.kind)
        assertEquals(ShaderBindingKind.TIME, ShaderBinding.time().descriptor.kind)
        assertEquals(ShaderBindingKind.FRAME_FLOAT, ShaderBinding.frameFloat("live").descriptor.kind)
        assertEquals(ShaderBindingKind.FRAME_IMAGE, ShaderBinding.frameImage("texture").descriptor.kind)
        assertNull(ShaderBinding.frameImage("texture").descriptor.constant)
        assertEquals(ColorUniform("tint", 0x12345678L), ShaderBinding.color("tint", 0x12345678L).descriptor.constant)
    }
}
