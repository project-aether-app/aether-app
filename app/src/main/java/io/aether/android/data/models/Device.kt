// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data.models

import io.aether.android.matter.DeviceTypeId
import io.aether.android.matter.EndpointId
import io.aether.android.matter.NodeId
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import java.time.Instant

/** A commissioned Matter node and the endpoints it exposes. */
data class Device(
    val nodeId: NodeId,
    val vendorId: VendorId = VendorId(0u),
    val vendorName: String = "",
    val productId: ProductId = ProductId(0u),
    val productName: String = "",
    val name: String = "",
    val dateCommissioned: Instant? = null,
    val isOnline: Boolean = false,
    val endpoints: List<Endpoint> = emptyList(),
) {
  /** An endpoint belonging to this physical Matter node. */
  data class Endpoint(
      val endpointId: EndpointId,
      val label: String = "",
      val deviceTypeId: DeviceTypeId = DeviceTypeId(0u),
      val supportsLevelControl: Boolean = false,
      val supportsColorTemperature: Boolean = false,
      val isOn: Boolean = false,
      val level: Int = 0,
      val colorTemperature: Int = 0,
  )

  val primaryEndpoint: Endpoint?
    get() = endpoints.minByOrNull { it.endpointId.toInt() }
}
