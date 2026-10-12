// SPDX-FileCopyrightText: 2023 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.scanner

import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import java.util.*

/**
 * Representation of a single Matter device beacon.
 *
 * @param name a name used to represent the beaconing device
 * @param vendorId the vendor ID of the beaconing device, or zero if not indicated
 * @param productId the product ID unique to the vendor ID, if present, or zero if not indicated
 * @param discriminator the semi-unique discriminator used to identify this beaconing device
 * @param transport the transport information on which this device was discovered
 */
data class MatterBeacon(
  val name: String,
  val vendorId: VendorId,
  val productId: ProductId,
  val discriminator: Int,
  val transport: Transport,
) {
  override fun toString(): String {
    return String.format(
      Locale.ROOT,
      "MatterBeacon([%s] VID=%04X, PID=%04X, Discriminator=%03X, Transport=%s",
      name,
      vendorId.toInt(),
      productId.toInt(),
      discriminator,
      transport,
    )
  }
}

/** Sealed enumeration of supported transports for Matter beacon discovery. */
sealed class Transport {
  /**
   * Bluetooth LE transport.
   *
   * @param address the Bluetooth address of the device which was discovered
   */
  data class Ble(val address: String) : Transport()

  /**
   * Wi-Fi hotspot transport.
   *
   * @param ssid the Wi-Fi SSID of the device which was discovered
   */
  data class Hotspot(val ssid: String) : Transport()

  /**
   * mDNS transport.
   *
   * @param ipAddress the IP address of the device which was discovered
   * @param port the port at which the service can be reached
   * @param port whether the service is active or not
   */
  data class Mdns(val ipAddress: String, val port: Int, val active: Boolean) : Transport()
}
