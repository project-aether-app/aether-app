// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.thread

import android.content.IntentSender
import android.graphics.Bitmap
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.threadnetwork.ThreadNetworkCredentials
import com.google.common.io.BaseEncoding
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class ThreadUiState(
    // Lifecycle Flags
    val isFirstTimeLoading: Boolean = false,
    val isBackgroundRefreshing: Boolean = false,
    val isOnline: Boolean = false,
    @field:StringRes val errorMessageRes: Int? = null,

    // Core Screen Data
    val threadCredentialsInfo: ThreadCredentialsInfo = ThreadCredentialsInfo(),
    val threadClientIntentSender: IntentSender? = null,

    // UI Overlay / Dialog States
    val currentActionInfo: ActionDialogInfo = ActionDialogInfo(),
)

/** The ViewModel for the Thread Fragment. See [ThreadFragment] for additional information. */
@HiltViewModel
class ThreadViewModel @Inject constructor() : ViewModel() {

  private val _uiState = MutableStateFlow(ThreadUiState())
  val uiState: StateFlow<ThreadUiState> = _uiState.asStateFlow()

  // OpenThread BorderRouter constants.
  private val otbrPort = "80"
  private val otbrDatasetPendingEndpoint = "/node/dataset/pending"
  private val otbrDatasetActiveEndpoint = "/node/dataset/active"
  private val threadCredentialsQRCodePrefix = "TD:"

  // -----------------------------------------------------------------------------------------------
  // Setter methods for the state that drives the UI.

  fun setActionDialogInfo(actionType: ActionType, actionState: ActionState) {
    _uiState.update {
      it.copy(currentActionInfo = it.currentActionInfo.copy(type = actionType, state = actionState))
    }
  }

  private fun setActionDialogInfoWithBorderRoutersList(
      actionType: ActionType,
      actionState: ActionState,
      borderRoutersList: List<NsdServiceInfo>,
  ) {
    _uiState.update {
      it.copy(
          currentActionInfo =
              it.currentActionInfo.copy(
                  type = actionType,
                  state = actionState,
                  borderRoutersList = borderRoutersList,
              )
      )
    }
  }

  private fun setActionDialogInfoWithQrCodeBitmap(
      actionType: ActionType,
      actionState: ActionState,
      qrCodeBitmap: Bitmap,
  ) {
    _uiState.update {
      it.copy(
          currentActionInfo =
              it.currentActionInfo.copy(
                  type = actionType,
                  state = actionState,
                  qrCodeBitmap = qrCodeBitmap,
              )
      )
    }
  }

  fun setActionDialogInfoWithError(actionType: ActionType, error: String) {
    _uiState.update {
      it.copy(
          currentActionInfo =
              it.currentActionInfo.copy(
                  type = actionType,
                  state = ActionState.Error,
                  data = error,
              )
      )
    }
  }

  fun setActionDialogInfoWithMessage(actionType: ActionType, message: String) {
    _uiState.update {
      it.copy(
          currentActionInfo =
              it.currentActionInfo.copy(
                  type = actionType,
                  state = ActionState.Completed,
                  data = message,
              )
      )
    }
  }

  fun setThreadCredentialsInfo(
      selectedThreadBorderRouterId: ByteArray?,
      threadNetworkCredentials: ThreadNetworkCredentials?,
  ) {
    _uiState.update {
      it.copy(
          threadCredentialsInfo =
              ThreadCredentialsInfo(selectedThreadBorderRouterId, threadNetworkCredentials)
      )
    }
  }

  /**
   * Caller should make sure to follow up setting the IntentSender value with setting it back to
   * "null" to avoid re-processing the IntentSender after a configuration change (where the LiveData
   * is re-posted).
   */
  fun setThreadClientIntentSender(intentSender: IntentSender?) {
    _uiState.update { it.copy(threadClientIntentSender = intentSender) }
  }

  // -----------------------------------------------------------------------------------------------
  // Actions for GPS Preferred Credentials

  fun clearGPSPreferredCreds(actionRequest: ActionRequest) {
    setActionDialogInfo(actionRequest.type, ActionState.Completed)
  }

  // -----------------------------------------------------------------------------------------------
  // Actions for OTBR running on Raspberry Pi

  /** Gets the credentials of a sample RPi running a Thread Border Router */
  fun getOTBRActiveThreadCredentials(
      actionRequest: ActionRequest,
      borderRoutersList: List<NsdServiceInfo>,
  ) {
    if (actionRequest.task == ActionTask.Init) {
      // We get the list of OTBRs and return it to the UI.
      setActionDialogInfoWithBorderRoutersList(
          actionRequest.type,
          ActionState.BorderRoutersProvided,
          borderRoutersList,
      )
    } else if (actionRequest.task == ActionTask.Process) {
      // Update UI with processing state for the action
      setActionDialogInfo(actionRequest.type, ActionState.Processing)
      // Coroutine to get the Thread credentials for the ServiceInfo specified.
      viewModelScope.launch {
        try {
          val threadNetworkCredentials =
              getOtbrActiveThreadCredentialsProcess(actionRequest.serviceInfo!!)
          if (threadNetworkCredentials == null) {
            setActionDialogInfo(actionRequest.type, ActionState.Error)
          } else {
            // FIXME: save otbr id...
            val selectedThreadBorderRouterId = actionRequest.serviceInfo.attributes["id"]
            setThreadCredentialsInfo(selectedThreadBorderRouterId, threadNetworkCredentials)
          }
        } catch (e: Exception) {
          setActionDialogInfoWithError(actionRequest.type, e.toString())
        }
      }
    }
  }

