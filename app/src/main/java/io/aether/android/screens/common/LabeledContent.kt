// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import io.aether.android.spacing

@Composable
fun LabeledContent(
  label: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier.semantics(mergeDescendants = true) {},
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingTiny),
  ) {
    CompositionLocalProvider(
      LocalTextStyle provides MaterialTheme.typography.labelSmall,
      LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
      label()
    }
    content()
  }
}

/** Convenience overload for the most common "just a string" case */
@Composable
fun LabeledContent(
  label: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  LabeledContent(
    label = { Text(label) },
    modifier = modifier,
    content = content,
  )
}
