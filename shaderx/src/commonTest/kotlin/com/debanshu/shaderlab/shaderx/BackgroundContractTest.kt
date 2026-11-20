package com.debanshu.shaderlab.shaderx

import com.debanshu.shaderlab.shaderx.authoring.*
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.effect.impl.WaveEffect
import kotlin.test.*

class BackgroundContractTest {
    @Test fun declarationsRejectContentWithoutChangingExistingProgramConstructor() {
        val definition = ShaderDefinition("free", program = ShaderProgram("// content is not sampled\nhalf4 main(float2 p) { return half4(0); }"))
        assertSame(definition, definition.proceduralBackground().definition)
        assertFailsWith<IllegalArgumentException> {
            ShaderDefinition("content", program = ShaderProgram("uniform shader content; half4 main(float2 p) { return content.eval(p); }")).proceduralBackground()
        }
    }
    @Test fun backgroundPreflightMatchesBackendAndInputRequirementsWithoutCallingProviders() {
        for (api in listOf(null, 33, 37)) {
            assertEquals(ShaderSupportStatus.SUPPORTED, builtInSupport(api,ShaderOperation.BACKGROUND_RENDER,null).status)
            assertEquals(ShaderSupportReason.BACKGROUND_UNAVAILABLE,builtInSupport(api,ShaderOperation.BACKGROUND_RENDER,WaveEffect()).reason)
            assertEquals(if (api == null) ShaderSupportReason.RAW_DATA_UNAVAILABLE else null,builtInSupport(api,ShaderOperation.BACKGROUND_RENDER,null,setOf(ShaderInput.RAW_DATA_TEXTURE)).reason)
            assertEquals(ShaderSupportStatus.SUPPORTED,builtInSupport(api,ShaderOperation.BACKGROUND_RENDER,null,setOf(ShaderInput.NESTED_SHADER)).status)
        }
        assertEquals(ShaderSupportReason.ANDROID_API_33_REQUIRED,builtInSupport(32,ShaderOperation.BACKGROUND_RENDER,null).reason)
    }
}
