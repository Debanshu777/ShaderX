package com.debanshu.verticalcarousel.data.repository

import androidx.compose.ui.graphics.Color
import com.debanshu.verticalcarousel.data.Movie
import com.debanshu.verticalcarousel.data.api.MovieApi
import com.debanshu.verticalcarousel.data.api.MovieDto
import kotlin.random.Random

/**
 * Repository for fetching and transforming movie data.
 * Abstracts the API layer from the rest of the app.
 */
class MovieRepository(
    private val api: MovieApi = MovieApi(),
) {
    /**
     * Fetches popular movies and transforms them to domain models.
     *
     * @param page Page number to fetch
     * @return List of Movie domain objects
     */
    suspend fun getPopularMovies(page: Int = 1): Result<List<Movie>> =
        try {
            val response = api.discoverMovies(page)
            val movies = response.results.map { it.toDomain() }
            Result.success(movies)
        } catch (e: Exception) {
            Result.failure(e)
        }
}

private fun MovieDto.toDomain(): Movie {
    val random = Random(id)
    val gradientColors = generateGradientColors(random)
    val year = releaseDate.takeIf { it.length >= 4 }?.take(4) ?: "Unknown"
    val posterUrl = posterPath?.let { "${MovieApi.IMAGE_BASE_URL}$it" }

    return Movie(
        id = id,
        title = title,
        subtitle = "$year / ${getGenreName(genreIds.firstOrNull())}",
        rating = voteAverage,
        gradientColors = gradientColors,
        posterUrl = posterUrl,
    )
}

private fun generateGradientColors(random: Random): List<Color> {
    // Generate a base hue
    val baseHue = random.nextFloat() * 360f

    // Create a gradient from darker to lighter with the same hue family
    return listOf(
        hslToColor(baseHue, 0.6f, 0.15f),
        hslToColor(baseHue, 0.7f, 0.25f),
        hslToColor((baseHue + 30f) % 360f, 0.8f, 0.4f),
    )
}

/**
 * Converts HSL values to a Compose Color.
 */
private fun hslToColor(
    hue: Float,
    saturation: Float,
    lightness: Float,
): Color {
    val c = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
    val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val m = lightness - c / 2f

    val (r, g, b) =
        when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
    )
}

/**
 * Maps TMDB genre IDs to genre names.
 */
private fun getGenreName(genreId: Int?): String =
    when (genreId) {
        28 -> "Action"
        12 -> "Adventure"
        16 -> "Animation"
        35 -> "Comedy"
        80 -> "Crime"
        99 -> "Documentary"
        18 -> "Drama"
        10751 -> "Family"
        14 -> "Fantasy"
        36 -> "History"
        27 -> "Horror"
        10402 -> "Music"
        9648 -> "Mystery"
        10749 -> "Romance"
        878 -> "Sci-Fi"
        10770 -> "TV Movie"
        53 -> "Thriller"
        10752 -> "War"
        37 -> "Western"
        else -> "Movie"
    }
