package com.debanshu.verticalcarousel.data

import androidx.compose.ui.graphics.Color

/**
 * Represents a movie in the carousel.
 *
 * @property id Unique identifier
 * @property title Movie title
 * @property subtitle Tagline or year
 * @property rating IMDB-style rating
 * @property gradientColors Gradient colors for the poster background
 * @property posterUrl URL to the movie poster image (optional)
 */
data class Movie(
    val id: Int,
    val title: String,
    val subtitle: String,
    val rating: Float,
    val gradientColors: List<Color>,
    val posterUrl: String? = null,
)

/**
 * Sample movie data for the carousel demo.
 * Uses gradient backgrounds to simulate movie posters.
 */
val sampleMovies = listOf(
    Movie(
        id = 1,
        title = "Blade Runner 2049",
        subtitle = "2017 / Sci-Fi",
        rating = 8.0f,
        gradientColors = listOf(
            Color(0xFF1A1A2E),
            Color(0xFF16213E),
            Color(0xFFE94560),
        ),
    ),
    Movie(
        id = 2,
        title = "Interstellar",
        subtitle = "2014 / Adventure",
        rating = 8.7f,
        gradientColors = listOf(
            Color(0xFF0F0F23),
            Color(0xFF1E3A5F),
            Color(0xFF3D5A80),
        ),
    ),
    Movie(
        id = 3,
        title = "The Grand Budapest Hotel",
        subtitle = "2014 / Comedy",
        rating = 8.1f,
        gradientColors = listOf(
            Color(0xFFE8B4BC),
            Color(0xFFD4838F),
            Color(0xFF7D4E57),
        ),
    ),
    Movie(
        id = 4,
        title = "Mad Max: Fury Road",
        subtitle = "2015 / Action",
        rating = 8.1f,
        gradientColors = listOf(
            Color(0xFFFF6B35),
            Color(0xFFD64933),
            Color(0xFF2C1810),
        ),
    ),
    Movie(
        id = 5,
        title = "Arrival",
        subtitle = "2016 / Drama",
        rating = 7.9f,
        gradientColors = listOf(
            Color(0xFF4A5568),
            Color(0xFF2D3748),
            Color(0xFF1A202C),
        ),
    ),
    Movie(
        id = 6,
        title = "Dune",
        subtitle = "2021 / Sci-Fi",
        rating = 8.0f,
        gradientColors = listOf(
            Color(0xFFD4A574),
            Color(0xFF8B6914),
            Color(0xFF2D2416),
        ),
    ),
    Movie(
        id = 7,
        title = "Everything Everywhere",
        subtitle = "2022 / Adventure",
        rating = 7.8f,
        gradientColors = listOf(
            Color(0xFF667EEA),
            Color(0xFF764BA2),
            Color(0xFFFF6B6B),
        ),
    ),
    Movie(
        id = 8,
        title = "Parasite",
        subtitle = "2019 / Thriller",
        rating = 8.5f,
        gradientColors = listOf(
            Color(0xFF2D5016),
            Color(0xFF1A3409),
            Color(0xFF0D1B05),
        ),
    ),
    Movie(
        id = 9,
        title = "La La Land",
        subtitle = "2016 / Musical",
        rating = 8.0f,
        gradientColors = listOf(
            Color(0xFF1E3A8A),
            Color(0xFF7C3AED),
            Color(0xFFFBBF24),
        ),
    ),
    Movie(
        id = 10,
        title = "Oppenheimer",
        subtitle = "2023 / Biography",
        rating = 8.4f,
        gradientColors = listOf(
            Color(0xFFFF4500),
            Color(0xFF8B0000),
            Color(0xFF1A1A1A),
        ),
    ),
)

