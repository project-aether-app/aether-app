// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.aether.android.R

@Composable
fun SearchTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: @Composable (() -> Unit),
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    trailingIcon = {
      if (value.isNotEmpty()) {
        IconButton(onClick = { onValueChange("") }) {
          Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.device_explorer_search_clear),
          )
        }
      }
    },
    modifier = Modifier.fillMaxWidth(),
  )
}
