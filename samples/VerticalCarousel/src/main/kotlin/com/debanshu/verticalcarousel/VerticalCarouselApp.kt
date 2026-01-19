package com.debanshu.verticalcarousel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.debanshu.verticalcarousel.ui.VerticalCarousel
import com.debanshu.verticalcarousel.ui.theme.CarouselTheme
import com.debanshu.verticalcarousel.viewmodel.MovieUiState
import com.debanshu.verticalcarousel.viewmodel.MovieViewModel

/**
 * Main app composable for the Vertical Carousel sample.
 *
 * A minimal, immersive carousel experience where movie posters
 * are center-aligned and scale based on their distance from center,
 * similar to an iOS alarm clock picker.
 */
@Composable
fun VerticalCarouselApp(viewModel: MovieViewModel = viewModel { MovieViewModel() }) {
    val uiState by viewModel.uiState.collectAsState()

    CarouselTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color(0xFF0D0D0D),
                                        Color(0xFF1A1A1A),
                                        Color(0xFF0D0D0D),
                                    ),
                            ),
                    ),
        ) {
            when (val state = uiState) {
                is MovieUiState.Loading -> {
                    LoadingContent()
                }

                is MovieUiState.Success -> {
                    VerticalCarousel(
                        movies = state.movies,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is MovieUiState.Error -> {
                    Text("Error ${state.message}")
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color(0xFFE94560),
                strokeWidth = 3.dp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading movies...",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

