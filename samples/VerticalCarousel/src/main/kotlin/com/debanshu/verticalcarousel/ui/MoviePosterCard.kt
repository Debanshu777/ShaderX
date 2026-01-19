package com.debanshu.verticalcarousel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.verticalcarousel.data.Movie
import com.debanshu.verticalcarousel.effect.CarouselEffect
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.animation.crossfade.CrossfadePlugin
import com.skydoves.landscapist.coil3.CoilImage
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.placeholder.shimmer.Shimmer
import com.skydoves.landscapist.placeholder.shimmer.ShimmerPlugin
import kotlin.math.round

@Composable
fun MoviePosterCard(
    movie: Movie,
    distanceFromCenter: Float,
    scrollVelocity: Float,
    scrollDirection: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val shouldApplyShader = distanceFromCenter > 0.05f
    val carouselEffect =
        remember(distanceFromCenter, scrollVelocity, scrollDirection) {
            if (shouldApplyShader) {
                CarouselEffect(
                    distanceFromCenter = distanceFromCenter,
                    scrollVelocity = scrollVelocity,
                    vignetteIntensity = 0.3f,
                    scaleAmount = 0.2f,
                    stretchIntensity = 0.4f,
                    scrollDirection = scrollDirection,
                )
            } else {
                null
            }
        }

    Box(
        modifier =
            modifier
                .then(
                    if (carouselEffect != null) {
                        Modifier.shaderEffect(carouselEffect)
                    } else {
                        Modifier
                    },
                ),
    ) {
        val posterUrl = movie.posterUrl
        if (posterUrl != null) {
            println("Poster URL: $posterUrl")
            CoilImage(
                imageModel = { posterUrl },
                modifier = Modifier.fillMaxSize(),
                imageOptions =
                    ImageOptions(
                        contentScale = ContentScale.Crop,
                        contentDescription = movie.title,
                    ),
                component =
                    rememberImageComponent {
                        +CrossfadePlugin(duration = 350)
                        +ShimmerPlugin(
                            shimmer =
                                Shimmer.Resonate(
                                    baseColor = Color(0xFF1A1A1A),
                                    highlightColor = Color(0xFF3A3A3A),
                                ),
                        )
                    },
                failure = {
                    println(it.toString())
                    GradientBackground(movie.gradientColors)
                },
            )
        } else {
            GradientBackground(movie.gradientColors)
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.7f),
                                    ),
                            ),
                    ),
        )

        // Content
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = movie.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.5).sp,
                lineHeight = 26.sp,
            )

            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = movie.subtitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.7f),
                )

                Text(
                    text = "•",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.5f),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = movie.rating.formatRating(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

@Composable
private fun GradientBackground(gradientColors: List<Color>) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors = gradientColors,
                        ),
                ),
    )
}

private fun Float.formatRating(): String {
    val rounded = round(this * 10) / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        "${rounded.toLong()}.0"
    } else {
        rounded.toString()
    }
}
