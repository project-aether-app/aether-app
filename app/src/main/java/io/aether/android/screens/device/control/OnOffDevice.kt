// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.aether.android.data.DevicesRepository
import io.aether.android.data.models.Device
import io.aether.android.screens.device.cluster.OnOffClusterControl
import io.aether.android.spacing

/**
 * Device-type control for endpoints that expose only the **OnOff** cluster (e.g. smart outlets,
 * on/off lights). Manages local state and delegates rendering to [OnOffClusterControl].
 *
 * @param endpointModel the endpoint whose state is shown
 * @param lastUpdatedDeviceState the most recent state broadcast from the repository; used to keep
 *   local state in sync without a full model reload
 * @param onOnOffClick called when the user toggles the switch; receives the new on/off value
 */
@Composable
internal fun OnOffDeviceControl(
    device: Device,
    endpoint: Device.Endpoint,
    lastUpdatedDeviceState: DevicesRepository.EndpointStateSnapshot?,
    onOnOffClick: (Boolean) -> Unit,
) {
  var isOnline by remember(device, endpoint) { mutableStateOf(device.isOnline) }
  var isOn by remember(device, endpoint) { mutableStateOf(endpoint.isOn) }

  LaunchedEffect(device, endpoint, lastUpdatedDeviceState) {
    when {
      lastUpdatedDeviceState?.nodeId == device.nodeId &&
          lastUpdatedDeviceState.endpointId == endpoint.endpointId -> {
        isOnline = lastUpdatedDeviceState.online
        isOn = lastUpdatedDeviceState.on
      }
      lastUpdatedDeviceState == null -> {
        isOnline = device.isOnline
        isOn = endpoint.isOn
      }
    }
  }

  Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingNormal)) {
    OnOffClusterControl(
        isOnline = isOnline,
        isOn = isOn,
        onToggle = { value ->
          isOn = value
          onOnOffClick(value)
        },
    )
  }
}
