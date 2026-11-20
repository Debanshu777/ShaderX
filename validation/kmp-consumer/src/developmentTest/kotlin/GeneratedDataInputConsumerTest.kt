package io.github.debanshu.shaderx.validation

import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.uniform.*
import kotlin.test.*

/** Compiles/executes actual sample generator output using only public SDK contracts. */
class GeneratedDataInputConsumerTest {
    @Test fun explicitPortableSamplerIsReboundAsNestedShader() {
        // Complete producer output is compiled above; only application data is supplied here.
        val data = Rgba8Data(2, 1, byteArrayOf(64, 0, 0, -1, -65, 0, 0, -1))
        val child = ShaderNode.rgba8Sampler(data, DataSampling.LINEAR, ShaderTileMode.REPEAT, ShaderTileMode.CLAMP)
        val effect = sx10.generated.portable.ExportedShader.effect(child) as DefinedShaderEffect
        val uniforms = effect.buildUniforms(8f, 8f)
        assertTrue(uniforms.none { it is DataUniform })
        assertSame(child, uniforms.filterIsInstance<NestedUniform>().single().input)
        assertEquals(ShaderBindingKind.NESTED, effect.definition.program.bindings.single().descriptor.kind)
        assertFails { ShaderNode.rgba8Sampler(Rgba8Data(65, 1, ByteArray(65 * 4))) }
    }

    @Test fun callerRebindingPreservesKindsSamplingAndIndependentLiveIdentity() {
        val bytes = byteArrayOf(23, 51, 79, 0)
        val data = Rgba8Data(1, 1, bytes)
        val child = ShaderNode.snapshot(ShaderDefinition("caller-child", program = ShaderProgram(
            "half4 main(float2 p) { return half4(0.7, 0.2, 0.1, 1.0); }")).proceduralBackground(), timeSeconds = 7f)
        val effect = sx10.generated.structural.ExportedShader.effect(data, child) as DefinedShaderEffect
        val uniforms = effect.buildUniforms(8f, 8f)
        val raw = uniforms.filterIsInstance<DataUniform>().single()
        assertSame(data, raw.data)
        assertEquals(DataSampling.LINEAR, raw.sampling)
        assertEquals(ShaderTileMode.MIRROR, raw.tileModeX)
        assertEquals(ShaderTileMode.REPEAT, raw.tileModeY)
        assertSame(child, uniforms.filterIsInstance<NestedUniform>().single().input)
        bytes[0] = 0
        assertEquals(23, data.bytes()[0].toInt())
        val provider = ShaderFrameProvider { listOf(DataUniform("raw", data), NestedUniform("child", child)) }
        val live = sx10.generated.live.ExportedShader.effect(provider) as DefinedAnimatedShaderEffect
        assertSame(provider, live.frameProvider)
        assertSame(child, live.withTime(100f).buildFrameUniforms().filterIsInstance<NestedUniform>().single().input)
        assertEquals(7f, child.timeSeconds)
    }
}
