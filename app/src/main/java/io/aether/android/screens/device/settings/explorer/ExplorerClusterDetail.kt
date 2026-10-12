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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.aether.android.R
import io.aether.android.screens.common.LoadingIndicator
import io.aether.android.screens.common.SearchTextField
import io.aether.android.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClusterDetailContent(
  tab: ExplorerTab,
  isLoading: Boolean,
  details: ExplorerClusterDetails?,
  showSearch: Boolean,
  attributeSearchQuery: String,
  commandSearchQuery: String,
  eventSearchQuery: String,
  onAttributeSearchQueryChange: (String) -> Unit,
  onCommandSearchQueryChange: (String) -> Unit,
  onEventSearchQueryChange: (String) -> Unit,
  onTabSelected: (ExplorerTab) -> Unit,
  onAttributeSelected: (ExplorerAttributeUiItem) -> Unit,
  onCommandSelected: (ExplorerCommandUiItem) -> Unit,
) {
  val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

  Column(modifier = Modifier.fillMaxSize()) {
    PrimaryTabRow(selectedTabIndex = tab.ordinal) {
      ExplorerTab.entries.forEach { t ->
        Tab(
          selected = tab == t,
          onClick = { onTabSelected(t) },
          text = { Text(stringResource(t.titleRes)) },
        )
      }
    }

    if (isLoading || details == null) {
      LoadingIndicator(stringResource(R.string.device_explorer_loading_clusters))
      return@Column
    }

    when (tab) {
      ExplorerTab.ATTRIBUTES -> {
        Column(
          modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.paddingNormal),
          verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
        ) {
          if (showSearch) {
            SearchTextField(
              value = attributeSearchQuery,
              onValueChange = onAttributeSearchQueryChange,
              label = { Text(stringResource(R.string.device_explorer_search_attribute)) },
            )
          }

          val filtered =
            details.attributes.filter { attr ->
              matchesExplorerQuery(
                attributeSearchQuery,
                attr.name.orEmpty(),
                formatExplorerId(attr.id.value),
              )
            }
          val normalizedQuery = attributeSearchQuery.trim().lowercase()

          if (filtered.isEmpty()) {
            Text(
              text =
                if (normalizedQuery.isBlank())
                  stringResource(R.string.device_explorer_attributes_empty)
                else stringResource(R.string.device_explorer_no_results),
              style = MaterialTheme.typography.bodyMedium,
            )
          } else {
            LazyColumn(
              state = listState,
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(filtered, key = { "attr-${it.id}" }) { attribute ->
                ExplorerRow(
                  text =
                    formatIdAndName(
                      attribute.id.value,
                      attribute.name ?: stringResource(R.string.device_explorer_attribute_unknown),
                    ),
                  secondaryText =
                    explorerSupportStatusText(
                      baseText =
                        buildString {
                          append(
                            stringResource(
                              R.string.device_explorer_attribute_privileges,
                              attribute.readPrivilege.toLabel(),
                              attribute.writePrivilege.toLabel(),
                            ),
                          )
                          append("\n")
                          append(
                            stringResource(
                              R.string.device_explorer_attribute_type,
                              attribute.type.label,
                            ),
                          )
                        },
                      isSupported = attribute.isSupported,
                    ),
                  isDimmed = !attribute.isSupported,
                  onClick = { onAttributeSelected(attribute) },
                )
              }
            }
          }
        }
      }
      ExplorerTab.COMMANDS -> {
        Column(
          modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.paddingNormal),
          verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
        ) {
          if (showSearch) {
            SearchTextField(
              value = commandSearchQuery,
              onValueChange = onCommandSearchQueryChange,
              label = { Text(stringResource(R.string.device_explorer_search_command)) },
            )
          }

          val filteredCommands =
            details.commands.filter { command ->
              matchesExplorerQuery(
                commandSearchQuery,
                command.name.orEmpty(),
                formatExplorerId(command.id.value),
              )
            }
          val normalizedQuery = commandSearchQuery.trim().lowercase()

          if (filteredCommands.isEmpty()) {
            Text(
              text =
                if (normalizedQuery.isBlank())
                  stringResource(R.string.device_explorer_commands_empty)
                else stringResource(R.string.device_explorer_no_results),
              style = MaterialTheme.typography.bodyMedium,
            )
          } else {
            LazyColumn(
              state = listState,
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(filteredCommands, key = { "cmd-${it.id}" }) { command ->
                ExplorerRow(
                  text =
                    formatIdAndName(
                      command.id.value,
                      command.name ?: stringResource(R.string.device_explorer_command_unknown),
                    ),
                  secondaryText =
                    explorerSupportStatusText(
                      baseText =
                        stringResource(
                          R.string.device_explorer_command_arguments_count,
                          command.arguments.size,
                        ),
                      isSupported = command.isSupported,
                    ),
                  isDimmed = !command.isSupported,
                  onClick = { onCommandSelected(command) },
                )
              }
            }
          }
        }
      }
      ExplorerTab.EVENTS -> {
        Column(
          modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.paddingNormal),
          verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
        ) {
          if (showSearch) {
            SearchTextField(
              value = eventSearchQuery,
              onValueChange = onEventSearchQueryChange,
              label = { Text(stringResource(R.string.device_explorer_search_event)) },
            )
          }

          val filteredEvents =
            details.events.filter { event ->
              matchesExplorerQuery(
                eventSearchQuery,
                event.name.orEmpty(),
                formatExplorerId(event.id.value),
              )
            }
          val normalizedQuery = eventSearchQuery.trim().lowercase()

          if (filteredEvents.isEmpty()) {
            Text(
              text =
                if (normalizedQuery.isBlank()) stringResource(R.string.device_explorer_events_empty)
                else stringResource(R.string.device_explorer_no_results),
              style = MaterialTheme.typography.bodyMedium,
            )
          } else {
            LazyColumn(
              state = listState,
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(filteredEvents, key = { "evt-${it.id}" }) { event ->
                ExplorerRow(
                  text =
                    formatIdAndName(
                      event.id.value,
                      event.name ?: stringResource(R.string.device_explorer_event_unknown),
                    ),
                )
              }
            }
          }
        }
      }
    }
  }
}
