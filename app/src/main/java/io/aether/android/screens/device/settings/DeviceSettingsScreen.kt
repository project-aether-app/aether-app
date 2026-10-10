// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aether.android.R
import io.aether.android.formatTimestamp
import io.aether.android.getDeviceTypeDisplayStringId
import io.aether.android.matter.DeviceTypeId
import io.aether.android.matter.NodeId
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import io.aether.android.matter.vendorLabel
import io.aether.android.screens.common.EmptyStateErrorBanner
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.screens.common.MsgAlertDialog
import io.aether.android.screens.common.OfflineLabel
import io.aether.android.screens.device.actions.ForceRemoveDeviceConfirmationDialog
import io.aether.android.screens.device.actions.RemoveDeviceConfirmationDialog
import io.aether.android.screens.device.actions.ShareDeviceConfirmationDialog
import io.aether.android.screens.device.actions.shareDevice
import io.aether.android.screens.thread.getActivity
import io.aether.android.spacing

@Composable
fun DeviceSettingsRoute(
    navigateToHome: () -> Unit,
    navigateToDeviceExplorer: (nodeId: NodeId) -> Unit,
    navigateToDeviceFabrics: (nodeId: NodeId) -> Unit,
    navigateToDeviceDiagnostics: (nodeId: NodeId) -> Unit,
    onBackClick: () -> Unit,
    nodeId: NodeId,
    viewModel: DeviceSettingsViewModel = hiltViewModel(),
) {

  val activity = LocalContext.current.getActivity()
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // GPS share activity launcher.
  val shareDeviceLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartIntentSenderForResult(),
      ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
          viewModel.shareDeviceSucceeded()
        } else {
          viewModel.shareDeviceFailed(result.resultCode)
        }
      }

  // Launch the GPS share activity once the pairing window is open.
  if (uiState.pairingWindowOpenForDeviceSharing) {
    val deviceName = uiState.device?.name ?: ""
    LaunchedEffect(uiState.pairingWindowOpenForDeviceSharing) {
      if (uiState.pairingWindowOpenForDeviceSharing) {
        viewModel.resetPairingWindowOpenForDeviceSharing()
        activity?.let { act ->
          shareDevice(
              act.applicationContext,
              shareDeviceLauncher,
              deviceName,
              onShareFailed = { title, error -> viewModel.showMsgDialog(title, error) },
          )
        }
      }
    }
  }

  // Navigate back to home when removal is done.
  if (uiState.deviceRemovalCompleted) {
    navigateToHome()
    viewModel.resetDeviceRemovalCompleted()
  }

  LifecycleResumeEffect(nodeId) {
    viewModel.loadDevice(nodeId)
    onPauseOrDispose {}
  }

  DeviceSettingsScreen(
      uiState = uiState,
      onBackClick = onBackClick,
      onDismissMsgDialog = { viewModel.dismissMsgDialog() },
      onDeviceNameChange = { name -> viewModel.renameDevice(nodeId, name) },
      onDeviceTypeChange = { type -> viewModel.changeDeviceType(nodeId, type) },
      onManageFabricsClick = { navigateToDeviceFabrics(nodeId) },
      onDataModelExplorerClick = { navigateToDeviceExplorer(nodeId) },
      onDiagnosticsClick = { navigateToDeviceDiagnostics(nodeId) },
      onShareDeviceClick = { viewModel.showShareDeviceAlertDialog() },
      onShareDeviceResult = { doIt ->
        viewModel.dismissShareDeviceAlertDialog()
        if (doIt) viewModel.openPairingWindow(nodeId)
      },
      onRemoveDeviceClick = { viewModel.showRemoveDeviceAlertDialog() },
      onRemoveDeviceResult = { doIt ->
        viewModel.dismissRemoveDeviceAlertDialog()
        if (doIt) viewModel.removeDevice(nodeId)
      },
      onForceRemoveDeviceResult = { doIt ->
        viewModel.dismissRemoveDeviceConfirmAlertDialog()
        if (doIt) viewModel.removeDeviceWithoutUnlink(nodeId)
      },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsScreen(
    uiState: DeviceSettingsUiState,
    onBackClick: () -> Unit,
    onDismissMsgDialog: () -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onDeviceTypeChange: (DeviceTypeId) -> Unit,
    onManageFabricsClick: () -> Unit,
    onDataModelExplorerClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onShareDeviceClick: () -> Unit,
    onShareDeviceResult: (Boolean) -> Unit,
    onRemoveDeviceClick: () -> Unit,
    onRemoveDeviceResult: (Boolean) -> Unit,
    onForceRemoveDeviceResult: (Boolean) -> Unit,
) {
  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.device_settings)) },
            navigationIcon = {
              IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_button),
                )
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
          DeviceSettings(
              uiState = uiState,
              onDismissMsgDialog = onDismissMsgDialog,
              onDeviceNameChange = onDeviceNameChange,
              onDeviceTypeChange = onDeviceTypeChange,
              onManageFabricsClick = onManageFabricsClick,
              onDataModelExplorerClick = onDataModelExplorerClick,
              onDiagnosticsClick = onDiagnosticsClick,
              onShareDeviceClick = onShareDeviceClick,
              onShareDeviceResult = onShareDeviceResult,
              onRemoveDeviceClick = onRemoveDeviceClick,
              onRemoveDeviceResult = onRemoveDeviceResult,
              onForceRemoveDeviceResult = onForceRemoveDeviceResult,
              modifier = Modifier.fillMaxSize().padding(innerPadding),
          )
    }
  }
}

