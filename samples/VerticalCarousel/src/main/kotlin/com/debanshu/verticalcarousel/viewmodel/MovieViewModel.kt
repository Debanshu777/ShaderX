package com.debanshu.verticalcarousel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.debanshu.verticalcarousel.data.Movie
import com.debanshu.verticalcarousel.data.repository.MovieRepository
import com.debanshu.verticalcarousel.data.sampleMovies
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the movie carousel.
 */
sealed interface MovieUiState {
    data object Loading : MovieUiState
    data class Success(val movies: List<Movie>) : MovieUiState
    data class Error(val message: String, val fallbackMovies: List<Movie>) : MovieUiState
}

/**
 * ViewModel for managing movie data and UI state.
 * Fetches movies from TMDB API and falls back to sample data on error.
 */
class MovieViewModel(
    private val repository: MovieRepository = MovieRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<MovieUiState>(MovieUiState.Loading)
    val uiState: StateFlow<MovieUiState> = _uiState.asStateFlow()

    // Exception handler to prevent crashes from unhandled exceptions
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        println("MovieViewModel: Caught exception: ${throwable.message}")
        _uiState.value = MovieUiState.Error(
            message = throwable.message ?: "Unknown error",
            fallbackMovies = sampleMovies
        )
    }

    init {
        loadMovies()
    }

    /**
     * Loads popular movies from the API.
     */
    fun loadMovies() {
        viewModelScope.launch(exceptionHandler) {
            _uiState.value = MovieUiState.Loading

            try {
                repository.getPopularMovies()
                    .onSuccess { movies ->
                        _uiState.value = if (movies.isNotEmpty()) {
                            MovieUiState.Success(movies)
                        } else {
                            MovieUiState.Success(sampleMovies)
                        }
                    }
                    .onFailure { error ->
                        println("MovieViewModel: API failed: ${error.message}")
                        // Fall back to sample movies on error
                        _uiState.value = MovieUiState.Error(
                            message = error.message ?: "Failed to load movies",
                            fallbackMovies = sampleMovies
                        )
                    }
            } catch (e: Exception) {
                println("MovieViewModel: Exception caught: ${e.message}")
                _uiState.value = MovieUiState.Error(
                    message = e.message ?: "Failed to load movies",
                    fallbackMovies = sampleMovies
                )
            }
        }
    }

    /**
     * Retries loading movies after an error.
     */
    fun retry() {
        loadMovies()
    }
}

