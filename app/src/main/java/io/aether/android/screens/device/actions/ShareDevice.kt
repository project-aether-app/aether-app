// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.actions

import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningWindow
import com.google.android.gms.home.matter.commissioning.ShareDeviceRequest
import com.google.android.gms.home.matter.common.DeviceDescriptor
import com.google.android.gms.home.matter.common.Discriminator
import io.aether.android.DISCRIMINATOR
import io.aether.android.OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS
import io.aether.android.R
import io.aether.android.SETUP_PIN_CODE
import timber.log.Timber

@Composable
internal fun ShareDeviceConfirmationDialog(
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
  AlertDialog(
      title = { Text(stringResource(R.string.device_share_dialog_title)) },
      text = { Text(stringResource(R.string.device_share_dialog_body)) },
      confirmButton = {
        Button(onClick = onConfirm) { Text(stringResource(R.string.device_share_dialog_yes)) }
      },
      onDismissRequest = onDismissRequest,
      dismissButton = {
        TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) }
      },
  )
}

internal fun shareDevice(
    context: Context,
    shareDeviceLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
    deviceName: String,
    onShareFailed: (title: String, error: String) -> Unit,
) {
  Timber.d("Starting device sharing")

  val shareDeviceRequest =
      ShareDeviceRequest.builder()
          .setDeviceDescriptor(DeviceDescriptor.builder().build())
          .setDeviceName(deviceName)
          .setCommissioningWindow(
              CommissioningWindow.builder()
                  .setDiscriminator(Discriminator.forLongValue(DISCRIMINATOR))
                  .setPasscode(SETUP_PIN_CODE)
                  .setWindowOpenMillis(SystemClock.elapsedRealtime())
                  .setDurationSeconds(OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS.toLong())
                  .build(),
          )
          .build()
  Timber.d("Sharing device discriminator=${shareDeviceRequest.commissioningWindow.discriminator}")

  Matter.getCommissioningClient(context)
      .shareDevice(shareDeviceRequest)
      .addOnSuccessListener { result ->
        Timber.d("Got sharing intent sender result=$result")
        shareDeviceLauncher.launch(IntentSenderRequest.Builder(result).build())
      }
      .addOnFailureListener { error ->
        Timber.e(error)
        onShareFailed(context.getString(R.string.device_share_dialog_failed), error.toString())
      }
}
