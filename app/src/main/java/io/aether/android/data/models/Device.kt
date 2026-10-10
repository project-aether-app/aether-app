// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data.models

import io.aether.android.matter.DeviceTypeId
import io.aether.android.matter.EndpointId
import io.aether.android.matter.NodeId
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import java.time.Instant

/** A physical Matter device commissioned into the network. */
data class Device(
    val id: NodeId,
    val vendor: Vendor = Vendor(),
    val product: Product = Product(),
    val name: String = "",
    val dateCommissioned: Instant? = null,
    val isOnline: Boolean = false,
    val endpoints: List<Endpoint> = emptyList(),
) {
  val primaryEndpoint: Endpoint?
    get() = endpoints.minByOrNull { it.id.toInt() }
}

data class Vendor(
    val id: VendorId = VendorId(0u),
    val name: String = "",
)

data class Product(
    val id: ProductId = ProductId(0u),
    val name: String = "",
)

/** An endpoint exposed by a physical Matter device. */
data class Endpoint(
    val id: EndpointId,
    val label: String = "",
    val deviceTypeId: DeviceTypeId = DeviceTypeId(0u),
    val supportsLevelControl: Boolean = false,
    val supportsColorTemperature: Boolean = false,
    val isOn: Boolean = false,
    val level: Int = 0,
    val colorTemperature: Int = 0,
)
