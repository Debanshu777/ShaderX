package com.debanshu.verticalcarousel.ui

import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.debanshu.verticalcarousel.data.Movie
import kotlin.math.abs

@Composable
fun VerticalCarousel(
    movies: List<Movie>,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 450.dp,
    onCenterMovieChanged: (Movie?) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val itemSpacing = 60.dp
    val itemSpacingPx = with(density) { itemSpacing.toPx() }

    var scrollVelocity by remember { mutableFloatStateOf(0f) }
    var previousScrollOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(listState, movies) {
        snapshotFlow {
            findCenterMovie(listState, movies)
        }.collect { centerMovie ->
            onCenterMovieChanged(centerMovie)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex * (itemHeightPx + itemSpacingPx) +
                listState.firstVisibleItemScrollOffset
        }.collect { currentOffset ->
            scrollVelocity = (currentOffset - previousScrollOffset).coerceIn(-100f, 100f)
            previousScrollOffset = currentOffset
        }
    }

    LaunchedEffect(scrollVelocity) {
        if (abs(scrollVelocity) > 0.1f) {
            kotlinx.coroutines.delay(16)
            scrollVelocity *= 0.85f
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val viewportHeight = constraints.maxHeight
        val verticalPadding =
            with(density) {
                ((viewportHeight - itemHeightPx) / 2f).toDp()
            }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
            flingBehavior = rememberSnapFlingBehavior(listState),
        ) {
            itemsIndexed(
                items = movies,
                key = { _, movie -> movie.id },
            ) { index, movie ->
                val distanceFromCenter by remember {
                    derivedStateOf {
                        calculateDistanceFromCenter(
                            listState = listState,
                            itemIndex = index,
                        )
                    }
                }

                val scrollDirection by remember {
                    derivedStateOf {
                        calculateScrollDirection(
                            listState = listState,
                            itemIndex = index,
                        )
                    }
                }

                MoviePosterCard(
                    movie = movie,
                    distanceFromCenter = distanceFromCenter,
                    scrollVelocity = scrollVelocity,
                    scrollDirection = scrollDirection,
                    modifier =
                        Modifier
                            .fillMaxWidth(0.75f)
                            .height(itemHeight)
                            .clipScrollableContainer(Orientation.Vertical),
                )
            }
        }
    }
}

/**
 * Holds computed viewport metrics to avoid redundant calculations.
 */
private data class ViewportMetrics(
    val viewportCenter: Float,
    val viewportHeight: Int,
    val maxDistance: Float,
)

private fun LazyListState.getViewportMetrics(): ViewportMetrics {
    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    val viewportHeight = viewportEnd - viewportStart
    return ViewportMetrics(
        viewportCenter = (viewportStart + viewportEnd) / 2f,
        viewportHeight = viewportHeight,
        maxDistance = viewportHeight / 2.5f,
    )
}

private fun calculateDistanceFromCenter(
    listState: LazyListState,
    itemIndex: Int,
): Float {
    val metrics = listState.getViewportMetrics()
    val itemInfo =
        listState.layoutInfo.visibleItemsInfo.find { it.index == itemIndex }
            ?: return 1f

    val itemCenter = itemInfo.offset + (itemInfo.size / 2f)
    val distanceFromCenter = abs(itemCenter - metrics.viewportCenter)
    return (distanceFromCenter / metrics.maxDistance).coerceIn(0f, 1f)
}

private fun calculateScrollDirection(
    listState: LazyListState,
    itemIndex: Int,
): Float {
    val metrics = listState.getViewportMetrics()
    val itemInfo =
        listState.layoutInfo.visibleItemsInfo.find { it.index == itemIndex }
            ?: return 0f

    val itemCenter = itemInfo.offset + (itemInfo.size / 2f)
    val signedDistance = itemCenter - metrics.viewportCenter
    return (signedDistance / metrics.maxDistance).coerceIn(-1f, 1f)
}

private fun findCenterMovie(
    listState: LazyListState,
    movies: List<Movie>,
): Movie? {
    val layoutInfo = listState.layoutInfo
    if (layoutInfo.visibleItemsInfo.isEmpty()) return movies.firstOrNull()

    val metrics = listState.getViewportMetrics()
    val centerItem =
        layoutInfo.visibleItemsInfo.minByOrNull { itemInfo ->
            val itemCenter = itemInfo.offset + (itemInfo.size / 2f)
            abs(itemCenter - metrics.viewportCenter)
        }

    return centerItem?.let { movies.getOrNull(it.index) }
}
