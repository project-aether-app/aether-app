package io.aether.android.screens

import app.cash.paparazzi.Paparazzi
import io.aether.android.Device
import io.aether.android.R
import io.aether.android.chip.BasicInformationAttributes
import io.aether.android.matter.Devices
import io.aether.android.matter.NodeId
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import io.aether.android.screens.device.DeviceScreen
import io.aether.android.screens.device.DeviceUiState
import io.aether.android.screens.home.DeviceUiModel
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
          deviceTypeId = Devices.OnOffPluginUnit.ID,
          name = "Test Plug-in Device",
          isOnline = true,
      )

  private val testDeviceBasicInformation =
      BasicInformationAttributes(
          vendorId = VendorId(0x1135u),
          productId = ProductId(0x1234u),
          hardwareVersion = "2.4",
          softwareVersion = "1.31.42",
      )

  private fun snapshotDeviceScreen(uiState: DeviceUiState) {
    paparazzi.snapshot {
      DeviceScreen(
          uiState = uiState,
          onBackClick = {},
          onSettingsClick = {},
          onOnOffClick = { endpointModel: DeviceUiModel, value: Boolean -> },
          onBrightnessChange = { endpointModel: DeviceUiModel, value: Int -> },
          onColorTemperatureChange = { endpointModel: DeviceUiModel, value: Int -> },
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
  fun previewDeviceSettings() {
    snapshotDeviceScreen(
        uiState =
            DeviceUiState(
                isFirstTimeLoading = false,
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
