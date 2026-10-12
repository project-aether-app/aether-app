// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens

import app.cash.paparazzi.Paparazzi
import io.aether.android.R
import io.aether.android.data.models.ManagedFabric
import io.aether.android.matter.FabricId
import io.aether.android.matter.NodeId
import io.aether.android.matter.VendorId
import io.aether.android.screens.device.settings.fabrics.FabricsScreen
import io.aether.android.screens.device.settings.fabrics.FabricsUiState
import org.junit.Rule
import org.junit.Test

class DeviceSettingsFabricsPreviewTest {

  @get:Rule val paparazzi = Paparazzi(theme = "android:Theme.Material.Light.NoActionBar")

  private val testRootFabricKey = List(64) { it.toByte() }

  private fun snapshotFabricsScreen(uiState: FabricsUiState) {
    paparazzi.snapshot {
      FabricsScreen(
        uiState = uiState,
        onBackClick = {},
        onRefresh = {},
        onRemoveController = {},
      )
    }
  }

  @Test
  fun previewLoading() {
    snapshotFabricsScreen(uiState = FabricsUiState(isFirstTimeLoading = true))
  }

  @Test
  fun previewFabrics() {
    snapshotFabricsScreen(
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
    )
  }

  @Test
  fun previewErrorMessage() {
    snapshotFabricsScreen(
      uiState =
        FabricsUiState(
          isFirstTimeLoading = false,
          errorMessageRes = R.string.device_fabrics_error,
        ),
    )
  }
}
