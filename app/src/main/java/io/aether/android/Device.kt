// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import io.aether.android.matter.DeviceTypeId
import io.aether.android.matter.EndpointId
import io.aether.android.matter.NodeId
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import io.aether.android.matter.toNodeId

/** App-level device model used by UI, backed by node+endpoint proto state. */
data class Device(
    // Basic device metadata.
    val nodeId: NodeId = NodeId(0u),
    val vendorId: VendorId = VendorId(0u),
    val vendorName: String = "",
    val productId: ProductId = ProductId(0u),
    val productName: String = "",
    val deviceTypeId: DeviceTypeId = DeviceTypeId(0u),

    // Device state information.
    val isOnline: Boolean = false,
    val endpointId: EndpointId = EndpointId(0u),
    val name: String = "",
    val supportsLevelControl: Boolean = false,
    val supportsColorTemperature: Boolean = false,
    val on: Boolean = false,
    val level: Int = 0,
    val colorTemperature: Int = 0,
) {
  fun toBuilder(): Builder = Builder(this)

  class Builder internal constructor(private var device: Device = Device()) {
    fun setNodeId(nodeId: NodeId) = apply { device = device.copy(nodeId = nodeId) }

    fun setNodeId(value: Long) = apply { device = device.copy(nodeId = value.toNodeId()) }

    fun setEndpointId(endpointId: EndpointId) = apply {
      device = device.copy(endpointId = endpointId)
    }

    fun setName(name: String) = apply { device = device.copy(name = name) }

    fun setVendorId(vendorId: VendorId) = apply { device = device.copy(vendorId = vendorId) }

    fun setVendorName(vendorName: String) = apply { device = device.copy(vendorName = vendorName) }

    fun setProductId(productId: ProductId) = apply { device = device.copy(productId = productId) }

    fun setProductName(productName: String) = apply {
      device = device.copy(productName = productName)
    }

    fun setDeviceTypeId(deviceTypeId: DeviceTypeId) = apply {
      device = device.copy(deviceTypeId = deviceTypeId)
    }

    fun setSupportsLevelControl(supportsLevelControl: Boolean) = apply {
      device = device.copy(supportsLevelControl = supportsLevelControl)
    }

    fun setSupportsColorTemperature(supportsColorTemperature: Boolean) = apply {
      device = device.copy(supportsColorTemperature = supportsColorTemperature)
    }

    fun setOn(on: Boolean) = apply { device = device.copy(on = on) }

    fun setLevel(level: Int) = apply { device = device.copy(level = level) }

    fun setColorTemperature(colorTemperature: Int) = apply {
      device = device.copy(colorTemperature = colorTemperature)
    }

    fun build(): Device = device
  }

  companion object {
    fun newBuilder(): Builder = Builder()

    fun newBuilder(device: Device): Builder = Builder(device)
  }
}