@Composable
private fun DeviceSettings(
    uiState: DeviceSettingsUiState,
    onDismissMsgDialog: () -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onDeviceTypeChange: (DeviceTypeId) -> Unit,
    onManageFabricsClick: () -> Unit,
    onDataModelExplorerClick: () -> Unit,
    onDiagnosticsClick: () -> Unit,
    onShareDeviceClick: () -> Unit,
    onShareDeviceResult: (Boolean) -> Unit,
    onRemoveDeviceClick: () -> Unit,
    onRemoveDeviceResult: (Boolean) -> Unit,
    onForceRemoveDeviceResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {

  var showRenameDialog by remember { mutableStateOf(false) }
  var showTypeDialog by remember { mutableStateOf(false) }
  val scrollState =
      rememberSaveable(saver = androidx.compose.foundation.ScrollState.Saver) {
        androidx.compose.foundation.ScrollState(0)
      }

  val device = uiState.device

  when {
    device == null -> return
    uiState.msgDialogInfo != null -> {
      MsgAlertDialog(uiState.msgDialogInfo, onDismissMsgDialog)
    }
    showRenameDialog ->
        ChangeDeviceNameDialog(
            currentName = device.name,
            onConfirm = { name ->
              onDeviceNameChange(name)
              showRenameDialog = false
            },
            onDismissRequest = { showRenameDialog = false },
        )
    showTypeDialog ->
        ChangeDeviceTypeDialog(
            currentType = device.primaryEndpoint?.deviceTypeId ?: DeviceTypeId(0u),
            onConfirm = { type ->
              onDeviceTypeChange(type)
              showTypeDialog = false
            },
            onDismissRequest = { showTypeDialog = false },
        )
    uiState.showShareDeviceAlertDialog ->
        ShareDeviceConfirmationDialog(
            onConfirm = { onShareDeviceResult(true) },
            onDismissRequest = { onShareDeviceResult(false) },
        )
    uiState.showRemoveDeviceAlertDialog ->
        RemoveDeviceConfirmationDialog(
            onConfirm = { onRemoveDeviceResult(true) },
            onDismissRequest = { onRemoveDeviceResult(false) },
        )
    uiState.showLocalRemoveDeviceAlertDialog ->
        ForceRemoveDeviceConfirmationDialog(
            onConfirm = { onForceRemoveDeviceResult(true) },
            onDismissRequest = { onForceRemoveDeviceResult(false) },
        )
  }

  Column(
      modifier = modifier.verticalScroll(scrollState).padding(MaterialTheme.spacing.paddingNormal),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
  ) {
    if (!device.isOnline) {
      OfflineLabel(modifier = Modifier.fillMaxWidth())
    }
    // Basic section
    SettingsSection(stringResource(R.string.device_settings_section_basic)) {
      val unknown = stringResource(R.string.device_type_unknown)
      SettingsInfoRow(stringResource(R.string.device_settings_basic_vendor)) {
        Text(
            vendorLabel(
                uiState.basicInformation?.vendorId?.takeIf { it != VendorId(0u) }
                    ?: device.vendor.id,
                uiState.basicInformation?.vendorName?.takeIf { it.isNotBlank() }
                    ?: device.vendor.name.takeIf { it.isNotBlank() },
            ),
        )
      }
      SettingsInfoRow(stringResource(R.string.device_settings_basic_product)) {
        Text(
            stringResource(
                R.string.device_settings_basic_product_value,
                uiState.basicInformation?.productName?.takeIf { it.isNotBlank() }
                    ?: device.product.name.takeIf { it.isNotBlank() }
                    ?: unknown,
                uiState.basicInformation?.productId?.takeIf { it != ProductId(0u) }?.toString()
                    ?: device.product.id.toString(),
            ),
        )
      }
      uiState.basicInformation?.hardwareVersion?.let {
        SettingsInfoRow(stringResource(R.string.device_settings_basic_hardware_version)) {
          Text(it.takeIf { it.isNotBlank() } ?: unknown)
        }
      }
      uiState.basicInformation?.softwareVersion?.let {
        SettingsInfoRow(stringResource(R.string.device_settings_basic_software_version)) {
          Text(it.takeIf { it.isNotBlank() } ?: unknown)
        }
      }
      SettingsInfoRow(stringResource(R.string.device_settings_basic_added_on)) {
        Text(
            uiState.dateCommissioned?.let { formatTimestamp(LocalContext.current, it) } ?: unknown,
        )
      }
      SettingsInfoRow(stringResource(R.string.device_settings_basic_node_id)) {
        Text(device.id.toString())
      }
    }

    // General section
    SettingsSection(stringResource(R.string.device_settings_section_general)) {
      val unknown = stringResource(R.string.device_type_unknown)
      SettingsClickableRow(
          label = stringResource(R.string.device_settings_general_name),
          value =
              device.name.takeIf { it.isNotBlank() }
                  ?: uiState.basicInformation?.nodeLabel?.takeIf { it.isNotBlank() }
                  ?: unknown,
          onClick = { showRenameDialog = true },
      )
      SettingsClickableRow(
          label = stringResource(R.string.device_settings_general_type),
          value =
              getDeviceTypeDisplayStringId(
                  device.primaryEndpoint?.deviceTypeId ?: DeviceTypeId(0u),
              ),
          onClick = { showTypeDialog = true },
      )
    }

    // Admin section
    SettingsSection(stringResource(R.string.device_settings_section_admin)) {
      SettingsActionRow(
          icon = Icons.Outlined.Info,
          label = stringResource(R.string.device_settings_admin_diagnostics),
          subtitle = stringResource(R.string.device_settings_admin_diagnostics_subtitle),
          onClick = onDiagnosticsClick,
      )
      SettingsActionRow(
          icon = Icons.Outlined.Search,
          label = stringResource(R.string.device_settings_admin_explorer),
          subtitle = stringResource(R.string.device_settings_admin_explorer_subtitle),
          onClick = onDataModelExplorerClick,
      )
      SettingsActionRow(
          icon = Icons.Outlined.Settings,
          label = stringResource(R.string.device_settings_admin_fabrics),
          subtitle = stringResource(R.string.device_settings_admin_fabrics_subtitle),
          onClick = onManageFabricsClick,
      )
      SettingsActionRow(
          icon = Icons.Outlined.Share,
          label = stringResource(R.string.device_settings_admin_share),
          subtitle = stringResource(R.string.device_settings_admin_share_subtitle),
          onClick = onShareDeviceClick,
      )
      SettingsActionRow(
          icon = Icons.Outlined.Delete,
          label = stringResource(R.string.device_settings_admin_remove),
          subtitle = stringResource(R.string.device_settings_admin_remove_subtitle),
          onClick = onRemoveDeviceClick,
          iconTint = MaterialTheme.colorScheme.error,
          labelColor = MaterialTheme.colorScheme.error,
          subtitleColor = MaterialTheme.colorScheme.error,
      )
    }
  }
}
