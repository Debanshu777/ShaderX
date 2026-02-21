package com.debanshu.asciicamera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.debanshu.asciicamera.viewmodel.ASCIICameraUiState
import com.debanshu.asciicamera.viewmodel.EffectMode

@Composable
fun EffectControls(
    state: ASCIICameraUiState,
    onEffectModeChange: (EffectMode) -> Unit,
    onCellSizeChange: (Float) -> Unit,
    onUseFourColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Text(
            text = "Effect",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            SegmentedButton(
                selected = state.effectMode == EffectMode.None,
                onClick = { onEffectModeChange(EffectMode.None) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            ) {
                Text("None")
            }
            SegmentedButton(
                selected = state.effectMode == EffectMode.AsciiArt,
                onClick = { onEffectModeChange(EffectMode.AsciiArt) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            ) {
                Text("ASCII")
            }
            SegmentedButton(
                selected = state.effectMode == EffectMode.ColorAsciiArt,
                onClick = { onEffectModeChange(EffectMode.ColorAsciiArt) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            ) {
                Text("Color")
            }
        }

        if (state.effectMode != EffectMode.None) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Cell size: ${state.cellSize.toInt()} px",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = state.cellSize,
                onValueChange = onCellSizeChange,
                valueRange = 4f..32f,
                steps = 27,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.effectMode == EffectMode.ColorAsciiArt) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "4-color (CMYK)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = state.useFourColor,
                    onCheckedChange = onUseFourColorChange,
                )
            }
        }
    }
}
