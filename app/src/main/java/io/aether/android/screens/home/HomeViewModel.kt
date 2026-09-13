// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.activity.result.ActivityResult
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import chip.devicecontroller.AttestationInfo
import chip.devicecontroller.DeviceAttestationDelegate
import chip.devicecontroller.model.NodeState
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.CommissioningResult
import com.google.android.gms.home.matter.commissioning.DeviceInfo
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS
import io.aether.android.MatterEndpoint
import io.aether.android.MatterFabricState
import io.aether.android.MatterNode
import io.aether.android.PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS
import io.aether.android.R
import io.aether.android.STATE_CHANGES_MONITORING_MODE
import io.aether.android.StateChangesMonitoringMode
import io.aether.android.TaskStatus
import io.aether.android.UserPreferences
import io.aether.android.chip.ChipClient
import io.aether.android.chip.ClustersHelper
import io.aether.android.chip.SubscriptionHelper
import io.aether.android.chip.isCommunicationTimeoutError
import io.aether.android.commissioning.AppCommissioningService
import io.aether.android.data.DevicesRepository
import io.aether.android.data.DevicesStateRepository
import io.aether.android.data.UserPreferencesRepository
import io.aether.android.endpointIdTyped
import io.aether.android.matter.Clusters
import io.aether.android.matter.DeviceTypeId
import io.aether.android.matter.EndpointId
import io.aether.android.matter.NodeId
import io.aether.android.matter.toDeviceTypeId
import io.aether.android.matter.toEndpointId
import io.aether.android.matter.toNodeId
import io.aether.android.matter.toProductId
import io.aether.android.matter.toVendorId
import io.aether.android.screens.common.DialogInfo
import io.aether.android.supportsColorTemperature
import io.aether.android.supportsLevelControl
import java.time.LocalDateTime
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

// -----------------------------------------------------------------------------
// Data structures

/**
 * Encapsulates all of the information on a specific device. Note that the app currently only
 * supports Matter devices with server attribute "ON/OFF".
 */
data class DeviceUiModel(
    // Device information that is persisted in a Proto DataStore. See DevicesRepository.
    val node: MatterNode,
    val endpoint: MatterEndpoint,

    // Device state information that is retrieved dynamically.
    // Whether the device is online or offline.
    var isOnline: Boolean,
    // Whether the device is on or off.
    var isOn: Boolean,
    // Level of device
    var level: Int = 0,
    // Color temperature of device
    var colorTemperature: Int = 0,
) {
  val nodeId: NodeId
    get() = node.nodeId.toNodeId()

  val name: String
    get() = if (node.name.isNotBlank()) node.name else endpoint.label

  val deviceTypeId: DeviceTypeId
    get() = endpoint.deviceTypesList.firstOrNull()?.toLong()?.toDeviceTypeId() ?: DeviceTypeId(0u)
}

/**
 * UI model that encapsulates the information about the devices to be displayed on the Home screen.
 */
data class DevicesListUiModel(
    // The list of devices.
    val devices: List<DeviceUiModel>,
    // Whether offline devices should be shown.
    val showOfflineDevices: Boolean,
)

data class HomeUiState(
    // Lifecycle Flags
    val isFirstTimeLoading: Boolean = false,
    val isBackgroundRefreshing: Boolean = false,
    val isOnline: Boolean = false,
    @field:StringRes val errorMessageRes: Int? = null,

    // Core Screen Data
    val devices: List<DeviceUiModel> = emptyList(),
    val showOfflineDevices: Boolean = true,
    val multiadminCommissionDeviceTaskStatus: TaskStatus = TaskStatus.NotStarted,
    val deviceAttestationFailureIgnored: Boolean = true,

    // UI Overlay / Dialog States
    val msgDialogInfo: DialogInfo? = null,
    val showNewDeviceNameAlertDialog: Boolean = false,
)

// -----------------------------------------------------------------------------
// ViewModel

