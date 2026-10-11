// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aether.android.MatterEndpoint
import io.aether.android.MatterFabricState
import io.aether.android.MatterNode
import io.aether.android.data.models.Device
import io.aether.android.data.models.DeviceType
import io.aether.android.data.models.Endpoint
import io.aether.android.data.models.Product
import io.aether.android.data.models.Vendor
import io.aether.android.matter.DeviceTypeId
import io.aether.android.matter.EndpointId
import io.aether.android.matter.NodeId
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import io.aether.android.matter.toDeviceTypeId
import io.aether.android.matter.toEndpointId
import io.aether.android.matter.toNodeId
import io.aether.android.matter.toProductId
import io.aether.android.matter.toVendorId
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

/** Owns persisted fabric state and exposes commissioned physical Matter nodes. */
@Singleton
class FabricRepository @Inject constructor(@ApplicationContext context: Context) {
  private val dataStore = context.fabricDataStore

  private val stateFlow: Flow<MatterFabricState> =
      dataStore.data.catch { exception ->
        if (exception is IOException) {
          Timber.e(exception, "Error reading fabric state")
          emit(MatterFabricState.getDefaultInstance())
        } else {
          throw exception
        }
      }

  /** All commissioned nodes. */
  val devicesFlow: Flow<List<Device>> = stateFlow.map { state ->
    state.nodesList.map(::toDevice)
  }

  suspend fun getDevices(): List<Device> = devicesFlow.first()

  internal suspend fun getDevice(nodeId: NodeId): Device? =
      devicesFlow.first().firstOrNull { it.id == nodeId }

  internal suspend fun addOrUpdateDevice(device: Device) {
    dataStore.updateData { state ->
      val index = state.nodesList.indexOfFirst { it.nodeId == device.id.toLong() }
      val node = toNode(device, state.nodesList.getOrNull(index))
      state.toBuilder().apply { if (index < 0) addNodes(node) else setNodes(index, node) }.build()
    }
  }

  internal suspend fun addOrUpdateEndpoint(
      nodeId: NodeId,
      nodeName: String,
      vendorId: VendorId,
      vendorName: String,
      productId: ProductId,
      productName: String,
      endpoint: Endpoint,
  ) {
    val existing = getDevice(nodeId)
    val existingEndpoint = existing?.endpoints?.firstOrNull { it.id == endpoint.id }
    val updatedEndpoint =
        endpoint.copy(
            isOn = existingEndpoint?.isOn ?: endpoint.isOn,
            level = existingEndpoint?.level ?: endpoint.level,
            colorTemperature = existingEndpoint?.colorTemperature ?: endpoint.colorTemperature,
        )
    val device =
        (existing
                ?: Device(
                    id = nodeId,
                    name = nodeName,
                    vendor = Vendor(id = vendorId, name = vendorName),
                    product = Product(id = productId, name = productName),
                ))
            .copy(
                name = nodeName.ifBlank { existing?.name.orEmpty() },
                vendor =
                    Vendor(
                        id =
                            vendorId.takeUnless { it == VendorId(0u) }
                                ?: existing?.vendor?.id
                                ?: vendorId,
                        name = vendorName.ifBlank { existing?.vendor?.name.orEmpty() },
                    ),
                product =
                    Product(
                        id =
                            productId.takeUnless { it == ProductId(0u) }
                                ?: existing?.product?.id
                                ?: productId,
                        name = productName.ifBlank { existing?.product?.name.orEmpty() },
                    ),
                endpoints =
                    existing.orEmptyEndpoints().filterNot { it.id == endpoint.id } +
                        updatedEndpoint,
            )
    addOrUpdateDevice(device)
  }

  internal suspend fun updateNodeBasicInfo(
      nodeId: NodeId,
      vendorId: VendorId?,
      vendorName: String?,
      productId: ProductId?,
      productName: String?,
      nodeLabel: String?,
  ) {
    val device = getDevice(nodeId) ?: return
    addOrUpdateDevice(
        device.copy(
            vendor =
                Vendor(
                    id = vendorId?.takeUnless { it == VendorId(0u) } ?: device.vendor.id,
                    name = vendorName?.takeIf { it.isNotBlank() } ?: device.vendor.name,
                ),
            product =
                Product(
                    id = productId?.takeUnless { it == ProductId(0u) } ?: device.product.id,
                    name = productName?.takeIf { it.isNotBlank() } ?: device.product.name,
                ),
            name = nodeLabel?.takeIf { it.isNotBlank() } ?: device.name,
        ),
    )
  }

