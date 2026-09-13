// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.protobuf.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.DISCRIMINATOR
import io.aether.android.Device
import io.aether.android.ITERATION
import io.aether.android.MatterFabricState
import io.aether.android.OPEN_COMMISSIONING_WINDOW_API
import io.aether.android.OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS
import io.aether.android.OpenCommissioningWindowApi
import io.aether.android.R
import io.aether.android.SETUP_PIN_CODE
import io.aether.android.chip.BasicInformationAttributes
import io.aether.android.chip.ChipClient
import io.aether.android.chip.ClustersHelper
import io.aether.android.data.DevicesRepository
import io.aether.android.data.DevicesStateRepository
import io.aether.android.matter.DeviceTypeId
import io.aether.android.matter.EndpointId
import io.aether.android.matter.NodeId
import io.aether.android.screens.common.DialogInfo
import io.aether.android.screens.shared.SetDeviceNameResult
import io.aether.android.screens.shared.SetDeviceNameUseCase
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import timber.log.Timber

data class DeviceSettingsUiState(
    // Lifecycle Flags
    val isFirstTimeLoading: Boolean = true,
    val isBackgroundRefreshing: Boolean = false,
    val isOnline: Boolean = false,
    @field:StringRes val errorMessageRes: Int? = null,

    // Core Screen Data
    val device: Device? = null,
    val basicInformation: BasicInformationAttributes? = null,
    val dateCommissioned: Timestamp? = null,

    // UI Overlay / Dialog States
    val msgDialogInfo: DialogInfo? = null,
    val showShareDeviceAlertDialog: Boolean = false,
    val showRemoveDeviceAlertDialog: Boolean = false,
    val showRemoveDeviceConfirmAlertDialog: Boolean = false,
    val deviceRemovalCompleted: Boolean = false,
    val pairingWindowOpenForDeviceSharing: Boolean = false,
)

private data class DeviceSettingsCoreData(
    val device: Device? = null,
    val basicInformation: BasicInformationAttributes? = null,
    val isFirstTimeLoading: Boolean = true,
    val isBackgroundRefreshing: Boolean = false,
    val errorMessageRes: Int? = null,
)

