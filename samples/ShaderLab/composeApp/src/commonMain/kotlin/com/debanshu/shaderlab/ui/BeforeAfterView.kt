package com.debanshu.shaderlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.ui.components.ShaderLabImage
import com.debanshu.shaderlab.viewmodel.ImageSource
import kotlin.math.roundToInt

@Composable
fun BeforeAfterView(
    imageSource: ImageSource,
    effect: ShaderEffect?,
    factory: ShaderFactory,
    modifier: Modifier = Modifier,
) {
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }
    var dividerPosition by remember { mutableFloatStateOf(0.5f) }
    var shaderError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(effect?.id, effect?.hashCode()) {
        shaderError = null
    }

    val afterModifier =
        if (effect != null && factory.isSupported()) {
            Modifier.shaderEffect(
                effect = effect,
                factory = factory,
                onError = { error -> shaderError = error.message },
            )
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onSizeChanged { size ->
                    containerWidth = size.width.toFloat()
                    containerHeight = size.height.toFloat()
                },
    ) {
        ShaderLabImage(
            imageSource = imageSource,
            modifier = Modifier.fillMaxSize().then(afterModifier),
            contentDescription = "After - with effect",
            contentScale = ContentScale.Fit,
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(
                        GenericShape { size, _ ->
                            addRect(Rect(0f, 0f, size.width * dividerPosition, size.height))
                        },
                    ),
        ) {
            ShaderLabImage(
                imageSource = imageSource,
                modifier = Modifier.fillMaxSize(),
                contentDescription = "Before - original",
                contentScale = ContentScale.Fit,
            )
        }

        Box(
            modifier =
                Modifier
                    .offset { IntOffset((containerWidth * dividerPosition).roundToInt() - 2, 0) }
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val newPosition = dividerPosition + (dragAmount / containerWidth)
                            dividerPosition = newPosition.coerceIn(0.05f, 0.95f)
                        }
                    },
        )

        Box(
            modifier =
                Modifier
                    .offset {
                        IntOffset(
                            (containerWidth * dividerPosition).roundToInt() - 36,
                            (containerHeight / 2).roundToInt() - 24,
                        )
                    }.width(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp),
                    ).padding(vertical = 12.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val newPosition = dividerPosition + (dragAmount / containerWidth)
                            dividerPosition = newPosition.coerceIn(0.05f, 0.95f)
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "⟷",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                    ).padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Before",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp),
                    ).padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "After",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        shaderError?.let { message ->
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(8.dp),
                        ).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
