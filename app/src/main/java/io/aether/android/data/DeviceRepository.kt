// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data

import io.aether.android.data.models.Device
import io.aether.android.data.models.DeviceType
import io.aether.android.data.models.Endpoint
import io.aether.android.matter.EndpointId
import io.aether.android.matter.NodeId
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/** Provides detailed information and state updates for one commissioned Matter node. */
@Singleton
class DeviceRepository @Inject constructor(private val fabricRepository: FabricRepository) {
  data class EndpointStateSnapshot(
      val nodeId: NodeId,
      val endpointId: EndpointId,
      val online: Boolean,
      val on: Boolean,
      val level: Int,
      val colorTemperature: Int,
  )

  private val _lastUpdatedEndpointState = MutableStateFlow<EndpointStateSnapshot?>(null)
  val lastUpdatedEndpointState = _lastUpdatedEndpointState

  fun deviceFlow(nodeId: NodeId): Flow<Device> =
      fabricRepository.devicesFlow
          .map { devices -> devices.firstOrNull { it.id == nodeId } }
          .filterNotNull()

  suspend fun getDevice(nodeId: NodeId): Device =
      fabricRepository.getDevice(nodeId) ?: error("Device not found for nodeId: $nodeId")

  suspend fun addOrUpdateDevice(device: Device) = fabricRepository.addOrUpdateDevice(device)

  suspend fun addOrUpdateEndpoint(
      nodeId: NodeId,
      nodeName: String,
      vendorId: VendorId,
      vendorName: String,
      productId: ProductId,
      productName: String,
      endpoint: Endpoint,
  ) =
      fabricRepository.addOrUpdateEndpoint(
          nodeId,
          nodeName,
          vendorId,
          vendorName,
          productId,
          productName,
          endpoint,
      )

  suspend fun updateNodeBasicInfo(
      nodeId: NodeId,
      vendorId: VendorId?,
      vendorName: String?,
      productId: ProductId?,
      productName: String?,
      nodeLabel: String?,
  ) =
      fabricRepository.updateNodeBasicInfo(
          nodeId,
          vendorId,
          vendorName,
          productId,
          productName,
          nodeLabel,
      )

  suspend fun updateDeviceTypes(nodeId: NodeId, deviceTypes: List<DeviceType>) =
      fabricRepository.updateDeviceTypes(nodeId, deviceTypes)

  suspend fun removeEndpointsFromNode(nodeId: NodeId, endpointIds: Set<EndpointId>) =
      fabricRepository.removeEndpoints(nodeId, endpointIds)

  suspend fun removeDevice(nodeId: NodeId) = fabricRepository.removeDevice(nodeId)

  suspend fun clearAllData() = fabricRepository.clearAllData()

  suspend fun updateNodeOnlineState(nodeId: NodeId, isOnline: Boolean) =
      fabricRepository.updateOnlineState(nodeId, isOnline)

  suspend fun upsertEndpointState(
      nodeId: NodeId,
      endpointId: EndpointId,
      isOnline: Boolean,
      isOn: Boolean,
      level: Int,
      colorTemperature: Int,
  ) {
    fabricRepository.updateEndpointState(
        nodeId,
        endpointId,
        isOnline,
        isOn,
        level,
        colorTemperature,
    )
    _lastUpdatedEndpointState.value =
        EndpointStateSnapshot(
            nodeId,
            endpointId,
            isOnline,
            isOn,
            level,
            colorTemperature,
        )
  }
}