@HiltViewModel
class DeviceSettingsViewModel
@Inject
constructor(
    private val devicesRepository: DevicesRepository,
    private val devicesStateRepository: DevicesStateRepository,
    private val chipClient: ChipClient,
    private val clustersHelper: ClustersHelper,
    private val setDeviceNameUseCase: SetDeviceNameUseCase,
) : ViewModel() {

  private val refreshTrigger = MutableSharedFlow<NodeId>(replay = 1)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val deviceCoreDataState: StateFlow<DeviceSettingsCoreData> =
      refreshTrigger
          .flatMapLatest { nodeId ->
            flow {
              // Use existing state as a local cache: emit stale data immediately so the UI
              // never flashes "Loading" or shows unknown values while refreshing.
              val cached = deviceCoreDataState.value
              if (cached.device != null && cached.device.nodeId == nodeId) {
                emit(cached.copy(isBackgroundRefreshing = true))
              } else {
                emit(DeviceSettingsCoreData(isFirstTimeLoading = true))
              }
              coroutineScope {
                // Kick off the network read immediately (async) so total wait time is
                // bounded by the network round-trip, not storage + network sequentially.
                val networkDeferred = async {
                  runCatching { clustersHelper.readBasicInformationAttributes(nodeId) }
                      .onFailure { Timber.w(it, "Network read failed") }
                      .getOrNull()
                }
                // Fetch fresh data from storage (fast local read). getDevice() throws if the
                // device isn't found, so fall back to a stub device instead of propagating.
                val fallbackDevice = Device.newBuilder().setNodeId(nodeId).build()
                val deviceDeferred = async {
                  runCatching { devicesRepository.getDevice(nodeId) }
                      .onFailure { Timber.w(it, "Storage read failed") }
                      .getOrDefault(fallbackDevice)
                }
                val cachedBasicInfo =
                    cached.takeIf { it.device?.nodeId == nodeId }?.basicInformation
                // Race the local storage fetch against the timeout.
                val device =
                    select<Device> {
                      deviceDeferred.onAwait { it }
                      onTimeout(500.milliseconds) {
                        Timber.d("Storage read took too long. Emitting fallback first.")
                        // Emit fallback right away so the UI doesn't freeze, then keep waiting.
                        emit(
                            DeviceSettingsCoreData(
                                device = fallbackDevice,
                                basicInformation = cachedBasicInfo,
                                isFirstTimeLoading = false,
                                isBackgroundRefreshing = true,
                            )
                        )
                        deviceDeferred.await()
                      }
                    }
                // Emit storage-fresh device with cached basicInfo so e.g. a rename is
                // reflected immediately without waiting for the network round-trip.
                emit(
                    DeviceSettingsCoreData(
                        device = device,
                        basicInformation = cachedBasicInfo,
                        isFirstTimeLoading = false,
                        isBackgroundRefreshing = true,
                    )
                )
                // Await network result and update with live basic information.
                val basicInfo = networkDeferred.await()
                if (basicInfo != null) {
                  syncBasicInfoToStorage(nodeId, basicInfo)
                  emit(
                      DeviceSettingsCoreData(
                          device = device,
                          basicInformation = basicInfo,
                          isFirstTimeLoading = false,
                          isBackgroundRefreshing = false,
                      )
                  )
                } else {
                  emit(
                      DeviceSettingsCoreData(
                          device = device,
                          basicInformation = cachedBasicInfo,
                          isFirstTimeLoading = false,
                          isBackgroundRefreshing = false,
                      )
                  )
                }
              }
            }
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(5000),
              DeviceSettingsCoreData(),
          )

  fun loadDevice(nodeId: NodeId) {
    refreshTrigger.tryEmit(nodeId)
  }

  private suspend fun syncBasicInfoToStorage(
      nodeId: NodeId,
      basicInfo: BasicInformationAttributes,
  ) {
    runCatching {
      devicesRepository.updateNodeBasicInfo(
          nodeId,
          basicInfo.vendorId,
          basicInfo.vendorName,
          basicInfo.productId,
          basicInfo.productName,
          basicInfo.nodeLabel,
      )
    }
        .onFailure { Timber.e(it, "syncBasicInfoToStorage failed") }
  }

  private fun isDefaultTimestamp(timestamp: Timestamp): Boolean {
    return timestamp.seconds == 0L && timestamp.nanos == 0
  }

  // Controls whether the "Message" AlertDialog should be shown in the UI.
  private var _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)

  private var _showShareDeviceAlertDialog = MutableStateFlow(false)

  private var _showRemoveDeviceAlertDialog = MutableStateFlow(false)

  private var _showRemoveDeviceConfirmAlertDialog = MutableStateFlow(false)

  // Communicates to the UI that removal of the device has completed successfully.
  private var _deviceRemovalCompleted = MutableStateFlow(false)

  // Communicates to the UI that the pairing window is open for device sharing.
  private var _pairingWindowOpenForDeviceSharing = MutableStateFlow(false)
  val uiState: StateFlow<DeviceSettingsUiState> =
      combine(
              deviceCoreDataState,
              devicesStateRepository.devicesStateFlow,
              _msgDialogInfo.asStateFlow(),
              _showShareDeviceAlertDialog.asStateFlow(),
              _showRemoveDeviceAlertDialog.asStateFlow(),
              _showRemoveDeviceConfirmAlertDialog.asStateFlow(),
              _deviceRemovalCompleted.asStateFlow(),
              _pairingWindowOpenForDeviceSharing.asStateFlow(),
          ) { array ->
            val deviceCoreData = array[0] as DeviceSettingsCoreData
            val nodesState = array[1] as MatterFabricState
            val msgDialogInfo = array[2] as DialogInfo?
            val showShareDeviceAlertDialog = array[3] as Boolean
            val showRemoveDeviceAlertDialog = array[4] as Boolean
            val showRemoveDeviceConfirmAlertDialog = array[5] as Boolean
            val deviceRemovalCompleted = array[6] as Boolean
            val pairingWindowOpenForDeviceSharing = array[7] as Boolean

            val node =
                deviceCoreData.device?.let { dev ->
                  nodesState.nodesList.firstOrNull { it.nodeId == dev.nodeId.toLong() }
                }
            val isOnline = node?.online ?: false
            val dateCommissioned = node?.dateCommissioned?.takeUnless { isDefaultTimestamp(it) }

            DeviceSettingsUiState(
                isFirstTimeLoading = deviceCoreData.isFirstTimeLoading,
                isBackgroundRefreshing = deviceCoreData.isBackgroundRefreshing,
                isOnline = isOnline,
                errorMessageRes = deviceCoreData.errorMessageRes,
                device = deviceCoreData.device,
                basicInformation = deviceCoreData.basicInformation,
                dateCommissioned = dateCommissioned,
                msgDialogInfo = msgDialogInfo,
                showShareDeviceAlertDialog = showShareDeviceAlertDialog,
                showRemoveDeviceAlertDialog = showRemoveDeviceAlertDialog,
                showRemoveDeviceConfirmAlertDialog = showRemoveDeviceConfirmAlertDialog,
                deviceRemovalCompleted = deviceRemovalCompleted,
                pairingWindowOpenForDeviceSharing = pairingWindowOpenForDeviceSharing,
            )
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeviceSettingsUiState())

  // -----------------------------------------------------------------------------------------------
  // Rename device

  fun renameDevice(nodeId: NodeId, newName: String) {
    Timber.d("Renaming device nodeId=$nodeId newName=$newName")
    viewModelScope.launch {
      val result =
          setDeviceNameUseCase.execute(nodeId, newName) {
            // Refresh from storage immediately so the UI reflects the new name.
            loadDevice(nodeId)
          }
      if (result is SetDeviceNameResult.LocalError) {
        showMsgDialog(R.string.set_device_name_failed, result.exception.message)
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Change device type

  fun changeDeviceType(nodeId: NodeId, deviceTypeId: DeviceTypeId) {
    Timber.d("Changing device type nodeId=$nodeId deviceTypeId=$deviceTypeId")
    viewModelScope.launch {
      try {
        devicesRepository.updateDeviceType(nodeId, deviceTypeId)
        loadDevice(nodeId)
      } catch (e: Exception) {
        Timber.e(e, "changeDeviceType failed")
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Dialog and transient UI state

  fun showShareDeviceAlertDialog() {
    _showShareDeviceAlertDialog.value = true
  }

  fun dismissShareDeviceAlertDialog() {
    _showShareDeviceAlertDialog.value = false
  }

  fun showRemoveDeviceAlertDialog() {
    _showRemoveDeviceAlertDialog.value = true
  }

  fun dismissRemoveDeviceAlertDialog() {
    _showRemoveDeviceAlertDialog.value = false
  }

  fun showRemoveDeviceConfirmAlertDialog() {
    _showRemoveDeviceConfirmAlertDialog.value = true
  }

  fun dismissRemoveDeviceConfirmAlertDialog() {
    _showRemoveDeviceConfirmAlertDialog.value = false
  }

  fun resetDeviceRemovalCompleted() {
    _deviceRemovalCompleted.value = false
  }

  fun resetPairingWindowOpenForDeviceSharing() {
    _pairingWindowOpenForDeviceSharing.value = false
  }

  fun shareDeviceSucceeded() {
    Timber.d("Device sharing succeeded")
    _pairingWindowOpenForDeviceSharing.value = false
  }

  fun shareDeviceFailed(resultCode: Int) {
    Timber.d("Device sharing failed resultCode=$resultCode")
    _pairingWindowOpenForDeviceSharing.value = false
  }

  // -----------------------------------------------------------------------------------------------
  // Share device

  // Open commissioning window for device sharing.
  fun openPairingWindow(nodeId: NodeId) {
    Timber.d("Opening pairing window")
    viewModelScope.launch {
      showMsgDialog(
          R.string.opening_pairing_window_title,
          R.string.opening_pairing_window_message,
          false,
      )
      try {
        val devicePtr = chipClient.awaitGetConnectedDevicePointer(nodeId)
        val isCommissioningWindowOpen = clustersHelper.isCommissioningWindowOpen(devicePtr)
        if (isCommissioningWindowOpen) {
          Timber.d("Commissioning window already open, closing it")
          clustersHelper.closeCommissioningWindow(devicePtr)
        }
        when (OPEN_COMMISSIONING_WINDOW_API) {
          OpenCommissioningWindowApi.ChipDeviceController ->
              openCommissioningWindowUsingOpenPairingWindowWithPin(nodeId)
          OpenCommissioningWindowApi.AdministratorCommissioningCluster ->
              openCommissioningWindowWithAdministratorCommissioningCluster(nodeId)
        }
        dismissMsgDialog()
        _pairingWindowOpenForDeviceSharing.value = true
      } catch (e: Exception) {
        Timber.e(e, "Failed to open pairing window")
        dismissMsgDialog()
        showMsgDialog(R.string.device_share_dialog_failed, e.message)
      }
    }
  }

  private suspend fun openCommissioningWindowUsingOpenPairingWindowWithPin(nodeId: NodeId) {
    Timber.d(
        "Opening pairing window durationSeconds=$OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS " +
            "iteration=$ITERATION discriminator=$DISCRIMINATOR"
    )
    val connectedDevicePointer = chipClient.awaitGetConnectedDevicePointer(nodeId)
    chipClient.awaitOpenPairingWindowWithPIN(
        connectedDevicePointer,
        OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS,
        ITERATION,
        DISCRIMINATOR,
        SETUP_PIN_CODE,
    )
  }

  private suspend fun openCommissioningWindowWithAdministratorCommissioningCluster(nodeId: NodeId) {
    val salt = Random.nextBytes(32)
    val timedInvokeTimeoutMs = 10000
    val connectedDevicePointer = chipClient.awaitGetConnectedDevicePointer(nodeId)
    val verifier =
        chipClient.computePaseVerifier(connectedDevicePointer, SETUP_PIN_CODE, ITERATION, salt)
    clustersHelper.openCommissioningWindowAdministratorCommissioningCluster(
        nodeId.toLong(),
        EndpointId(0u),
        OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS,
        verifier.pakeVerifier,
        DISCRIMINATOR,
        ITERATION,
        salt,
        timedInvokeTimeoutMs,
    )
  }

  // -----------------------------------------------------------------------------------------------
  // Remove device

  fun removeDevice(nodeId: NodeId) {
    Timber.d("Removing device nodeId=$nodeId")
    showMsgDialog(R.string.unlinking_device_title, R.string.unlinking_device_body, false)
    viewModelScope.launch {
      try {
        chipClient.awaitUnpairDevice(nodeId)
      } catch (e: Exception) {
        Timber.e(e, "Unlinking the device failed.")
        dismissMsgDialog()
        showRemoveDeviceConfirmAlertDialog()
        return@launch
      }
      Timber.d("Device removal succeeded nodeId=$nodeId")
      dismissMsgDialog()
      removePhysicalDevice(nodeId)
      _deviceRemovalCompleted.value = true
    }
  }

  fun removeDeviceWithoutUnlink(nodeId: NodeId) {
    Timber.d("Removing device without unlink nodeId=$nodeId")
    viewModelScope.launch {
      try {
        removePhysicalDevice(nodeId)
        _deviceRemovalCompleted.value = true
      } catch (e: Exception) {
        Timber.e(e, "removeDeviceWithoutUnlink failed")
        showMsgDialog(R.string.device_remove_dialog_title, e.message)
      }
    }
  }

  private suspend fun removePhysicalDevice(nodeId: NodeId) {
    devicesRepository.removeDevice(nodeId)
  }

  // -----------------------------------------------------------------------------------------------
  // UI State update

  fun showMsgDialog(
      title: String,
      msg: String?,
      showConfirmButton: Boolean = true,
  ) {
    Timber.d("Showing message dialog title=$title")
    _msgDialogInfo.value =
        DialogInfo(title = title, message = msg, showConfirmButton = showConfirmButton)
  }

  fun showMsgDialog(
      @StringRes titleRes: Int,
      @StringRes msgRes: Int,
      showConfirmButton: Boolean = true,
  ) {
    Timber.d("Showing message dialog titleRes=$titleRes")
    _msgDialogInfo.value =
        DialogInfo(titleRes = titleRes, messageRes = msgRes, showConfirmButton = showConfirmButton)
  }

  fun showMsgDialog(
      @StringRes titleRes: Int,
      msg: String?,
      showConfirmButton: Boolean = true,
  ) {
    Timber.d("Showing message dialog titleRes=$titleRes")
    _msgDialogInfo.value =
        DialogInfo(titleRes = titleRes, message = msg, showConfirmButton = showConfirmButton)
  }

  fun dismissMsgDialog() {
    Timber.d("dismissMsgDialog()")
    _msgDialogInfo.value = null
  }
}
