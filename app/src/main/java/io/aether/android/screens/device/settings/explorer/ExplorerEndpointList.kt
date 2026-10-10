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
import io.aether.android.matter.DEVICES
import io.aether.android.matter.EndpointId
import io.aether.android.screens.common.SearchTextField
import io.aether.android.spacing

@Composable
internal fun EndpointListContent(
    infos: List<DeviceMatterInfo>,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectEndpoint: (EndpointId) -> Unit,
) {
  val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

  val filteredInfos = infos.filter { info ->
    val endpointText = formatEndpointId(info.endpointId)
    val typeNames = info.types.joinToString(" ") { typeId -> DEVICES[typeId].orEmpty() }
    matchesExplorerQuery(searchQuery, endpointText, typeNames)
  }
  val normalizedQuery = searchQuery.trim().lowercase()

  Column(
      modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.paddingNormal),
      verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
  ) {
    if (showSearch) {
      SearchTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          label = { Text(stringResource(R.string.device_explorer_search_endpoint)) },
      )
    }

    if (filteredInfos.isEmpty()) {
      Text(
          text =
              if (normalizedQuery.isBlank())
                  stringResource(R.string.device_explorer_endpoints_empty)
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
      items(filteredInfos, key = { "e-${it.endpointId}" }) { info ->
        val deviceTypes =
            info.types
                .map { typeId ->
                  val typeName =
                      DEVICES[typeId]
                          ?: stringResource(R.string.device_explorer_endpoint_type_unknown)
                  typeId to typeName
                }
                .sortedBy { it.first }
        val titleName = deviceTypes.joinToString(" & ") { it.second }
        ExplorerRow(
            text = formatEndpointLabel(info.endpointId, titleName),
            secondaryText =
                buildString {
                  if (deviceTypes.size == 1) {
                    append(
                        stringResource(
                            R.string.device_explorer_device_type,
                            formatIdAndName(
                                deviceTypes.first().first.value,
                                deviceTypes.first().second,
                            ),
                        ),
                    )
                    append("\n")
                  } else if (deviceTypes.isNotEmpty()) {
                    append(stringResource(R.string.device_explorer_device_types))
                    append("\n")
                    deviceTypes.forEach { (typeId, name) ->
                      append(
                          stringResource(
                              R.string.device_explorer_device_types_item,
                              formatIdAndName(typeId.value, name),
                          ),
                      )
                      append("\n")
                    }
                  }
                  append(
                      stringResource(
                          R.string.device_explorer_endpoint_metadata,
                          info.serverClusters.size,
                          info.clientClusters.size,
                      ),
                  )
                },
            onClick = { onSelectEndpoint(info.endpointId) },
        )
      }
    }
  }
}
