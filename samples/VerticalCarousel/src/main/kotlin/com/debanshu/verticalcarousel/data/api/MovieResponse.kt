package com.debanshu.verticalcarousel.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from TMDB /discover/movie endpoint.
 */
@Serializable
data class MovieResponse(
    val page: Int,
    val results: List<MovieDto>,
    @SerialName("total_pages") val totalPages: Int,
    @SerialName("total_results") val totalResults: Int,
)

/**
 * Movie data transfer object matching TMDB API response.
 */
@Serializable
data class MovieDto(
    val id: Int,
    val title: String,
    val overview: String,
    @SerialName("poster_path") val posterPath: String?,
    @SerialName("backdrop_path") val backdropPath: String?,
    @SerialName("vote_average") val voteAverage: Float,
    @SerialName("vote_count") val voteCount: Int,
    @SerialName("release_date") val releaseDate: String,
    @SerialName("genre_ids") val genreIds: List<Int>,
    val popularity: Float,
    val adult: Boolean,
    @SerialName("original_language") val originalLanguage: String,
    @SerialName("original_title") val originalTitle: String,
    val video: Boolean,
)

