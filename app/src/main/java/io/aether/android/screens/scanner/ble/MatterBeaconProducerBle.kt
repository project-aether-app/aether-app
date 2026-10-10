// SPDX-FileCopyrightText: 2023 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.scanner.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import android.os.ParcelUuid
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import io.aether.android.screens.scanner.MatterBeacon
import io.aether.android.screens.scanner.MatterBeaconInject
import io.aether.android.screens.scanner.MatterBeaconProducer
import io.aether.android.screens.scanner.Transport
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/** [MatterBeaconProducer] which emits Bluetooth LE beacons as they are discovered. */
class MatterBeaconProducerBle
@Inject
constructor(
    @param:MatterBeaconInject private val bluetoothLeScanner: BluetoothLeScanner?,
    @param:ApplicationContext private val context: Context,
) : MatterBeaconProducer {

  // ---------------------------------------------------------------------------
  // MatterBeaconProducer interface functions

  @SuppressLint("MissingPermission")
  override fun getBeaconsFlow(): Flow<MatterBeacon> = callbackFlow {
    val beaconEmittedTime = ConcurrentHashMap<MatterBeacon, Long>()

    val scanCallback =
        object : ScanCallback() {
          override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.toMatterBeaconOrNull()?.let { beacon ->
              val currentTime = SystemClock.elapsedRealtime()
              val shouldWeEmitItAgain =
                  currentTime - (beaconEmittedTime[beacon] ?: 0) > BEACON_EMITTING_DEBOUNCE_IN_MS
              if (shouldWeEmitItAgain) {
                beaconEmittedTime[beacon] = currentTime
                Timber.d("Emitting BLE beacon=$beacon")
                trySend(beacon)
              }
            }
          }
        }

    if (bluetoothLeScanner != null) {
      Timber.d("Starting BLE scan.")
      bluetoothLeScanner.startScan(
          listOf(
              ScanFilter.Builder()
                  .setServiceData(MATTER_UUID, byteArrayOf(0), byteArrayOf(0))
                  .build(),
          ),
          ScanSettings.Builder()
              .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
              .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
              .build(),
          scanCallback,
      )
    } else {
      Timber.d("BLE Scanner not available.")
    }

    awaitClose {
      if (bluetoothLeScanner == null) {
        Timber.d("BLE Scanner not available.")
        return@awaitClose
      }

      val bluetoothAdapter: BluetoothAdapter =
          (context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
      if (bluetoothAdapter.state == BluetoothAdapter.STATE_ON) {
        Timber.d("Stopping BLE scan.")
        bluetoothLeScanner.stopScan(scanCallback)
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Utility functions

  private fun ScanResult.toMatterBeaconOrNull(): MatterBeacon? {
    val data = scanRecord?.bytes ?: return null
    // Full record must be at least 14 bytes.
    if (data.size < 14) {
      Timber.d("Dropping BLE ad recordLength=${data.size} expectedLength=14")
      return null
    }

    // Data payload length is byte 4 and should be exactly 10.
    val dataLength = data[3].toInt()
    if (dataLength < 10) {
      Timber.w("Dropping BLE ad dataLength=$dataLength expectedMinimum=10")
      return null
    }

    return MatterBeacon(
        name = device.address,
        vendorId = VendorId(((data[10].toInt() or (data[11].toInt() shl 8)) and 0xFFFF).toUShort()),
        productId =
            ProductId(((data[12].toInt() or (data[13].toInt() shl 8)) and 0xFFFF).toUShort()),
        discriminator = ((data[8].toInt() or (data[9].toInt() shl 8)) and 0xFFF),
        Transport.Ble(device.address),
    )
  }

  // ---------------------------------------------------------------------------
  // Companion

  companion object {
    private val MATTER_UUID = ParcelUuid.fromString("0000FFF6-0000-1000-8000-00805F9B34FB")
    private const val BEACON_EMITTING_DEBOUNCE_IN_MS = 1000
  }
}
