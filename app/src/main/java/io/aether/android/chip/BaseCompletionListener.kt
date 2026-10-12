// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.chip

import chip.devicecontroller.ChipDeviceController
import timber.log.Timber

/**
 * ChipDeviceController uses a CompletionListener for callbacks. This is a "base" default
 * implementation for that CompletionListener.
 */
abstract class BaseCompletionListener : ChipDeviceController.CompletionListener {
  override fun onConnectDeviceComplete() {
    Timber.d("Device connection completed")
  }

  override fun onStatusUpdate(status: Int) {
    Timber.d("Connection status=$status")
  }

  override fun onPairingComplete(code: Int) {
    Timber.d("Pairing completed code=$code")
  }

  override fun onPairingDeleted(code: Int) {
    Timber.d("Pairing deleted code=$code")
  }

  override fun onCommissioningComplete(id: Long, errorCode: Int) {
    Timber.d("Commissioning completed nodeId=$id errorCode=$errorCode")
  }

  override fun onNotifyChipConnectionClosed() {
    Timber.d("Chip connection closed")
  }

  override fun onCloseBleComplete() {
    Timber.d("BLE close completed")
  }

  override fun onError(error: Throwable) {
    Timber.e(error, "Chip operation failed")
  }

  override fun onOpCSRGenerationComplete(csr: ByteArray) {
    Timber.d("CSR generation completed csr=$csr")
  }

  override fun onReadCommissioningInfo(
    vendorId: Int,
    productId: Int,
    wifiEndpointId: Int,
    threadEndpointId: Int,
  ) {
    Timber.d(
      "Commissioning info vendorId=$vendorId productId=$productId wifiEndpointId=$wifiEndpointId threadEndpointId=$threadEndpointId",
    )
  }

  override fun onCommissioningStatusUpdate(id: Long, stage: String?, errorCode: Int) {
    Timber.d("Commissioning status nodeId=$id stage=$stage errorCode=$errorCode")
  }
}
