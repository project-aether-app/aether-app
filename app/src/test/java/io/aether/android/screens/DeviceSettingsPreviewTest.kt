// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens

import app.cash.paparazzi.Paparazzi
import io.aether.android.R
import io.aether.android.chip.BasicInformationAttributes
import io.aether.android.data.models.Device
import io.aether.android.matter.Devices
import io.aether.android.matter.NodeId
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import io.aether.android.screens.device.settings.DeviceSettingsScreen
import io.aether.android.screens.device.settings.DeviceSettingsUiState
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class DeviceSettingsPreviewTest {

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

  private val testDeviceBasicInformation =
      BasicInformationAttributes(
          vendorId = VendorId(0x1135u),
          productId = ProductId(0x1234u),
          hardwareVersion = "2.4",
          softwareVersion = "1.31.42",
      )

  private fun snapshotDeviceSettingsScreen(uiState: DeviceSettingsUiState) {
    paparazzi.snapshot {
      DeviceSettingsScreen(
          uiState = uiState,
          onBackClick = {},
          onDismissMsgDialog = {},
          onDeviceNameChange = {},
          onDeviceTypeChange = {},
          onManageFabricsClick = {},
          onDataModelExplorerClick = {},
          onDiagnosticsClick = {},
          onShareDeviceClick = {},
          onShareDeviceResult = {},
          onRemoveDeviceClick = {},
          onRemoveDeviceResult = {},
          onForceRemoveDeviceResult = {},
      )
    }
  }

  @Test
  fun previewLoading() {
    snapshotDeviceSettingsScreen(
        uiState = DeviceSettingsUiState(isFirstTimeLoading = true),
    )
  }

  @Test
  fun previewDeviceSettings() {
    snapshotDeviceSettingsScreen(
        uiState =
            DeviceSettingsUiState(
                isFirstTimeLoading = false,
                device = testDevice,
                basicInformation = testDeviceBasicInformation,
                dateCommissioned = Instant.ofEpochSecond(1680000000L),
            ),
    )
  }

  @Test
  fun previewDeviceSettingsShowShareDialog() {
    snapshotDeviceSettingsScreen(
        uiState =
            DeviceSettingsUiState(
                isFirstTimeLoading = false,
                device = testDevice,
                dateCommissioned = Instant.ofEpochSecond(1680000000L),
                showShareDeviceAlertDialog = true,
            ),
    )
  }

  @Test
  fun previewDeviceSettingsShowRemoveDialog() {
    snapshotDeviceSettingsScreen(
        uiState =
            DeviceSettingsUiState(
                isFirstTimeLoading = false,
                device = testDevice,
                dateCommissioned = Instant.ofEpochSecond(1680000000L),
                showRemoveDeviceAlertDialog = true,
            ),
    )
  }

  @Test
  fun previewDeviceSettingsShowLocalRemoveDialog() {
    snapshotDeviceSettingsScreen(
        uiState =
            DeviceSettingsUiState(
                isFirstTimeLoading = false,
                device = testDevice,
                dateCommissioned = Instant.ofEpochSecond(1680000000L),
                showLocalRemoveDeviceAlertDialog = true,
            ),
    )
  }

  @Test
  fun previewErrorMessage() {
    snapshotDeviceSettingsScreen(
        uiState =
            DeviceSettingsUiState(
                isFirstTimeLoading = false,
                errorMessageRes = R.string.controllers_offline,
            ),
    )
  }
}
