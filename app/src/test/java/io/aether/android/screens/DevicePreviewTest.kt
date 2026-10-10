// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens

import app.cash.paparazzi.Paparazzi
import io.aether.android.R
import io.aether.android.data.models.Device
import io.aether.android.matter.Devices
import io.aether.android.matter.NodeId
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import io.aether.android.screens.device.DeviceScreen
import io.aether.android.screens.device.DeviceUiState
import org.junit.Rule
import org.junit.Test

class DevicePreviewTest {

  @get:Rule
  val paparazzi =
      Paparazzi(
          theme = "android:Theme.Material.Light.NoActionBar",
      )

  private val testDevice =
      Device(
          nodeId = NodeId(0x1234u),
          vendorId = VendorId(0x1135u),
          productId = ProductId(0x1234u),
          name = "Test Plug-in Device",
          isOnline = true,
          endpoints =
              listOf(
                  Device.Endpoint(
                      endpointId = io.aether.android.matter.EndpointId(1u),
                      deviceTypeId = Devices.OnOffPluginUnit.ID,
                  ),
              ),
      )

  private fun snapshotDeviceScreen(uiState: DeviceUiState) {
    paparazzi.snapshot {
      DeviceScreen(
          uiState = uiState,
          onBackClick = {},
          onSettingsClick = {},
          onOnOffClick = { _: Device.Endpoint, _: Boolean -> },
          onBrightnessChange = { _: Device.Endpoint, _: Int -> },
          onColorTemperatureChange = { _: Device.Endpoint, _: Int -> },
          onDismissMsgDialog = {},
      )
    }
  }

  @Test
  fun previewLoading() {
    snapshotDeviceScreen(
        uiState = DeviceUiState(isFirstTimeLoading = true),
    )
  }

  @Test
  fun previewDevice() {
    snapshotDeviceScreen(
        uiState =
            DeviceUiState(
                isFirstTimeLoading = false,
                device = testDevice,
            ),
    )
  }

  @Test
  fun previewErrorMessage() {
    snapshotDeviceScreen(
        uiState =
            DeviceUiState(
                isFirstTimeLoading = false,
                errorMessageRes = R.string.controllers_offline,
            ),
    )
  }
}
