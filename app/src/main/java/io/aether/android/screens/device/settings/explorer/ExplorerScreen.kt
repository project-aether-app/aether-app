// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aether.android.R
import io.aether.android.matter.NodeId
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.screens.common.MsgAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerRoute(
    onBackClick: () -> Unit,
    nodeId: NodeId,
    viewModel: ExplorerViewModel = hiltViewModel(),
) {
  val typedNodeId = nodeId
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  var showSearch by rememberSaveable { mutableStateOf(false) }
  val saveableStateHolder = rememberSaveableStateHolder()

  val atRoot = uiState.navStack.size <= 1
  BackHandler(enabled = !atRoot) { viewModel.navigateBack() }

  LifecycleResumeEffect(nodeId) {
    viewModel.loadExplorer(typedNodeId)
    onPauseOrDispose {}
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.device_settings_admin_explorer)) },
            navigationIcon = {
              IconButton(onClick = if (atRoot) onBackClick else viewModel::navigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_button),
                )
              }
            },
            actions = {
              IconButton(onClick = { showSearch = !showSearch }) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.device_explorer_search_toggle),
                )
              }
            },
        )
      }
  ) { innerPadding ->
    uiState.msgDialogInfo?.let { dialogInfo ->
      MsgAlertDialog(dialogInfo, viewModel::dismissMsgDialog)
    }

    if (uiState.isFirstTimeLoading || uiState.deviceMatterInfoList == null) {
      LoadingIndicator(
          stringResource(R.string.device_explorer_loading_endpoints),
          modifier = Modifier.fillMaxSize().padding(innerPadding),
      )
      return@Scaffold
    }

    val infos = uiState.deviceMatterInfoList ?: emptyList()
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      BreadcrumbBar(
          navStack = uiState.navStack,
          deviceMatterInfoList = infos,
          onPopToIndex = viewModel::popToIndex,
      )

      when (val level = uiState.navStack.last()) {
        ExplorerLevel.EndpointList ->
            saveableStateHolder.SaveableStateProvider("endpoint-list") {
              EndpointListContent(
                  infos = infos,
                  showSearch = showSearch,
                  searchQuery = uiState.endpointSearchQuery,
                  onSearchQueryChange = viewModel::onEndpointSearchQueryChange,
                  onSelectEndpoint = viewModel::selectEndpoint,
              )
            }

        is ExplorerLevel.ClusterList ->
            saveableStateHolder.SaveableStateProvider("cluster-list-${level.endpointId}") {
              ClusterListContent(
                  endpointId = level.endpointId,
                  infos = infos,
                  knownClustersById = uiState.knownClustersById,
                  showSearch = showSearch,
                  searchQuery = uiState.clusterSearchQuery,
                  onSearchQueryChange = viewModel::onClusterSearchQueryChange,
                  onSelectCluster = { clusterId ->
                    viewModel.selectCluster(typedNodeId, level.endpointId, clusterId)
                  },
              )
            }

        is ExplorerLevel.ClusterDetail -> {
          val key = ExplorerClusterKey(level.endpointId, level.clusterId)
          saveableStateHolder.SaveableStateProvider(
              "cluster-detail-${level.endpointId}-${level.clusterId}-${level.tab}"
          ) {
            ClusterDetailContent(
                tab = level.tab,
                isLoading = uiState.loadingClusterKeys.contains(key),
                details = uiState.clusterDetailsByKey[key],
                showSearch = showSearch,
                attributeSearchQuery = uiState.attributeSearchQuery,
                commandSearchQuery = uiState.commandSearchQuery,
                eventSearchQuery = uiState.eventSearchQuery,
                onAttributeSearchQueryChange = viewModel::onAttributeSearchQueryChange,
                onCommandSearchQueryChange = viewModel::onCommandSearchQueryChange,
                onEventSearchQueryChange = viewModel::onEventSearchQueryChange,
                onTabSelected = { tab ->
                  viewModel.setClusterDetailTab(level.endpointId, level.clusterId, tab)
                },
                onAttributeSelected = { attribute ->
                  viewModel.openAttributeDetail(level.endpointId, level.clusterId, attribute)
                },
                onCommandSelected = { command ->
                  viewModel.openCommandInvoke(level.endpointId, level.clusterId, command)
                },
            )
          }
        }

        is ExplorerLevel.AttributeDetail ->
            AttributeDetailContent(
                attribute = level.attribute,
                currentValue =
                    uiState.attributeValueByKey[
                            viewModel.attributeKey(
                                level.endpointId,
                                level.clusterId,
                                level.attribute.id,
                            )],
                readSuccessCount = uiState.attributeReadSuccessCount,
                writeSuccessCount = uiState.attributeWriteSuccessCount,
                onRead = {
                  viewModel.readAttribute(
                      typedNodeId,
                      level.endpointId,
                      level.clusterId,
                      level.attribute.id,
                  )
                },
                onWrite = { value ->
                  viewModel.writeAttribute(
                      typedNodeId,
                      level.endpointId,
                      level.clusterId,
                      level.attribute.id,
                      value,
                  )
                },
            )

        is ExplorerLevel.CommandInvoke ->
            CommandInvokeContent(
                command = level.command,
                invokeSuccessCount = uiState.commandInvokeSuccessCount,
                onInvoke = { argumentValues ->
                  viewModel.invokeCommand(
                      typedNodeId,
                      level.endpointId,
                      level.clusterId,
                      level.command.id,
                      argumentValues,
                  )
                },
            )
      }
    }
  }
}
