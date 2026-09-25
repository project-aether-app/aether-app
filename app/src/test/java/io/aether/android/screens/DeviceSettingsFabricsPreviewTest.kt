package io.aether.android.screens

import app.cash.paparazzi.Paparazzi
import io.aether.android.R
import io.aether.android.data.models.ManagedFabric
import io.aether.android.matter.FabricId
import io.aether.android.matter.NodeId
import io.aether.android.matter.VendorId
import io.aether.android.screens.device.settings.fabrics.FabricsRouteContent
import io.aether.android.screens.device.settings.fabrics.FabricsUiState
import org.junit.Rule
import org.junit.Test

class DeviceSettingsFabricsPreviewTest {

  @get:Rule
  val paparazzi =
      Paparazzi(
          theme = "android:Theme.Material.Light.NoActionBar",
      )

  private val testRootFabricKey = ByteArray(64) { it.toByte() }

  @Test
  fun previewLoading() {
    paparazzi.snapshot {
      FabricsRouteContent(
          uiState = FabricsUiState(isFirstTimeLoading = true),
          onBackClick = {},
          onRefresh = {},
          onRemoveController = {},
      )
    }
  }

  @Test
  fun previewFabrics() {
    paparazzi.snapshot {
      FabricsRouteContent(
          uiState =
              FabricsUiState(
                  isFirstTimeLoading = false,
                  fabrics =
                      listOf(
                          ManagedFabric(
                              fabricIndex = 1,
                              rootPublicKey = testRootFabricKey,
                              vendorId = VendorId(0x1349u),
                              fabricId = FabricId(0x123456u),
                              nodeId = NodeId(0x42u),
                              isCurrentFabric = true,
                          ),
                          ManagedFabric(
                              fabricIndex = 2,
                              rootPublicKey = testRootFabricKey,
                              vendorId = VendorId(0x100Cu),
                              fabricId = FabricId(0xABCDEu),
                              nodeId = NodeId(0x42u),
                          ),
                          ManagedFabric(
                              fabricIndex = 3,
                              rootPublicKey = testRootFabricKey,
                              vendorId = VendorId(0x1234u),
                              fabricId = FabricId(0xABCDEu),
                              nodeId = NodeId(0x42u),
                              label = "Home Assistant - Living Room",
                          ),
                      ),
              ),
          onBackClick = {},
          onRefresh = {},
          onRemoveController = {},
      )
    }
  }

  @Test
  fun previewNoFabrics() {
    paparazzi.snapshot {
      FabricsRouteContent(
          uiState =
              FabricsUiState(
                  isFirstTimeLoading = false,
              ),
          onBackClick = {},
          onRefresh = {},
          onRemoveController = {},
      )
    }
  }

  @Test
  fun previewErrorMessage() {
    paparazzi.snapshot {
      FabricsRouteContent(
          uiState =
              FabricsUiState(
                  isFirstTimeLoading = false,
                  errorMessageRes = R.string.controllers_offline,
              ),
          onBackClick = {},
          onRefresh = {},
          onRemoveController = {},
      )
    }
  }
}
