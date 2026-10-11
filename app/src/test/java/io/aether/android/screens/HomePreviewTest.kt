// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens

import app.cash.paparazzi.Paparazzi
import io.aether.android.data.models.Device
import io.aether.android.data.models.DeviceType
import io.aether.android.data.models.Endpoint
import io.aether.android.matter.Devices
import io.aether.android.matter.NodeId
import io.aether.android.screens.home.HomeScreen
import io.aether.android.screens.home.HomeUiState
import org.junit.Rule
import org.junit.Test

class HomePreviewTest {

  @get:Rule
  val paparazzi =
      Paparazzi(
          theme = "android:Theme.Material.Light.NoActionBar",
      )

  private val testDevice1 =
      Device(
          id = NodeId(0x1234u),
          name = "Test Device 1",
          isOnline = false,
          endpoints =
              listOf(
                  Endpoint(
                      id = io.aether.android.matter.EndpointId(1u),
                      deviceTypes = listOf(Devices.OnOffPluginUnit.ID).map { DeviceType(id = it) },
                  ),
              ),
      )

  private val testDevice2 =
      Device(
          id = NodeId(0xABCDu),
          name = "Test Device 2",
          isOnline = true,
          endpoints =
              listOf(
                  Endpoint(
                      id = io.aether.android.matter.EndpointId(1u),
                      deviceTypes = listOf(Devices.DimmableLight.ID).map { DeviceType(id = it) },
                  ),
              ),
      )

  private fun snapshotHomeScreen(uiState: HomeUiState) {
    paparazzi.snapshot {
      HomeScreen(
          uiState = uiState,
          onMenuClick = {},
          onUpdatePlayServicesClick = {},
          onCommissionDeviceClick = {},
          onCommissionedDeviceNameCaptured = {},
          onDeviceClick = { _: Device -> },
          onOnOffClick = { _: NodeId, _: Boolean -> },
          onDismissMsgDialog = {},
      )
    }
  }

  @Test
  fun previewEmpty() {
    snapshotHomeScreen(
        uiState = HomeUiState(),
    )
  }

  @Test
  fun previewDevices() {
    snapshotHomeScreen(
        uiState =
            HomeUiState(
                devices = listOf(testDevice1, testDevice2),
            ),
    )
  }
}
