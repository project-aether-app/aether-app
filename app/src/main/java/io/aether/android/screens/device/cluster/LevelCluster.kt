// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.cluster

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.aether.android.spacing

/**
 * Displays a labeled slider that controls a single Level-type cluster attribute (brightness or
 * colour temperature). [level] is normalised to `0f..1f`; the caller converts to/from raw cluster
 * values.
 */
@Composable
internal fun LevelClusterControl(
  title: String,
  isOnline: Boolean,
  isOn: Boolean,
  level: Float,
  onLevelChange: (Float) -> Unit,
  onLevelChangeFinished: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(MaterialTheme.spacing.roundedCorner),
  ) {
    Column(modifier = Modifier.padding(MaterialTheme.spacing.paddingSurfaceContent)) {
      Text(text = title)
      Slider(
        enabled = isOnline && isOn,
        value = level,
        onValueChange = onLevelChange,
        onValueChangeFinished = onLevelChangeFinished,
        valueRange = 0f..1f,
      )
      Text(
        text = (level * 100).toInt().toString(),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
