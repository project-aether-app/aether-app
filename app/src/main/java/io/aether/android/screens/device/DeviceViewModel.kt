// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import chip.devicecontroller.model.NodeState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.MatterEndpoint
import io.aether.android.MatterNode
import io.aether.android.PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS
import io.aether.android.STATE_CHANGES_MONITORING_MODE
import io.aether.android.StateChangesMonitoringMode
import io.aether.android.chip.ChipClient
import io.aether.android.chip.ClustersHelper
import io.aether.android.chip.SubscriptionHelper
import io.aether.android.chip.isCommunicationTimeoutError
import io.aether.android.data.DevicesRepository
import io.aether.android.data.DevicesStateRepository
import io.aether.android.endpointIdTyped
import io.aether.android.matter.Clusters
import io.aether.android.matter.EndpointId
import io.aether.android.matter.NodeId
import io.aether.android.matter.toEndpointId
import io.aether.android.matter.toNodeId
import io.aether.android.matter.toProductId
import io.aether.android.matter.toVendorId
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.home.DeviceUiModel
import io.aether.android.screens.shared.SetDeviceNameUseCase
import io.aether.android.supportsColorTemperature
import io.aether.android.supportsLevelControl
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class DeviceScreenUiState(
    // Lifecycle Flags
    val isFirstTimeLoading: Boolean = true,
    val isBackgroundRefreshing: Boolean = false,
    val isOnline: Boolean = true,
    @StringRes val errorMessageRes: Int? = null,

    // Core Screen Data
    val device: DeviceUiModel? = null,
    val allEndpointUiModels: List<DeviceUiModel> = emptyList(),
    val lastUpdatedEndpointState: DevicesStateRepository.EndpointStateSnapshot? = null,

    // UI Overlay / Dialog States
    val msgDialogInfo: DialogInfo? = null,
)

