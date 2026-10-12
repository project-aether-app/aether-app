// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.aether.android.screens.common.GroupBox
import io.aether.android.screens.common.LabeledContent

@Composable
internal fun DiagnosticsSection(title: String, content: @Composable ColumnScope.() -> Unit) =
  GroupBox(title = title, content = content)

@Composable
internal fun DiagnosticsInfoRow(
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
