// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.aether.android.R
import io.aether.android.matter.DataType
import io.aether.android.matter.isNumeric
import io.aether.android.screens.common.HighlightedOutlinedComboField
import io.aether.android.screens.common.HighlightedOutlinedComboOption
import io.aether.android.screens.common.HighlightedOutlinedTextField

@Composable
internal fun ExplorerTypedValueField(
  value: String,
  onValueChange: (String) -> Unit,
  type: DataType,
  successTrigger: Int,
  resetKey: Any,
  label: @Composable (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  when {
    type == DataType.BOOLEAN ->
      HighlightedOutlinedComboField(
        value = value,
        onValueChange = onValueChange,
        options =
          listOf(
            HighlightedOutlinedComboOption(
              value = "true",
              label = stringResource(R.string.device_explorer_boolean_true),
            ),
            HighlightedOutlinedComboOption(
              value = "false",
              label = stringResource(R.string.device_explorer_boolean_false),
            ),
          ),
        successTrigger = successTrigger,
        resetKey = resetKey,
        label = label,
        modifier = modifier.fillMaxWidth(),
      )
    type.isNumeric() ->
      HighlightedOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        successTrigger = successTrigger,
        resetKey = resetKey,
        label = label,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      )
    else ->
      HighlightedOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        successTrigger = successTrigger,
        resetKey = resetKey,
        label = label,
        modifier = modifier.fillMaxWidth(),
      )
  }
}