/** The ViewModel for the Device Screen. */
@HiltViewModel
class DeviceViewModel
@Inject
constructor(
    private val devicesRepository: DevicesRepository,
    val devicesStateRepository: DevicesStateRepository,
    private val chipClient: ChipClient,
    private val clustersHelper: ClustersHelper,
    private val subscriptionHelper: SubscriptionHelper,
    private val setDeviceNameUseCase: SetDeviceNameUseCase,
) : ViewModel() {

  // The UI model for device shown on the Device screen.
  private var _deviceUiModel = MutableStateFlow<DeviceUiModel?>(null)
  private val deviceUiModel: StateFlow<DeviceUiModel?> = _deviceUiModel.asStateFlow()

  // All endpoint UI models for the same physical node shown on the Device screen.
  // Sorted by ascending endpoint number.
  private var _allEndpointUiModels = MutableStateFlow<List<DeviceUiModel>>(emptyList())
  private val allEndpointUiModels: StateFlow<List<DeviceUiModel>> =
      _allEndpointUiModels.asStateFlow()

  // Controls whether a periodic ping to the device is enabled or not.
  private var devicePeriodicPingEnabled: Boolean = true

  // Controls whether the "Message" AlertDialog should be shown in the UI.
  private var _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)
  private val msgDialogInfo: StateFlow<DialogInfo?> = _msgDialogInfo.asStateFlow()

  val uiState: StateFlow<DeviceScreenUiState> =
      combine(
              deviceUiModel,
              allEndpointUiModels,
              msgDialogInfo,
              devicesStateRepository.devicesStateFlow,
              devicesStateRepository.lastUpdatedEndpointState.asFlow().onStart {
                emit(devicesStateRepository.lastUpdatedEndpointState.value)
              },
          ) {
              deviceUiModel,
              allEndpointUiModels,
              msgDialogInfo,
              devicesState,
              lastUpdatedEndpointState,
            ->
            val isOnline =
                devicesState.nodesList
                    .firstOrNull { it.nodeId == deviceUiModel?.nodeId?.toLong() }
                    ?.online ?: deviceUiModel?.isOnline ?: true
            DeviceScreenUiState(
                isFirstTimeLoading = deviceUiModel == null,
                isBackgroundRefreshing = false,
                isOnline = isOnline,
                errorMessageRes = null,
                device = deviceUiModel,
                allEndpointUiModels = allEndpointUiModels,
                lastUpdatedEndpointState = lastUpdatedEndpointState,
                msgDialogInfo = msgDialogInfo,
            )
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeviceScreenUiState())

  // -----------------------------------------------------------------------------------------------
  // Load device

  fun loadDevice(nodeId: NodeId) {
    if (nodeId == deviceUiModel.value?.nodeId) {
      // Already loaded, but we still want to sync from device in case the device state has changed
      // since last time.
      viewModelScope.launch { syncEndpointsFromDevice(nodeId) }
      return
    }

    Timber.d("Loading data for nodeId=$nodeId")
    viewModelScope.launch {
      // 1. Kick off the actual fetch job in parallel.
      val fetchJob = launch {
        val state = devicesStateRepository.getAllDevicesState()
        val node = state.nodesList.firstOrNull { it.nodeId == nodeId.toLong() }
        if (node == null) {
          _deviceUiModel.value = null
          _allEndpointUiModels.value = emptyList()
          return@launch
        }
        val endpoint =
            node.endpointsList.minByOrNull { it.endpointId } ?: MatterEndpoint.getDefaultInstance()
        val deviceState =
            devicesStateRepository.loadEndpointState(
                node.nodeId.toNodeId(),
                endpoint.endpointIdTyped(),
            )
        var isOnline = false
        var isOn = false
        var level = 0
        var colorTemperature = 0
        if (deviceState != null) {
          isOnline = deviceState.online
          isOn = deviceState.on
          level = deviceState.level
          colorTemperature = deviceState.colorTemperature
        }
        _deviceUiModel.value =
            DeviceUiModel(node, endpoint, isOnline, isOn, level, colorTemperature)

        // Load all endpoint devices (siblings) for the same physical node.
        val siblings = node.endpointsList.sortedBy { it.endpointId }
        val models = siblings.map { sibling ->
          val siblingState =
              devicesStateRepository.loadEndpointState(
                  node.nodeId.toNodeId(),
                  sibling.endpointIdTyped(),
              )
          if (siblingState != null) {
            DeviceUiModel(
                node,
                sibling,
                siblingState.online,
                siblingState.on,
                siblingState.level,
                siblingState.colorTemperature,
            )
          } else {
            DeviceUiModel(node, sibling, false, false)
          }
        }
        _allEndpointUiModels.value = models
      }

      // 2. If the fetch isn't done in 500ms, show fallback UI while it keeps loading.
      delay(500.milliseconds)
      if (fetchJob.isActive) {
        Timber.d("Timeout reached before data loaded. Showing fallback UI.")
        val fallbackNode = MatterNode.newBuilder().setNodeId(nodeId.toLong()).build()
        val fallbackEndpoint = MatterEndpoint.newBuilder().setEndpointId(1).build()
        val fallbackDevice = DeviceUiModel(fallbackNode, fallbackEndpoint, false, false)
        _deviceUiModel.value = fallbackDevice
        _allEndpointUiModels.value = listOf(fallbackDevice)
      }

      fetchJob.join()
      syncEndpointsFromDevice(nodeId)
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Sync endpoints from device

  private suspend fun syncEndpointsFromDevice(nodeId: NodeId) {
    Timber.d("Sync endpoints from nodeId=$nodeId")
    try {
      val deviceMatterInfoList = clustersHelper.fetchDeviceMatterInfo(nodeId)

      val appEndpoints =
          deviceMatterInfoList
              .filter { info ->
                info.endpointId != EndpointId(0u) && info.serverClusters.contains(Clusters.OnOff.ID)
              }
              .ifEmpty { deviceMatterInfoList.filter { info -> info.endpointId != EndpointId(0u) } }
      val deviceEndpointIds = appEndpoints.map { it.endpointId }.toSet()

      val state = devicesStateRepository.getAllDevicesState()
      val node = state.nodesList.firstOrNull { it.nodeId == nodeId.toLong() } ?: return
      val storedEndpointIds = node.endpointsList.map { it.endpointId.toEndpointId() }.toSet()

      if (deviceEndpointIds == storedEndpointIds) return

      // Add new endpoints
      val newEndpointIds = deviceEndpointIds - storedEndpointIds
      for (endpointId in newEndpointIds) {
        val info = deviceMatterInfoList.firstOrNull { it.endpointId == endpointId } ?: continue
        val supportsLevel = info.serverClusters.contains(Clusters.LevelControl.ID)
        val supportsColorTemperature =
            if (info.serverClusters.contains(Clusters.ColorControl.ID)) {
              try {
                clustersHelper
                    .readColorControlClusterAttributeList(nodeId, info.endpointId)
                    .contains(Clusters.ColorControl.Attributes.ColorTemperatureMireds.ID)
              } catch (e: Exception) {
                Timber.w(
                    e,
                    "syncEndpointsFromDevice: could not read color control attributes for endpoint $endpointId",
                )
                false
              }
            } else false
        val endpoint =
            MatterEndpoint.newBuilder()
                .setEndpointId(endpointId.toInt())
                .setSupportsLevelControl(supportsLevel)
                .setSupportsColorTemperature(supportsColorTemperature)
                .addAllDeviceTypes(info.types.map { it.toInt() })
                .build()
        devicesRepository.addOrUpdateEndpoint(
            nodeId = nodeId,
            nodeName = node.name,
            vendorId = node.vendorId.toVendorId(),
            vendorName = node.vendorName,
            productId = node.productId.toProductId(),
            productName = node.productName,
            endpoint = endpoint,
        )
        devicesStateRepository.addEndpointState(
            nodeId,
            endpointId,
            isOnline = true,
            isOn = false,
            level = 0,
            colorTemperature = 0,
        )
      }

      // Remove endpoints that no longer exist on the device
      val removedEndpointIds = storedEndpointIds - deviceEndpointIds
      if (removedEndpointIds.isNotEmpty()) {
        devicesRepository.removeEndpointsFromNode(nodeId, removedEndpointIds)
      }

      // Reload and update UI in-place
      val updatedState = devicesStateRepository.getAllDevicesState()
      val updatedNode =
          updatedState.nodesList.firstOrNull { it.nodeId == nodeId.toLong() } ?: return
      val updatedSiblings = updatedNode.endpointsList.sortedBy { it.endpointId }
      val updatedModels = updatedSiblings.map { sibling ->
        val siblingState =
            devicesStateRepository.loadEndpointState(nodeId, sibling.endpointIdTyped())
        if (siblingState != null) {
          DeviceUiModel(
              updatedNode,
              sibling,
              siblingState.online,
              siblingState.on,
              siblingState.level,
              siblingState.colorTemperature,
          )
        } else {
          DeviceUiModel(updatedNode, sibling, false, false)
        }
      }
      _allEndpointUiModels.value = updatedModels

      val primaryEndpoint = updatedNode.endpointsList.minByOrNull { it.endpointId }
      if (primaryEndpoint != null) {
        val primaryState =
            devicesStateRepository.loadEndpointState(nodeId, primaryEndpoint.endpointIdTyped())
        _deviceUiModel.update { current ->
          if (current != null)
              DeviceUiModel(
                  updatedNode,
                  primaryEndpoint,
                  primaryState?.online ?: false,
                  primaryState?.on ?: false,
                  primaryState?.level ?: 0,
                  primaryState?.colorTemperature ?: 0,
              )
          else null
        }
      }
    } catch (e: Exception) {
      Timber.w(e, "Failed to sync endpoints nodeId=$nodeId")
    }
  }

  // On/Off
  fun updateDeviceStateOn(deviceUiModel: DeviceUiModel, isOn: Boolean) {
    Timber.d("Updating device power isOn=$isOn")
    val nodeId = deviceUiModel.nodeId
    viewModelScope.launch {
      Timber.d("Handling real device")
      try {
        clustersHelper.setOnOffDeviceStateOnOffCluster(
            nodeId.toLong(),
            isOn,
            deviceUiModel.endpoint.endpointId.toEndpointId(),
        )
        val endpoint = deviceUiModel.endpoint.endpointId
        // Read the current stored state to avoid overwriting fresh level/colorTemperature values
        // with whatever was loaded at screen-open time.
        val current = devicesStateRepository.loadEndpointState(nodeId, endpoint.toEndpointId())
        devicesStateRepository.upsertEndpointState(
            nodeId,
            endpoint.toEndpointId(),
            true,
            isOn,
            current?.level ?: deviceUiModel.level,
            current?.colorTemperature ?: deviceUiModel.colorTemperature,
        )
      } catch (e: Throwable) {
        Timber.e(e, "Failed setting on/off state")
        if (e.isCommunicationTimeoutError()) {
          devicesStateRepository.updateNodeOnlineState(nodeId, isOnline = false)
        }
      }
    }
  }

  // Level
  fun updateDeviceStateLevel(deviceUiModel: DeviceUiModel, level: Int) {
    Timber.d("Updating device level level=$level")
    val nodeId = deviceUiModel.nodeId
    viewModelScope.launch {
      if (!supportsLevelControl(deviceUiModel.endpoint)) {
        return@launch
      }

      Timber.d("Handling real device")
      try {
        clustersHelper.setLevelStateLevelControlCluster(
            nodeId.toLong(),
            level,
            deviceUiModel.endpoint.endpointId.toEndpointId(),
        )
        val endpoint = deviceUiModel.endpoint.endpointId
        // Read the current stored state to avoid overwriting fresh isOn/colorTemperature values.
        val current = devicesStateRepository.loadEndpointState(nodeId, endpoint.toEndpointId())
        devicesStateRepository.upsertEndpointState(
            nodeId,
            endpoint.toEndpointId(),
            true,
            current?.on ?: deviceUiModel.isOn,
            level,
            current?.colorTemperature ?: deviceUiModel.colorTemperature,
        )
      } catch (e: Throwable) {
        Timber.e(e, "Failed setting level")
        if (e.isCommunicationTimeoutError()) {
          devicesStateRepository.updateNodeOnlineState(nodeId, isOnline = false)
        }
      }
    }
  }

  // Color Temperature
  fun updateDeviceStateColorTemperature(deviceUiModel: DeviceUiModel, colorTemperature: Int) {
    Timber.d("Updating color temperature colorTemperature=$colorTemperature")
    val nodeId = deviceUiModel.nodeId
    viewModelScope.launch {
      if (!supportsColorTemperature(deviceUiModel.endpoint)) {
        return@launch
      }

      Timber.d("Handling real device")
      try {
        clustersHelper.setColorTemperatureColorControlCluster(
            nodeId.toLong(),
            colorTemperature,
            deviceUiModel.endpoint.endpointId.toEndpointId(),
        )
        val endpoint = deviceUiModel.endpoint.endpointId
        // Read the current stored state to avoid overwriting fresh isOn/level values.
        val current = devicesStateRepository.loadEndpointState(nodeId, endpoint.toEndpointId())
        devicesStateRepository.upsertEndpointState(
            nodeId,
            endpoint.toEndpointId(),
            true,
            current?.on ?: deviceUiModel.isOn,
            current?.level ?: deviceUiModel.level,
            colorTemperature,
        )
      } catch (e: Throwable) {
        Timber.e(e, "Failed setting color temperature")
        if (e.isCommunicationTimeoutError()) {
          devicesStateRepository.updateNodeOnlineState(nodeId, isOnline = false)
        }
      }
    }
  }

  fun inspectDescriptorCluster(deviceUiModel: DeviceUiModel) {
    val nodeId = deviceUiModel.nodeId
    val name = deviceUiModel.name
    val divider = "-".repeat(20)

    Timber.d("$divider Inspect device name=$name nodeId=$nodeId $divider")
    viewModelScope.launch {
      val partsListAttribute =
          clustersHelper.readDescriptorClusterPartsListAttribute(
              chipClient.getConnectedDevicePointer(nodeId),
              EndpointId(0u),
          )
      Timber.d("Parts list=$partsListAttribute")

      partsListAttribute.orEmpty().forEach { part ->
        val endpoint = part
        Timber.d("Processing part=$part")

        val deviceListAttribute =
            clustersHelper.readDescriptorClusterDeviceListAttribute(
                chipClient.getConnectedDevicePointer(nodeId),
                endpoint,
            )
        deviceListAttribute.forEach { Timber.d("Device attribute=$it") }

        val serverListAttribute =
            clustersHelper.readDescriptorClusterServerListAttribute(
                chipClient.getConnectedDevicePointer(nodeId),
                endpoint,
            )
        serverListAttribute.forEach { Timber.d("Server attribute=$it") }
      }
    }
  }

  fun inspectApplicationBasicCluster(nodeId: NodeId) {
    Timber.d("Inspecting application basic cluster nodeId=$nodeId")
    viewModelScope.launch {
      val attributeList =
          clustersHelper.readApplicationBasicClusterAttributeList(nodeId.toLong(), 1.toEndpointId())
      attributeList.forEach { Timber.d("Device attribute=$it") }
    }
  }

  fun inspectBasicCluster(nodeId: NodeId) {
    Timber.d("Inspecting basic cluster nodeId=$nodeId")
    viewModelScope.launch {
      val vendorId =
          clustersHelper.readBasicClusterVendorIDAttribute(nodeId.toLong(), EndpointId(0u))
      Timber.d("Vendor ID=$vendorId")

      val attributeList =
          clustersHelper.readBasicClusterAttributeList(nodeId.toLong(), EndpointId(0u))
      Timber.d("Attribute list=$attributeList")
    }
  }

  /**
   * The way we monitor state changes is defined by constant [StateChangesMonitoringMode].
   * [StateChangesMonitoringMode.Subscription] is the preferred mode.
   * [StateChangesMonitoringMode.PeriodicRead] was used initially because of issues with
   * subscriptions. We left its associated code as it could be useful to some developers.
   */
  fun startMonitoringStateChanges() {
    Timber.d("Starting state monitoring mode=$STATE_CHANGES_MONITORING_MODE")
    when (STATE_CHANGES_MONITORING_MODE) {
      StateChangesMonitoringMode.Subscription -> subscribeToPeriodicUpdates()
      StateChangesMonitoringMode.PeriodicRead -> startDevicePeriodicPing()
    }
  }

  fun stopMonitoringStateChanges() {
    val nodeId = deviceUiModel.value?.nodeId
    if (nodeId == null) {
      Timber.d("No loaded device; skipping state monitoring stop")
      return
    }
    when (STATE_CHANGES_MONITORING_MODE) {
      StateChangesMonitoringMode.Subscription -> unsubscribeToPeriodicUpdates()
      StateChangesMonitoringMode.PeriodicRead -> stopDevicePeriodicPing()
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Subscription to periodic device updates.
  // See:
  //   - Spec section "8.5 Subscribe Interaction"
  //   - Matter primer:
  // https://developers.home.google.com/matter/primer/interaction-model-reading#subscription_transaction
  //
  // TODO:
  //   - Properly implement unsubscribe behavior
  //   - Implement algorithm for online/offline detection.
  //     (Issue is that not clear how to register a callback for messages coming at maxInterval.

  /*
    Sample message coming at maxInterval.
  ```
  01-06 05:27:53.736 16814 16850 D EM      : >>> [E:59135r M:51879653] (S) Msg RX from 1:0000000000000001 [171D] --- Type 0001:05 (IM:ReportData)
  01-06 05:27:53.736 16814 16850 D EM      : Handling via exchange: 59135r, Delegate: 0x76767a7668
  01-06 05:27:53.736 16814 16850 D DMG     : ReportDataMessage =
  01-06 05:27:53.737 16814 16850 D DMG     : {
  01-06 05:27:53.737 16814 16850 D DMG     : 	SubscriptionId = 0x7e169ca8,
  01-06 05:27:53.737 16814 16850 D DMG     : 	InteractionModelRevision = 1
  01-06 05:27:53.737 16814 16850 D DMG     : }
  01-06 05:27:53.737 16814 16850 D DMG     : Refresh LivenessCheckTime for 35000 milliseconds with SubscriptionId = 0x7e169ca8 Peer = 01:0000000000000001
  01-06 05:27:53.737 16814 16850 D EM      : <<< [E:59135r M:213699489 (Ack:51879653)] (S) Msg TX to 1:0000000000000001 [171D] --- Type 0001:01 (IM:StatusResponse)
  01-06 05:27:53.737 16814 16850 D IN      : (S) Sending msg 213699489 on secure session with LSID: 25418
  01-06 05:27:53.838 16814 16850 D EM      : >>> [E:59135r M:51879654 (Ack:213699489)] (S) Msg RX from 1:0000000000000001 [171D] --- Type 0000:10 (SecureChannel:StandaloneAck)
  01-06 05:27:53.839 16814 16850 D EM      : Found matching exchange: 59135r, Delegate: 0x0
  01-06 05:27:53.839 16814 16850 D EM      : Rxd Ack; Removing MessageCounter:213699489 from Retrans Table on exchange 59135r
  ```
  */
  private fun subscribeToPeriodicUpdates() {
    Timber.d("Subscribing to periodic updates")
    val primaryDevice =
        deviceUiModel.value
            ?: run {
              Timber.w(
                  "subscribeToPeriodicUpdates(): deviceUiModel not yet loaded, skipping subscription"
              )
              return
            }
    val reportCallback =
        object : SubscriptionHelper.ReportCallbackForDevice(primaryDevice.nodeId) {
          override fun onError(
              attributePath: chip.devicecontroller.model.ChipAttributePath?,
              eventPath: chip.devicecontroller.model.ChipEventPath?,
              ex: Exception,
          ) {
            super.onError(attributePath, eventPath, ex)
            if (ex.isCommunicationTimeoutError()) {
              viewModelScope.launch {
                devicesStateRepository.updateNodeOnlineState(primaryDevice.nodeId, isOnline = false)
              }
            }
          }

          override fun onReport(nodeState: NodeState) {
            super.onReport(nodeState)
            viewModelScope.launch {
              // Look up the current endpoint list on every report rather than capturing a snapshot
              // at subscription time, so endpoints that finish loading after the subscription is
              // set up are also updated.
              val currentEndpoints = allEndpointUiModels.value
              val devicesToUpdate = currentEndpoints.ifEmpty { listOf(primaryDevice) }
              devicesToUpdate.forEach { endpointModel ->
                val endpoint = endpointModel.endpoint.endpointId
                val onOffState =
                    subscriptionHelper.extractAttribute(
                        nodeState,
                        endpoint.toEndpointId(),
                        Clusters.OnOff.ID,
                        Clusters.OnOff.Attributes.OnOff.ID.toLong(),
                    ) as Boolean?
                val levelState =
                    subscriptionHelper.extractAttribute(
                        nodeState,
                        endpoint.toEndpointId(),
                        Clusters.LevelControl.ID,
                        Clusters.LevelControl.Attributes.CurrentLevel.ID.toLong(),
                    ) as Int?
                val colorTemperatureState =
                    subscriptionHelper.extractAttribute(
                        nodeState,
                        endpoint.toEndpointId(),
                        Clusters.ColorControl.ID,
                        Clusters.ColorControl.Attributes.ColorTemperatureMireds.ID.toLong(),
                    ) as Int?
                Timber.d("Device power state onOffState=$onOffState endpointId=$endpoint")
                if (onOffState == null) {
                  Timber.e(
                      "onReport(): WARNING -> onOffState is NULL for endpoint $endpoint. Ignoring."
                  )
                  return@forEach
                }
                if (supportsLevelControl(endpointModel.endpoint) && levelState == null) {
                  Timber.e(
                      "onReport(): WARNING -> levelState is NULL for endpoint $endpoint. Ignoring."
                  )
                  return@forEach
                }
                if (
                    supportsColorTemperature(endpointModel.endpoint) &&
                        colorTemperatureState == null
                ) {
                  Timber.e(
                      "onReport(): WARNING -> colorTemperatureState is NULL for endpoint $endpoint. Ignoring."
                  )
                  return@forEach
                }
                val level = if (supportsLevelControl(endpointModel.endpoint)) levelState!! else 0
                val colorTemperature =
                    if (supportsColorTemperature(endpointModel.endpoint)) colorTemperatureState!!
                    else 0
                devicesStateRepository.upsertEndpointState(
                    endpointModel.nodeId,
                    endpoint.toEndpointId(),
                    isOnline = true,
                    isOn = onOffState,
                    level = level,
                    colorTemperature = colorTemperature,
                )
              }
            }
          }
        }
    viewModelScope.launch {
      try {
        val nodeId = primaryDevice.nodeId
        val connectedDevicePointer = chipClient.getConnectedDevicePointer(nodeId)
        subscriptionHelper.awaitSubscribeToPeriodicUpdates(
            connectedDevicePointer,
            object : SubscriptionHelper.SubscriptionEstablishedCallbackForDevice(nodeId) {
              override fun onSubscriptionEstablished(subscriptionId: Long) {
                super.onSubscriptionEstablished(subscriptionId)
                viewModelScope.launch {
                  devicesStateRepository.updateNodeOnlineState(nodeId, isOnline = true)
                }
              }
            },
            SubscriptionHelper.ResubscriptionAttemptCallbackForDevice(nodeId),
            reportCallback,
        )
      } catch (e: IllegalStateException) {
        Timber.e(
            "Can't get connectedDevicePointer for nodeId=${primaryDevice.nodeId} " +
                "(endpoint=${primaryDevice.endpoint.endpointId})."
        )
        if (e.isCommunicationTimeoutError()) {
          devicesStateRepository.updateNodeOnlineState(primaryDevice.nodeId, isOnline = false)
        }
        return@launch
      }
    }
  }

  private fun unsubscribeToPeriodicUpdates() {
    Timber.d("Unsubscribing from periodic updates")
    val primaryDevice =
        deviceUiModel.value
            ?: run {
              Timber.d(
                  "unsubscribeToPeriodicUpdates(): nothing to unsubscribe, deviceUiModel is null"
              )
              return
            }
    viewModelScope.launch {
      try {
        val nodeId = primaryDevice.nodeId
        val connectedDevicePtr = chipClient.getConnectedDevicePointer(nodeId)
        subscriptionHelper.awaitUnsubscribeToPeriodicUpdates(connectedDevicePtr)
      } catch (e: IllegalStateException) {
        Timber.e(
            "Can't get connectedDevicePointer for nodeId=${primaryDevice.nodeId} " +
                "(endpoint=${primaryDevice.endpoint.endpointId})."
        )
        if (e.isCommunicationTimeoutError()) {
          devicesStateRepository.updateNodeOnlineState(primaryDevice.nodeId, isOnline = false)
        }
        return@launch
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Task that runs periodically to get and update the device state.
  // Periodic monitoring of a device state should be done with Subscription mode.
  // This code is left here in case it might be useful to some developers.

  private fun startDevicePeriodicPing() {
    Timber.d(
        "${LocalDateTime.now()} startDevicePeriodicPing every $PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS seconds"
    )
    devicePeriodicPingEnabled = true
    runDevicePeriodicUpdate()
  }

  private fun runDevicePeriodicUpdate() {
    if (PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS == -1) {
      return
    }
    viewModelScope.launch {
      while (devicePeriodicPingEnabled) {
        val endpointModels =
            allEndpointUiModels.value.ifEmpty { listOfNotNull(deviceUiModel.value) }
        endpointModels.forEach { endpointUiModel ->
          var isOn: Boolean?
          var isOnline: Boolean
          var level: Int
          var colorTemperature: Int
          val nodeId = endpointUiModel.nodeId
          val endpointId = endpointUiModel.endpoint.endpointId.toEndpointId()
          val hasLevelControl = supportsLevelControl(endpointUiModel.endpoint)
          val hasColorTemperature = supportsColorTemperature(endpointUiModel.endpoint)
          isOn = clustersHelper.getDeviceStateOnOffCluster(nodeId.toLong(), endpointId)
          val levelRead =
              if (hasLevelControl) {
                clustersHelper.getDeviceStateLevelControlCluster(nodeId.toLong(), endpointId)
              } else {
                null
              }
          val colorTemperatureRead =
              if (hasColorTemperature) {
                clustersHelper.getColorTemperatureColorControlCluster(nodeId.toLong(), endpointId)
              } else {
                null
              }
          if (
              isOn == null ||
                  (hasLevelControl && levelRead == null) ||
                  (hasColorTemperature && colorTemperatureRead == null)
          ) {
            Timber.e("Device ping failed endpointId=$endpointId")
            isOn = false
            isOnline = false
            level = 0
            colorTemperature = 0
          } else {
            level = if (hasLevelControl) levelRead!! else 0
            colorTemperature = if (hasColorTemperature) colorTemperatureRead!! else 0
            Timber.d("Device ping succeeded isOn=$isOn endpointId=$endpointId")
            isOnline = true
          }
          devicesStateRepository.upsertEndpointState(
              nodeId,
              endpointId,
              isOnline = isOnline,
              isOn = isOn == true,
              level = level,
              colorTemperature = colorTemperature,
          )
        }
        delay(PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS * 1000L)
      }
    }
  }

  private fun stopDevicePeriodicPing() {
    devicePeriodicPingEnabled = false
  }

  // -----------------------------------------------------------------------------------------------
  // UI State update

  fun showMsgDialog(title: String?, msg: String?, showConfirmButton: Boolean = true) {
    Timber.d("Showing message dialog title=$title")
    _msgDialogInfo.value =
        DialogInfo(title = title, message = msg, showConfirmButton = showConfirmButton)
  }

  fun showMsgDialog(@StringRes titleRes: Int, msg: String?, showConfirmButton: Boolean = true) {
    Timber.d("Showing message dialog titleRes=$titleRes")
    _msgDialogInfo.value =
        DialogInfo(titleRes = titleRes, message = msg, showConfirmButton = showConfirmButton)
  }

  fun showMsgDialog(
      @StringRes titleRes: Int,
      @StringRes msgRes: Int,
      showConfirmButton: Boolean = true,
  ) {
    Timber.d("Showing message dialog titleRes=$titleRes msgRes=$msgRes")
    _msgDialogInfo.value =
        DialogInfo(titleRes = titleRes, messageRes = msgRes, showConfirmButton = showConfirmButton)
  }

  // Called after user dismisss the Info dialog. If we don't consume, a config change redisplays the
  // alert dialog.
  fun dismissMsgDialog() {
    Timber.d("dismissMsgDialog()")
    _msgDialogInfo.value = null
  }
}
