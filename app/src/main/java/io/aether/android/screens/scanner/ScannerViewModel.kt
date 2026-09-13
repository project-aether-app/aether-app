// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.scanner

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

data class ScannerUiState(
    // Lifecycle Flags
    val isFirstTimeLoading: Boolean = false,
    val isBackgroundRefreshing: Boolean = false,
    val isOnline: Boolean = false,
    @field:StringRes val errorMessageRes: Int? = null,

    // Core Screen Data
    val beacons: List<MatterBeacon> = emptyList(),
)

/**
 * ViewModel which provides a unified view into nearby [MatterBeacon]s as provided by all bound
 * [MatterBeaconProducer]s in the dependency injection graph.
 */
@HiltViewModel
class ScannerViewModel
@Inject
constructor(producers: Set<@JvmSuppressWildcards MatterBeaconProducer>) : ViewModel() {
  /**
   * Provides a [Flow] representing a live [Set] of nearby [MatterBeacon]s. The set of items will be
   * amended as more beacons are detected, so can be observed to see the most recently discovered
   * view.
   */
  private val beaconsFlow: Flow<Set<MatterBeacon>> =
      merge(*producers.map { it.getBeaconsFlow() }.toTypedArray())
          .runningFold(setOf<MatterBeacon>()) { set, item -> set + item }
          .stateIn(
              scope = viewModelScope,
              started = WhileSubscribed(2000),
              initialValue = emptySet(),
          )

  val uiState: StateFlow<ScannerUiState> =
      beaconsFlow
          .map { ScannerUiState(beacons = it.toList()) }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), ScannerUiState())
}
