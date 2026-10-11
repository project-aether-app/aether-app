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
import io.aether.android.data.DeviceRepository
import io.aether.android.data.models.Device
import io.aether.android.data.models.Endpoint
import io.aether.android.screens.device.cluster.COLOR_TEMPERATURE_MAX
import io.aether.android.screens.device.cluster.LEVEL_MAX
import io.aether.android.screens.device.cluster.LevelClusterControl
import io.aether.android.screens.device.cluster.OnOffClusterControl
import io.aether.android.spacing

/**
 * Device-type control for endpoints that expose the **OnOff**, **Level Control**, and **Color
 * Control** (colour temperature) clusters (e.g. colour-temperature and extended-colour lights).
 * Manages local state and delegates rendering to [OnOffClusterControl] and [LevelClusterControl].
 *
 * @param endpointModel the endpoint whose state is shown
 * @param lastUpdatedDeviceState the most recent state broadcast from the repository
 * @param onOnOffClick called when the user toggles the switch
 * @param onBrightnessChange called when the brightness slider is released; raw value `0..254`
 * @param onColorTemperatureChange called when the colour-temperature slider is released; raw mireds
 *   value `0..1667`
 */
@Composable
internal fun ColorTemperatureDeviceControl(
    device: Device,
    endpoint: Endpoint,
    lastUpdatedDeviceState: DeviceRepository.EndpointStateSnapshot?,
    onOnOffClick: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onColorTemperatureChange: (Int) -> Unit,
) {
  var isOnline by remember(device, endpoint) { mutableStateOf(device.isOnline) }
  var isOn by remember(device, endpoint) { mutableStateOf(endpoint.isOn) }
  var brightness by remember(device, endpoint) { mutableFloatStateOf(endpoint.level / LEVEL_MAX) }
  var colorTemperature by
      remember(device, endpoint) {
        mutableFloatStateOf(endpoint.colorTemperature / COLOR_TEMPERATURE_MAX)
      }

  LaunchedEffect(device, endpoint, lastUpdatedDeviceState) {
    when {
      lastUpdatedDeviceState?.nodeId == device.id &&
          lastUpdatedDeviceState.endpointId == endpoint.id -> {
        isOnline = lastUpdatedDeviceState.online
        isOn = lastUpdatedDeviceState.on
        brightness = lastUpdatedDeviceState.level / LEVEL_MAX
        colorTemperature = lastUpdatedDeviceState.colorTemperature / COLOR_TEMPERATURE_MAX
      }
      lastUpdatedDeviceState == null -> {
        isOnline = device.isOnline
        isOn = endpoint.isOn
        brightness = endpoint.level / LEVEL_MAX
        colorTemperature = endpoint.colorTemperature / COLOR_TEMPERATURE_MAX
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
    LevelClusterControl(
        title = stringResource(R.string.color_temperature),
        isOnline = isOnline,
        isOn = isOn,
        level = colorTemperature,
        onLevelChange = { colorTemperature = it },
        onLevelChangeFinished = {
          onColorTemperatureChange((colorTemperature * COLOR_TEMPERATURE_MAX).toInt())
        },
    )
  }
}
