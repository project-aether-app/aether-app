// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.aether.android.R
import io.aether.android.data.models.WiFiNetworkDiagnosticsData
import io.aether.android.spacing

private const val SEPARATOR = " • "

@Composable
fun WiFiNetworkDiagnostics(data: WiFiNetworkDiagnosticsData) {

  val networkTrafficUnicast = buildList {
    data.packetUnicastRxCount?.let {
      add(stringResource(R.string.device_diagnostics_network_rx, it.toString()))
    }
    data.packetUnicastTxCount?.let {
      add(stringResource(R.string.device_diagnostics_network_tx, it.toString()))
    }
  }
      .joinToString(SEPARATOR)
  val networkTrafficMulticast = buildList {
    data.packetMulticastRxCount?.let {
      add(stringResource(R.string.device_diagnostics_network_rx, it.toString()))
    }
    data.packetMulticastTxCount?.let {
      add(stringResource(R.string.device_diagnostics_network_tx, it.toString()))
    }
  }
      .joinToString(SEPARATOR)

  val linkQuality = buildList {
    data.beaconRxCount?.let {
      add(stringResource(R.string.device_diagnostics_network_beacon_rx, it.toString()))
    }
    data.beaconLostCount?.let {
      add(stringResource(R.string.device_diagnostics_network_beacon_lost, it.toString()))
    }
    data.overrunCount?.let {
      add(stringResource(R.string.device_diagnostics_network_drops, it.toString()))
    }
  }
      .joinToString(SEPARATOR)

  val linkProperties = buildList {
    data.securityType?.let { add(it.name) }
    data.wifiVersion?.let { add(it.name) }
    data.channelNumber?.let {
      add(stringResource(R.string.device_diagnostics_network_channel, it.toString()))
    }
    data.currentMaxRate?.let {
      add(
          stringResource(
              R.string.device_diagnostics_network_rate,
              it.toFloat() / 1000000,
          ),
      )
    }
  }
      .joinToString(SEPARATOR)

  DiagnosticsSection(stringResource(R.string.device_diagnostics_section_wifi_network)) {
    data.bssid?.let {
      DiagnosticsInfoRow(stringResource(R.string.device_diagnostics_label_bssid)) {
        Text(it.joinToString(separator = ":") { "%02X".format(it) })
      }
    }
    if (networkTrafficUnicast.isNotEmpty() || networkTrafficMulticast.isNotEmpty()) {
      DiagnosticsInfoRow(stringResource(R.string.device_diagnostics_label_network_traffic)) {
        networkTrafficUnicast
            .takeIf { it.isNotEmpty() }
            ?.let { Text(stringResource(R.string.device_diagnostics_network_unicast, it)) }
        networkTrafficMulticast
            .takeIf { it.isNotEmpty() }
            ?.let { Text(stringResource(R.string.device_diagnostics_network_multicast, it)) }
      }
    }
    if (data.rssi != null || linkQuality.isNotEmpty()) {
      DiagnosticsInfoRow(stringResource(R.string.device_diagnostics_label_network_link_quality)) {
        data.rssi?.let {
          // Normalize RSSI (-100 to -30 dBm) to a 0.0f to 1.0f float range.
          val progress = (it.coerceIn(-100, -30) + 100) / 70f
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(stringResource(R.string.device_diagnostics_network_rssi))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f),
                color =
                    when {
                      progress > 0.7f -> MaterialTheme.colorScheme.primary
                      progress > 0.4f -> Color.Yellow
                      else -> MaterialTheme.colorScheme.error
                    },
            )
            Text(stringResource(R.string.device_diagnostics_network_dbm, it))
          }
        }
        linkQuality.takeIf { it.isNotEmpty() }?.let { Text(it) }
      }
    }
    if (linkProperties.isNotEmpty()) {
      DiagnosticsInfoRow(
          stringResource(R.string.device_diagnostics_label_network_link_properties),
      ) {
        Text(linkProperties)
      }
    }
  }
}
