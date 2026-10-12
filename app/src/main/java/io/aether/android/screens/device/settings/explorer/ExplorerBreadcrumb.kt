// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aether.android.R
import io.aether.android.chip.DeviceMatterInfo
import io.aether.android.matter.CLUSTERS
import io.aether.android.matter.DEVICES
import io.aether.android.spacing

@Composable
internal fun BreadcrumbBar(
  navStack: List<ExplorerLevel>,
  deviceMatterInfoList: List<DeviceMatterInfo>,
  onPopToIndex: (Int) -> Unit,
) {
  val scrollState = androidx.compose.foundation.rememberScrollState()
  LaunchedEffect(navStack.size) { scrollState.animateScrollTo(scrollState.maxValue) }

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .horizontalScroll(scrollState)
        .padding(
          start = 6.dp,
          top = 6.dp,
          end = MaterialTheme.spacing.paddingNormal,
          bottom = 6.dp,
        ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    navStack.forEachIndexed { index, level ->
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      val label = breadcrumbLabelFor(level, deviceMatterInfoList)
      val isLast = index == navStack.size - 1
      if (isLast) {
        Text(
          text = label,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
      } else {
        Text(
          text = label,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.clickable { onPopToIndex(index) },
        )
      }
    }
  }
  HorizontalDivider()
}

@Composable
private fun breadcrumbLabelFor(
  level: ExplorerLevel,
  deviceMatterInfoList: List<DeviceMatterInfo>,
): String =
  when (level) {
    ExplorerLevel.EndpointList -> stringResource(R.string.device_explorer_root)
    is ExplorerLevel.ClusterList -> {
      val info = deviceMatterInfoList.firstOrNull { it.endpointId == level.endpointId }
      val endpointName =
        info
          ?.types
          .orEmpty()
          .map { typeId ->
            DEVICES[typeId] ?: stringResource(R.string.device_explorer_endpoint_type_unknown)
          }
          .joinToString(" & ")
      formatEndpointLabel(level.endpointId, endpointName)
    }
    is ExplorerLevel.ClusterDetail -> {
      val name =
        CLUSTERS[level.clusterId]?.name ?: stringResource(R.string.device_explorer_cluster_unknown)
      formatIdAndName(level.clusterId, name)
    }
    is ExplorerLevel.AttributeDetail ->
      formatIdAndName(level.attribute.id.value, level.attribute.name)
    is ExplorerLevel.CommandInvoke -> formatIdAndName(level.command.id.value, level.command.name)
  }
