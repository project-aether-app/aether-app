// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.fabrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aether.android.R
import io.aether.android.data.models.ManagedFabric
import io.aether.android.matter.NodeId
import io.aether.android.matter.vendorLabel
import io.aether.android.screens.common.EmptyState
import io.aether.android.screens.common.ErrorMessage
import io.aether.android.screens.common.GroupBox
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FabricsRoute(
    nodeId: NodeId,
    onBackClick: () -> Unit,
    viewModel: FabricsViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(nodeId) { viewModel.loadFabrics(nodeId) }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.device_settings_admin_fabrics)) },
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
    if (uiState.isFirstTimeLoading) {
      LoadingIndicator(
          stringResource(R.string.device_fabrics_loading),
          modifier = Modifier.fillMaxSize().padding(innerPadding),
      )
      return@Scaffold
    }
    FabricsScreen(
        uiState = uiState,
        onRefresh = { viewModel.loadFabrics(nodeId) },
        onRemoveController = { index -> viewModel.removeFabric(nodeId, index) },
        modifier = Modifier.fillMaxSize().padding(innerPadding),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FabricsScreen(
    uiState: FabricsUiState,
    onRefresh: () -> Unit,
    onRemoveController: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
  PullToRefreshBox(
      isRefreshing = uiState.isBackgroundRefreshing,
      onRefresh = onRefresh,
      modifier = modifier,
  ) {
    when {
      uiState.errorMessageRes != null -> {
        ErrorMessage(stringResource(uiState.errorMessageRes))
      }
      uiState.fabrics.isEmpty() -> {
        EmptyState(stringResource(R.string.device_fabrics_empty))
      }
      else -> {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(MaterialTheme.spacing.paddingNormal),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
        ) {
          uiState.fabrics.forEach { fabric ->
            FabricItem(
                fabric = fabric,
                onRemove = { onRemoveController(fabric.fabricIndex) },
                canRemove = !fabric.isCurrentFabric,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun FabricItem(
    fabric: ManagedFabric,
    onRemove: () -> Unit,
    canRemove: Boolean,
) {
  var showConfirmDialog by remember(fabric.fabricIndex) { mutableStateOf(false) }

  if (showConfirmDialog && canRemove) {
    AlertDialog(
        title = { Text(stringResource(R.string.device_fabric_remove_dialog_title)) },
        text = { Text(stringResource(R.string.device_fabric_remove_dialog_body)) },
        confirmButton = {
          Button(
              onClick = {
                showConfirmDialog = false
                onRemove()
              },
              colors =
                  ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.error,
                      contentColor = MaterialTheme.colorScheme.onError,
                  ),
          ) {
            Text(stringResource(R.string.device_fabric_remove_dialog_yes))
          }
        },
        dismissButton = {
          TextButton(onClick = { showConfirmDialog = false }) {
            Text(stringResource(R.string.cancel))
          }
        },
        onDismissRequest = { showConfirmDialog = false },
    )
  }

  val label =
      fabric.label.takeIf { it.isNotBlank() }
          ?: stringResource(R.string.device_fabrics_fabric_label, fabric.fabricIndex)

  GroupBox(title = label) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text =
                stringResource(R.string.device_fabrics_fabric_vendor, vendorLabel(fabric.vendorId)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                stringResource(
                    R.string.device_fabrics_fabric_fabric_id,
                    fabric.fabricId.toString(),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.device_fabrics_fabric_node_id, fabric.nodeId.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                stringResource(
                    R.string.device_fabrics_fabric_root_pub_key,
                    fabric.rootPublicKey.take(8).joinToString(separator = "") { "%02X".format(it) },
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (canRemove) {
        IconButton(onClick = { showConfirmDialog = true }) {
          Icon(
              imageVector = Icons.Outlined.Delete,
              contentDescription = stringResource(R.string.device_fabrics_fabric_remove),
              tint = MaterialTheme.colorScheme.error,
          )
        }
      }
    }
  }
}
