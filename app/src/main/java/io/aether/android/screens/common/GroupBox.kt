// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.aether.android.spacing

@Composable
fun GroupBox(
  title: String,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(MaterialTheme.spacing.roundedCorner)
  val labelFontSize = MaterialTheme.typography.labelMedium.fontSize
  val labelOffsetY = with(LocalDensity.current) { -(labelFontSize.toDp() / 2) }
  Box(
    modifier =
      modifier.padding(top = MaterialTheme.spacing.paddingSmall).semantics(
        mergeDescendants = true,
      ) {
        contentDescription = title
      },
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
          .padding(MaterialTheme.spacing.paddingNormal),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingSmall),
      content = content,
    )
    Text(
      text = title,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.primary,
      modifier =
        Modifier.align(Alignment.TopStart)
          .offset(x = MaterialTheme.spacing.paddingNormal, y = labelOffsetY)
          .background(MaterialTheme.colorScheme.surface)
          .padding(horizontal = 4.dp),
    )
  }
}
