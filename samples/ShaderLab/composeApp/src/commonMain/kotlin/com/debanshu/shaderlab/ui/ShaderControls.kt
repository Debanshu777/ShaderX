package com.debanshu.shaderlab.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.ColorParameter
import com.debanshu.shaderlab.shaderx.parameter.ParameterFormatter
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.ToggleParameter
import com.debanshu.shaderlab.viewmodel.ShaderLabTab

private val colorPresets =
    listOf(
        0xFF7B2FF7,
        0xFF00D4FF,
        0xFFFF5733,
        0xFF33FF57,
        0xFFFFD700,
        0xFFFF1493,
        0xFF1E90FF,
        0xFFFFFFFF,
    )

@Composable
fun ShaderControls(
    tabEffects: List<ShaderEffect>,
    activeEffect: ShaderEffect?,
    selectedTab: ShaderLabTab,
    onTabSelected: (ShaderLabTab) -> Unit,
    onEffectSelected: (ShaderEffect) -> Unit,
    onParameterChanged: (String, Float) -> Unit,
    onColorParameterChanged: (String, Long) -> Unit,
    onClearEffect: () -> Unit,
    onToggleAnimation: () -> Unit = {},
    sidePanel: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .then(if (sidePanel) Modifier.fillMaxHeight() else Modifier)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(if (sidePanel) 0.dp else 24.dp),
                )
                .padding(vertical = 16.dp)
                .animateContentSize(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Shader Effects",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (activeEffect != null && selectedTab != ShaderLabTab.COMPOSITE) {
                IconButton(
                    onClick = onClearEffect,
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear effect",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ShaderLabTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = {
                        Text(
                            text =
                                when (tab) {
                                    ShaderLabTab.STATIC -> "Static"
                                    ShaderLabTab.ANIMATED -> "Animated"
                                    ShaderLabTab.COMPOSITE -> "Composite"
                                    ShaderLabTab.AUDIO -> "Audio"
                                },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            ShaderLabTab.COMPOSITE -> {
                CompositeTabPlaceholder(modifier = Modifier.padding(horizontal = 16.dp))
            }

            else -> {
                if (sidePanel) {
                    EffectChipColumn(
                        effects = tabEffects,
                        activeEffect = activeEffect,
                        onEffectSelected = onEffectSelected,
                    )
                } else {
                    EffectChipRow(
                        effects = tabEffects,
                        activeEffect = activeEffect,
                        onEffectSelected = onEffectSelected,
                    )
                }

                val animatedEffect = activeEffect as? AnimatedShaderEffect
                val speedParamId = animatedEffect?.primarySpeedParameterId()

                if (selectedTab == ShaderLabTab.ANIMATED && animatedEffect != null) {
                    AnimationControls(
                        effect = animatedEffect,
                        onToggleAnimation = onToggleAnimation,
                        onParameterChanged = onParameterChanged,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                if (activeEffect != null) {
                    val adjustableParameters =
                        activeEffect.parameters.filter { parameter ->
                            when {
                                selectedTab != ShaderLabTab.ANIMATED -> true
                                parameter.id == "animate" -> false
                                parameter.id == speedParamId -> false
                                else -> true
                            }
                        }
                    if (adjustableParameters.isNotEmpty()) {
                        if (sidePanel) {
                            LazyColumn(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                contentPadding =
                                    PaddingValues(
                                        top = 16.dp,
                                        start = 16.dp,
                                        end = 16.dp,
                                    ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(adjustableParameters, key = { it.id }) { parameter ->
                                    EffectParameterControl(
                                        effect = activeEffect,
                                        parameter = parameter,
                                        onValueChange = { value ->
                                            onParameterChanged(
                                                parameter.id,
                                                value,
                                            )
                                        },
                                        onColorChange = { color ->
                                            onColorParameterChanged(
                                                parameter.id,
                                                color,
                                            )
                                        },
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                            ) {
                                adjustableParameters.forEachIndexed { index, parameter ->
                                    EffectParameterControl(
                                        effect = activeEffect,
                                        parameter = parameter,
                                        onValueChange = { value ->
                                            onParameterChanged(
                                                parameter.id,
                                                value,
                                            )
                                        },
                                        onColorChange = { color ->
                                            onColorParameterChanged(
                                                parameter.id,
                                                color,
                                            )
                                        },
                                    )
                                    if (index < adjustableParameters.lastIndex) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No adjustable parameters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun AnimatedShaderEffect.primarySpeedParameterId(): String? =
    parameters.find { it.id == "speed" }?.id
        ?: parameters.find { it.id == "frequency" }?.id

@Composable
private fun CompositeTabPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Composite chaining",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                "Combine multiple effects with the + operator in code. " +
                    "Multi-effect composites are supported on Android API 33+.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AnimationControls(
    effect: AnimatedShaderEffect,
    onToggleAnimation: () -> Unit,
    onParameterChanged: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAnimating = effect.isAnimating
    val speedParam =
        effect.parameters.find { it.id == "speed" }
            ?: effect.parameters.find { it.id == "frequency" }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Animation",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isAnimating) "Playing" else "Paused",
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onToggleAnimation) {
                Icon(
                    imageVector = if (isAnimating) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isAnimating) "Pause animation" else "Play animation",
                )
            }
        }
        if (speedParam != null) {
            Spacer(modifier = Modifier.height(4.dp))
            SliderControl(
                label = speedParam.label,
                value = effect.getParameterValue(speedParam.id),
                valueRange = speedParam.range,
                onValueChange = { value -> onParameterChanged(speedParam.id, value) },
                formatValue = { ParameterFormatter.format(speedParam, it) },
            )
        }
    }
}

@Composable
private fun EffectParameterControl(
    effect: ShaderEffect,
    parameter: ParameterSpec,
    onValueChange: (Float) -> Unit,
    onColorChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (parameter) {
        is ToggleParameter -> {
            ToggleControl(
                label = parameter.label,
                isEnabled = effect.getParameterValue(parameter.id) > 0.5f,
                onToggle = { enabled -> onValueChange(if (enabled) 1f else 0f) },
                modifier = modifier,
            )
        }

        is ColorParameter -> {
            val currentColor =
                (effect.getTypedParameterValue(parameter.id) as? ParameterValue.ColorValue)?.color
                    ?: parameter.defaultColor
            ColorParameterControl(
                label = parameter.label,
                colorArgb = currentColor,
                onColorChange = onColorChange,
                modifier = modifier,
            )
        }

        else -> {
            SliderControl(
                label = parameter.label,
                value = effect.getParameterValue(parameter.id),
                valueRange = parameter.range,
                onValueChange = onValueChange,
                formatValue = { ParameterFormatter.format(parameter, it) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun ColorParameterControl(
    label: String,
    colorArgb: Long,
    onColorChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    val currentIndex = colorPresets.indexOf(colorArgb)
                    val nextIndex =
                        if (currentIndex >= 0) (currentIndex + 1) % colorPresets.size else 0
                    onColorChange(colorPresets[nextIndex])
                }.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = ParameterFormatter.formatColor(colorArgb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(colorArgb))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                        ),
            )
        }
    }
}

@Composable
private fun SliderControl(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    formatValue: (Float) -> String,
    modifier: Modifier = Modifier,
) {
    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatValue(coercedValue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = coercedValue,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors =
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        )
    }
}

@Composable
private fun ToggleControl(
    label: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                ),
        )
    }
}

@Composable
private fun EffectChipRow(
    effects: List<ShaderEffect>,
    activeEffect: ShaderEffect?,
    onEffectSelected: (ShaderEffect) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(effects, key = { it.id }) { effect ->
            EffectChip(
                effect = effect,
                isSelected = activeEffect?.id == effect.id,
                onClick = { onEffectSelected(effect) },
            )
        }
    }
}

@Composable
private fun EffectChipColumn(
    effects: List<ShaderEffect>,
    activeEffect: ShaderEffect?,
    onEffectSelected: (ShaderEffect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        effects.forEach { effect ->
            EffectChip(
                effect = effect,
                isSelected = activeEffect?.id == effect.id,
                onClick = { onEffectSelected(effect) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun EffectChip(
    effect: ShaderEffect,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    val borderColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        }

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(backgroundColor)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(20.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = effect.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}
