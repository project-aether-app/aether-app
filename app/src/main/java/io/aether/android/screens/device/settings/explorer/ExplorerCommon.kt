// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.aether.android.R
import io.aether.android.matter.AttributeId
import io.aether.android.matter.ClusterId
import io.aether.android.matter.CommandId
import io.aether.android.matter.DeviceTypeId
import io.aether.android.matter.EndpointId
import io.aether.android.matter.EventId

@Composable
internal fun ExplorerRow(
  text: String,
  secondaryText: String? = null,
  isDimmed: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val primaryColor =
    if (isDimmed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
    else MaterialTheme.colorScheme.onSurface
  val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(enabled = onClick != null) { onClick?.invoke() }
        .padding(horizontal = 8.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(text = text, style = MaterialTheme.typography.bodyLarge, color = primaryColor)
      if (!secondaryText.isNullOrBlank()) {
        Text(
          text = secondaryText,
          style = MaterialTheme.typography.bodySmall,
          color = secondaryColor,
        )
      }
    }
  }
}

internal fun formatEndpointLabel(endpointId: EndpointId, name: String?): String =
  if (name.isNullOrBlank()) {
    "[${formatEndpointId(endpointId)}]"
  } else {
    "[${formatEndpointId(endpointId)}] $name"
  }

internal fun formatIdAndName(id: UInt, name: String?): String {
  val idText = formatExplorerId(id)
  return if (name.isNullOrBlank()) {
    "[$idText]"
  } else {
    "[$idText] $name"
  }
}

internal fun formatIdAndName(id: ClusterId, name: String?): String = formatIdAndName(id.value, name)

internal fun formatIdAndName(id: AttributeId, name: String?): String =
  formatIdAndName(id.value, name)

internal fun formatIdAndName(id: CommandId, name: String?): String = formatIdAndName(id.value, name)

internal fun formatIdAndName(id: EventId, name: String?): String = formatIdAndName(id.value, name)

internal fun formatIdAndName(id: DeviceTypeId, name: String?): String =
  formatIdAndName(id.value, name)

internal fun formatExplorerId(id: UInt): String =
  if (id <= 0xFFFFu) String.format("0x%04X", id.toLong()) else String.format("0x%08X", id.toLong())

internal fun formatExplorerId(id: ClusterId): String = formatExplorerId(id.value)

internal fun formatEndpointId(endpointId: EndpointId): String =
  if (endpointId.toInt() <= 0xFF) String.format("0x%02X", endpointId.toInt())
  else String.format("0x%04X", endpointId.toInt())

internal fun matchesExplorerQuery(query: String, vararg fields: String): Boolean {
  val normalizedQuery = query.trim().lowercase()
  if (normalizedQuery.isBlank()) {
    return true
  }
  return fields.any { it.lowercase().contains(normalizedQuery) }
}

@Composable
internal fun explorerSupportStatusText(baseText: String, isSupported: Boolean): String =
  if (isSupported) {
    baseText
  } else {
    "$baseText\n${stringResource(R.string.device_explorer_not_supported)}"
  }
