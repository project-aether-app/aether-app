// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.aether.android.R
import io.aether.android.data.DevicesRepository
import io.aether.android.data.models.Device
import io.aether.android.screens.device.cluster.LEVEL_MAX
import io.aether.android.screens.device.cluster.LevelClusterControl
import io.aether.android.screens.device.cluster.OnOffClusterControl
import io.aether.android.spacing

/**
 * Device-type control for endpoints that expose the **OnOff** and **Level Control** clusters (e.g.
 * dimmable lights). Manages local state and delegates rendering to [OnOffClusterControl] and
 * [LevelClusterControl].
 *
 * @param endpointModel the endpoint whose state is shown
 * @param lastUpdatedDeviceState the most recent state broadcast from the repository
 * @param onOnOffClick called when the user toggles the switch
 * @param onBrightnessChange called when the user finishes moving the brightness slider; receives
 *   the raw cluster value in the range `0..254`
 */
@Composable
internal fun DimmableDeviceControl(
    device: Device,
    endpoint: Device.Endpoint,
    lastUpdatedDeviceState: DevicesRepository.EndpointStateSnapshot?,
    onOnOffClick: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
) {
  var isOnline by remember(device, endpoint) { mutableStateOf(device.isOnline) }
  var isOn by remember(device, endpoint) { mutableStateOf(endpoint.isOn) }
  var brightness by remember(device, endpoint) { mutableFloatStateOf(endpoint.level / LEVEL_MAX) }

  LaunchedEffect(device, endpoint, lastUpdatedDeviceState) {
    when {
      lastUpdatedDeviceState?.nodeId == device.nodeId &&
          lastUpdatedDeviceState.endpointId == endpoint.endpointId -> {
        isOnline = lastUpdatedDeviceState.online
        isOn = lastUpdatedDeviceState.on
        brightness = lastUpdatedDeviceState.level / LEVEL_MAX
      }
      lastUpdatedDeviceState == null -> {
        isOnline = device.isOnline
        isOn = endpoint.isOn
        brightness = endpoint.level / LEVEL_MAX
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
    LevelClusterControl(
        title = stringResource(R.string.brightness),
        isOnline = isOnline,
        isOn = isOn,
        level = brightness,
        onLevelChange = { brightness = it },
        onLevelChangeFinished = { onBrightnessChange((brightness * LEVEL_MAX).toInt()) },
    )
  }
}
