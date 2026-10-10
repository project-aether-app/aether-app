// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aether.android.R
import io.aether.android.data.DeviceRepository
import io.aether.android.data.models.Device
import io.aether.android.data.models.Endpoint
import io.aether.android.matter.NodeId
import io.aether.android.screens.common.EmptyStateErrorBanner
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.screens.common.MsgAlertDialog
import io.aether.android.screens.common.OfflineLabel
import io.aether.android.screens.device.control.ColorTemperatureDeviceControl
import io.aether.android.screens.device.control.DimmableDeviceControl
import io.aether.android.screens.device.control.OnOffDeviceControl
import io.aether.android.spacing
import io.aether.android.supportsColorTemperature
import io.aether.android.supportsLevelControl
import timber.log.Timber

/**
 * The Device Screen shows all the information about the device that was selected in the Home
 * screen. It supports the following actions:
 * ```
 * - toggle the on/off state of the device
 * - navigate to device settings (gear icon in the title bar)
 * ```
 *
 * When the screen is shown, state monitoring is activated to get the device's latest state. This
 * makes it possible to update the device's online status dynamically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceRoute(
    navigateToDeviceSettings: (nodeId: NodeId) -> Unit,
    onBackClick: () -> Unit,
    nodeId: NodeId,
    deviceViewModel: DeviceViewModel = hiltViewModel(),
) {
  Timber.d("Opening device nodeId=$nodeId")

  // Observes values needed by the DeviceScreen.
  val uiState by deviceViewModel.uiState.collectAsStateWithLifecycle()
  Timber.d("Loaded device nodeId=${uiState.device?.id}")
  val onDismissMsgDialog: () -> Unit = remember { { deviceViewModel.dismissMsgDialog() } }

  // Per-endpoint callbacks.
  val onOnOffClick: (endpoint: Endpoint, value: Boolean) -> Unit = remember {
    { endpoint, value -> deviceViewModel.updateDeviceStateOn(endpoint, value) }
  }
  val onBrightnessChange: (endpoint: Endpoint, value: Int) -> Unit = remember {
    { endpoint, value -> deviceViewModel.updateDeviceStateLevel(endpoint, value) }
  }
  val onColorTemperatureChange: (endpoint: Endpoint, value: Int) -> Unit = remember {
    { endpoint, value -> deviceViewModel.updateDeviceStateColorTemperature(endpoint, value) }
  }

  // When app is sent to the background, and pulled back, this kicks in.
  LifecycleResumeEffect(Unit) {
    deviceViewModel.loadDevice(nodeId)
    onPauseOrDispose { deviceViewModel.stopMonitoringStateChanges() }
  }

  LaunchedEffect(uiState.device?.id) {
    if (uiState.device != null) {
      deviceViewModel.startMonitoringStateChanges()
    }
  }

  DeviceScreen(
      uiState = uiState,
      onBackClick = onBackClick,
      onSettingsClick = { navigateToDeviceSettings(nodeId) },
      onOnOffClick = onOnOffClick,
      onBrightnessChange = onBrightnessChange,
      onColorTemperatureChange = onColorTemperatureChange,
      onDismissMsgDialog = onDismissMsgDialog,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    uiState: DeviceUiState,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onOnOffClick: (endpoint: Endpoint, value: Boolean) -> Unit,
    onBrightnessChange: (endpoint: Endpoint, value: Int) -> Unit,
    onColorTemperatureChange: (endpoint: Endpoint, value: Int) -> Unit,
    onDismissMsgDialog: () -> Unit,
) {
  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(uiState.device?.name ?: stringResource(R.string.device_screen_title)) },
            navigationIcon = {
              IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_button),
                )
              }
            },
            actions = {
              if (uiState.device != null) {
                IconButton(onClick = onSettingsClick) {
                  Icon(
                      imageVector = Icons.Filled.Settings,
                      contentDescription = stringResource(R.string.device_settings),
                  )
                }
              }
            },
        )
      },
  ) { innerPadding ->
    when {
      uiState.isFirstTimeLoading -> {
        LoadingIndicator(
            stringResource(R.string.loading_device_info),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
        return@Scaffold
      }
      uiState.errorMessageRes != null -> {
        EmptyStateErrorBanner(
            stringResource(uiState.errorMessageRes),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
        return@Scaffold
      }
      else ->
          Device(
              uiState = uiState,
              onOnOffClick = onOnOffClick,
              onBrightnessChange = onBrightnessChange,
              onColorTemperatureChange = onColorTemperatureChange,
              onDismissMsgDialog = onDismissMsgDialog,
              modifier = Modifier.fillMaxSize().padding(innerPadding),
          )
    }
  }
}

@Composable
private fun Device(
    uiState: DeviceUiState,
    onOnOffClick: (endpoint: Endpoint, value: Boolean) -> Unit,
    onBrightnessChange: (endpoint: Endpoint, value: Int) -> Unit,
    onColorTemperatureChange: (endpoint: Endpoint, value: Int) -> Unit,
    onDismissMsgDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {

  uiState.msgDialogInfo?.let { dialogInfo -> MsgAlertDialog(dialogInfo, onDismissMsgDialog) }

  if (uiState.device == null) {
    return
  }

  val endpointsToShow = uiState.device.endpoints

  Column(
      modifier =
          modifier
              .verticalScroll(rememberScrollState())
              .padding(MaterialTheme.spacing.paddingNormal),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
  ) {
    if (!uiState.device.isOnline) {
      OfflineLabel(modifier = Modifier.fillMaxWidth())
    }
    endpointsToShow.forEach { endpoint ->
      Surface(
          modifier = Modifier.fillMaxWidth(),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(MaterialTheme.spacing.roundedCorner),
      ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.paddingSurfaceContent)) {
          EndpointDeviceControl(
              device = uiState.device,
              endpoint = endpoint,
              lastUpdatedEndpointState = uiState.lastUpdatedEndpointState,
              onOnOffClick = { value -> onOnOffClick(endpoint, value) },
              onBrightnessChange = { value -> onBrightnessChange(endpoint, value) },
              onColorTemperatureChange = { value -> onColorTemperatureChange(endpoint, value) },
          )
        }
      }
    }
  }
}

// -----------------------------------------------------------------------------------------------
// Endpoint dispatcher: selects the device-type control that matches the endpoint's capabilities.

@Composable
private fun EndpointDeviceControl(
    device: Device,
    endpoint: Endpoint,
    lastUpdatedEndpointState: DeviceRepository.EndpointStateSnapshot?,
    onOnOffClick: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onColorTemperatureChange: (Int) -> Unit,
) {
  when {
    supportsColorTemperature(endpoint) ->
        ColorTemperatureDeviceControl(
            device,
            endpoint,
            lastUpdatedEndpointState,
            onOnOffClick,
            onBrightnessChange,
            onColorTemperatureChange,
        )
    supportsLevelControl(endpoint) ->
        DimmableDeviceControl(
            device,
            endpoint,
            lastUpdatedEndpointState,
            onOnOffClick,
            onBrightnessChange,
        )
    else -> OnOffDeviceControl(device, endpoint, lastUpdatedEndpointState, onOnOffClick)
  }
}
