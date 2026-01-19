package com.debanshu.verticalcarousel.data.api

import com.debanshu.verticalcarousel.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

/**
 * API client for TheMovieDB API.
 * Fetches popular movies using the discover endpoint.
 */
class MovieApi {
    // Use HttpClient with OkHttp engine for Android
    private val client: HttpClient = createHttpClient()

    /**
     * Fetches popular movies from TMDB.
     *
     * @param page Page number (1-indexed)
     * @return MovieResponse containing list of movies
     */
    suspend fun discoverMovies(page: Int = 1): MovieResponse =
        client
            .get("$BASE_URL/discover/movie") {
                url {
                    parameters.append("include_adult", "false")
                    parameters.append("include_video", "false")
                    parameters.append("language", "en-US")
                    parameters.append("page", page.toString())
                    parameters.append("sort_by", "popularity.desc")
                }
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.Authorization, "Bearer ${BuildConfig.TMDB_ACCESS_TOKEN}")
            }.body()

    companion object {
        private const val BASE_URL = "https://api.themoviedb.org/3"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"
    }
}
