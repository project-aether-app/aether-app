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
import io.aether.android.data.DeviceRepository
import io.aether.android.data.models.Device
import io.aether.android.data.models.DeviceType
import io.aether.android.data.models.Endpoint
import io.aether.android.matter.Clusters
import io.aether.android.matter.EndpointId
import io.aether.android.matter.NodeId
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.shared.SetDeviceNameUseCase
import javax.inject.Inject
import kotlinx.coroutines.Job
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
    // Lifecycle Flags
    val isFirstTimeLoading: Boolean = true,
    val isBackgroundRefreshing: Boolean = false,
    @field:StringRes val errorMessageRes: Int? = null,

    // Core Screen Data
    val device: Device? = null,
    val lastUpdatedEndpointState: DeviceRepository.EndpointStateSnapshot? = null,
    val msgDialogInfo: DialogInfo? = null,
)

@HiltViewModel
class DeviceViewModel
@Inject
constructor(
    private val devicesRepository: DeviceRepository,
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

  private var deviceObserverJob: Job? = null

  fun loadDevice(nodeId: NodeId) {
    deviceObserverJob?.cancel()
    deviceObserverJob = viewModelScope.launch {
      devicesRepository.deviceFlow(nodeId).collect { _device.value = it }
    }
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
        Endpoint(
            id = info.endpointId,
            label = current.name,
            deviceTypes = info.types.map { DeviceType(it) },
            supportsLevelControl = info.serverClusters.contains(Clusters.LevelControl.ID),
            supportsColorTemperature = supportsColorTemperature,
        )
      }
      val endpointIds = endpoints.map { it.id }.toSet()
      current.endpoints
          .filter { it.id !in endpointIds }
          .takeIf { it.isNotEmpty() }
          ?.let {
            devicesRepository.removeEndpointsFromNode(
                nodeId,
                it.map { ep -> ep.id }.toSet(),
            )
          }
      endpoints.forEach {
        devicesRepository.addOrUpdateEndpoint(
            nodeId,
            current.name,
            current.vendor.id,
            current.vendor.name,
            current.product.id,
            current.product.name,
            it,
        )
      }
      _device.value = devicesRepository.getDevice(nodeId)
    }
        .onFailure { Timber.w(it, "Failed to sync endpoints nodeId=$nodeId") }
  }

  fun updateDeviceStateOn(endpoint: Endpoint, isOn: Boolean) =
      updateEndpoint(
          endpoint,
          isOn = isOn,
          level = endpoint.level,
          colorTemperature = endpoint.colorTemperature,
      ) { nodeId ->
        clustersHelper.setOnOffDeviceStateOnOffCluster(nodeId.toLong(), isOn, endpoint.id)
      }

  fun updateDeviceStateLevel(endpoint: Endpoint, level: Int) {
    if (!endpoint.hasLevelControlSupport) return
    updateEndpoint(endpoint, endpoint.isOn, level, endpoint.colorTemperature) { nodeId ->
      clustersHelper.setLevelStateLevelControlCluster(nodeId.toLong(), level, endpoint.id)
    }
  }

  fun updateDeviceStateColorTemperature(endpoint: Endpoint, colorTemperature: Int) {
    if (!endpoint.hasColorTemperatureSupport) return
    updateEndpoint(endpoint, endpoint.isOn, endpoint.level, colorTemperature) { nodeId ->
      clustersHelper.setColorTemperatureColorControlCluster(
          nodeId.toLong(),
          colorTemperature,
          endpoint.id,
      )
    }
  }

  private fun updateEndpoint(
      endpoint: Endpoint,
      isOn: Boolean,
      level: Int,
      colorTemperature: Int,
      action: suspend (NodeId) -> Unit,
  ) {
    val nodeId = device.value?.id ?: return
    viewModelScope.launch {
      runCatching {
        action(nodeId)
        devicesRepository.upsertEndpointState(
            nodeId,
            endpoint.id,
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
    val nodeId = device.value?.id ?: return
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
            chipClient.getConnectedDevicePointer(current.id),
            object : SubscriptionHelper.SubscriptionEstablishedCallbackForDevice(current.id) {
              override fun onSubscriptionEstablished(subscriptionId: Long) {
                viewModelScope.launch {
                  devicesRepository.updateNodeOnlineState(current.id, true)
                }
              }
            },
            object : SubscriptionHelper.ResubscriptionAttemptCallbackForDevice(current.id) {
              override fun onResubscriptionAttempt(
                  terminationCause: Int,
                  nextResubscribeIntervalMsec: Int,
              ) {
                super.onResubscriptionAttempt(
                    terminationCause,
                    nextResubscribeIntervalMsec,
                )
                viewModelScope.launch {
                  devicesRepository.updateNodeOnlineState(current.id, false)
                }
              }
            },
            object : SubscriptionHelper.ReportCallbackForDevice(current.id) {
              override fun onError(
                  attributePath: chip.devicecontroller.model.ChipAttributePath?,
                  eventPath: chip.devicecontroller.model.ChipEventPath?,
                  ex: Exception,
              ) {
                if (ex.isCommunicationTimeoutError()) {
                  viewModelScope.launch {
                    devicesRepository.updateNodeOnlineState(current.id, false)
                  }
                }
              }

              override fun onReport(nodeState: NodeState) {
                val latest = device.value ?: return
                latest.endpoints.forEach { endpoint ->
                  updateFromReport(current, endpoint, nodeState)
                }
              }
            },
        )
      }
          .onFailure { Timber.w(it, "Failed to subscribe nodeId=${current.id}") }
    }
  }

  private fun updateFromReport(device: Device, endpoint: Endpoint, nodeState: NodeState) {
    val on =
        subscriptionHelper.extractAttribute(
            nodeState,
            endpoint.id,
            Clusters.OnOff.ID,
            Clusters.OnOff.Attributes.OnOff.ID.toLong(),
        ) as Boolean? ?: return
    val level =
        if (endpoint.hasLevelControlSupport) {
          subscriptionHelper.extractAttribute(
              nodeState,
              endpoint.id,
              Clusters.LevelControl.ID,
              Clusters.LevelControl.Attributes.CurrentLevel.ID.toLong(),
          ) as Int? ?: return
        } else 0
    val colorTemperature =
        if (endpoint.hasColorTemperatureSupport) {
          subscriptionHelper.extractAttribute(
              nodeState,
              endpoint.id,
              Clusters.ColorControl.ID,
              Clusters.ColorControl.Attributes.ColorTemperatureMireds.ID.toLong(),
          ) as Int? ?: return
        } else 0
    viewModelScope.launch {
      devicesRepository.upsertEndpointState(
          device.id,
          endpoint.id,
          true,
          on,
          level,
          colorTemperature,
      )
      _device.value = devicesRepository.getDevice(device.id)
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
                  current.id.toLong(),
                  endpoint.id,
              )
          val level =
              if (endpoint.hasLevelControlSupport) {
                clustersHelper.getDeviceStateLevelControlCluster(
                    current.id.toLong(),
                    endpoint.id,
                )
              } else 0
          val colorTemperature =
              if (endpoint.hasColorTemperatureSupport) {
                clustersHelper.getColorTemperatureColorControlCluster(
                    current.id.toLong(),
                    endpoint.id,
                )
              } else 0
          devicesRepository.upsertEndpointState(
              current.id,
              endpoint.id,
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
