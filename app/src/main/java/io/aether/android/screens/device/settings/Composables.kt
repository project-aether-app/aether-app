// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.aether.android.screens.common.GroupBox
import io.aether.android.screens.common.LabeledContent

@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  GroupBox(title = title, content = content)
}

@Composable
internal fun SettingsInfoRow(
  label: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) =
  LabeledContent(
    label = label,
    modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
  ) {
    CompositionLocalProvider(
      LocalTextStyle provides MaterialTheme.typography.bodyMedium,
      content = content,
    )
  }

@Composable
internal fun SettingsClickableRow(label: String, value: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    LabeledContent(label = label, modifier = Modifier.weight(1f)) {
      Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
internal fun SettingsActionRow(
  icon: ImageVector,
  label: String,
  subtitle: String,
  onClick: () -> Unit,
  iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  labelColor: Color = MaterialTheme.colorScheme.onSurface,
  subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = iconTint,
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(text = label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = subtitleColor,
      )
    }
  }
}
