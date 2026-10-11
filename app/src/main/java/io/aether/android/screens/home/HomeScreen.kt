// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.home

import android.text.method.LinkMovementMethod
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.textview.MaterialTextView
import io.aether.android.R
import io.aether.android.data.models.Device
import io.aether.android.isOnDisplayString
import io.aether.android.matter.NodeId
import io.aether.android.screens.common.MsgAlertDialog
import io.aether.android.screens.common.OfflineLabel
import io.aether.android.screens.thread.getActivity
import io.aether.android.spacing

@Composable
internal fun HomeRoute(
    navigateToDevice: (nodeId: NodeId) -> Unit,
    onMenuClick: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
  val activity = LocalContext.current.getActivity()!!
  val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

  val commissionDeviceLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartIntentSenderForResult(),
      ) { result ->
        homeViewModel.onCommissioningResult(result.resultCode, result.data)
      }

  LaunchedEffect(homeViewModel, commissionDeviceLauncher) {
    homeViewModel.effects.collect { effect ->
      when (effect) {
        is HomeEffect.LaunchCommissioning ->
            commissionDeviceLauncher.launch(
                IntentSenderRequest.Builder(effect.intentSender).build(),
            )
      }
    }
  }

  LifecycleResumeEffect(Unit) {
    homeViewModel.onResume(activity.intent)
    onPauseOrDispose { homeViewModel.onPause() }
  }

  HomeScreen(
      uiState = uiState,
      onMenuClick = onMenuClick,
      onUpdatePlayServicesClick = homeViewModel::onUpdatePlayServicesClick,
      onCommissionDeviceClick = homeViewModel::onAddDeviceClick,
      onCommissionedDeviceNameCaptured = homeViewModel::onCommissionedDeviceNameCaptured,
      onDeviceClick = { navigateToDevice(it.id) },
      onOnOffClick = homeViewModel::updateDeviceStateOn,
      onDismissMsgDialog = homeViewModel::dismissMsgDialog,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onMenuClick: () -> Unit,
    onUpdatePlayServicesClick: () -> Unit,
    onCommissionDeviceClick: () -> Unit,
    onCommissionedDeviceNameCaptured: (name: String) -> Unit,
    onDeviceClick: (device: Device) -> Unit,
    onOnOffClick: (nodeId: NodeId, value: Boolean) -> Unit,
    onDismissMsgDialog: () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    if (uiState.showUpdatePlayServicesDialog) {
      AlertDialog(
          onDismissRequest = {},
          title = { Text(stringResource(R.string.gps_update_title)) },
          text = { Text(stringResource(R.string.gps_update_message)) },
          confirmButton = {
            TextButton(onClick = onUpdatePlayServicesClick) {
              Text(stringResource(R.string.gps_update_button))
            }
          },
      )
    }
    if (uiState.devices.isEmpty()) {
      NoDevices()
    }
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
          TopAppBar(
              title = {},
              navigationIcon = {
                IconButton(onClick = onMenuClick) {
                  Icon(
                      imageVector = Icons.Filled.Menu,
                      contentDescription = stringResource(R.string.menu_button),
                      tint = MaterialTheme.colorScheme.onBackground,
                  )
                }
              },
              colors =
                  TopAppBarDefaults.topAppBarColors(
                      containerColor = Color.Transparent,
                      scrolledContainerColor = Color.Transparent,
                      navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                      titleContentColor = MaterialTheme.colorScheme.onBackground,
                  ),
          )
        },
        floatingActionButton = {
          FloatingActionButton(
              onClick = onCommissionDeviceClick,
              modifier = Modifier.padding(16.dp),
          ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.home_device_add_button),
            )
          }
        },
    ) { innerPadding ->
      Home(
          uiState = uiState,
          onConsumeMsgDialog = onDismissMsgDialog,
          onCommissionedDeviceNameCaptured = onCommissionedDeviceNameCaptured,
          onDeviceClick = onDeviceClick,
          onOnOffClick = onOnOffClick,
          modifier = Modifier.fillMaxSize().padding(innerPadding),
      )
    }
  }
}

