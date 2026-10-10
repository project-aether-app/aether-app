// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens

import app.cash.paparazzi.Paparazzi
import io.aether.android.R
import io.aether.android.data.models.EthernetNetworkDiagnosticsData
import io.aether.android.data.models.GeneralDiagnosticsData
import io.aether.android.data.models.NetworkInterface
import io.aether.android.data.models.SoftwareDiagnosticsData
import io.aether.android.data.models.ThreadMetrics
import io.aether.android.data.models.ThreadNetworkDiagnosticsData
import io.aether.android.data.models.WiFiNetworkDiagnosticsData
import io.aether.android.matter.Enums
import io.aether.android.screens.device.settings.diagnostics.DiagnosticsScreen
import io.aether.android.screens.device.settings.diagnostics.DiagnosticsUiState
import java.net.Inet6Address
import java.net.InetAddress
import org.junit.Rule
import org.junit.Test

class DeviceSettingsDiagnosticsPreviewTest {

  @get:Rule
  val paparazzi =
      Paparazzi(
          theme = "android:Theme.Material.Light.NoActionBar",
      )

  private val testGeneralDiagnostics =
      GeneralDiagnosticsData(
          networkInterfaces =
              listOf(
                  NetworkInterface(
                      type = Enums.DiagnosticsGeneralClusterInterfaceType.Ethernet,
                      name = "et0",
                      hardwareAddress = List(6) { it.toByte() },
                      ipv4Addresses = listOf(InetAddress.getByName("127.0.0.1")),
                      ipv6Addresses = listOf(Inet6Address.getByName("::1") as Inet6Address),
                  ),
                  NetworkInterface(
                      type = Enums.DiagnosticsGeneralClusterInterfaceType.WiFi,
                      name = "wl0",
                      hardwareAddress = List(6) { it.toByte() },
                      ipv6Addresses =
                          listOf(
                              Inet6Address.getByName("fe80::1") as Inet6Address,
                              Inet6Address.getByName("fe80::2") as Inet6Address,
                          ),
                      isOperational = true,
                  ),
              ),
          rebootCount = 8u,
          upTime = 200u,
          totalOperationalHours = 42u,
          bootReason = Enums.DiagnosticsGeneralClusterBootReason.PowerOnReboot,
      )

  private val testSoftwareDiagnostics =
      SoftwareDiagnosticsData(
          threadMetrics =
              listOf(
                  ThreadMetrics(
                      id = 1u,
                      name = "Main Thread",
                  ),
                  ThreadMetrics(
                      id = 2u,
                      name = "WiFi Dispatcher",
                      stackFreeCurrent = 3072u,
                      stackFreeMinimum = 1024u,
                      stackSize = 8192u,
                  ),
                  ThreadMetrics(
                      id = 3u,
                      name = "Matter Event Loop",
                      stackFreeCurrent = 2048u,
                      stackSize = 8192u,
                  ),
              ),
          currentHeapFree = 3072u,
          currentHeapUsed = 5120u,
          currentHeapHighWatermark = 6144u,
      )

  private val testEthernetNetworkDiagnostics =
      EthernetNetworkDiagnosticsData(
          phyRate = Enums.DiagnosticsEthernetClusterPHYRate.Rate5G,
          fullDuplex = true,
          packetRxCount = 100u,
          packetTxCount = 50u,
          txErrCount = 20u,
          collisionCount = 10u,
          overrunCount = 5u,
          carrierDetect = true,
          timeSinceReset = 3600u,
      )

  private val testWifiNetworkDiagnostics =
      WiFiNetworkDiagnosticsData(
          bssid = List(6) { it.toByte() },
          securityType = Enums.DiagnosticsWiFiClusterSecurityType.WPA3,
          wifiVersion = Enums.DiagnosticsWiFiClusterWiFiVersion.N,
          channelNumber = 36u,
          rssi = -50,
          beaconLostCount = 7u,
          beaconRxCount = 100u,
          packetMulticastRxCount = 50u,
          packetMulticastTxCount = 25u,
          packetUnicastRxCount = 750u,
          packetUnicastTxCount = 1600u,
          currentMaxRate = 1200000u,
          overrunCount = 5u,
      )

  private val testThreadNetworkDiagnostics = ThreadNetworkDiagnosticsData()

  private fun snapshotDiagnosticsScreen(uiState: DiagnosticsUiState) {
    paparazzi.snapshot {
      DiagnosticsScreen(
          uiState = uiState,
          onBackClick = {},
          onRefresh = {},
      )
    }
  }

  @Test
  fun previewLoading() {
    snapshotDiagnosticsScreen(
        uiState = DiagnosticsUiState(isFirstTimeLoading = true),
    )
  }

  @Test
  fun previewGeneralDiagnostics() {
    snapshotDiagnosticsScreen(
        uiState =
            DiagnosticsUiState(
                isFirstTimeLoading = false,
                generalDiagnostics = testGeneralDiagnostics,
            ),
    )
  }

  @Test
  fun previewSoftwareDiagnostics() {
    snapshotDiagnosticsScreen(
        uiState =
            DiagnosticsUiState(
                isFirstTimeLoading = false,
                softwareDiagnostics = testSoftwareDiagnostics,
            ),
    )
  }

  @Test
  fun previewEthernetNetworkDiagnostics() {
    snapshotDiagnosticsScreen(
        uiState =
            DiagnosticsUiState(
                isFirstTimeLoading = false,
                ethernetNetworkDiagnostics = testEthernetNetworkDiagnostics,
            ),
    )
  }

  @Test
  fun previewWifiNetworkDiagnostics() {
    snapshotDiagnosticsScreen(
        uiState =
            DiagnosticsUiState(
                isFirstTimeLoading = false,
                wifiNetworkDiagnostics = testWifiNetworkDiagnostics,
            ),
    )
  }

  @Test
  fun previewThreadNetworkDiagnostics() {
    snapshotDiagnosticsScreen(
        uiState =
            DiagnosticsUiState(
                isFirstTimeLoading = false,
                threadNetworkDiagnostics = testThreadNetworkDiagnostics,
            ),
    )
  }

  @Test
  fun previewErrorMessage() {
    snapshotDiagnosticsScreen(
        uiState =
            DiagnosticsUiState(
                isFirstTimeLoading = false,
                errorMessageRes = R.string.device_diagnostics_load_failed,
            ),
    )
  }
}
