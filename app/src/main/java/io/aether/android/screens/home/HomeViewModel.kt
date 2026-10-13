// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.home

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chip.devicecontroller.model.NodeState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.R
import io.aether.android.TaskStatus
import io.aether.android.chip.ChipClient
import io.aether.android.chip.ClustersHelper
import io.aether.android.chip.SubscriptionHelper
import io.aether.android.chip.isCommunicationTimeoutError
import io.aether.android.commissioning.CommissionedDevice
import io.aether.android.commissioning.CommissioningWindowExpiringException
import io.aether.android.commissioning.DeviceCommissioner
import io.aether.android.data.DeviceRepository
import io.aether.android.data.FabricRepository
import io.aether.android.data.UserPreferencesRepository
import io.aether.android.data.models.Device
import io.aether.android.data.models.DeviceType
import io.aether.android.data.models.Endpoint
import io.aether.android.data.models.Product
import io.aether.android.data.models.Vendor
import io.aether.android.isMultiAdminCommissioning
import io.aether.android.matter.Clusters
import io.aether.android.matter.EndpointId
import io.aether.android.matter.NodeId
import io.aether.android.platform.PlayServices
import io.aether.android.screens.common.DialogInfo
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

data class HomeUiState(
  // Core Screen Data
  val devices: List<Device> = emptyList(),
  val showOfflineDevices: Boolean = true,
  val showNetworkValidityWarning: Boolean = false,
  val multiadminCommissionDeviceTaskStatus: TaskStatus = TaskStatus.NotStarted,
  val deviceAttestationFailureIgnored: Boolean = true,

  // UI Overlay / Dialog States
  val msgDialogInfo: DialogInfo? = null,
  val showNewDeviceNameAlertDialog: Boolean = false,
  val showUpdatePlayServicesDialog: Boolean = false,
)

sealed interface HomeEffect {
  data class LaunchCommissioning(val intentSender: IntentSender) : HomeEffect
}