  internal suspend fun updateDeviceTypes(nodeId: NodeId, deviceTypes: List<DeviceType>) {
    val device = getDevice(nodeId) ?: return
    addOrUpdateDevice(
        device.copy(endpoints = device.endpoints.map { it.copy(deviceTypes = deviceTypes) }),
    )
  }

  internal suspend fun removeEndpoints(nodeId: NodeId, endpointIds: Set<EndpointId>) {
    val device = getDevice(nodeId) ?: return
    addOrUpdateDevice(device.copy(endpoints = device.endpoints.filterNot { it.id in endpointIds }))
  }

  internal suspend fun updateEndpointState(
      nodeId: NodeId,
      endpointId: EndpointId,
      isOnline: Boolean,
      isOn: Boolean,
      level: Int,
      colorTemperature: Int,
  ) {
    val device = getDevice(nodeId) ?: return
    addOrUpdateDevice(
        device.copy(
            isOnline = isOnline,
            endpoints =
                device.endpoints.map {
                  if (it.id == endpointId) {
                    it.copy(isOn = isOn, level = level, colorTemperature = colorTemperature)
                  } else {
                    it
                  }
                },
        ),
    )
  }

  internal suspend fun updateOnlineState(nodeId: NodeId, isOnline: Boolean) {
    getDevice(nodeId)?.let { addOrUpdateDevice(it.copy(isOnline = isOnline)) }
  }

  internal suspend fun removeDevice(nodeId: NodeId) {
    dataStore.updateData { state ->
      val index = state.nodesList.indexOfFirst { it.nodeId == nodeId.toLong() }
      if (index < 0) state else state.toBuilder().removeNodes(index).build()
    }
  }

  internal suspend fun clearAllData() {
    dataStore.updateData { it.toBuilder().clearNodes().build() }
  }

  private fun toBasicDevice(node: MatterNode): Device =
      Device(
          id = node.nodeId.toNodeId(),
          vendor =
              Vendor(
                  id = node.vendorId.toVendorId(),
                  name = node.vendorName,
              ),
          product =
              Product(
                  id = node.productId.toProductId(),
                  name = node.productName,
              ),
          name = node.name,
          dateCommissioned =
              node.dateCommissioned
                  .takeUnless { it.seconds == 0L && it.nanos == 0 }
                  ?.let { Instant.ofEpochSecond(it.seconds, it.nanos.toLong()) },
          isOnline = node.online,
      )

  private fun toDevice(node: MatterNode): Device =
      toBasicDevice(node).copy(endpoints = node.endpointsList.map(::toEndpoint))

  private fun toEndpoint(endpoint: MatterEndpoint): Endpoint =
      Endpoint(
          id = endpoint.endpointId.toEndpointId(),
          label = endpoint.label,
          deviceTypes =
              endpoint.deviceTypesList
                  .map { DeviceType(it.toLong().toDeviceTypeId()) }
                  .ifEmpty { listOf(DeviceType(DeviceTypeId(0u))) },
          supportsLevelControl = endpoint.supportsLevelControl,
          supportsColorTemperature = endpoint.supportsColorTemperature,
          isOn = endpoint.on,
          level = endpoint.level,
          colorTemperature = endpoint.colorTemperature,
      )

  private fun toNode(device: Device, existing: MatterNode?): MatterNode {
    val source = existing ?: MatterNode.getDefaultInstance()
    return source
        .toBuilder()
        .apply {
          nodeId = device.id.toLong()
          vendorId = device.vendor.id.toInt()
          vendorName = device.vendor.name
          productId = device.product.id.toInt()
          productName = device.product.name
          name = device.name
          online = device.isOnline
          if (existing == null) {
            val commissionedAt = device.dateCommissioned ?: Instant.now()
            dateCommissioned =
                com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(commissionedAt.epochSecond)
                    .setNanos(commissionedAt.nano)
                    .build()
          }
          clearEndpoints()
          device.endpoints.sortedBy { it.id.toInt() }.forEach { addEndpoints(toEndpoint(it)) }
        }
        .build()
  }

  private fun toEndpoint(endpoint: Endpoint): MatterEndpoint =
      MatterEndpoint.newBuilder()
          .apply {
            endpointId = endpoint.id.toInt()
            label = endpoint.label
            supportsLevelControl = endpoint.supportsLevelControl
            supportsColorTemperature = endpoint.supportsColorTemperature
            on = endpoint.isOn
            level = endpoint.level
            colorTemperature = endpoint.colorTemperature
            if (endpoint.deviceTypes.isNotEmpty())
                endpoint.deviceTypes.forEach { addDeviceTypes(it.id.toInt()) }
          }
          .build()

  private fun Device?.orEmptyEndpoints(): List<Endpoint> = this?.endpoints.orEmpty()
}
