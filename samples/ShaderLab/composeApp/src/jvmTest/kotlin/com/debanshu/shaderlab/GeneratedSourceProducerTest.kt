package com.debanshu.shaderlab

import com.debanshu.shaderlab.authoring.CustomDefinitions
import com.debanshu.shaderlab.playground.KotlinExport
import com.debanshu.shaderlab.playground.PlaygroundRegistry
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** Writes actual generator output for the separate consumer, never handwritten approximations. */
class GeneratedSourceProducerTest {
    @Test fun generateConsumerSourcesUnderNonEnglishLocale() {
        val prior = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val directory = File("build/generated-playground-consumer").apply { mkdirs() }
            directory.listFiles()?.filter { it.extension == "kt" }?.forEach { it.delete() }
            val cases = PlaygroundRegistry.standard().entries.map { it.starter }.filterNot { it.id.contains("audio") } + AdversarialExportFixture.effect()
            for ((index, effect) in cases.withIndex()) {
                val actual = KotlinExport.generate(effect)
                File(directory, "GeneratedPlayground$index.kt").writeText("package sx06.generated.case$index\n\n$actual")
                if (effect.id == "fixture") {
                    File(directory, "GeneratedAdversarial.kt").writeText("package sx06.generated.adversarial\n\n$actual")
                }
            }
            for ((index, preset) in com.debanshu.shaderlab.playground.CollectionPresets.all.withIndex()) {
                File(directory, "GeneratedCollection$index.kt").writeText("package sx09.generated.case$index\n\n" + KotlinExport.generate(preset.effect()))
            }
            for ((name, effect) in mapOf("structural" to DataInputExportFixture.structural(), "live" to DataInputExportFixture.live(), "portable" to DataInputExportFixture.portable())) {
                File(directory, "GeneratedDataInput$name.kt").writeText("package sx10.generated.$name\n\n" + KotlinExport.generate(effect))
            }
            val image = androidx.compose.ui.graphics.ImageBitmap(2, 2)
            val imageCases = mapOf(
                "image" to RebindingExportFixture.staticImage(image),
                "liveimage" to RebindingExportFixture.liveImage(image),
            )
            for ((name, effect) in imageCases) {
                val actual = KotlinExport.generate(effect)
                File(directory, "GeneratedRebinding$name.kt").writeText("package sx07.generated.$name\n\n$actual")
                kotlin.test.assertTrue(actual.contains("input0: ImageBitmap"))
                kotlin.test.assertTrue(actual.contains("ShaderTileMode.MIRROR"))
                kotlin.test.assertTrue(actual.contains("ShaderTileMode.REPEAT"))
                if (name == "image") kotlin.test.assertTrue(actual.contains("BODY INPUT_ARGUMENTS INPUT_PARAMETERS"), "Source must not be modified by generator placeholders")
            }
            val workbench = listOf(
                com.debanshu.shaderlab.playground.WorkbenchStarters.tint,
                com.debanshu.shaderlab.playground.WorkbenchStarters.distortion,
                com.debanshu.shaderlab.playground.WorkbenchStarters.animated,
                com.debanshu.shaderlab.playground.WorkbenchStarters.image,
            )
            for ((index, draft) in workbench.withIndex()) {
                val effect = draft.build(image).effect
                val actual = KotlinExport.generate(effect)
                File(directory, "GeneratedWorkbench$index.kt").writeText("package sx07.generated.workbench$index\n\n$actual")
            }
            val background = com.debanshu.shaderlab.playground.WorkbenchStarters.background
            val backgroundCases = listOf(
                background.copy(currentValues = mapOf("intensity" to com.debanshu.shaderlab.shaderx.parameter.ParameterValue.FloatValue(.375f))),
                background.copy(source = "uniform float time;\n" + background.source.replace("p.x / resolution.x", "fract(p.x / resolution.x + time)"), animated = true, timeName = "time"),
                background.copy(source = "uniform shader imageInput;\n" + background.source.replace("half3(x, 0.2, 1.0 - x)", "imageInput.eval(p).rgb"), imageName = "imageInput"),
            )
            for ((index, draft) in backgroundCases.withIndex()) {
                val actual = KotlinExport.generate(draft.build(image).effect, com.debanshu.shaderlab.playground.WorkbenchOutput.BACKGROUND)
                File(directory, "GeneratedBackground$index.kt").writeText("package sx08.generated.background$index\n\n$actual")
            }
            val liveDefinition = com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition("background_live",
                program = com.debanshu.shaderlab.shaderx.authoring.ShaderProgram(
                    "uniform float level; half4 main(float2 p) { return half4(level, 0.0, 0.0, 1.0); }",
                    listOf(com.debanshu.shaderlab.shaderx.authoring.ShaderBinding.frameFloat("level"))))
            val liveEffect = liveDefinition.animatedEffect(frameProvider = com.debanshu.shaderlab.shaderx.authoring.ShaderFrameProvider {
                listOf(com.debanshu.shaderlab.shaderx.uniform.FloatUniform("level", floatArrayOf(.25f)))
            })
            File(directory, "GeneratedBackgroundLive.kt").writeText("package sx08.generated.live\n\n" +
                KotlinExport.generate(liveEffect, com.debanshu.shaderlab.playground.WorkbenchOutput.BACKGROUND))
            val pipelineRegistry = PlaygroundRegistry.standard()
            val pipeline = com.debanshu.shaderlab.playground.PipelineProject(listOf(
                com.debanshu.shaderlab.playground.PipelineChild("tint-one", com.debanshu.shaderlab.playground.PlaygroundProject(pipelineRegistry.entry("custom_tint"))),
                com.debanshu.shaderlab.playground.PipelineChild("distortion", com.debanshu.shaderlab.playground.PlaygroundProject(pipelineRegistry.entry("custom_distortion"))),
                com.debanshu.shaderlab.playground.PipelineChild("tint-two", com.debanshu.shaderlab.playground.PlaygroundProject(pipelineRegistry.entry("custom_tint"))),
            ))
            File(directory, "GeneratedPipeline.kt").writeText("package sx05.generated\n\n" + com.debanshu.shaderlab.playground.PipelineKotlinExport.generate(pipeline))
            val source = KotlinExport.generate(CustomDefinitions.controls.effect())
            Locale.setDefault(Locale.US)
            assertEquals(source, KotlinExport.generate(CustomDefinitions.controls.effect()))
        } finally { Locale.setDefault(prior) }
    }
}
