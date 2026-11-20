package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.uniform.*
import kotlin.test.*
import com.debanshu.shaderlab.shaderx.factory.*

class DataInputContractTest {
    @Test fun byteSnapshotsAndStrictBounds() {
        val bytes = byteArrayOf(1, 127, -1, 0)
        val data = Rgba8Data(1, 1, bytes)
        bytes[0] = 42
        assertEquals(1, data.bytes()[0].toInt())
        data.bytes()[0] = 7
        assertEquals(1, data.bytes()[0].toInt())
        for ((w,h) in listOf(0 to 1, -1 to 1, Int.MAX_VALUE to Int.MAX_VALUE, 4096 to 4096, 4097 to 1)) {
            assertFailsWith<IllegalArgumentException> { Rgba8Data(w,h,ByteArray(0)) }
        }
        assertFailsWith<IllegalArgumentException> { Rgba8Data(1,1,ByteArray(3)) }
        assertFailsWith<IllegalArgumentException> { Rgba8Data(1,1,ByteArray(5)) }
        val descriptor = ShaderBinding.data("data", data, DataSampling.LINEAR, ShaderTileMode.REPEAT).descriptor
        assertEquals(ShaderBindingKind.DATA, descriptor.kind)
        assertEquals(DataSampling.LINEAR, descriptor.dataSampling)
        assertEquals(ShaderTileMode.REPEAT, descriptor.tileModeX)
        assertNull(descriptor.constant)
    }

    @Test fun capabilityMetadataRejectsHiddenRawDescendantsWithoutCallingProvider() {
        var calls = 0
        val definition=ShaderDefinition("raw-live",program=ShaderProgram("invalid source is not read/compiled by query",listOf(ShaderBinding.frameData("bytes"))))
        val effect=definition.animatedEffect(frameProvider=ShaderFrameProvider { calls++; error("must not execute") })
        assertEquals(ShaderSupportReason.RAW_DATA_UNAVAILABLE,builtInSupport(null,ShaderOperation.CONTENT_RENDER,effect).reason)
        assertEquals(ShaderSupportStatus.SUPPORTED,builtInSupport(33,ShaderOperation.CONTENT_RENDER,effect).status)
        assertEquals(0,calls)
        val data=Rgba8Data(1,1,byteArrayOf(1,2,3,0))
        val child=node(listOf(ShaderBinding.data("bytes",data)),"uniform shader bytes; half4 main(float2 p) { return bytes.eval(p); }")
        val parent=ShaderDefinition("parent",program=ShaderProgram("invalid source",listOf(ShaderBinding.nested("child",child)))).effect()
        assertEquals(ShaderSupportReason.RAW_DATA_UNAVAILABLE,builtInSupport(null,ShaderOperation.CONTENT_RENDER,parent).reason)
    }

    private fun node(bindings: List<ShaderBinding> = emptyList(), source: String = "half4 main(float2 p) { return half4(1); }"): ShaderNode =
        ShaderNode.snapshot(ShaderDefinition("node", program = ShaderProgram(source, bindings)).proceduralBackground())

    @Test fun graphBoundsAndIndependentSameSourceSiblings() {
        val leaf = node()
        val siblings = node(listOf(ShaderBinding.nested("a",leaf), ShaderBinding.nested("b",leaf)),
            "uniform shader a; uniform shader b; half4 main(float2 p) { return a.eval(p)*b.eval(p); }")
        assertNotNull(siblings)
        var nested = leaf
        repeat(3) { nested = node(listOf(ShaderBinding.nested("a", nested)), "uniform shader a; half4 main(float2 p) { return a.eval(p); }") }
        assertFailsWith<IllegalArgumentException> { node(listOf(ShaderBinding.nested("a",nested)), "uniform shader a; half4 main(float2 p) { return a.eval(p); }") }
        assertFailsWith<IllegalArgumentException> { node((0..31).map { ShaderBinding.nested("n$it", leaf) },
            (0..31).joinToString(" ") { "uniform shader n$it;" } + " half4 main(float2 p) { return half4(1); }") }
        assertFailsWith<IllegalArgumentException> { node(source="uniform shader missing; half4 main(float2 p) { return missing.eval(p); }") }
    }

    @Test fun inputDeclarationsFailClosedWithoutRestrictingNumericArrays() {
        for (source in listOf(
            "uniform shader a, b; half4 main(float2 p) { return a.eval(p); }",
            "#define CHILD uniform shader a;\n CHILD half4 main(float2 p) { return a.eval(p); }",
            "uniform shader a[2]; half4 main(float2 p) { return half4(1); }",
            "/* unterminated",
        )) assertFailsWith<IllegalArgumentException>(source) { node(source=source) }
        val leaf=node()
        assertNotNull(node(listOf(ShaderBinding.nested("a",leaf),ShaderBinding.nested("b",leaf)),
            "/* uniform shader ignored; #define ignored */ uniform /* comment */ shader a; // uniform shader ignored;\n uniform shader b; half4 main(float2 p) { return a.eval(p)*b.eval(p); }"))
        assertNotNull(node(listOf(ShaderBinding.uniform(FloatUniform("values",floatArrayOf(1f,2f,3f,4f)))),
            "uniform float4 values[1]; half4 main(float2 p) { return half4(values[0]); }"))
    }

    @Test fun newKindsValidateParentAliasesBeforeNativeSetters() {
        val leaf=node()
        val input=NestedUniform("child",leaf)
        assertFailsWith<IllegalArgumentException> { validateInputAliases("uniform float child; half4 main(float2 p) { return half4(child); }",listOf(input)) }
        assertFailsWith<IllegalArgumentException> { validateInputAliases("uniform shader other; half4 main(float2 p) { return other.eval(p); }",listOf(input)) }
        validateInputAliases("uniform shader content; uniform shader child; half4 main(float2 p) { return child.eval(p)*content.eval(p); }",listOf(input))
        // Legacy source/parser restrictions are not imposed when new kinds are absent.
        validateInputAliases("#define OLD_CUSTOM_SOURCE",listOf(FloatUniform("legacy",1f)))
    }

    @Test fun liveKindValidationAndProviderIdentity() {
        val data = Rgba8Data(1,1,byteArrayOf(1,2,3,0))
        val definition = ShaderDefinition("live", program=ShaderProgram("uniform shader bytes; half4 main(float2 p) { return bytes.eval(p); }", listOf(ShaderBinding.frameData("bytes"))))
        val provider = ShaderFrameProvider { listOf(DataUniform("bytes",data)) }
        val a = definition.animatedEffect(frameProvider=provider)
        assertEquals(a, definition.animatedEffect(frameProvider=provider))
        assertNotEquals(a, definition.animatedEffect(frameProvider=ShaderFrameProvider { listOf(DataUniform("bytes",data)) }))
        assertIs<DataUniform>(a.buildFrameUniforms().single())
        assertFailsWith<IllegalArgumentException> { definition.animatedEffect(frameProvider=ShaderFrameProvider { listOf(TextureUniform("bytes", androidx.compose.ui.graphics.ImageBitmap(1,1))) }).buildFrameUniforms() }
    }
}