/** The ViewModel for the [HomeScreen]. */
@HiltViewModel
class HomeViewModel
@Inject
constructor(
    private val devicesRepository: DevicesRepository,
    private val devicesStateRepository: DevicesStateRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val clustersHelper: ClustersHelper,
    private val chipClient: ChipClient,
    private val subscriptionHelper: SubscriptionHelper,
) : ViewModel() {

  // Controls whether the "Message" AlertDialog should be shown in the UI.
  private var _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)

  // Controls whether the "New Device" AlertDialog should be shown in the UI.
  private var _showNewDeviceNameAlertDialog = MutableStateFlow(false)

  /** The current status of multiadmin commissioning. */
  private val _multiadminCommissionDeviceTaskStatus =
      MutableStateFlow<TaskStatus>(TaskStatus.NotStarted)

  // Controls whether a Device Attestation failure is ignored or not.
  // FIXME: set to true for now until issues with attestation resolved.
  private var _deviceAttestationFailureIgnored = MutableStateFlow(true)

  // Controls whether a periodic ping to the devices is enabled or not.
  private var devicesPeriodicPingEnabled: Boolean = true

  // Saves the result of the GPS Commissioning action (step 4).
  // It is then used in step 5 to complete the commissioning.
  private var gpsCommissioningResult: CommissioningResult? = null

  // -----------------------------------------------------------------------------------------------
  // Repositories handling.

  // The initial setup event which triggers the Home screen to get the data it needs.
  // TODO: Clarify if this is really necessary and how that works?
  init {
    liveData { emit(devicesRepository.getAllDevices()) }
    liveData { emit(devicesStateRepository.getAllDevicesState()) }
    liveData { emit(userPreferencesRepository.getData()) }
  }

  private val devicesStateFlow = devicesStateRepository.devicesStateFlow
  private val userPreferencesFlow = userPreferencesRepository.userPreferencesFlow

  // Every time the list of devices or user preferences are updated (emit is triggered),
  // we recreate the DevicesListUiModel
  private val devicesListUiModelFlow =
      combine(devicesStateFlow, userPreferencesFlow) {
          devicesStates: MatterFabricState,
          userPreferences: UserPreferences ->
        Timber.d("*** devicesListUiModelFlow changed ***")
        return@combine DevicesListUiModel(
            devices = processDevices(devicesStates, userPreferences),
            showOfflineDevices = !userPreferences.hideOfflineDevices,
        )
      }

  val uiState: StateFlow<HomeUiState> =
      combine(
              devicesListUiModelFlow,
              _msgDialogInfo.asStateFlow(),
              _showNewDeviceNameAlertDialog.asStateFlow(),
              _multiadminCommissionDeviceTaskStatus.asStateFlow(),
              _deviceAttestationFailureIgnored.asStateFlow(),
          ) {
              devicesUiModel,
              msgDialogInfo,
              showNewDeviceNameAlertDialog,
              multiadminCommissionDeviceTaskStatus,
              deviceAttestationFailureIgnored,
            ->
            HomeUiState(
                isFirstTimeLoading = false,
                isBackgroundRefreshing = false,
                isOnline = false,
                errorMessageRes = null,
                devices = devicesUiModel.devices,
                showOfflineDevices = devicesUiModel.showOfflineDevices,
                multiadminCommissionDeviceTaskStatus = multiadminCommissionDeviceTaskStatus,
                deviceAttestationFailureIgnored = deviceAttestationFailureIgnored,
                msgDialogInfo = msgDialogInfo,
                showNewDeviceNameAlertDialog = showNewDeviceNameAlertDialog,
            )
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

  private fun processDevices(
      devicesStates: MatterFabricState,
      userPreferences: UserPreferences,
  ): List<DeviceUiModel> {
    val devicesUiModel = ArrayList<DeviceUiModel>()
    // Show only one entry per physical node (grouped by nodeId).
    // Among endpoints for the same node, pick the one with the lowest endpoint number as
    // the representative shown on the home screen.
    val sortedNodes = devicesStates.nodesList.sortedBy { it.nodeId }
    sortedNodes.forEach { node ->
      val nId = node.nodeId.toNodeId()
      Timber.d("Processing device nodeId=$nId")
      val endpointState = node.endpointsList.minByOrNull { it.endpointId }
      if (userPreferences.hideOfflineDevices) {
        if (!node.online) return@forEach
      }
      if (endpointState == null) {
        Timber.d("    nodeId setting default value for state")
        devicesUiModel.add(
            DeviceUiModel(
                node = node,
                endpoint = MatterEndpoint.getDefaultInstance(),
                isOnline = node.online,
                isOn = false,
            )
        )
      } else {
        Timber.d("    nodeId setting its own value for state")
        devicesUiModel.add(
            DeviceUiModel(
                node = node,
                endpoint = endpointState,
                isOnline = node.online,
                endpointState.on,
                endpointState.level,
                endpointState.colorTemperature,
            )
        )
      }
    }
    return devicesUiModel
  }

  // -----------------------------------------------------------------------------------------------
  // Commission Device
  //
  // See "docs/Google Home Mobile SDK.pdf" for a good overview of all the artifacts needed
  // to transfer control from the sample app's UI to the GPS CommissionDevice UI, and get a result
  // back.

  /**
   * Sample app has been invoked for multi-admin commissionning. TODO: Can we do it without going
   * through GMSCore? All we're missing is network location.
   */
  fun multiadminCommissioning(intent: Intent, context: Context) {
    Timber.d("Starting multi-admin commissioning")

    val sharedDeviceData = fromIntent(intent)
    Timber.d("Starting multi-admin commissioning data=$sharedDeviceData")
    Timber.d("Multi-admin pairing code=${sharedDeviceData.manualPairingCode}")

    val commissionRequestBuilder =
        CommissioningRequest.builder()
            .setCommissioningService(ComponentName(context, AppCommissioningService::class.java))

    // EXTRA_COMMISSIONING_WINDOW_EXPIRATION is a hint of how much time is remaining in the
    // commissioning window for multi-admin. It is based on the current system uptime.
    // If the user takes too long to select the target commissioning app, then there's not
    // enougj time to complete the multi-admin commissioning and we message it to the user.
    val commissioningWindowExpirationMillis =
        intent.getLongExtra(EXTRA_COMMISSIONING_WINDOW_EXPIRATION, -1L)
    val currentUptimeMillis = SystemClock.elapsedRealtime()
    val timeLeftSeconds = (commissioningWindowExpirationMillis - currentUptimeMillis) / 1000
    Timber.d(
        "commissionDevice: TargetCommissioner for MultiAdmin. " +
            "uptimeMillis=$currentUptimeMillis " +
            "commissioningWindowExpirationMillis=$commissioningWindowExpirationMillis " +
            "-> expires in $timeLeftSeconds seconds"
    )

    if (commissioningWindowExpirationMillis == -1L) {
      Timber.e(
          "EXTRA_COMMISSIONING_WINDOW_EXPIRATION not specified in multi-admin call. " +
              "Still going ahead with the multi-admin though."
      )
    } else if (timeLeftSeconds < MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS) {
      showMsgDialog(
          "Commissioning Window Expiration",
          "The commissioning window will " +
              "expire in ${timeLeftSeconds} seconds, not long enough to " +
              "complete the commissioning.\n\n" +
              "In the future, please select the target commissioning application faster " +
              "to avoid this situation.",
      )
      return
    }

    val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
    commissionRequestBuilder.setDeviceNameHint(deviceName)

    val vendorId = intent.getIntExtra(EXTRA_VENDOR_ID, -1)
    val productId = intent.getIntExtra(EXTRA_PRODUCT_ID, -1)
    val deviceInfo = DeviceInfo.builder().setProductId(productId).setVendorId(vendorId).build()
    commissionRequestBuilder.setDeviceInfo(deviceInfo)

    val manualPairingCode = intent.getStringExtra(EXTRA_MANUAL_PAIRING_CODE)
    commissionRequestBuilder.setOnboardingPayload(manualPairingCode)

    val commissioningRequest = commissionRequestBuilder.build()

    Timber.d(
        "multiadmin: commissioningRequest " +
            "onboardingPayload=${commissioningRequest.onboardingPayload} " +
            "vendorId=${commissioningRequest.deviceInfo!!.vendorId} " +
            "productId=${commissioningRequest.deviceInfo!!.productId}"
    )
  }

  // This is step 4 of the commissioning flow where GPS takes over.
  // We save the result we get from GPS, which will be used by commissionedDeviceNameCaptured
  // after the device name is captured.
  fun gpsCommissioningDeviceSucceeded(activityResult: ActivityResult) {
    gpsCommissioningResult =
        CommissioningResult.fromIntentSenderResult(activityResult.resultCode, activityResult.data)
    Timber.i("Device commissioned successfully deviceName=${gpsCommissioningResult!!.deviceName}")
    Timber.i(
        "Device commissioned successfully! DeviceDescriptor of device:\n" +
            "productId=${gpsCommissioningResult!!.commissionedDeviceDescriptor.productId}\n" +
            "vendorId=${gpsCommissioningResult!!.commissionedDeviceDescriptor.vendorId}\n" +
            "hashCode=${gpsCommissioningResult!!.commissionedDeviceDescriptor.hashCode()}"
    )

    // Now we need to capture the device name.
    _showNewDeviceNameAlertDialog.value = true
  }

  // Called when the device name has been captured in the UI.
  // This follows a successful gps commissioning (see gpsCommissioningDeviceSucceeded)
  fun onCommissionedDeviceNameCaptured(deviceName: String) {
    _showNewDeviceNameAlertDialog.value = false
    viewModelScope.launch {
      val nodeId = gpsCommissioningResult?.token?.toLong()?.toNodeId()!!
      // read device's vendor name and product name
      val vendorName =
          try {
            clustersHelper.readBasicClusterVendorNameAttribute(nodeId.toLong())
          } catch (ex: Exception) {
            Timber.e(ex, "Failed to read VendorName attribute")
            ""
          }

      val productName =
          try {
            clustersHelper.readBasicClusterProductNameAttribute(nodeId.toLong())
          } catch (ex: Exception) {
            Timber.e(ex, "Failed to read ProductName attribute")
            ""
          }

      try {
        val deviceMatterInfoList = clustersHelper.fetchDeviceMatterInfo(nodeId)
        val appEndpoints = deviceMatterInfoList.filter { info ->
          info.endpointId != EndpointId(0u) && info.serverClusters.contains(Clusters.OnOff.ID)
        }

        if (appEndpoints.isEmpty()) {
          // Fallback for devices that expose no application endpoints with On/Off cluster
          // (e.g. legacy or non-standard devices). Fall back to first non-root endpoint.
          val fallbackEndpointInfo = deviceMatterInfoList.firstOrNull { info ->
            info.endpointId != EndpointId(0u)
          }
          val commissionedDeviceTypes = fallbackEndpointInfo?.types ?: emptyList()
          val supportsLevel =
              fallbackEndpointInfo?.serverClusters?.contains(Clusters.LevelControl.ID) == true
          val supportsColorTemperature =
              if (
                  fallbackEndpointInfo?.serverClusters?.contains(Clusters.ColorControl.ID) == true
              ) {
                try {
                  clustersHelper
                      .readColorControlClusterAttributeList(
                          nodeId,
                          fallbackEndpointInfo.endpointId,
                      )
                      .contains(Clusters.ColorControl.Attributes.ColorTemperatureMireds.ID)
                } catch (e: Exception) {
                  Timber.w(
                      e,
                      "Could not read Color Control attribute list for endpoint ${fallbackEndpointInfo.endpointId}; assuming color temperature unsupported",
                  )
                  false
                }
              } else {
                false
              }
          val device =
              MatterEndpoint.newBuilder()
                  .setEndpointId(fallbackEndpointInfo?.endpointId?.toInt() ?: 1)
                  .setLabel(deviceName)
                  .setSupportsLevelControl(supportsLevel)
                  .setSupportsColorTemperature(supportsColorTemperature)
                  .addAllDeviceTypes(commissionedDeviceTypes.map { it.toInt() })
                  .build()
          devicesRepository.addOrUpdateEndpoint(
              nodeId = nodeId,
              nodeName = deviceName,
              vendorId =
                  (gpsCommissioningResult?.commissionedDeviceDescriptor?.vendorId ?: 0)
                      .toVendorId(),
              vendorName = vendorName,
              productId =
                  (gpsCommissioningResult?.commissionedDeviceDescriptor?.productId ?: 0)
                      .toProductId(),
              productName = productName,
              endpoint = device,
          )
          devicesStateRepository.addEndpointState(
              nodeId,
              device.endpointIdTyped(),
              isOnline = true,
              isOn = false,
              level = 0,
              colorTemperature = 0,
          )
        } else {
          appEndpoints.forEach { info ->
            val endpointDisplayName = deviceName
            val supportsLevel = info.serverClusters.contains(Clusters.LevelControl.ID)
            // Check the Color Control cluster's AttributeList to confirm that the optional
            // color temperature attribute (id 7) is actually present, not just the cluster.
            val supportsColorTemperature =
                if (info.serverClusters.contains(Clusters.ColorControl.ID)) {
                  try {
                    clustersHelper
                        .readColorControlClusterAttributeList(nodeId, info.endpointId)
                        .contains(Clusters.ColorControl.Attributes.ColorTemperatureMireds.ID)
                  } catch (e: Exception) {
                    Timber.w(
                        e,
                        "Could not read Color Control attribute list for endpoint ${info.endpointId}; assuming color temperature unsupported",
                    )
                    false
                  }
                } else {
                  false
                }

            val device =
                MatterEndpoint.newBuilder()
                    .setEndpointId(info.endpointId.toInt())
                    .setLabel(endpointDisplayName)
                    .setSupportsLevelControl(supportsLevel)
                    .setSupportsColorTemperature(supportsColorTemperature)
                    .addAllDeviceTypes(info.types.map { it.toInt() })
                    .build()
            devicesRepository.addOrUpdateEndpoint(
                nodeId = nodeId,
                nodeName = deviceName,
                vendorId =
                    (gpsCommissioningResult?.commissionedDeviceDescriptor?.vendorId ?: 0)
                        .toVendorId(),
                vendorName = vendorName,
                productId =
                    (gpsCommissioningResult?.commissionedDeviceDescriptor?.productId ?: 0)
                        .toProductId(),
                productName = productName,
                endpoint = device,
            )
            devicesStateRepository.addEndpointState(
                nodeId,
                device.endpointIdTyped(),
                isOnline = true,
                isOn = false,
                level = 0,
                colorTemperature = 0,
            )
          }
        }
      } catch (e: Exception) {
        val msg = "Failed to add device nodeId=$nodeId deviceName=$deviceName"
        Timber.e(e, msg)
        showMsgDialog(R.string.add_device_to_repository_failed, "$msg\n\n${e.message ?: e}")
      }

      // update device name
      try {
        clustersHelper.writeBasicClusterNodeLabelAttribute(nodeId.toLong(), deviceName)
      } catch (ex: Exception) {
        Timber.e(ex, "Failed to write NodeLabel")
        showMsgDialog(R.string.write_node_label_failed, ex.message ?: ex.toString())
      }
    }
  }

  // Called in Step 5 of the Device Commissioning flow when the GPS activity for
  // commissioning the device has failed.
  fun commissionDeviceFailed(resultCode: Int) {
    if (resultCode == 0) {
      // User simply wilfully exited from GPS commissioning.
      return
    }
    val title = "Commissioning the device failed"
    Timber.e(title)
    showMsgDialog(title, "result code: $resultCode")
  }

  fun updateDeviceStateOn(nodeId: NodeId, isOn: Boolean) {
    Timber.d("Updating device power nodeId=$nodeId isOn=$isOn")
    viewModelScope.launch {
      try {
        val node =
            devicesStateRepository.getAllDevicesState().nodesList.firstOrNull {
              it.nodeId == nodeId.toLong()
            } ?: return@launch
        val endpointDevice = node.endpointsList.minByOrNull { it.endpointId } ?: return@launch
        val endpoint = endpointDevice.endpointId
        Timber.d("Handling real device nodeId=$nodeId endpointId=$endpoint")
        clustersHelper.setOnOffDeviceStateOnOffCluster(
            nodeId.toLong(),
            isOn,
            endpoint.toEndpointId(),
        )
        val level =
            if (supportsLevelControl(endpointDevice)) {
              clustersHelper.getDeviceStateLevelControlCluster(
                  nodeId.toLong(),
                  endpoint.toEndpointId(),
              ) ?: 0
            } else {
              0
            }
        val colorTemperature =
            if (supportsColorTemperature(endpointDevice)) {
              clustersHelper.getColorTemperatureColorControlCluster(
                  nodeId.toLong(),
                  endpoint.toEndpointId(),
              ) ?: 0
            } else {
              0
            }
        devicesStateRepository.upsertEndpointState(
            nodeId,
            endpoint.toEndpointId(),
            true,
            isOn,
            level,
            colorTemperature,
        )
      } catch (e: Exception) {
        Timber.e(e, "Failed to update device power nodeId=$nodeId")
        if (e.isCommunicationTimeoutError()) {
          devicesStateRepository.updateNodeOnlineState(nodeId, isOnline = false)
        }
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // State Changes Monitoring

  /**
   * The way we monitor state changes is defined by constant [StateChangesMonitoringMode].
   * [StateChangesMonitoringMode.Subscription] is the preferred mode.
   * [StateChangesMonitoringMode.PeriodicRead] was used initially because of issues with
   * subscriptions. We left its associated code as it could be useful to some developers.
   */
  fun startMonitoringStateChanges() {
    when (STATE_CHANGES_MONITORING_MODE) {
      StateChangesMonitoringMode.Subscription -> subscribeToDevicesPeriodicUpdates()
      StateChangesMonitoringMode.PeriodicRead -> startDevicesPeriodicPing()
    }
  }

  fun stopMonitoringStateChanges() {
    when (STATE_CHANGES_MONITORING_MODE) {
      StateChangesMonitoringMode.Subscription -> unsubscribeToDevicesPeriodicUpdates()
      StateChangesMonitoringMode.PeriodicRead -> stopDevicesPeriodicPing()
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Subscription to periodic device updates.
  // See:
  //   - Spec section "8.5 Subscribe Interaction"
  //   - Matter primer:
  //
  // https://developers.home.google.com/matter/primer/interaction-model-reading#subscription_transaction

  private fun subscribeToDevicesPeriodicUpdates() {
    Timber.d("subscribeToDevicesPeriodicUpdates()")
    viewModelScope.launch {
      // Subscribe once per physical node (deduplicated by nodeId).
      val nodes = devicesStateRepository.getAllDevicesState().nodesList
      nodes.forEach { node ->
        val nId = node.nodeId.toNodeId()
        val endpointDevice = node.endpointsList.minByOrNull { it.endpointId } ?: return@forEach
        val endpoint = endpointDevice.endpointId
        val reportCallback =
            object : SubscriptionHelper.ReportCallbackForDevice(nId) {
              override fun onError(
                  attributePath: chip.devicecontroller.model.ChipAttributePath?,
                  eventPath: chip.devicecontroller.model.ChipEventPath?,
                  ex: Exception,
              ) {
                super.onError(attributePath, eventPath, ex)
                if (ex.isCommunicationTimeoutError()) {
                  viewModelScope.launch {
                    devicesStateRepository.updateNodeOnlineState(nId, isOnline = false)
                  }
                }
              }

              override fun onReport(nodeState: NodeState) {
                super.onReport(nodeState)
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
                Timber.d("Device power state onOffState=$onOffState")
                if (onOffState == null) {
                  Timber.e("Ignoring report with missing onOffState")
                  return
                }
                if (supportsLevelControl(endpointDevice) && levelState == null) {
                  Timber.e("Ignoring report with missing levelState")
                  return
                }
                if (supportsColorTemperature(endpointDevice) && colorTemperatureState == null) {
                  Timber.e("Ignoring report with missing colorTemperatureState")
                  return
                }
                val level = if (supportsLevelControl(endpointDevice)) levelState!! else 0
                val colorTemperature =
                    if (supportsColorTemperature(endpointDevice)) colorTemperatureState!! else 0
                viewModelScope.launch {
                  devicesStateRepository.upsertEndpointState(
                      nId,
                      endpoint.toEndpointId(),
                      isOnline = true,
                      isOn = onOffState,
                      level = level,
                      colorTemperature = colorTemperature,
                  )
                }
              }
            }

        try {
          val connectedDevicePointer = chipClient.getConnectedDevicePointer(nId)
          subscriptionHelper.awaitSubscribeToPeriodicUpdates(
              connectedDevicePointer,
              object : SubscriptionHelper.SubscriptionEstablishedCallbackForDevice(nId) {
                override fun onSubscriptionEstablished(subscriptionId: Long) {
                  super.onSubscriptionEstablished(subscriptionId)
                  viewModelScope.launch {
                    devicesStateRepository.updateNodeOnlineState(nId, isOnline = true)
                  }
                }
              },
              SubscriptionHelper.ResubscriptionAttemptCallbackForDevice(nId),
              reportCallback,
          )
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connected device pointer nodeId=$nId")
          if (e.isCommunicationTimeoutError()) {
            devicesStateRepository.updateNodeOnlineState(nId, isOnline = false)
          }
          return@forEach
        }
      }
    }
  }

  private fun unsubscribeToDevicesPeriodicUpdates() {
    Timber.d("unsubscribeToPeriodicUpdates()")
    viewModelScope.launch {
      // Unsubscribe once per physical node (deduplicated by nodeId).
      val nodes = devicesStateRepository.getAllDevicesState().nodesList
      nodes.forEach { node ->
        val nId = node.nodeId.toNodeId()
        try {
          val connectedDevicePtr = chipClient.getConnectedDevicePointer(nId)
          subscriptionHelper.awaitUnsubscribeToPeriodicUpdates(connectedDevicePtr)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connected device pointer nodeId=$nId")
          if (e.isCommunicationTimeoutError()) {
            devicesStateRepository.updateNodeOnlineState(nId, isOnline = false)
          }
          return@forEach
        }
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Task that runs periodically to update the devices state.

  private fun startDevicesPeriodicPing() {
    if (PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS == -1) {
      return
    }
    Timber.d(
        "${LocalDateTime.now()} startDevicesPeriodicPing every $PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS seconds"
    )
    devicesPeriodicPingEnabled = true
    runDevicesPeriodicPing()
  }

  private fun runDevicesPeriodicPing() {
    viewModelScope.launch {
      while (devicesPeriodicPingEnabled) {
        // Poll each endpoint entry.
        val nodes = devicesStateRepository.getAllDevicesState().nodesList
        nodes.forEach { node ->
          val nId = node.nodeId.toNodeId()
          Timber.d("Pinging device nodeId=$nId")
          node.endpointsList
              .sortedBy { it.endpointId }
              .forEach { endpointDevice ->
                val endpoint = endpointDevice.endpointId
                val hasLevelControl = supportsLevelControl(endpointDevice)
                val hasColorTemperature = supportsColorTemperature(endpointDevice)
                var isOn =
                    clustersHelper.getDeviceStateOnOffCluster(nId.toLong(), endpoint.toEndpointId())
                val levelRead =
                    if (hasLevelControl) {
                      clustersHelper.getDeviceStateLevelControlCluster(
                          nId.toLong(),
                          endpoint.toEndpointId(),
                      )
                    } else {
                      null
                    }
                val colorTemperatureRead =
                    if (hasColorTemperature) {
                      clustersHelper.getColorTemperatureColorControlCluster(
                          nId.toLong(),
                          endpoint.toEndpointId(),
                      )
                    } else {
                      null
                    }
                var level: Int
                var colorTemperature: Int
                val isOnline: Boolean
                if (
                    isOn == null ||
                        (hasLevelControl && levelRead == null) ||
                        (hasColorTemperature && colorTemperatureRead == null)
                ) {
                  Timber.e("Cannot get device state; marking device offline")
                  isOn = false
                  isOnline = false
                  level = 0
                  colorTemperature = 0
                } else {
                  level = if (hasLevelControl) levelRead!! else 0
                  colorTemperature = if (hasColorTemperature) colorTemperatureRead!! else 0
                  isOnline = true
                }
                Timber.d("Device ping nodeId=$nId isOnline=$isOnline isOn=$isOn")
                // TODO: only need to do it if state has changed
                devicesStateRepository.upsertEndpointState(
                    nId,
                    endpoint.toEndpointId(),
                    isOnline = isOnline,
                    isOn = isOn,
                    level = level,
                    colorTemperature = colorTemperature,
                )
              }
        }
        delay(PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS * 1000L)
      }
    }
  }

  private fun stopDevicesPeriodicPing() {
    devicesPeriodicPingEnabled = false
  }

  // -----------------------------------------------------------------------------------------------
  // Device Attestation

  fun setDeviceAttestationDelegate(
      failureTimeoutSeconds: Int = DEVICE_ATTESTATION_FAILED_TIMEOUT_SECONDS
  ) {
    Timber.d("setDeviceAttestationDelegate")
    chipClient.chipDeviceController.setDeviceAttestationDelegate(failureTimeoutSeconds) {
        devicePtr,
        _,
        errorCode ->
      Timber.d(
          "Device attestation errorCode: $errorCode, " +
              "Look at 'src/credentials/attestation_verifier/DeviceAttestationVerifier.h' " +
              "AttestationVerificationResult enum to understand the errors"
      )

      if (errorCode == STATUS_PAIRING_SUCCESS) {
        Timber.d("Device attestation succeeded")
        viewModelScope.launch {
          chipClient.chipDeviceController.continueCommissioning(devicePtr, true)
        }
      } else {
        Timber.e("Device attestation failed errorCode=$errorCode")
        // Ideally, we'd want to show a Dialog and ask the user whether the attestation
        // failure should be ignored or not.
        // Unfortunately, the GPS commissioning API is in control at this point, and the
        // Dialog will only show up after GPS gives us back control.
        // So, we simply ignore the attestation failure for now.
        // TODO: Add a new setting to control that behavior.
        _deviceAttestationFailureIgnored.value = true
        Timber.w("Ignoring attestation failure.")
        viewModelScope.launch {
          chipClient.chipDeviceController.continueCommissioning(devicePtr, true)
        }
      }
    }
  }

  fun resetDeviceAttestationDelegate() {
    Timber.d("resetDeviceAttestationDelegate")
    chipClient.chipDeviceController.setDeviceAttestationDelegate(0, EmptyAttestationDelegate())
  }

  private class EmptyAttestationDelegate : DeviceAttestationDelegate {
    override fun onDeviceAttestationCompleted(
        devicePtr: Long,
        attestationInfo: AttestationInfo,
        errorCode: Int,
    ) {}
  }

  // -----------------------------------------------------------------------------------------------
  // UI State update

  fun showMsgDialog(title: String, msg: String) {
    _msgDialogInfo.value = DialogInfo(title = title, message = msg)
  }

  fun showMsgDialog(@StringRes titleRes: Int, msg: String?) {
    _msgDialogInfo.value = DialogInfo(message = msg, titleRes = titleRes)
  }

  // Called after user dismisses the Info dialog. If we don't consume, a config change redisplays
  // the
  // alert dialog.
  fun dismissMsgDialog() {
    _msgDialogInfo.value = null
  }

  fun setMultiadminCommissioningTaskStatus(taskStatus: TaskStatus) {
    _multiadminCommissionDeviceTaskStatus.value = taskStatus
  }

  // ---------------------------------------------------------------------------
  // Companion object

  companion object {
    private const val STATUS_PAIRING_SUCCESS = 0

    /** Set for the fail-safe timer before onDeviceAttestationFailed is invoked. */
    private const val DEVICE_ATTESTATION_FAILED_TIMEOUT_SECONDS = 60
  }
}