  /** Gets the credentials of a sample RPi running a Thread Border Router. */
  private suspend fun getOtbrActiveThreadCredentialsProcess(
      serviceInfo: NsdServiceInfo
  ): ThreadNetworkCredentials? {
    val ipAddress = serviceInfoHostAddress(serviceInfo)
    val response =
        OtbrHttpClient.createJsonHttpRequest(
            URL("http://$ipAddress:$otbrPort$otbrDatasetActiveEndpoint"),
            OtbrHttpClient.Verbs.GET,
            acceptMimeType = "text/plain",
        )
    return if (response.first.responseCode in OtbrHttpClient.okResponses) {
      ThreadNetworkCredentials.fromActiveOperationalDataset(
          BaseEncoding.base16().decode(response.second.uppercase())
      )
    } else {
      null
    }
  }

  /**
   * Sets the Pending working set of Thread credentials of a sample RPi running a Thread Border
   * Router.
   */
  fun setOTBRPendingThreadCredentials(
      actionRequest: ActionRequest,
      borderRoutersList: List<NsdServiceInfo>,
  ) {
    val threadCredentialsInfo = uiState.value.threadCredentialsInfo
    if (threadCredentialsInfo.credentials == null) {
      setActionDialogInfoWithError(actionRequest.type, "You must set the working dataset.")
      return
    } else if (actionRequest.task == ActionTask.Init) {
      // We get the list of OTBRs and return it to the UI.
      setActionDialogInfoWithBorderRoutersList(
          actionRequest.type,
          ActionState.BorderRoutersProvided,
          borderRoutersList,
      )
    } else if (actionRequest.task == ActionTask.Process) {
      // Update UI with processing state for the action
      setActionDialogInfo(actionRequest.type, ActionState.Processing)
      // Coroutine to get the Thread credentials for the ServiceInfo specified.
      viewModelScope.launch {
        try {
          val ipAddress = serviceInfoHostAddress(actionRequest.serviceInfo!!)
          val jsonQuery =
              OtbrHttpClient.createJsonCredentialsObject(threadCredentialsInfo.credentials)
          val response =
              OtbrHttpClient.createJsonHttpRequest(
                  URL("http://$ipAddress:$otbrPort$otbrDatasetPendingEndpoint"),
                  OtbrHttpClient.Verbs.PUT,
                  jsonQuery.toString(),
              )
          if (response.first.responseCode in OtbrHttpClient.okResponses) {
            setActionDialogInfoWithMessage(actionRequest.type, "Success!")
          } else {
            setActionDialogInfoWithError(
                actionRequest.type,
                "Error: responseCode [${response.first.responseCode}",
            )
          }
        } catch (e: Exception) {
          setActionDialogInfoWithError(actionRequest.type, e.toString())
        }
      }
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Actions for QR Code of Thread credentials

  fun showWorkingSetQRCode(actionRequest: ActionRequest) {
    val mWriter = MultiFormatWriter()
    try {
      // BitMatrix class to encode entered text and set Width & Height
      val threadCredentialsInfo = uiState.value.threadCredentialsInfo
      if (threadCredentialsInfo.credentials != null) {
        val qrCodeContent =
            threadCredentialsQRCodePrefix +
                BaseEncoding.base16()
                    .encode(threadCredentialsInfo.credentials.activeOperationalDataset)
        Timber.d("Showing QRCode of $qrCodeContent")
        val mMatrix = mWriter.encode(qrCodeContent, BarcodeFormat.QR_CODE, 600, 600)
        val mEncoder = BarcodeEncoder()
        val mBitmap = mEncoder.createBitmap(mMatrix)
        setActionDialogInfoWithQrCodeBitmap(actionRequest.type, ActionState.Completed, mBitmap)
      } else {
        setActionDialogInfoWithError(actionRequest.type, "You must set the working dataset")
      }
    } catch (e: Exception) {
      setActionDialogInfoWithError(actionRequest.type, e.toString())
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Utility methods

  fun threadCredentialsExist(): Boolean {
    return uiState.value.threadCredentialsInfo.credentials != null
  }

  fun selectedThreadBorderRouterExists(): Boolean {
    return uiState.value.threadCredentialsInfo.selectedThreadBorderRouterId != null
  }

  fun getSelectedBorderRouterId(): ByteArray? {
    return uiState.value.threadCredentialsInfo.selectedThreadBorderRouterId
  }

  fun getThreadNetworkCredentials(): ThreadNetworkCredentials? {
    return uiState.value.threadCredentialsInfo.credentials
  }

  fun getBase16ThreadCredentials(): String {
    val credentials =
        uiState.value.threadCredentialsInfo.credentials
            ?: throw IllegalStateException("Credentials are null")
    return BaseEncoding.base16().encode(credentials.activeOperationalDataset)
  }
}

private fun serviceInfoHostAddress(serviceInfo: NsdServiceInfo): String? {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    serviceInfo.hostAddresses.firstOrNull()?.hostAddress
  } else {
    @Suppress("DEPRECATION") serviceInfo.host?.hostAddress
  }
}