@Composable
private fun Home(
    uiState: HomeUiState,
    onConsumeMsgDialog: () -> Unit,
    onCommissionedDeviceNameCaptured: (name: String) -> Unit,
    onDeviceClick: (device: Device) -> Unit,
    onOnOffClick: (nodeId: NodeId, value: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {

  if (uiState.msgDialogInfo != null) {
    MsgAlertDialog(uiState.msgDialogInfo, onConsumeMsgDialog)
  }

  if (uiState.showNewDeviceNameAlertDialog) {
    NewDeviceAlertDialog(
        onCommissionedDeviceNameCaptured,
        uiState.deviceAttestationFailureIgnored,
    )
  }

  LazyColumn(modifier = modifier) {
    this.items(uiState.devices) { device ->
      val onDeviceItemClick: () -> Unit = { onDeviceClick(device) }
      DeviceItem(
          device = device,
          onOnOffClick = onOnOffClick,
          onDeviceClick = onDeviceItemClick,
      )
    }
  }
}

@Composable
private fun DeviceItem(
    device: Device,
    onOnOffClick: (nodeId: NodeId, value: Boolean) -> Unit,
    onDeviceClick: (() -> Unit),
) {
  val isOn = device.primaryEndpoint.isOn
  val bgColor =
      if (device.isOnline && isOn) MaterialTheme.colorScheme.surfaceVariant
      else MaterialTheme.colorScheme.surface
  val contentColor =
      if (device.isOnline && isOn) MaterialTheme.colorScheme.onSurfaceVariant
      else MaterialTheme.colorScheme.onSurface
  val onCheckedChange: (value: Boolean) -> Unit = { onOnOffClick(device.id, it) }
  Surface(
      modifier =
          Modifier.padding(top = MaterialTheme.spacing.paddingSmall)
              .padding(PaddingValues(horizontal = MaterialTheme.spacing.paddingSmall)),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
      contentColor = contentColor,
      color = bgColor,
      shape = RoundedCornerShape(MaterialTheme.spacing.roundedCorner),
      onClick = onDeviceClick,
  ) {
    Column(modifier = Modifier.padding(MaterialTheme.spacing.paddingSurfaceContent)) {
      if (!device.isOnline) {
        OfflineLabel(modifier = Modifier.fillMaxWidth())
      }
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingSmall),
      ) {
        Icon(
            painter = painterResource(id = device.deviceType.iconId),
            contentDescription = null, // decorative element
        )
        Column {
          Text(text = device.name, style = MaterialTheme.typography.bodyLarge)
          Text(text = isOnDisplayString(isOn), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.weight(1f))
        Switch(enabled = device.isOnline, checked = isOn, onCheckedChange = onCheckedChange)
      }
    }
  }
}

@Composable
private fun NewDeviceAlertDialog(
    onCommissionedDeviceNameCaptured: (name: String) -> Unit,
    deviceAttestationFailureIgnored: Boolean,
) {
  var inputText by remember { mutableStateOf("") }
  AlertDialog(
      title = { Text(text = "Specify device name") },
      text = {
        Column {
          TextField(
              value = inputText,
              onValueChange = { inputText = it },
              label = { Text("Device name") },
              modifier = Modifier.fillMaxWidth(),
          )
          if (deviceAttestationFailureIgnored) {
            val htmlText =
                HtmlCompat.fromHtml(
                        stringResource(R.string.device_attestation_warning),
                        HtmlCompat.FROM_HTML_MODE_LEGACY,
                    )
                    .toString()
            AndroidView(
                modifier = Modifier.padding(top = 20.dp),
                update = { it.text = htmlText },
                factory = {
                  MaterialTextView(it).apply {
                    movementMethod = LinkMovementMethod.getInstance()
                  }
                },
            )
          }
        }
      },
      confirmButton = {
        Button(
            onClick = {
              // Process inputText
              onCommissionedDeviceNameCaptured(inputText)
            },
            enabled = inputText.isNotEmpty(),
        ) {
          Text(stringResource(R.string.ok))
        }
      },
      onDismissRequest = {},
      dismissButton = {},
  )
}

@Composable
private fun NoDevices() {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    // Crop-scale fills the entire area on both axes without letterboxing —
    // the image is scaled uniformly to whichever dimension (width or height)
    // needs the least scaling, and the other axis is cropped.
    Image(
        painter = painterResource(R.drawable.bg_empty_dashboard),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    // Place the title/subtitle in a column whose bottom sits 20 % above the
    // bottom edge of the screen.
    Column(
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .padding(bottom = maxHeight * 0.2f)
                .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
          text = stringResource(R.string.home_empty_dashboard_title),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
      )
      Text(
          text = stringResource(R.string.home_empty_dashboard_subtitle),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}
