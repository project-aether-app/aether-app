// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chip.devicecontroller.model.NodeState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS
import io.aether.android.STATE_CHANGES_MONITORING_MODE
import io.aether.android.StateChangesMonitoringMode
import io.aether.android.chip.ChipClient
import io.aether.android.chip.ClustersHelper
import io.aether.android.chip.SubscriptionHelper
import io.aether.android.chip.isCommunicationTimeoutError
import io.aether.android.data.DevicesRepository
import io.aether.android.data.models.Device
import io.aether.android.matter.Clusters
import io.aether.android.matter.EndpointId
import io.aether.android.matter.NodeId
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.shared.SetDeviceNameUseCase
import io.aether.android.supportsColorTemperature
import io.aether.android.supportsLevelControl
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

data class DeviceUiState(
    val isFirstTimeLoading: Boolean = true,
    val isBackgroundRefreshing: Boolean = false,
    @field:StringRes val errorMessageRes: Int? = null,
    val device: Device? = null,
    val lastUpdatedEndpointState: DevicesRepository.EndpointStateSnapshot? = null,
    val msgDialogInfo: DialogInfo? = null,
)

@HiltViewModel
class DeviceViewModel
@Inject
constructor(
    private val devicesRepository: DevicesRepository,
    private val chipClient: ChipClient,
    private val clustersHelper: ClustersHelper,
    private val subscriptionHelper: SubscriptionHelper,
    private val setDeviceNameUseCase: SetDeviceNameUseCase,
) : ViewModel() {
  private val _device = MutableStateFlow<Device?>(null)
  private val device = _device.asStateFlow()
  private val _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)
  private val msgDialogInfo = _msgDialogInfo.asStateFlow()
  private var devicePeriodicPingEnabled = true

  val uiState: StateFlow<DeviceUiState> =
      combine(device, devicesRepository.lastUpdatedEndpointState, msgDialogInfo) {
              currentDevice,
              lastUpdatedEndpointState,
              dialog ->
            DeviceUiState(
                isFirstTimeLoading = currentDevice == null,
                device = currentDevice,
                lastUpdatedEndpointState = lastUpdatedEndpointState,
                msgDialogInfo = dialog,
            )
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeviceUiState())

  fun loadDevice(nodeId: NodeId) {
    viewModelScope.launch {
      _device.value = runCatching { devicesRepository.getDevice(nodeId) }.getOrNull()
      syncEndpointsFromDevice(nodeId)
    }
  }

  private suspend fun syncEndpointsFromDevice(nodeId: NodeId) {
    val current = _device.value ?: return
    runCatching {
      val infos = clustersHelper.fetchDeviceMatterInfo(nodeId)
      val applicationEndpoints =
          infos
              .filter {
                it.endpointId != EndpointId(0u) && it.serverClusters.contains(Clusters.OnOff.ID)
              }
              .ifEmpty { infos.filter { it.endpointId != EndpointId(0u) } }
      val endpoints = applicationEndpoints.map { info ->
        val supportsColorTemperature =
            info.serverClusters.contains(Clusters.ColorControl.ID) &&
                clustersHelper
                    .readColorControlClusterAttributeList(nodeId, info.endpointId)
                    .contains(
                        Clusters.ColorControl.Attributes.ColorTemperatureMireds.ID,
                    )
        Device.Endpoint(
            endpointId = info.endpointId,
            label = current.name,
            deviceTypeId = info.types.firstOrNull() ?: io.aether.android.matter.DeviceTypeId(0u),
            supportsLevelControl = info.serverClusters.contains(Clusters.LevelControl.ID),
            supportsColorTemperature = supportsColorTemperature,
        )
      }
      val endpointIds = endpoints.map { it.endpointId }.toSet()
      current.endpoints
          .filter { it.endpointId !in endpointIds }
          .takeIf { it.isNotEmpty() }
          ?.let {
            devicesRepository.removeEndpointsFromNode(
                nodeId,
                it.map { ep -> ep.endpointId }.toSet(),
            )
          }
      endpoints.forEach {
        devicesRepository.addOrUpdateEndpoint(
            nodeId,
            current.name,
            current.vendorId,
            current.vendorName,
            current.productId,
            current.productName,
            it,
        )
      }
      _device.value = devicesRepository.getDevice(nodeId)
    }
        .onFailure { Timber.w(it, "Failed to sync endpoints nodeId=$nodeId") }
  }

  fun updateDeviceStateOn(endpoint: Device.Endpoint, isOn: Boolean) =
      updateEndpoint(
          endpoint,
          isOn = isOn,
          level = endpoint.level,
          colorTemperature = endpoint.colorTemperature,
      ) { nodeId ->
        clustersHelper.setOnOffDeviceStateOnOffCluster(nodeId.toLong(), isOn, endpoint.endpointId)
      }

  fun updateDeviceStateLevel(endpoint: Device.Endpoint, level: Int) {
    if (!supportsLevelControl(endpoint)) return
    updateEndpoint(endpoint, endpoint.isOn, level, endpoint.colorTemperature) { nodeId ->
      clustersHelper.setLevelStateLevelControlCluster(nodeId.toLong(), level, endpoint.endpointId)
    }
  }

  fun updateDeviceStateColorTemperature(endpoint: Device.Endpoint, colorTemperature: Int) {
    if (!supportsColorTemperature(endpoint)) return
    updateEndpoint(endpoint, endpoint.isOn, endpoint.level, colorTemperature) { nodeId ->
      clustersHelper.setColorTemperatureColorControlCluster(
          nodeId.toLong(),
          colorTemperature,
          endpoint.endpointId,
      )
    }
  }

  private fun updateEndpoint(
      endpoint: Device.Endpoint,
      isOn: Boolean,
      level: Int,
      colorTemperature: Int,
      action: suspend (NodeId) -> Unit,
  ) {
    val nodeId = device.value?.nodeId ?: return
    viewModelScope.launch {
      runCatching {
        action(nodeId)
        devicesRepository.upsertEndpointState(
            nodeId,
            endpoint.endpointId,
            true,
            isOn,
            level,
            colorTemperature,
        )
        _device.value = devicesRepository.getDevice(nodeId)
      }
          .onFailure {
            Timber.e(it, "Failed updating endpoint state")
            if (it.isCommunicationTimeoutError())
                devicesRepository.updateNodeOnlineState(nodeId, false)
          }
    }
  }

  fun startMonitoringStateChanges() {
    when (STATE_CHANGES_MONITORING_MODE) {
      StateChangesMonitoringMode.Subscription -> subscribeToPeriodicUpdates()
      StateChangesMonitoringMode.PeriodicRead -> startDevicePeriodicPing()
    }
  }

  fun stopMonitoringStateChanges() {
    devicePeriodicPingEnabled = false
    val nodeId = device.value?.nodeId ?: return
    if (STATE_CHANGES_MONITORING_MODE == StateChangesMonitoringMode.Subscription) {
      viewModelScope.launch {
        runCatching {
          subscriptionHelper.awaitUnsubscribeToPeriodicUpdates(
              chipClient.getConnectedDevicePointer(nodeId),
          )
        }
            .onFailure { Timber.w(it, "Failed to unsubscribe nodeId=$nodeId") }
      }
    }
  }

  private fun subscribeToPeriodicUpdates() {
    val current = device.value ?: return
    viewModelScope.launch {
      runCatching {
        subscriptionHelper.awaitSubscribeToPeriodicUpdates(
            chipClient.getConnectedDevicePointer(current.nodeId),
            object : SubscriptionHelper.SubscriptionEstablishedCallbackForDevice(current.nodeId) {
              override fun onSubscriptionEstablished(subscriptionId: Long) {
                viewModelScope.launch {
                  devicesRepository.updateNodeOnlineState(current.nodeId, true)
                }
              }
            },
            SubscriptionHelper.ResubscriptionAttemptCallbackForDevice(current.nodeId),
            object : SubscriptionHelper.ReportCallbackForDevice(current.nodeId) {
              override fun onError(
                  attributePath: chip.devicecontroller.model.ChipAttributePath?,
                  eventPath: chip.devicecontroller.model.ChipEventPath?,
                  ex: Exception,
              ) {
                if (ex.isCommunicationTimeoutError()) {
                  viewModelScope.launch {
                    devicesRepository.updateNodeOnlineState(current.nodeId, false)
                  }
                }
              }

              override fun onReport(nodeState: NodeState) {
                current.endpoints.forEach { endpoint ->
                  updateFromReport(current, endpoint, nodeState)
                }
              }
            },
        )
      }
          .onFailure { Timber.w(it, "Failed to subscribe nodeId=${current.nodeId}") }
    }
  }

  private fun updateFromReport(device: Device, endpoint: Device.Endpoint, nodeState: NodeState) {
    val on =
        subscriptionHelper.extractAttribute(
            nodeState,
            endpoint.endpointId,
            Clusters.OnOff.ID,
            Clusters.OnOff.Attributes.OnOff.ID.toLong(),
        ) as Boolean? ?: return
    val level =
        if (supportsLevelControl(endpoint)) {
          subscriptionHelper.extractAttribute(
              nodeState,
              endpoint.endpointId,
              Clusters.LevelControl.ID,
              Clusters.LevelControl.Attributes.CurrentLevel.ID.toLong(),
          ) as Int? ?: return
        } else 0
    val colorTemperature =
        if (supportsColorTemperature(endpoint)) {
          subscriptionHelper.extractAttribute(
              nodeState,
              endpoint.endpointId,
              Clusters.ColorControl.ID,
              Clusters.ColorControl.Attributes.ColorTemperatureMireds.ID.toLong(),
          ) as Int? ?: return
        } else 0
    viewModelScope.launch {
      devicesRepository.upsertEndpointState(
          device.nodeId,
          endpoint.endpointId,
          true,
          on,
          level,
          colorTemperature,
      )
      _device.value = devicesRepository.getDevice(device.nodeId)
    }
  }

  private fun startDevicePeriodicPing() {
    val current = device.value ?: return
    devicePeriodicPingEnabled = true
    viewModelScope.launch {
      while (devicePeriodicPingEnabled) {
        current.endpoints.forEach { endpoint ->
          val on =
              clustersHelper.getDeviceStateOnOffCluster(
                  current.nodeId.toLong(),
                  endpoint.endpointId,
              )
          val level =
              if (supportsLevelControl(endpoint)) {
                clustersHelper.getDeviceStateLevelControlCluster(
                    current.nodeId.toLong(),
                    endpoint.endpointId,
                )
              } else 0
          val colorTemperature =
              if (supportsColorTemperature(endpoint)) {
                clustersHelper.getColorTemperatureColorControlCluster(
                    current.nodeId.toLong(),
                    endpoint.endpointId,
                )
              } else 0
          devicesRepository.upsertEndpointState(
              current.nodeId,
              endpoint.endpointId,
              on != null,
              on ?: false,
              level ?: 0,
              colorTemperature ?: 0,
          )
        }
        delay(PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS * 1000L)
      }
    }
  }

  fun inspectDescriptorCluster(device: Device) = Unit

  fun inspectApplicationBasicCluster(nodeId: NodeId) = Unit

  fun inspectBasicCluster(nodeId: NodeId) = Unit

  fun showMsgDialog(title: String?, msg: String?, showConfirmButton: Boolean = true) {
    _msgDialogInfo.value =
        DialogInfo(title = title, message = msg, showConfirmButton = showConfirmButton)
  }

  fun dismissMsgDialog() {
    _msgDialogInfo.value = null
  }
}
