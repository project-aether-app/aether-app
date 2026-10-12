// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import android.bluetooth.BluetoothAdapter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.aether.android.screens.common.HtmlInfoDialog
import io.aether.android.screens.common.MsgAlertDialog
import io.aether.android.screens.settings.DeveloperUtilitiesViewModel
import io.aether.android.screens.thread.getActivity
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.rememberPreferenceState
import timber.log.Timber

@Composable
fun AppLayout(navController: NavHostController) {
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val scope = rememberCoroutineScope()

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route

  val developerUtilitiesViewModel: DeveloperUtilitiesViewModel = hiltViewModel()
  val msgDialogInfo by developerUtilitiesViewModel.msgDialogInfo.collectAsStateWithLifecycle()
  val onDismissMsgDialog: () -> Unit = remember {
    { developerUtilitiesViewModel.dismissMsgDialog() }
  }

  val showGpsMatterDiscoveryPref = rememberPreferenceState("halfsheet_preference", false)
  var showGpsMatterDiscoveryDialog by remember { mutableStateOf(false) }
  var showAboutDialog by remember { mutableStateOf(false) }

  val activity = LocalContext.current.getActivity()

  val drawerWidthDp =
    with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.width * 0.8f).toDp() }

  val scanningPermissionsLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
      if (results.values.any { !it }) {
        developerUtilitiesViewModel.showMsgDialog(
          "Scanning Permissions",
          "Scanning permissions were not granted, so unfortunately " +
            "the \"Commissionable Devices\" feature is not available.",
        )
      } else {
        navController.navigate(DEST_SCANNER)
      }
    }

  val onCommissionableDevicesClick: () -> Unit = {
    scope.launch { drawerState.close() }
    val ctx = activity?.applicationContext
    if (ctx == null) {
      Timber.w("Cannot check scanning permissions: activity is null")
    } else {
      developerUtilitiesViewModel.logScanningPermissions(ctx)
      if (!developerUtilitiesViewModel.allScanningPermissionsGranted(ctx)) {
        Timber.d("All scanning permissions NOT granted. Asking for them.")
        scanningPermissionsLauncher.launch(
          developerUtilitiesViewModel.getRequiredScanningPermissions(),
        )
      } else {
        @Suppress("DEPRECATION") val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter.isEnabled) {
          navController.navigate(DEST_SCANNER)
        } else {
          developerUtilitiesViewModel.showMsgDialog(
            "Bluetooth is not enabled",
            "Bluetooth must be enabled on your phone to allow discovery of matter devices",
          )
        }
      }
    }
  }

  msgDialogInfo?.let { dialogInfo -> MsgAlertDialog(dialogInfo, onDismissMsgDialog) }

  ModalNavigationDrawer(
    drawerState = drawerState,
    // Gestures are enabled only when the drawer is already open, so the user can swipe it
    // closed. Opening is intentionally restricted to the hamburger icon tap only.
    gesturesEnabled = drawerState.isOpen,
    drawerContent = {
      ModalDrawerSheet(modifier = Modifier.width(drawerWidthDp)) {
        // Menu header with app icon and name
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_launcher_aether),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
          )
        }
        HorizontalDivider()
        NavigationDrawerItem(
          icon = {
            Icon(
              painter = painterResource(id = R.drawable.ic_baseline_search_24),
              contentDescription = null,
            )
          },
          label = { Text(stringResource(R.string.menu_item_scanner)) },
          selected = currentRoute == DEST_SCANNER,
          onClick = onCommissionableDevicesClick,
          modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
          icon = {
            Icon(
              painter = painterResource(id = R.drawable.baseline_device_hub_24),
              contentDescription = null,
            )
          },
          label = { Text(stringResource(R.string.menu_item_thread)) },
          selected = currentRoute == DEST_THREAD,
          onClick = {
            scope.launch { drawerState.close() }
            navController.navigate(DEST_THREAD)
          },
          modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        HorizontalDivider()
        // GPS Matter Discovery Notification toggle
        var gpsDiscoveryValue by showGpsMatterDiscoveryPref
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clickable {
                gpsDiscoveryValue = !gpsDiscoveryValue
                showGpsMatterDiscoveryDialog = true
              }
              .padding(horizontal = 28.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            painter = painterResource(id = R.drawable.baseline_notifications_24),
            contentDescription = null,
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = stringResource(R.string.menu_item_matter_gps_notification),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
          )
          Switch(
            checked = gpsDiscoveryValue,
            onCheckedChange = {
              gpsDiscoveryValue = it
              showGpsMatterDiscoveryDialog = true
            },
          )
        }
        NavigationDrawerItem(
          icon = {
            Icon(
              painter = painterResource(id = R.drawable.ic_baseline_help_24),
              contentDescription = null,
            )
          },
          label = { Text(stringResource(R.string.menu_item_about)) },
          selected = false,
          onClick = {
            scope.launch { drawerState.close() }
            showAboutDialog = true
          },
          modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
      }
    },
  ) {
    AppNavigation(navController, onMenuClick = { scope.launch { drawerState.open() } })
  }

  if (showAboutDialog) {
    HtmlInfoDialog(
      stringResource(R.string.menu_item_about),
      stringResource(R.string.about_app, VERSION_NAME),
      onClick = { showAboutDialog = false },
    )
  }
  if (showGpsMatterDiscoveryDialog) {
    HtmlInfoDialog(
      stringResource(R.string.menu_item_matter_gps_notification),
      stringResource(R.string.gps_matter_discovery_notification_alert),
      onClick = { showGpsMatterDiscoveryDialog = false },
    )
  }
}
