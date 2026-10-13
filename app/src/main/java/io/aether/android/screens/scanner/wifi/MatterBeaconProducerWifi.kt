// SPDX-FileCopyrightText: 2023 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.scanner.wifi

import android.annotation.SuppressLint
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import io.aether.android.screens.scanner.MatterBeacon
import io.aether.android.screens.scanner.MatterBeaconInject
import io.aether.android.screens.scanner.MatterBeaconProducer
import io.aether.android.screens.scanner.Transport
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import timber.log.Timber

private val SCAN_QUERY_DELAY = 30.seconds
// See section 5.4.2.6 "Using Wi-Fi Temporary Access Points (Soft-AP)" of the Matter Specification.
private val MATTER_SSID_PATTERN =
  """MATTER-(\p{XDigit}{3})-(\p{XDigit}{4})-(\p{XDigit}{4})""".toRegex()

/**
 * [MatterBeaconProducer] that looks for Wi-Fi Soft AP advertisements matching a Matter scanner
 * device.
 *
 * See these links for important details on Wi-Fi scanning.
 * - https://developer.android.com/guide/topics/connectivity/wifi-scan
 * - https://stackoverflow.com/questions/56401057/wifimanager-startscan-deprecated-alternative
 */
class MatterBeaconProducerWifi
@Inject
constructor(@param:MatterBeaconInject private val wifiManager: WifiManager) : MatterBeaconProducer {
  @SuppressLint("MissingPermission")
  override fun getBeaconsFlow(): Flow<MatterBeacon> = flow {
    while (coroutineContext.isActive) {
      val scanResults = wifiManager.scanResults
      Timber.d("WiFi scan results=${scanResults.size}")
      scanResults
        .orEmpty()
        .mapNotNull { scanResult -> scanResult.toMatterBeaconOrNull() }
        .forEach { beacon ->
          Timber.d("Emitting Matter hotspot beacon=$beacon")
          emit(beacon)
        }

      requestScan()
      delay(SCAN_QUERY_DELAY)
    }
  }

  @Suppress("DEPRECATION") // Currently the only option to refresh scan results.
  private fun requestScan() {
    // This may stop working in a future Android release, but for now this allows us to refresh the
    // nearby Wi-Fi SSIDs.
    wifiManager.startScan()
  }
}

private fun ScanResult.toMatterBeaconOrNull(): MatterBeacon? {
  val ssid =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      wifiSsid?.toString().orEmpty().stripSurroundingQuotes()
    } else {
      @Suppress("DEPRECATION") SSID.stripSurroundingQuotes()
    }
  return MATTER_SSID_PATTERN.find(ssid)?.let { result ->
    val (discriminator, vid, pid) = result.destructured
    MatterBeacon(
      ssid,
      VendorId(vid.toInt(16).toUShort()),
      ProductId(pid.toInt(16).toUShort()),
      discriminator.toInt(16),
      Transport.Hotspot(ssid),
    )
  }
}

private fun String.stripSurroundingQuotes(): String {
  return if (length > 1 && startsWith("\"") && endsWith("\"")) {
    substring(1, length - 1)
  } else {
    this
  }
}
