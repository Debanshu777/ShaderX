package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.effect.*
import com.debanshu.shaderlab.shaderx.factory.BaseShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderSession
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.result.ShaderError
import com.debanshu.shaderlab.shaderx.result.ShaderResult
import kotlin.test.*

class ContentDeclarationTest {
    private fun definition(source: String) = ShaderDefinition("declaration_probe", program = ShaderProgram(source))

    @Test fun authoredContentDeclarationAcceptsCommentsWhitespaceAndOtherTopLevelDeclarations() {
        for (prefix in listOf("", "// leading\n", "float level = .5;\n", "half helper() { return 1; }\n")) {
            val source = prefix + "uniform /* type */ shader\n content /* name */ ;\nhalf4 main(float2 p) { return content.eval(p); }"
            assertNull(definedContentError(definition(source).effect()), source)
        }
    }

    @Test fun sourceFreeLocalNamesCommentsAndAmbiguousDeclarationsFailConservatively() {
        for (source in listOf(
            "half4 main(float2 p) { return half4(1); }",
            "// uniform shader content;\nhalf4 main(float2 p) { return half4(1); }",
            "/* uniform shader content; */ half4 main(float2 p) { return half4(1); }",
            "half4 main(float2 p) { float content = 1; return half4(content); }",
            "half4 helper() { uniform shader content; return half4(1); }",
            "uniform shader content[1]; half4 main(float2 p) { return half4(1); }",
            "uniform shader other, content; half4 main(float2 p) { return content.eval(p); }",
            "#if 0\nuniform shader content;\n#endif\nhalf4 main(float2 p) { return half4(1); }",
            "#define INPUT content\nuniform shader INPUT; half4 main(float2 p) { return content.eval(p); }",
            "/* unterminated uniform shader content;",
        )) {
            val error = assertNotNull(definedContentError(definition(source).effect()), source)
            assertEquals(source, error.shaderSource)
            assertTrue(error.message.contains("proceduralBackground"))
        }
    }

    @Test fun wholeGraphRejectsBeforeBackendSupportOrManualSourceUniformWork() {
        val manual = object : RuntimeShaderEffect {
            override val id = "manual"
            override val displayName = "Manual"
            override val parameters: List<ParameterSpec> = emptyList()
            override val shaderSource: String get() = error("Earlier manual source must not be read")
            override fun buildUniforms(width: Float, height: Float) = error("Earlier uniforms must not run")
            override fun withTypedParameter(parameterId: String, value: ParameterValue) = this
        }
        val factory = object : BaseShaderFactory(1) {
            override fun isSupported(): Boolean = error("Backend support must not be reached")
            override val cacheSize = 0
            override fun clearCache() {}
            override fun close() {}
            override fun createAnimatedSession(effect: AnimatedShaderEffect, width: Float, height: Float): ShaderResult<ShaderSession> = error("No session")
            override fun createNativeEffect(effect: NativeEffect): ShaderResult<RenderEffect> = error("No native creation")
            override fun createRuntimeShaderEffect(effect: RuntimeShaderEffect, width: Float, height: Float): ShaderResult<RenderEffect> = error("No runtime creation")
        }
        val invalid = definition("half4 main(float2 p) { return half4(1); }").effect()
        assertIs<ShaderError.CompilationError>(assertIs<ShaderResult.Failure<*>>(
            factory.createRenderEffect(CompositeEffect.of(manual, invalid), 8f, 8f)).error)
        assertNull(definedContentError(manual), "Manual declarations retain legacy native handling")
    }
}