@HiltViewModel
class HomeViewModel
@Inject
constructor(
  private val fabricRepository: FabricRepository,
  private val devicesRepository: DeviceRepository,
  private val userPreferencesRepository: UserPreferencesRepository,
  private val clustersHelper: ClustersHelper,
  private val chipClient: ChipClient,
  private val subscriptionHelper: SubscriptionHelper,
  private val deviceCommissioner: DeviceCommissioner,
  private val playServices: PlayServices,
) : ViewModel() {
  private val _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)
  private val _showNewDeviceNameAlertDialog = MutableStateFlow(false)
  private val _multiadminCommissionDeviceTaskStatus =
    MutableStateFlow<TaskStatus>(TaskStatus.NotStarted)
  private val _showUpdatePlayServicesDialog = MutableStateFlow(false)
  private var commissionedDevice: CommissionedDevice? = null
  private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
  val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

  val uiState: StateFlow<HomeUiState> =
    combine(
        fabricRepository.devicesFlow,
        userPreferencesRepository.userPreferencesFlow,
        deviceCommissioner.isNetworkValidFlow,
        combine(
          _msgDialogInfo,
          _showNewDeviceNameAlertDialog,
          _showUpdatePlayServicesDialog,
        ) { dialog, showNameDialog, showUpdate ->
          Triple(dialog, showNameDialog, showUpdate)
        },
        _multiadminCommissionDeviceTaskStatus,
      ) { devices, preferences, isNetworkValid, (dialog, showNameDialog, showUpdate), status ->
        HomeUiState(
          devices = devices.filter { !preferences.hideOfflineDevices || it.isOnline },
          showOfflineDevices = !preferences.hideOfflineDevices,
          showNetworkValidityWarning = !isNetworkValid,
          multiadminCommissionDeviceTaskStatus = status,
          msgDialogInfo = dialog,
          showNewDeviceNameAlertDialog = showNameDialog,
          showUpdatePlayServicesDialog = showUpdate,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

  fun onResume(intent: Intent) {
    _showUpdatePlayServicesDialog.value = playServices.isUpdateRequired()
    if (!isMultiAdminCommissioning(intent)) {
      startMonitoringStateChanges()
    } else if (_multiadminCommissionDeviceTaskStatus.value == TaskStatus.NotStarted) {
      _multiadminCommissionDeviceTaskStatus.value = TaskStatus.InProgress
      launchCommissioning { deviceCommissioner.createMultiAdminIntentSender(intent) }
    }
  }

  fun onPause() = stopMonitoringStateChanges()

  fun onAddDeviceClick() {
    stopMonitoringStateChanges()
    launchCommissioning { deviceCommissioner.createIntentSender() }
  }

  fun onUpdatePlayServicesClick() = playServices.openInStore()

  private fun launchCommissioning(createIntentSender: suspend () -> IntentSender) {
    viewModelScope.launch {
      runCatching { createIntentSender() }
        .onSuccess { _effects.send(HomeEffect.LaunchCommissioning(it)) }
        .onFailure {
          Timber.e(it)
          if (it is CommissioningWindowExpiringException) {
            showMsgDialog(
              titleRes = R.string.home_device_add_commissioning_window_expiration_title,
              messageRes = R.string.home_device_add_commissioning_window_expiration_message,
              messageArgs = listOf(it.secondsLeft),
            )
          } else {
            showMsgDialog(
              R.string.home_device_add_intent_sender_failed_title,
              it.toString(),
            )
          }
        }
    }
  }

  fun onCommissioningResult(resultCode: Int, data: Intent?) {
    if (resultCode == Activity.RESULT_OK) {
      commissionedDevice = deviceCommissioner.parseResult(resultCode, data)
      _showNewDeviceNameAlertDialog.value = true
    } else if (resultCode != 0) {
      showMsgDialog(
        titleRes = R.string.home_device_add_commissioning_failed_title,
        messageRes = R.string.home_device_add_commissioning_failed_message,
        messageArgs = listOf(resultCode),
      )
    }
  }

  fun onCommissionedDeviceNameCaptured(deviceName: String) {
    _showNewDeviceNameAlertDialog.value = false
    val result = commissionedDevice ?: return
    viewModelScope.launch {
      val nodeId = result.nodeId
      runCatching {
        val vendorName = clustersHelper.readBasicClusterVendorNameAttribute(nodeId.toLong())
        val productName = clustersHelper.readBasicClusterProductNameAttribute(nodeId.toLong())
        val endpoints =
          clustersHelper
            .fetchDeviceMatterInfo(nodeId)
            .filter {
              it.endpointId != EndpointId(0u) && it.serverClusters.contains(Clusters.OnOff.ID)
            }
            .ifEmpty {
              clustersHelper.fetchDeviceMatterInfo(nodeId).filter {
                it.endpointId != EndpointId(0u)
              }
            }
            .map { info ->
              Endpoint(
                id = info.endpointId,
                label = deviceName,
                deviceTypes = info.types.map { DeviceType(it) },
                supportsLevelControl = info.serverClusters.contains(Clusters.LevelControl.ID),
                supportsColorTemperature =
                  info.serverClusters.contains(Clusters.ColorControl.ID) &&
                    clustersHelper
                      .readColorControlClusterAttributeList(
                        nodeId,
                        info.endpointId,
                      )
                      .contains(Clusters.ColorControl.Attributes.ColorTemperatureMireds.ID),
              )
            }
        devicesRepository.addOrUpdateDevice(
          Device(
            id = nodeId,
            vendor =
              Vendor(
                id = result.vendorId,
                name = vendorName,
              ),
            product =
              Product(
                id = result.productId,
                name = productName,
              ),
            name = deviceName,
            isOnline = true,
            endpoints = endpoints,
          ),
        )
        clustersHelper.writeBasicClusterNodeLabelAttribute(nodeId.toLong(), deviceName)
      }
        .onFailure {
          Timber.e(it, "Failed to add commissioned nodeId=$nodeId")
          showMsgDialog(R.string.add_device_to_repository_failed, it.message)
        }
    }
  }

  fun updateDeviceStateOn(nodeId: NodeId, isOn: Boolean) {
    viewModelScope.launch {
      runCatching {
        val device = devicesRepository.getDevice(nodeId)
        val primaryEndpoint = device.primaryEndpoint
        clustersHelper.setOnOffDeviceStateOnOffCluster(
          nodeId.toLong(),
          isOn,
          primaryEndpoint.id,
        )
        devicesRepository.upsertEndpointState(
          nodeId,
          primaryEndpoint.id,
          true,
          isOn,
          primaryEndpoint.level,
          primaryEndpoint.colorTemperature,
        )
      }
        .onFailure {
          if (it.isCommunicationTimeoutError())
            devicesRepository.updateNodeOnlineState(nodeId, false)
          Timber.e(it, "Failed to update device power nodeId=$nodeId")
        }
    }
  }

  private fun startMonitoringStateChanges() {
    viewModelScope.launch {
      fabricRepository.getDevices().forEach { basicDevice ->
        val device =
          runCatching { devicesRepository.getDevice(basicDevice.id) }.getOrNull() ?: return@forEach
        runCatching {
          subscriptionHelper.awaitSubscribeToPeriodicUpdates(
            chipClient.getConnectedDevicePointer(device.id),
            object : SubscriptionHelper.SubscriptionEstablishedCallbackForDevice(device.id) {
              override fun onSubscriptionEstablished(subscriptionId: Long) {
                viewModelScope.launch { devicesRepository.updateNodeOnlineState(device.id, true) }
              }
            },
            object : SubscriptionHelper.ResubscriptionAttemptCallbackForDevice(device.id) {
              override fun onResubscriptionAttempt(
                terminationCause: Int,
                nextResubscribeIntervalMsec: Int,
              ) {
                super.onResubscriptionAttempt(
                  terminationCause,
                  nextResubscribeIntervalMsec,
                )
                viewModelScope.launch { devicesRepository.updateNodeOnlineState(device.id, false) }
              }
            },
            object : SubscriptionHelper.ReportCallbackForDevice(device.id) {
              override fun onError(
                attributePath: chip.devicecontroller.model.ChipAttributePath?,
                eventPath: chip.devicecontroller.model.ChipEventPath?,
                ex: Exception,
              ) {
                if (ex.isCommunicationTimeoutError()) {
                  viewModelScope.launch {
                    devicesRepository.updateNodeOnlineState(device.id, false)
                  }
                }
              }

              override fun onReport(nodeState: NodeState) {
                updateEndpointFromReport(device.id, nodeState)
              }
            },
          )
        }
          .onFailure { Timber.w(it, "Failed to subscribe nodeId=${device.id}") }
      }
    }
  }

  private fun stopMonitoringStateChanges() {
    viewModelScope.launch {
      fabricRepository.getDevices().forEach { device ->
        runCatching {
          subscriptionHelper.awaitUnsubscribeToPeriodicUpdates(
            chipClient.getConnectedDevicePointer(device.id),
          )
        }
          .onFailure { Timber.w(it, "Failed to unsubscribe nodeId=${device.id}") }
      }
    }
  }

  private fun updateEndpointFromReport(nodeId: NodeId, nodeState: NodeState) {
    viewModelScope.launch {
      val endpoint =
        runCatching { devicesRepository.getDevice(nodeId).primaryEndpoint }.getOrNull()
          ?: return@launch
      val on =
        subscriptionHelper.extractAttribute(
          nodeState,
          endpoint.id,
          Clusters.OnOff.ID,
          Clusters.OnOff.Attributes.OnOff.ID.toLong(),
        ) as Boolean? ?: return@launch
      devicesRepository.upsertEndpointState(
        nodeId,
        endpoint.id,
        true,
        on,
        endpoint.level,
        endpoint.colorTemperature,
      )
    }
  }

  fun showMsgDialog(@StringRes titleRes: Int, msg: String?) {
    _msgDialogInfo.value = DialogInfo(titleRes = titleRes, message = msg)
  }

  fun showMsgDialog(
    @StringRes titleRes: Int,
    @StringRes messageRes: Int,
    messageArgs: List<Any> = emptyList(),
  ) {
    _msgDialogInfo.value =
      DialogInfo(titleRes = titleRes, messageRes = messageRes, messageArgs = messageArgs)
  }

  fun dismissMsgDialog() {
    _msgDialogInfo.value = null
  }
}
