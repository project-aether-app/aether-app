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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aether.android.R
import io.aether.android.data.DevicesStateRepository
import io.aether.android.matter.NodeId
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.screens.common.MsgAlertDialog
import io.aether.android.screens.device.control.ColorTemperatureDeviceControl
import io.aether.android.screens.device.control.DimmableDeviceControl
import io.aether.android.screens.device.control.OnOffDeviceControl
import io.aether.android.screens.home.DeviceUiModel
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
  Timber.d("Loaded device nodeId=${uiState.device?.nodeId}")
  val onDismissMsgDialog: () -> Unit = remember { { deviceViewModel.dismissMsgDialog() } }

  // Per-endpoint callbacks.
  val onOnOffClick: (endpointModel: DeviceUiModel, value: Boolean) -> Unit = remember {
    { endpointModel, value -> deviceViewModel.updateDeviceStateOn(endpointModel, value) }
  }
  val onBrightnessChange: (endpointModel: DeviceUiModel, value: Int) -> Unit = remember {
    { endpointModel, value -> deviceViewModel.updateDeviceStateLevel(endpointModel, value) }
  }
  val onColorTemperatureChange: (endpointModel: DeviceUiModel, value: Int) -> Unit = remember {
    { endpointModel, value ->
      deviceViewModel.updateDeviceStateColorTemperature(endpointModel, value)
    }
  }

  // When app is sent to the background, and pulled back, this kicks in.
  LifecycleResumeEffect(Unit) {
    deviceViewModel.loadDevice(nodeId)
    onPauseOrDispose { deviceViewModel.stopMonitoringStateChanges() }
  }

  LaunchedEffect(uiState.device?.nodeId) {
    if (uiState.device != null) {
      deviceViewModel.startMonitoringStateChanges()
    }
  }

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
                IconButton(onClick = { navigateToDeviceSettings(nodeId) }) {
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
    DeviceScreen(
        uiState = uiState,
        onOnOffClick,
        onBrightnessChange,
        onColorTemperatureChange,
        onDismissMsgDialog,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
    )
  }
}

// -----------------------------------------------------------------------------------------------
// Node-level screen: renders one device-type control per endpoint.

@Composable
private fun DeviceScreen(
    uiState: DeviceScreenUiState,
    onOnOffClick: (endpointModel: DeviceUiModel, value: Boolean) -> Unit,
    onBrightnessChange: (endpointModel: DeviceUiModel, value: Int) -> Unit,
    onColorTemperatureChange: (endpointModel: DeviceUiModel, value: Int) -> Unit,
    onDismissMsgDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {

  uiState.msgDialogInfo?.let { dialogInfo ->
    MsgAlertDialog(dialogInfo, onDismissMsgDialog)
  }

  if (uiState.isFirstTimeLoading || uiState.device == null) {
    LoadingIndicator(stringResource(R.string.loading_device_info), modifier = modifier)
    return
  }

  val endpointsToShow = uiState.allEndpointUiModels.ifEmpty { listOf(uiState.device) }

  Column(
      modifier =
          modifier
              .verticalScroll(rememberScrollState())
              .padding(MaterialTheme.spacing.paddingNormal),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
  ) {
    if (!uiState.isOnline) {
      Text(
          text = stringResource(R.string.device_offline_label),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
      )
    }
    endpointsToShow.forEach { endpointModel ->
      Surface(
          modifier = Modifier.fillMaxWidth(),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(MaterialTheme.spacing.roundedCorner),
      ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.paddingSurfaceContent)) {
          EndpointDeviceControl(
              endpointModel = endpointModel,
              lastUpdatedEndpointState = uiState.lastUpdatedEndpointState,
              onOnOffClick = { value -> onOnOffClick(endpointModel, value) },
              onBrightnessChange = { value -> onBrightnessChange(endpointModel, value) },
              onColorTemperatureChange = { value ->
                onColorTemperatureChange(endpointModel, value)
              },
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
    endpointModel: DeviceUiModel,
    lastUpdatedEndpointState: DevicesStateRepository.EndpointStateSnapshot?,
    onOnOffClick: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onColorTemperatureChange: (Int) -> Unit,
) {
  val endpoint = endpointModel.endpoint
  when {
    supportsColorTemperature(endpoint) ->
        ColorTemperatureDeviceControl(
            endpointModel,
            lastUpdatedEndpointState,
            onOnOffClick,
            onBrightnessChange,
            onColorTemperatureChange,
        )
    supportsLevelControl(endpoint) ->
        DimmableDeviceControl(
            endpointModel,
            lastUpdatedEndpointState,
            onOnOffClick,
            onBrightnessChange,
        )
    else -> OnOffDeviceControl(endpointModel, lastUpdatedEndpointState, onOnOffClick)
  }
}
