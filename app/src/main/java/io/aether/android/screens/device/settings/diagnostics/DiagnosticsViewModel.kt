// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.R
import io.aether.android.data.DiagnosticsRepository
import io.aether.android.data.models.EthernetNetworkDiagnosticsData
import io.aether.android.data.models.GeneralDiagnosticsData
import io.aether.android.data.models.SoftwareDiagnosticsData
import io.aether.android.data.models.ThreadNetworkDiagnosticsData
import io.aether.android.data.models.WiFiNetworkDiagnosticsData
import io.aether.android.matter.NodeId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    // Lifecycle Flags
    val isFirstTimeLoading: Boolean = true,
    val isBackgroundRefreshing: Boolean = false,
    val isOnline: Boolean = false,
    @field:StringRes val errorMessageRes: Int? = null,

    // Core Screen Data
    val generalDiagnostics: GeneralDiagnosticsData? = null,
    val softwareDiagnostics: SoftwareDiagnosticsData? = null,
    val ethernetNetworkDiagnostics: EthernetNetworkDiagnosticsData? = null,
    val wifiNetworkDiagnostics: WiFiNetworkDiagnosticsData? = null,
    val threadNetworkDiagnostics: ThreadNetworkDiagnosticsData? = null,
)

private data class RefreshRequest(val nodeId: NodeId)

private sealed interface DiagnosticUpdate {
  data class Software(val diag: SoftwareDiagnosticsData) : DiagnosticUpdate

  data class EthernetNetwork(val diag: EthernetNetworkDiagnosticsData) : DiagnosticUpdate

  data class WiFiNetwork(val diag: WiFiNetworkDiagnosticsData) : DiagnosticUpdate

  data class ThreadNetwork(val diag: ThreadNetworkDiagnosticsData) : DiagnosticUpdate
}

private sealed interface PartialState {
  data object Loading : PartialState

  data class GeneralDiagnosticsSuccess(val diag: GeneralDiagnosticsData) : PartialState

  data class SoftwareDiagnosticsSuccess(val diag: SoftwareDiagnosticsData) : PartialState

  data class EthernetNetworkDiagnosticsSuccess(val diag: EthernetNetworkDiagnosticsData) :
      PartialState

  data class WiFiNetworkDiagnosticsSuccess(val diag: WiFiNetworkDiagnosticsData) : PartialState

  data class ThreadNetworkDiagnosticsSuccess(val diag: ThreadNetworkDiagnosticsData) : PartialState

  data class Error(@field:StringRes val errorRes: Int) : PartialState
}

@HiltViewModel
class DiagnosticsViewModel
@Inject
constructor(private val diagnosticsRepository: DiagnosticsRepository) : ViewModel() {

  private val refreshTrigger = MutableSharedFlow<RefreshRequest>(replay = 1)

  @OptIn(ExperimentalCoroutinesApi::class)
  val uiState: StateFlow<DiagnosticsUiState> =
      refreshTrigger
          .flatMapLatest { request ->
            flow {
              emit(PartialState.Loading)
              coroutineScope {
                val generalDef = async {
                  diagnosticsRepository.readGeneralDiagnostics(request.nodeId)
                }
                val softwareDef = async {
                  diagnosticsRepository.readSoftwareDiagnostics(request.nodeId)
                }
                val ethernetDef = async {
                  diagnosticsRepository.readEthernetNetworkDiagnostics(request.nodeId)
                }
                val wifiDef = async {
                  diagnosticsRepository.readWiFiNetworkDiagnostics(request.nodeId)
                }
                val threadDef = async {
                  diagnosticsRepository.readThreadNetworkDiagnostics(request.nodeId)
                }

                val general = generalDef.await()
                if (general == null) {
                  softwareDef.cancel()
                  ethernetDef.cancel()
                  wifiDef.cancel()
                  threadDef.cancel()
                  emit(PartialState.Error(R.string.device_diagnostics_load_failed))
                  return@coroutineScope
                }

                emit(PartialState.GeneralDiagnosticsSuccess(general))

                channelFlow {
                  launch { softwareDef.await()?.let { send(DiagnosticUpdate.Software(it)) } }
                  launch {
                    ethernetDef.await()?.let { send(DiagnosticUpdate.EthernetNetwork(it)) }
                  }
                  launch { wifiDef.await()?.let { send(DiagnosticUpdate.WiFiNetwork(it)) } }
                  launch { threadDef.await()?.let { send(DiagnosticUpdate.ThreadNetwork(it)) } }
                }
                    .collect { update ->
                      when (update) {
                        is DiagnosticUpdate.Software ->
                            emit(PartialState.SoftwareDiagnosticsSuccess(update.diag))
                        is DiagnosticUpdate.EthernetNetwork ->
                            emit(PartialState.EthernetNetworkDiagnosticsSuccess(update.diag))
                        is DiagnosticUpdate.WiFiNetwork ->
                            emit(PartialState.WiFiNetworkDiagnosticsSuccess(update.diag))
                        is DiagnosticUpdate.ThreadNetwork ->
                            emit(PartialState.ThreadNetworkDiagnosticsSuccess(update.diag))
                      }
                    }
              }
            }
          }
          .scan(DiagnosticsUiState(isFirstTimeLoading = true)) { previousState, partial ->
            when (partial) {
              is PartialState.Loading ->
                  previousState.copy(
                      isBackgroundRefreshing = previousState.generalDiagnostics != null,
                      isFirstTimeLoading = previousState.generalDiagnostics == null,
                      errorMessageRes = null,
                  )
              is PartialState.GeneralDiagnosticsSuccess ->
                  previousState.copy(
                      isFirstTimeLoading = false,
                      isBackgroundRefreshing = false,
                      generalDiagnostics = partial.diag,
                      errorMessageRes = null,
                  )
              is PartialState.SoftwareDiagnosticsSuccess ->
                  previousState.copy(softwareDiagnostics = partial.diag)
              is PartialState.EthernetNetworkDiagnosticsSuccess ->
                  previousState.copy(ethernetNetworkDiagnostics = partial.diag)
              is PartialState.WiFiNetworkDiagnosticsSuccess ->
                  previousState.copy(wifiNetworkDiagnostics = partial.diag)
              is PartialState.ThreadNetworkDiagnosticsSuccess ->
                  previousState.copy(threadNetworkDiagnostics = partial.diag)
              is PartialState.Error ->
                  previousState.copy(
                      isFirstTimeLoading = false,
                      isBackgroundRefreshing = false,
                      errorMessageRes = partial.errorRes,
                  )
            }
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(5000),
              DiagnosticsUiState(isFirstTimeLoading = true),
          )

  fun loadDiagnostics(nodeId: NodeId, forceRefresh: Boolean = false) {
    val currentRequest = refreshTrigger.replayCache.firstOrNull()
    if (
        !forceRefresh &&
            currentRequest?.nodeId == nodeId &&
            uiState.value.generalDiagnostics != null
    ) {
      return
    }
    viewModelScope.launch { refreshTrigger.emit(RefreshRequest(nodeId)) }
  }
}
