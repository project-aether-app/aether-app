// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.shared

import io.aether.android.AppErrorNotifier
import io.aether.android.R
import io.aether.android.chip.ClustersHelper
import io.aether.android.chip.isCommunicationTimeoutError
import io.aether.android.data.DevicesRepository
import io.aether.android.matter.NodeId
import io.aether.android.screens.common.DialogInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/** Result of [SetDeviceNameUseCase.execute]. */
sealed class SetDeviceNameResult {
  /**
   * Both the local DataStore update and the on-device NodeLabel write succeeded (or is in
   * progress).
   */
  data object Success : SetDeviceNameResult()

  /** The local DataStore update failed; the on-device write was not attempted. */
  data class LocalError(val exception: Exception) : SetDeviceNameResult()
}

/**
 * Persists a device name in the local DataStore and writes it to the device's BasicInformation
 * NodeLabel attribute. Used both during commissioning (HomeViewModel) and when renaming an
 * already-commissioned device (DeviceViewModel) so both code paths share the same logic.
 *
 * The on-device NodeLabel write is launched in a singleton-scoped coroutine so that it survives
 * screen navigation. Any write failure is surfaced via [AppErrorNotifier] and shown as a global
 * error dialog regardless of which screen the user is on when the error occurs.
 */
@Singleton
class SetDeviceNameUseCase
@Inject
constructor(
    private val devicesRepository: DevicesRepository,
    private val clustersHelper: ClustersHelper,
    private val appErrorNotifier: AppErrorNotifier,
) {
  // Coroutine scope that lives for the lifetime of the singleton so that NodeLabel writes survive
  // screen navigation (i.e. are not cancelled when the caller's viewModelScope is cancelled).
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /**
   * Updates the device name in the local DataStore, then launches the on-device BasicInformation
   * NodeLabel write in the background.
   *
   * [onLocalPersisted] is invoked after the local DataStore update succeeds but before the
   * on-device write, so callers can update UI state immediately without waiting for the slower
   * network operation. Any exception thrown by [onLocalPersisted] propagates to the caller.
   *
   * @param nodeId the device node to update
   * @param name the new name
   * @param onLocalPersisted optional callback invoked after local persistence succeeds
   * @return [SetDeviceNameResult.Success] when the local DataStore update succeeds (the NodeLabel
   *   write is started in the background), or [SetDeviceNameResult.LocalError] if the DataStore
   *   update fails. NodeLabel write failures are delivered asynchronously via [AppErrorNotifier].
   */
  suspend fun execute(
      nodeId: NodeId,
      name: String,
      onLocalPersisted: suspend () -> Unit = {},
  ): SetDeviceNameResult {
    Timber.d("Setting device name nodeId=$nodeId nameLength=${name.length}")
    try {
      val device = devicesRepository.getDevice(nodeId)
      devicesRepository.addOrUpdateDevice(device.copy(name = name))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Timber.e(e, "Failed to persist device name locally")
      return SetDeviceNameResult.LocalError(e)
    }
    onLocalPersisted()
    scope.launch {
      try {
        clustersHelper.writeBasicClusterNodeLabelAttribute(nodeId.toLong(), name)
        devicesRepository.updateNodeOnlineState(nodeId, isOnline = true)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (e.isCommunicationTimeoutError()) {
          devicesRepository.updateNodeOnlineState(nodeId, isOnline = false)
        }
        Timber.e(e, "Failed to write node label")
        appErrorNotifier.notify(
            DialogInfo(
                titleRes = R.string.set_device_name_failed,
                message = e.message ?: e.toString(),
            ),
        )
      }
    }
    return SetDeviceNameResult.Success
  }
}
