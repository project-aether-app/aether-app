// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.aether.android.spacing

@Composable
fun WarningSurfaceBanner(
  message: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
) {
  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = MaterialTheme.spacing.paddingSmall)
        .padding(top = MaterialTheme.spacing.paddingSmall),
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    shape = RoundedCornerShape(MaterialTheme.spacing.roundedCorner),
  ) {
    Row(
      modifier = Modifier.padding(MaterialTheme.spacing.paddingSurfaceContent),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingSmall),
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.size(24.dp),
        )
      }
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
fun EmptyState(
  message: String,
  modifier: Modifier = Modifier,
  icon: ImageVector = Icons.Default.Info,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun EmptyStateBanner(
  message: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  iconColor: Color = MaterialTheme.colorScheme.secondary,
  textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = iconColor,
      modifier = Modifier.size(40.dp),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = textColor,
    )
  }
}

@Composable
fun EmptyStateErrorBanner(message: String, modifier: Modifier = Modifier) =
  EmptyStateBanner(
    message = message,
    icon = Icons.Default.Warning,
    modifier = modifier,
    iconColor = MaterialTheme.colorScheme.error,
    textColor = MaterialTheme.colorScheme.error,
  )
