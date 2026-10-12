// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.aether.android.matter.NodeId
import io.aether.android.matter.toNodeId
import io.aether.android.screens.device.DeviceRoute
import io.aether.android.screens.device.settings.DeviceSettingsRoute
import io.aether.android.screens.device.settings.diagnostics.DiagnosticsRoute
import io.aether.android.screens.device.settings.explorer.ExplorerRoute
import io.aether.android.screens.device.settings.fabrics.FabricsRoute
import io.aether.android.screens.home.HomeRoute
import io.aether.android.screens.scanner.ScannerRoute
import io.aether.android.screens.thread.ThreadRoute

// Constants for Navigation destinations
const val DEST_HOME = "home"
const val DEST_SCANNER = "scanner"
const val DEST_THREAD = "thread"

const val ARG_NODE_ID = "nodeId"

const val ROUTE_DEVICE = "device/{$ARG_NODE_ID}"
const val ROUTE_DEVICE_SETTINGS = "device/{$ARG_NODE_ID}/settings"
const val ROUTE_DEVICE_EXPLORER = "device/{$ARG_NODE_ID}/explorer"
const val ROUTE_DEVICE_FABRICS = "device/{$ARG_NODE_ID}/fabrics"
const val ROUTE_DEVICE_DIAGNOSTICS = "device/{$ARG_NODE_ID}/diagnostics"

fun routeToDevice(nodeId: NodeId): String = "device/${nodeId.toLong()}"

fun routeToDeviceSettings(nodeId: NodeId): String = "device/${nodeId.toLong()}/settings"

fun routeToDeviceExplorer(nodeId: NodeId): String = "device/${nodeId.toLong()}/explorer"

fun routeToDeviceFabrics(nodeId: NodeId): String = "device/${nodeId.toLong()}/fabrics"

fun routeToDeviceDiagnostics(nodeId: NodeId): String = "device/${nodeId.toLong()}/diagnostics"

@Composable
fun AppNavigation(
  navController: NavHostController,
  onMenuClick: () -> Unit,
) {
  // Lambdas to all destinations needed in our various routes.
  // [Top level Route Composables should not be passed the navController explicitly,
  // as NavController is an unstable type. Indirection like a lambda should be used
  // as the compiler considers lambdas stable.]
  val navigateToHome: () -> Unit = remember { { navController.navigate(DEST_HOME) } }
  val navigateToDevice: (nodeId: NodeId) -> Unit = remember {
    { nodeId -> navController.navigate(routeToDevice(nodeId)) }
  }
  val navigateToDeviceSettings: (nodeId: NodeId) -> Unit = remember {
    { nodeId -> navController.navigate(routeToDeviceSettings(nodeId)) }
  }
  val navigateToDeviceExplorer: (nodeId: NodeId) -> Unit = remember {
    { nodeId -> navController.navigate(routeToDeviceExplorer(nodeId)) }
  }
  val navigateToDeviceFabrics: (nodeId: NodeId) -> Unit = remember {
    { nodeId -> navController.navigate(routeToDeviceFabrics(nodeId)) }
  }
  val navigateToDeviceDiagnostics: (nodeId: NodeId) -> Unit = remember {
    { nodeId -> navController.navigate(routeToDeviceDiagnostics(nodeId)) }
  }

  NavHost(navController = navController, startDestination = DEST_HOME) {
    // Home
    composable(DEST_HOME) { HomeRoute(navigateToDevice, onMenuClick) }
    // Device
    composable(
      ROUTE_DEVICE,
      arguments = listOf(navArgument(ARG_NODE_ID) { type = NavType.LongType }),
    ) {
      DeviceRoute(
        navigateToDeviceSettings = navigateToDeviceSettings,
        onBackClick = { navController.popBackStack() },
        nodeId = it.arguments?.getLong(ARG_NODE_ID)!!.toNodeId(),
      )
    }
    // Device settings
    composable(
      ROUTE_DEVICE_SETTINGS,
      arguments = listOf(navArgument(ARG_NODE_ID) { type = NavType.LongType }),
    ) {
      DeviceSettingsRoute(
        navigateToHome = navigateToHome,
        navigateToDeviceExplorer = navigateToDeviceExplorer,
        navigateToDeviceFabrics = navigateToDeviceFabrics,
        navigateToDeviceDiagnostics = navigateToDeviceDiagnostics,
        onBackClick = { navController.popBackStack() },
        nodeId = it.arguments?.getLong(ARG_NODE_ID)!!.toNodeId(),
      )
    }
    // Explorer from Device Settings
    composable(
      ROUTE_DEVICE_EXPLORER,
      arguments = listOf(navArgument(ARG_NODE_ID) { type = NavType.LongType }),
    ) {
      ExplorerRoute(
        onBackClick = { navController.popBackStack() },
        nodeId = it.arguments?.getLong(ARG_NODE_ID)!!.toNodeId(),
      )
    }
    // Fabrics from Device Settings
    composable(
      ROUTE_DEVICE_FABRICS,
      arguments = listOf(navArgument(ARG_NODE_ID) { type = NavType.LongType }),
    ) {
      FabricsRoute(
        onBackClick = { navController.popBackStack() },
        nodeId = it.arguments?.getLong(ARG_NODE_ID)!!.toNodeId(),
      )
    }
    // Diagnostics from Device Settings
    composable(
      ROUTE_DEVICE_DIAGNOSTICS,
      arguments = listOf(navArgument(ARG_NODE_ID) { type = NavType.LongType }),
    ) {
      DiagnosticsRoute(
        onBackClick = { navController.popBackStack() },
        nodeId = it.arguments?.getLong(ARG_NODE_ID)!!.toNodeId(),
      )
    }
    // Matter Device Scanner
    composable(DEST_SCANNER) { ScannerRoute(onBackClick = { navController.popBackStack() }) }
    // Thread network utilities
    composable(DEST_THREAD) { ThreadRoute(onBackClick = { navController.popBackStack() }) }
  }
}
