// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.scanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aether.android.R
import io.aether.android.spacing
import timber.log.Timber

/**
 * Fragment used to display a list of nearby discovered Matter devices (discoverable over BLE,
 * Wi-Fi, or mDNS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScannerRoute(
    onBackClick: () -> Unit,
    scannerViewModel: ScannerViewModel = hiltViewModel(),
) {
  val uiState by scannerViewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.menu_item_scanner)) },
            navigationIcon = {
              IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_button),
                )
              }
            },
        )
      },
  ) { innerPadding ->
    val modifierWithInnerPadding = Modifier.fillMaxSize().padding(innerPadding)
    ScannerScreen(uiState = uiState, modifier = modifierWithInnerPadding)
  }
}

@Composable
private fun ScannerScreen(uiState: ScannerUiState, modifier: Modifier = Modifier) {
  Box(modifier = modifier) {
    LazyColumn(modifier = Modifier.padding(MaterialTheme.spacing.paddingSurfaceContent)) {
      this.items(uiState.beacons) { MatterBeaconItem(it) }
    }
  }
}

@Composable
fun MatterBeaconItem(beacon: MatterBeacon) {
  val icon =
      when (beacon.transport) {
        is Transport.Ble -> R.drawable.quantum_gm_ic_bluetooth_vd_theme_24
        is Transport.Hotspot -> R.drawable.quantum_gm_ic_wifi_vd_theme_24
        is Transport.Mdns -> R.drawable.quantum_gm_ic_router_vd_theme_24
      }
  Row(
      modifier =
          Modifier.clickable {
            // [TODO] Selecting an item in this list could display a screen with detailed
            // information
            //  about the device, and allow actions on it such as "commissioning".
            Timber.d("beacon item clicked")
          }
  ) {
    Image(
        painter = painterResource(icon),
        contentDescription = stringResource(R.string.transport_icon),
        modifier = Modifier.padding(4.dp).align(Alignment.CenterVertically),
    )
    Column(modifier = Modifier.padding(8.dp).align(Alignment.CenterVertically)) {
      val text: String
      val color: Color
      if (beacon.transport is Transport.Mdns) {
        val active = beacon.transport.active
        if (!active) {
          text = beacon.name + " [off]"
          color = MaterialTheme.colorScheme.error
        } else {
          text = beacon.name
          color = MaterialTheme.colorScheme.onSurface
        }
      } else {
        text = beacon.name
        color = MaterialTheme.colorScheme.onSurface
      }
      Text(text = text, color = color, style = MaterialTheme.typography.titleMedium)
      Text(
          text =
              stringResource(
                  R.string.beacon_detail_text,
                  beacon.vendorId.toInt(),
                  beacon.productId.toInt(),
                  beacon.discriminator,
              ),
          style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}
