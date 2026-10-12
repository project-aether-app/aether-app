// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.data.UserPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Class responsible for the information related to the user preferences.
 *
 * This is a shared ViewModel as multiple fragments in the app can update these user preferences and
 * be interested in observing its data.
 */
@HiltViewModel
class UserPreferencesViewModel
@Inject
constructor(private val userPreferencesRepository: UserPreferencesRepository) : ViewModel() {

  // Controls whether the "Offline" devices should be shown in the UI.
  private var _showOfflineDevices = MutableStateFlow<Boolean>(false)
  val showOfflineDevices: StateFlow<Boolean> = _showOfflineDevices.asStateFlow()

  init {
    viewModelScope.launch {
      val userPreferences = userPreferencesRepository.getData()
      _showOfflineDevices.value = !userPreferences.hideOfflineDevices
    }
  }
}
