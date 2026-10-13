// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aether.android.R
import io.aether.android.matter.NodeId
import io.aether.android.screens.common.EmptyStateErrorBanner
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.spacing

@Composable
internal fun DiagnosticsRoute(
  onBackClick: () -> Unit,
  nodeId: NodeId,
  viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LifecycleResumeEffect(nodeId) {
    viewModel.loadDiagnostics(nodeId)
    onPauseOrDispose {}
  }

  DiagnosticsScreen(
    uiState = uiState,
    onBackClick = onBackClick,
    onRefresh = { viewModel.loadDiagnostics(nodeId, forceRefresh = true) },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
  uiState: DiagnosticsUiState,
  onBackClick: () -> Unit,
  onRefresh: () -> Unit,
) {
  Scaffold(
    topBar = {
      Column {
        TopAppBar(
          title = { Text(stringResource(R.string.device_settings_admin_diagnostics)) },
          navigationIcon = {
            IconButton(onClick = onBackClick) {
              Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back_button),
              )
            }
          },
        )
      }
    },
  ) { innerPadding ->
    if (uiState.isFirstTimeLoading) {
      LoadingIndicator(
        stringResource(R.string.device_diagnostics_loading),
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      )
    } else {
      PullToRefreshBox(
        isRefreshing = uiState.isBackgroundRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        if (uiState.errorMessageRes != null) {
          // Satisfy the requirement of having a scrollable container.
          Box(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
          ) {
            EmptyStateErrorBanner(
              stringResource(uiState.errorMessageRes),
              modifier = Modifier.fillMaxSize(),
            )
          }
        } else {
          Diagnostics(
            uiState = uiState,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
  }
}

@Composable
private fun Diagnostics(
  uiState: DiagnosticsUiState,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(MaterialTheme.spacing.paddingNormal),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
  ) {
    uiState.generalDiagnostics?.let { GeneralDiagnostics(it) }
    uiState.ethernetNetworkDiagnostics?.let { EthernetNetworkDiagnostics(it) }
    uiState.wifiNetworkDiagnostics?.let { WiFiNetworkDiagnostics(it) }
    uiState.threadNetworkDiagnostics?.let { ThreadNetworkDiagnostics(it) }
    uiState.softwareDiagnostics?.let { SoftwareDiagnostics(it) }
  }
}
