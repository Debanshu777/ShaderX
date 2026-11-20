package com.debanshu.shaderlab.shaderx

import androidx.compose.ui.graphics.ImageBitmap
import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.ShaderBindingKind
import com.debanshu.shaderlab.shaderx.uniform.ShaderTileMode
import com.debanshu.shaderlab.shaderx.uniform.TextureUniform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShaderBindingImageDescriptorTest {
    @Test fun imageDescriptorsPreserveSamplingModesWithoutExposingBorrowedBitmap() {
        val image = ImageBitmap(1, 1)
        val declarations = listOf(
            ShaderBinding.image("direct", image, ShaderTileMode.REPEAT, ShaderTileMode.MIRROR),
            ShaderBinding.uniform(TextureUniform("uniform", image, ShaderTileMode.MIRROR, ShaderTileMode.REPEAT)),
        ).map { it.descriptor }
        assertEquals(listOf(ShaderBindingKind.IMAGE, ShaderBindingKind.IMAGE), declarations.map { it.kind })
        assertEquals(listOf(ShaderTileMode.REPEAT, ShaderTileMode.MIRROR), declarations.map { it.tileModeX })
        assertEquals(listOf(ShaderTileMode.MIRROR, ShaderTileMode.REPEAT), declarations.map { it.tileModeY })
        for (descriptor in declarations) assertNull(descriptor.constant)
        val scalar = ShaderBinding.float("amount", 1f).descriptor
        assertNull(scalar.tileModeX)
        assertNull(scalar.tileModeY)
        val live = ShaderBinding.frameImage("live").descriptor
        assertNull(live.tileModeX, "Live image sampling is supplied at runtime by its provider")
        assertNull(live.tileModeY)
    }
}
