// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.aether.android.R
import io.aether.android.chip.DeviceMatterInfo
import io.aether.android.matter.CLUSTERS
import io.aether.android.matter.ClusterId
import io.aether.android.matter.EndpointId
import io.aether.android.screens.common.SearchTextField
import io.aether.android.spacing

@Composable
internal fun ClusterListContent(
  endpointId: EndpointId,
  infos: List<DeviceMatterInfo>,
  knownClustersById: Map<ClusterId, ExplorerClusterDefinition>,
  showSearch: Boolean,
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  onSelectCluster: (ClusterId) -> Unit,
) {
  val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

  val endpointInfo = infos.firstOrNull { it.endpointId == endpointId }
  val serverClusters = endpointInfo?.serverClusters.orEmpty().sorted()
  val clientClusters = endpointInfo?.clientClusters.orEmpty().sorted()
  val clusterMatchesQuery: (ClusterId) -> Boolean = { clusterId ->
    matchesExplorerQuery(
      searchQuery,
      CLUSTERS[clusterId]?.name.orEmpty(),
      formatExplorerId(clusterId),
    )
  }
  val filteredServerClusters = serverClusters.filter(clusterMatchesQuery)
  val filteredClientClusters = clientClusters.filter(clusterMatchesQuery)
  val normalizedQuery = searchQuery.trim().lowercase()

  Column(
    modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.paddingNormal),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
  ) {
    if (showSearch) {
      SearchTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        label = { Text(stringResource(R.string.device_explorer_search_cluster)) },
      )
    }

    if (filteredServerClusters.isEmpty() && filteredClientClusters.isEmpty()) {
      Text(
        text =
          if (normalizedQuery.isBlank()) stringResource(R.string.device_explorer_clusters_empty)
          else stringResource(R.string.device_explorer_no_results),
        style = MaterialTheme.typography.bodyMedium,
      )
      return@Column
    }

    LazyColumn(
      state = listState,
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item(key = "server-title") {
        Text(
          text = stringResource(R.string.device_explorer_server_clusters_section),
          style = MaterialTheme.typography.titleSmall,
        )
      }
      if (filteredServerClusters.isEmpty()) {
        item(key = "server-empty") {
          Text(
            text = stringResource(R.string.device_explorer_clusters_section_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
          )
        }
      } else {
        items(filteredServerClusters, key = { "s-${it.value}" }) { clusterId ->
          val name =
            CLUSTERS[clusterId]?.name ?: stringResource(R.string.device_explorer_cluster_unknown)
          val known = knownClustersById[clusterId]
          ExplorerRow(
            text = formatIdAndName(clusterId.value, name),
            secondaryText =
              stringResource(
                R.string.device_explorer_cluster_counts,
                known?.attributes?.size ?: 0,
                known?.commands?.size ?: 0,
                known?.events?.size ?: 0,
              ),
            onClick = { onSelectCluster(clusterId) },
          )
        }
      }

      item(key = "client-title") {
        Text(
          text = stringResource(R.string.device_explorer_client_clusters_section),
          style = MaterialTheme.typography.titleSmall,
        )
      }
      if (filteredClientClusters.isEmpty()) {
        item(key = "client-empty") {
          Text(
            text = stringResource(R.string.device_explorer_clusters_section_empty),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
          )
        }
      } else {
        items(filteredClientClusters, key = { "c-${it.value}" }) { clusterId ->
          val name =
            CLUSTERS[clusterId]?.name ?: stringResource(R.string.device_explorer_cluster_unknown)
          val known = knownClustersById[clusterId]
          ExplorerRow(
            text = formatIdAndName(clusterId.value, name),
            secondaryText =
              stringResource(
                R.string.device_explorer_cluster_counts,
                known?.attributes?.size ?: 0,
                known?.commands?.size ?: 0,
                known?.events?.size ?: 0,
              ),
            onClick = { onSelectCluster(clusterId) },
          )
        }
      }
    }
  }
}
