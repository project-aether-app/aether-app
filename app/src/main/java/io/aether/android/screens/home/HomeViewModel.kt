// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.home

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chip.devicecontroller.model.NodeState
import com.google.android.gms.home.matter.commissioning.CommissioningResult
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.R
import io.aether.android.TaskStatus
import io.aether.android.chip.ChipClient
import io.aether.android.chip.ClustersHelper
import io.aether.android.chip.SubscriptionHelper
import io.aether.android.chip.isCommunicationTimeoutError
import io.aether.android.data.DeviceRepository
import io.aether.android.data.FabricRepository
import io.aether.android.data.UserPreferencesRepository
import io.aether.android.data.models.Device
import io.aether.android.data.models.Endpoint
import io.aether.android.data.models.Product
import io.aether.android.data.models.Vendor
import io.aether.android.matter.Clusters
import io.aether.android.matter.DeviceTypeId
import io.aether.android.matter.EndpointId
import io.aether.android.matter.NodeId
import io.aether.android.matter.toNodeId
import io.aether.android.matter.toProductId
import io.aether.android.matter.toVendorId
import io.aether.android.screens.common.DialogInfo
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

data class HomeUiState(
    val isFirstTimeLoading: Boolean = false,
    val isBackgroundRefreshing: Boolean = false,
    @field:StringRes val errorMessageRes: Int? = null,
    val devices: List<Device> = emptyList(),
    val showOfflineDevices: Boolean = true,
    val multiadminCommissionDeviceTaskStatus: TaskStatus = TaskStatus.NotStarted,
    val deviceAttestationFailureIgnored: Boolean = true,
    val msgDialogInfo: DialogInfo? = null,
    val showNewDeviceNameAlertDialog: Boolean = false,
)

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
) : ViewModel() {
  private val _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)
  private val _showNewDeviceNameAlertDialog = MutableStateFlow(false)
  private val _multiadminCommissionDeviceTaskStatus =
      MutableStateFlow<TaskStatus>(TaskStatus.NotStarted)
  private var commissioningResult: CommissioningResult? = null

  val uiState: StateFlow<HomeUiState> =
      combine(
              fabricRepository.devicesFlow,
              userPreferencesRepository.userPreferencesFlow,
              _msgDialogInfo,
              _showNewDeviceNameAlertDialog,
              _multiadminCommissionDeviceTaskStatus,
          ) { devices, preferences, dialog, showNameDialog, status ->
            HomeUiState(
                devices = devices.filter { !preferences.hideOfflineDevices || it.isOnline },
                showOfflineDevices = !preferences.hideOfflineDevices,
                multiadminCommissionDeviceTaskStatus = status,
                msgDialogInfo = dialog,
                showNewDeviceNameAlertDialog = showNameDialog,
            )
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

  fun multiadminCommissioning(intent: Intent, context: Context) = Unit

  fun gpsCommissioningDeviceSucceeded(activityResult: ActivityResult) {
    commissioningResult =
        CommissioningResult.fromIntentSenderResult(activityResult.resultCode, activityResult.data)
    _showNewDeviceNameAlertDialog.value = true
  }

  fun onCommissionedDeviceNameCaptured(deviceName: String) {
    _showNewDeviceNameAlertDialog.value = false
    val result = commissioningResult ?: return
    viewModelScope.launch {
      val nodeId = result.token?.toLong()?.toNodeId() ?: return@launch
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
                      deviceTypeId = info.types.firstOrNull() ?: DeviceTypeId(0u),
                      supportsLevelControl = info.serverClusters.contains(Clusters.LevelControl.ID),
                      supportsColorTemperature =
                          info.serverClusters.contains(Clusters.ColorControl.ID) &&
                              clustersHelper
                                  .readColorControlClusterAttributeList(nodeId, info.endpointId)
                                  .contains(
                                      Clusters.ColorControl.Attributes.ColorTemperatureMireds.ID,
                                  ),
                  )
                }
        devicesRepository.addOrUpdateDevice(
            Device(
                id = nodeId,
                vendor =
                    Vendor(
                        id = result.commissionedDeviceDescriptor.vendorId.toVendorId(),
                        name = vendorName,
                    ),
                product =
                    Product(
                        id = result.commissionedDeviceDescriptor.productId.toProductId(),
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

  fun commissionDeviceFailed(resultCode: Int) {
    if (resultCode != 0)
        showMsgDialog("Commissioning the device failed", "result code: $resultCode")
  }

  fun updateDeviceStateOn(nodeId: NodeId, isOn: Boolean) {
    viewModelScope.launch {
      runCatching {
        val device = devicesRepository.getDevice(nodeId)
        val endpoint = device.primaryEndpoint ?: return@launch
        clustersHelper.setOnOffDeviceStateOnOffCluster(nodeId.toLong(), isOn, endpoint.id)
        devicesRepository.upsertEndpointState(
            nodeId,
            endpoint.id,
            true,
            isOn,
            endpoint.level,
            endpoint.colorTemperature,
        )
      }
          .onFailure {
            if (it.isCommunicationTimeoutError())
                devicesRepository.updateNodeOnlineState(nodeId, false)
            Timber.e(it, "Failed to update device power nodeId=$nodeId")
          }
    }
  }

  fun startMonitoringStateChanges() {
    viewModelScope.launch {
      fabricRepository.getDevices().forEach { basicDevice ->
        val device =
            runCatching { devicesRepository.getDevice(basicDevice.id) }.getOrNull()
                ?: return@forEach
        val endpoint = device.primaryEndpoint ?: return@forEach
        runCatching {
          subscriptionHelper.awaitSubscribeToPeriodicUpdates(
              chipClient.getConnectedDevicePointer(device.id),
              object : SubscriptionHelper.SubscriptionEstablishedCallbackForDevice(device.id) {
                override fun onSubscriptionEstablished(subscriptionId: Long) {
                  viewModelScope.launch {
                    devicesRepository.updateNodeOnlineState(device.id, true)
                  }
                }
              },
              SubscriptionHelper.ResubscriptionAttemptCallbackForDevice(device.id),
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
                  updateEndpointFromReport(device, endpoint, nodeState)
                }
              },
          )
        }
            .onFailure { Timber.w(it, "Failed to subscribe nodeId=${device.id}") }
      }
    }
  }

  fun stopMonitoringStateChanges() {
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

  private fun updateEndpointFromReport(
      device: Device,
      endpoint: Endpoint,
      nodeState: NodeState,
  ) {
    val on =
        subscriptionHelper.extractAttribute(
            nodeState,
            endpoint.id,
            Clusters.OnOff.ID,
            Clusters.OnOff.Attributes.OnOff.ID.toLong(),
        ) as Boolean? ?: return
    viewModelScope.launch {
      devicesRepository.upsertEndpointState(
          device.id,
          endpoint.id,
          true,
          on,
          endpoint.level,
          endpoint.colorTemperature,
      )
    }
  }

  fun showMsgDialog(title: String, msg: String?) {
    _msgDialogInfo.value = DialogInfo(title = title, message = msg)
  }

  fun showMsgDialog(@StringRes titleRes: Int, msg: String?) {
    _msgDialogInfo.value = DialogInfo(titleRes = titleRes, message = msg)
  }

  fun dismissMsgDialog() {
    _msgDialogInfo.value = null
  }

  fun setMultiadminCommissioningTaskStatus(taskStatus: TaskStatus) {
    _multiadminCommissionDeviceTaskStatus.value = taskStatus
  }
}
