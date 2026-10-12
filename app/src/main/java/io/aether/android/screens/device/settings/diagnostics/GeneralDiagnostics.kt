// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aether.android.R
import io.aether.android.data.models.GeneralDiagnosticsData
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private const val SEPARATOR = " • "

@Composable
fun GeneralDiagnostics(data: GeneralDiagnosticsData) {
  val activeHardwareFaults = data.activeHardwareFaults.joinToString(SEPARATOR) { it.name }
  val activeRadioFaults = data.activeRadioFaults.joinToString(SEPARATOR) { it.name }
  val activeNetworkFaults = data.activeNetworkFaults.joinToString(SEPARATOR) { it.name }
  DiagnosticsSection(stringResource(R.string.device_diagnostics_section_general)) {
    data.upTime?.let {
      DiagnosticsInfoRow(stringResource(R.string.device_diagnostics_label_uptime)) {
        Text(it.toLong().seconds.toString())
      }
    }
    data.totalOperationalHours?.let {
      DiagnosticsInfoRow(stringResource(R.string.device_diagnostics_label_total_operational_time)) {
        Text(it.toInt().hours.toString())
      }
    }
    DiagnosticsInfoRow(stringResource(R.string.device_diagnostics_label_reboot_count)) {
      Text(data.rebootCount.toString())
    }
    data.bootReason?.let {
      DiagnosticsInfoRow(stringResource(R.string.device_diagnostics_label_boot_reason)) {
        Text(it.name)
      }
    }
    activeHardwareFaults
      .takeIf { it.isNotEmpty() }
      ?.let {
        DiagnosticsInfoRow(
          stringResource(R.string.device_diagnostics_label_active_hardware_faults),
        ) {
          Text(it)
        }
      }
    activeRadioFaults
      .takeIf { it.isNotEmpty() }
      ?.let {
        DiagnosticsInfoRow(stringResource(R.string.device_diagnostics_label_active_radio_faults)) {
          Text(it)
        }
      }
    activeNetworkFaults
      .takeIf { it.isNotEmpty() }
      ?.let {
        DiagnosticsInfoRow(
          stringResource(R.string.device_diagnostics_label_active_network_faults),
        ) {
          Text(it)
        }
      }
  }
  DiagnosticsSection(stringResource(R.string.device_diagnostics_section_interfaces)) {
    data.networkInterfaces.forEach {
      val status =
        if (it.isOperational) stringResource(R.string.device_diagnostics_network_up)
        else stringResource(R.string.device_diagnostics_network_down)
      val hardwareAddress = it.hardwareAddress.joinToString(separator = ":") { "%02X".format(it) }
      val ipv4Addresses = it.ipv4Addresses.mapNotNull { it.hostAddress }.joinToString(SEPARATOR)
      val ipv6Addresses = it.ipv6Addresses.mapNotNull { it.hostAddress }.joinToString(SEPARATOR)
      DiagnosticsInfoRow("${it.name} (${it.type.name}) [$status]") {
        Text(stringResource(R.string.device_diagnostics_interface_hw_address, hardwareAddress))
        ipv4Addresses
          .takeIf { it.isNotEmpty() }
          ?.let { Text(stringResource(R.string.device_diagnostics_interface_ipv4_address, it)) }
        ipv6Addresses
          .takeIf { it.isNotEmpty() }
          ?.let { Text(stringResource(R.string.device_diagnostics_interface_ipv6_address, it)) }
      }
    }
  }
}
