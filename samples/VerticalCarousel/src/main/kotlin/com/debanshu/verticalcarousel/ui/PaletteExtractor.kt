package com.debanshu.verticalcarousel.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.debanshu.verticalcarousel.data.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data class holding extracted palette colors for a movie.
 */
data class PaletteColors(
    val dominantColor: Color,
    val darkMutedColor: Color,
    val mutedColor: Color,
    val vibrantColor: Color,
)

/**
 * Default dark colors used when palette extraction fails.
 */
private val defaultPaletteColors =
    PaletteColors(
        dominantColor = Color(0xFF0D0D0D),
        darkMutedColor = Color(0xFF0D0D0D),
        mutedColor = Color(0xFF1A1A1A),
        vibrantColor = Color(0xFF1A1A1A),
    )

/**
 * Composable that extracts palette colors from a movie poster image.
 *
 * @param movie The movie to extract colors from
 * @param fallbackColors Colors to use if poster URL is null
 * @return PaletteColors extracted from the poster, or fallback/default colors
 */
@Composable
fun rememberPaletteColors(
    movie: Movie?,
    fallbackColors: List<Color> = emptyList(),
): PaletteColors {
    val context = LocalContext.current
    var paletteColors by remember { mutableStateOf(defaultPaletteColors) }

    // Cache ImageLoader to avoid recreation on each extraction
    val imageLoader = remember { ImageLoader(context) }

    // Cache for already extracted palettes
    val paletteCache = remember { mutableMapOf<Int, PaletteColors>() }

    LaunchedEffect(movie?.id) {
        if (movie == null) {
            paletteColors = defaultPaletteColors
            return@LaunchedEffect
        }

        // Check cache first
        paletteCache[movie.id]?.let {
            paletteColors = it
            return@LaunchedEffect
        }

        val posterUrl = movie.posterUrl
        if (posterUrl == null) {
            // Use movie's gradient colors as fallback
            val colors =
                if (movie.gradientColors.isNotEmpty()) {
                    PaletteColors(
                        dominantColor = movie.gradientColors.first(),
                        darkMutedColor = movie.gradientColors.first(),
                        mutedColor = movie.gradientColors.getOrElse(1) { movie.gradientColors.first() },
                        vibrantColor = movie.gradientColors.last(),
                    )
                } else {
                    defaultPaletteColors
                }
            paletteColors = colors
            paletteCache[movie.id] = colors
            return@LaunchedEffect
        }

        // Extract palette from poster image
        val extractedColors =
            withContext(Dispatchers.IO) {
                try {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(posterUrl)
                            .build()

                    val result = imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = result.image.toBitmap()
                        extractColorsFromBitmap(bitmap)
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }

        val finalColors =
            extractedColors ?: if (movie.gradientColors.isNotEmpty()) {
                PaletteColors(
                    dominantColor = movie.gradientColors.first(),
                    darkMutedColor = movie.gradientColors.first(),
                    mutedColor = movie.gradientColors.getOrElse(1) { movie.gradientColors.first() },
                    vibrantColor = movie.gradientColors.last(),
                )
            } else {
                defaultPaletteColors
            }

        paletteColors = finalColors
        paletteCache[movie.id] = finalColors
    }

    return paletteColors
}

/**
 * Extracts colors from a bitmap using Android Palette library.
 */
private fun extractColorsFromBitmap(bitmap: Bitmap): PaletteColors {
    val palette =
        Palette
            .from(bitmap)
            .maximumColorCount(16)
            .generate()

    val dominantSwatch = palette.dominantSwatch
    val darkMutedSwatch = palette.darkMutedSwatch
    val mutedSwatch = palette.mutedSwatch
    val vibrantSwatch = palette.vibrantSwatch

    // Get colors with fallbacks
    val dominantColor =
        dominantSwatch?.rgb?.let { Color(it) }
            ?: darkMutedSwatch?.rgb?.let { Color(it) }
            ?: Color(0xFF0D0D0D)

    val darkMutedColor =
        darkMutedSwatch?.rgb?.let { Color(it) }
            ?: dominantSwatch?.rgb?.let { Color(it) }
            ?: Color(0xFF0D0D0D)

    val mutedColor =
        mutedSwatch?.rgb?.let { Color(it) }
            ?: darkMutedSwatch?.rgb?.let { Color(it) }
            ?: Color(0xFF1A1A1A)

    val vibrantColor =
        vibrantSwatch?.rgb?.let { Color(it) }
            ?: mutedSwatch?.rgb?.let { Color(it) }
            ?: Color(0xFF1A1A1A)

    return PaletteColors(
        dominantColor = dominantColor,
        darkMutedColor = darkMutedColor,
        mutedColor = mutedColor,
        vibrantColor = vibrantColor,
    )
}
