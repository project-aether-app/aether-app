// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aether.android.R
import io.aether.android.data.models.EthernetNetworkDiagnosticsData
import kotlin.time.Duration.Companion.seconds

private const val SEPARATOR = " • "

@Composable
fun EthernetNetworkDiagnostics(data: EthernetNetworkDiagnosticsData) {

  val networkTraffic = buildList {
    data.packetRxCount?.let {
      add(stringResource(R.string.device_diagnostics_network_rx, it.toString()))
    }
    data.packetTxCount?.let {
      add(stringResource(R.string.device_diagnostics_network_tx, it.toString()))
    }
  }
    .joinToString(SEPARATOR)

  val linkQuality = buildList {
    data.txErrCount?.let {
      add(stringResource(R.string.device_diagnostics_network_tx_errors, it.toString()))
    }
    data.collisionCount?.let {
      add(stringResource(R.string.device_diagnostics_network_collisions, it.toString()))
    }
    data.overrunCount?.let {
      add(stringResource(R.string.device_diagnostics_network_drops, it.toString()))
    }
  }
    .joinToString(SEPARATOR)

  val linkProperties = buildList {
    data.phyRate?.let { add(it.name) }
    data.fullDuplex
      ?.takeIf { it }
      ?.let { add(stringResource(R.string.device_diagnostics_network_full_duplex)) }
    data.carrierDetect
      ?.takeIf { !it }
      ?.let { add(stringResource(R.string.device_diagnostics_network_no_carrier)) }
  }
    .joinToString(SEPARATOR)

  DiagnosticsSection(stringResource(R.string.device_diagnostics_section_ethernet_network)) {
    if (networkTraffic.isNotEmpty()) {
      DiagnosticsInfoRow(stringResource(R.string.device_diagnostics_label_network_traffic)) {
        Text(networkTraffic)
      }
    }
    if (linkQuality.isNotEmpty()) {
      DiagnosticsInfoRow(stringResource(R.string.device_diagnostics_label_network_link_quality)) {
        Text(linkQuality)
      }
    }
    if (linkProperties.isNotEmpty()) {
      DiagnosticsInfoRow(
        stringResource(R.string.device_diagnostics_label_network_link_properties),
      ) {
        Text(linkProperties)
      }
    }
    data.timeSinceReset?.let {
      DiagnosticsInfoRow(stringResource(R.string.device_diagnostics_label_time_since_reset)) {
        Text(it.toLong().seconds.toString())
      }
    }
  }
}
