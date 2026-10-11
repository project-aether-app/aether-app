// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data.models

import io.aether.android.matter.DEVICES
import io.aether.android.matter.DeviceTypeId
import io.aether.android.matter.Devices
import io.aether.android.matter.EndpointId
import io.aether.android.matter.NodeId
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import io.aether.android.matter.getDeviceTypeIconId
import java.time.Instant

/** A physical Matter device commissioned into the network. */
data class Device(
    val id: NodeId,
    val name: String = "",
    val vendor: Vendor = Vendor(),
    val product: Product = Product(),
    val dateCommissioned: Instant? = null,
    val isOnline: Boolean = false,
    // In a well designed Matter device the endpoint list is not empty.
    val endpoints: List<Endpoint> = emptyList(),
) {
  val primaryEndpoint: Endpoint
    get() = endpoints.minByOrNull { it.id.toInt() } ?: Endpoint(id = EndpointId(0u))

  /**
   * Returns the most occurring device type among all endpoints.
   * 1. Evaluates non-primary endpoints first.
   * 2. Ties are broken by prioritizing the type that appears first chronologically in non-primary
   *    endpoints.
   * 3. Falls back to any valid type on the primary node.
   * 4. Return the root device type if no other valid types are found.
   */
  val deviceType: DeviceType
    get() {
      // 1. Filter out primary endpoint and flatten all available types into an ordered sequence.
      val nonPrimaryTypes = endpoints.filter { it != primaryEndpoint }.flatMap { it.deviceTypes }

      if (nonPrimaryTypes.isNotEmpty()) {
        // Group with preserving the original insertion order of keys.
        val counts = nonPrimaryTypes.groupingBy { it }.eachCount()
        // Returns the first key that encounters the maximum value.
        return counts.maxByOrNull { it.value }?.key ?: error("Unexpected empty counts map!")
      }

      // 2. Fallback: Check the primary endpoint if non-primary had no types.
      val primaryFallbackType =
          primaryEndpoint.deviceTypes.firstOrNull { it.id != Devices.RootNode.ID }

      // 3. If the primary endpoint has no valid types, fall back to the root device type.
      return primaryFallbackType ?: DeviceType(Devices.RootNode.ID)
    }
}

data class Vendor(
    val id: VendorId = VendorId(0u),
    val name: String = "",
)

data class Product(
    val id: ProductId = ProductId(0u),
    val name: String = "",
)

data class DeviceType(
    val id: DeviceTypeId,
) {
  val name: String
    get() = DEVICES[id] ?: "Unknown (${id})"

  val iconId: Int
    get() = id.getDeviceTypeIconId()
}

/** An endpoint exposed by a physical Matter device. */
data class Endpoint(
    val id: EndpointId,
    val label: String = "",
    val deviceTypes: List<DeviceType> = listOf(DeviceType(Devices.RootNode.ID)),
    val supportsLevelControl: Boolean = false,
    val supportsColorTemperature: Boolean = false,
    val isOn: Boolean = false,
    val level: Int = 0,
    val colorTemperature: Int = 0,
) {
  val hasLevelControlSupport: Boolean
    get() =
        supportsLevelControl ||
            deviceTypes.any {
              it.id == Devices.DimmableLight.ID ||
                  it.id == Devices.ColorTemperatureLight.ID ||
                  it.id == Devices.ExtendedColorLight.ID
            }

  val hasColorTemperatureSupport: Boolean
    get() =
        supportsColorTemperature ||
            deviceTypes.any {
              it.id == Devices.ColorTemperatureLight.ID || it.id == Devices.ExtendedColorLight.ID
            }
}
