// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import io.aether.android.R
import io.aether.android.spacing

@Composable
internal fun CommandInvokeContent(
  command: ExplorerCommandUiItem,
  invokeSuccessCount: Int,
  onInvoke: (Map<String, String>) -> Unit,
) {
  val commandArguments =
    remember(command.id) {
      mutableStateMapOf<String, String>().also { map ->
        command.arguments.forEach { map[it.key] = "" }
      }
    }

  Column(
    modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.paddingNormal),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
  ) {
    val focusManager = LocalFocusManager.current
    if (command.arguments.isNotEmpty()) {
      command.arguments.forEach { argument ->
        ExplorerTypedValueField(
          value = commandArguments[argument.key].orEmpty(),
          onValueChange = { commandArguments[argument.key] = it },
          type = argument.type,
          successTrigger = invokeSuccessCount,
          resetKey = command.id,
          label = {
            Text(
              stringResource(
                R.string.device_explorer_label_with_type,
                argument.name,
                argument.type.label,
              ),
            )
          },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    Button(
      onClick = {
        focusManager.clearFocus()
        onInvoke(commandArguments.toMap())
      },
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(stringResource(R.string.device_explorer_invoke))
    }
  }
}
