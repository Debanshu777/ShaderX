package com.debanshu.shaderlab.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import shaderlab.samples.shaderlab.composeapp.generated.resources.Res
import shaderlab.samples.shaderlab.composeapp.generated.resources.sample_abstract
import shaderlab.samples.shaderlab.composeapp.generated.resources.sample_landscape
import shaderlab.samples.shaderlab.composeapp.generated.resources.sample_nature
import shaderlab.samples.shaderlab.composeapp.generated.resources.sample_portrait

@Composable
fun SampleImage(
    resourceName: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val painter =
        when (resourceName) {
            "sample_landscape" -> painterResource(Res.drawable.sample_landscape)
            "sample_portrait" -> painterResource(Res.drawable.sample_portrait)
            "sample_nature" -> painterResource(Res.drawable.sample_nature)
            "sample_abstract" -> painterResource(Res.drawable.sample_abstract)
            else -> painterResource(Res.drawable.sample_landscape)
        }

    Image(
        painter = painter,
        contentDescription = resourceName,
        modifier = modifier,
        contentScale = contentScale,
    )
}
