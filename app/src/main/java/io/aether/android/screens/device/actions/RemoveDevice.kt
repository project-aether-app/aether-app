// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.actions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aether.android.R

@Composable
internal fun RemoveDeviceConfirmationDialog(
  onConfirm: () -> Unit,
  onDismissRequest: () -> Unit,
) {
  AlertDialog(
    title = { Text(stringResource(R.string.device_remove_dialog_title)) },
    text = { Text(stringResource(R.string.device_remove_dialog_body)) },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
      ) {
        Text(stringResource(R.string.device_remove_dialog_yes))
      }
    },
    onDismissRequest = onDismissRequest,
    dismissButton = {
      TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) }
    },
  )
}

@Composable
internal fun ForceRemoveDeviceConfirmationDialog(
  onConfirm: () -> Unit,
  onDismissRequest: () -> Unit,
) {
  AlertDialog(
    title = { Text(stringResource(R.string.device_remove_failed_confirm_dialog_title)) },
    text = { Text(stringResource(R.string.device_remove_failed_confirm_dialog_body)) },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
      ) {
        Text(stringResource(R.string.device_remove_failed_confirm_dialog_yes))
      }
    },
    onDismissRequest = onDismissRequest,
    dismissButton = {
      TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) }
    },
  )
}
