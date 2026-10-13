// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens

import app.cash.paparazzi.Paparazzi
import io.aether.android.R
import io.aether.android.data.models.Device
import io.aether.android.data.models.DeviceType
import io.aether.android.data.models.Endpoint
import io.aether.android.data.models.Product
import io.aether.android.data.models.Vendor
import io.aether.android.matter.Devices
import io.aether.android.matter.NodeId
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import io.aether.android.screens.device.DeviceScreen
import io.aether.android.screens.device.DeviceUiState
import org.junit.Rule
import org.junit.Test

class DevicePreviewTest {

  @get:Rule val paparazzi = Paparazzi(theme = "android:Theme.Material.Light.NoActionBar")

  private val testDevice =
    Device(
      id = NodeId(0x1234u),
      vendor = Vendor(id = VendorId(0x1135u)),
      product = Product(id = ProductId(0x1234u)),
      name = "Test Device",
      isOnline = false,
      endpoints =
        listOf(
          Endpoint(
            id = io.aether.android.matter.EndpointId(1u),
            deviceTypes = listOf(Devices.OnOffPluginUnit.ID).map { DeviceType(id = it) },
          ),
          Endpoint(
            id = io.aether.android.matter.EndpointId(2u),
            deviceTypes =
              listOf(
                  Devices.DimmableLight.ID,
                  Devices.OnOffPluginUnit.ID,
                )
                .map { DeviceType(id = it) },
            level = 50,
          ),
        ),
    )

  private fun snapshotDeviceScreen(uiState: DeviceUiState) {
    paparazzi.snapshot {
      DeviceScreen(
        uiState = uiState,
        onBackClick = {},
        onSettingsClick = {},
        onRefresh = {},
        onOnOffClick = { _: Endpoint, _: Boolean -> },
        onBrightnessChange = { _: Endpoint, _: Int -> },
        onColorTemperatureChange = { _: Endpoint, _: Int -> },
        onDismissMsgDialog = {},
      )
    }
  }

  @Test
  fun previewLoading() {
    snapshotDeviceScreen(uiState = DeviceUiState(isFirstTimeLoading = true))
  }

  @Test
  fun previewDevice() {
    snapshotDeviceScreen(
      uiState =
        DeviceUiState(
          device = testDevice,
        ),
    )
  }

  @Test
  fun previewErrorMessage() {
    snapshotDeviceScreen(
      uiState =
        DeviceUiState(
          errorMessageRes = R.string.controllers_offline,
        ),
    )
  }
}
