// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import io.aether.android.R
import io.aether.android.spacing

@Composable
internal fun AttributeDetailContent(
    attribute: ExplorerAttributeUiItem,
    currentValue: String?,
    readSuccessCount: Int,
    writeSuccessCount: Int,
    onRead: () -> Unit,
    onWrite: (String) -> Unit,
) {
  LaunchedEffect(attribute.id) { onRead() }
  var editValue by remember(attribute.id) { mutableStateOf("") }
  LaunchedEffect(attribute.id, currentValue) {
    if (currentValue != null) {
      editValue = currentValue
    }
  }

  Column(
      modifier =
          Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(MaterialTheme.spacing.paddingNormal),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
  ) {
    ExplorerTypedValueField(
        value = editValue,
        onValueChange = { editValue = it },
        type = attribute.type,
        successTrigger = readSuccessCount + writeSuccessCount,
        resetKey = attribute.id,
        label = {
          Text(
              stringResource(
                  R.string.device_explorer_label_with_type,
                  stringResource(R.string.device_explorer_value),
                  attribute.type.label,
              ),
          )
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        text =
            stringResource(
                R.string.device_explorer_attribute_read_privilege,
                attribute.readPrivilege.toLabel(),
            ),
        style = MaterialTheme.typography.bodyMedium,
    )

    Text(
        text =
            stringResource(
                R.string.device_explorer_attribute_write_privilege,
                attribute.writePrivilege.toLabel(),
            ),
        style = MaterialTheme.typography.bodyMedium,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
    ) {
      val focusManager = LocalFocusManager.current
      Button(
          modifier = Modifier.weight(1f),
          onClick = {
            focusManager.clearFocus()
            onRead()
          },
      ) {
        Text(stringResource(R.string.device_explorer_read))
      }
      Button(
          modifier = Modifier.weight(1f),
          onClick = {
            onWrite(editValue)
            focusManager.clearFocus()
          },
      ) {
        Text(stringResource(R.string.device_explorer_write))
      }
    }
  }
}
