// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.aether.android.R

@Composable
fun OfflineLabel(modifier: Modifier = Modifier) {
  Text(
      text = stringResource(R.string.device_offline_label),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
      textAlign = TextAlign.Center,
      modifier = modifier.fillMaxWidth(),
  )
}
