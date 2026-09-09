// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.thread

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.ContextWrapper
import android.net.nsd.NsdServiceInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.threadnetwork.ThreadNetworkCredentials
import com.google.common.io.BaseEncoding
import io.aether.android.R
import io.aether.android.spacing
import timber.log.Timber

/**
 * The Thread Screen.
 *
 * Quite a lot of UI logic and business logic needed in this fragment. All logic calls are directed
 * to the UIState class, which then has the ability to call into the ViewModel for business logic.
 * [It's ok for UIState to call into ViewModel since the lifetime of ViewModel is longer than UIState.]
 *
 * See https://developer.android.com/jetpack/compose/state-hoisting#ui-element-state.
 *
 * There are 3 key components in this fragment.
 *
 * (1) Action Buttons There is a total of 8 ThreadNetwork-related action buttons that the user can
 * trigger in this fragment. They are divided in 3 categories: 1.1 Actions on GPS Preferred Thread
 * Credentials Exist, Get, Set, Clear 1.2 Actions on credentials for OpenThread Border Routers
 * running on Raspberry Pis Get, Set 1.3 Actions related to QR-code Thread credentials Read, Show
 *
 * (2) Action Dialogs There are two different dialogs that support the processing of the actions
 * described in (1) 2.1 SimpleDialog is used for (1.1) and (1.3) actions. 2.2 OtbrDialog is used for
 * (1.2) actions. This dialog is a bit more complex because it supports the selection of a border
 * router.
 *
 * (3) Thread Credentials Working Dataset This shows the Thread credentials that were read from a
 * specific source. That source can be:
 * - (1.1-Get) GPS Preferred Thread credentials
 * - (1.2-Get) OpenThread BorderRouter active credentials (includes a border router id)
 * - (1.3-Read) Thread credentials read from a QR Code. The Tread Credentials Working data set can
 *   then be used to set these credentials for a specific destination. That destination can be:
 * - (1.1-Set) GPS Preferred Thread credentials (border router id required)
 * - (1.2-Set) OpenThread BorderRouter pending credentials
 * - (1.3-Show) QR Code for these Thread credentials
 */

// -----------------------------------------------------------------------------------------------
// Top level Composables

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadRoute(
    onBackClick: () -> Unit,
    threadViewModel: ThreadViewModel = hiltViewModel(),
) {
  // UI Logic implemented in ThreadNetworkUiState requires Activity.
  val activity = LocalContext.current.getActivity()

  // All Thread Network actions are handled by UI State class ThreadNetworkUiState.
  // We are not using a ViewModel because ThreadNetwork actions require Activity
  // and the lifetime of a ViewModel is longer than Activity.
  val threadNetworkUiState =
      remember(activity) { ThreadNetworkUiState(activity!!, threadViewModel) }

  // All calls into the ThreadNetworkUiState are encapsulated within this lambda which makes
  // it possible to avoid exposing ThreadNetworkUiState into the composable ThreadScreen
  // (which is less optimal for recompositions as there is a lot of stuff in ThreadNetworkUiState).
  // ActionRequest provides all the information needed by ThreadNetworkUiState
  // to properly process the Action triggered by the user.
  //
  // Note that this makes it challenging to handle UIState functions that are suspendable.
  // [TODO: Comment from TJ:
  //  For this, if you did ThreadNetworkUiState(activity!!, rememberCoroutineScope(), viewModel),
  //  would the passed in coroutine scope help in launching suspending functions
  //  in the ThreadNetworkUiState?]
  // At the point of invocation in the composable, one would normally use rememberCoroutineScope
  // but the call must be done directly on the UIState object, which we strive to avoid.
  // For now, suspend functions are handled in the ViewModel by using viewModelScope.
  // [Not possible to have a lambda for a suspend function that needs arguments:
  // https://youtrack.jetbrains.com/issue/KT-51067/Function-for-creating-suspending-lambdas-doesnt-allow-lambda-parameters
  // https://medium.com/livefront/suspending-lambdas-in-kotlin-7319d2d7092a]
  val onThreadNetworkAction =
      remember(threadNetworkUiState) {
        { actionRequest: ActionRequest -> threadNetworkUiState.processAction(actionRequest) }
      }
  val uiState by threadViewModel.uiState.collectAsStateWithLifecycle()

  // Registers for activity result from Google Play Services.
  // This defines a launcher for the IntentSender of an Activity to
  // access Thread network credentials.
  val threadClientLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartIntentSenderForResult()
      ) { result ->
        if (result.resultCode == RESULT_OK) {
          val threadNetworkCredentials =
              ThreadNetworkCredentials.fromIntentSenderResultData(result.data!!)
          threadViewModel.setThreadCredentialsInfo(null, threadNetworkCredentials)
        } else {
          val error = "User denied request."
          Timber.d(error)
          threadViewModel.setThreadCredentialsInfo(null, null)
        }
      }

  // The IntentSender used to trigger the ThreadClient GPS activity.
  uiState.threadClientIntentSender?.let { threadClientIntentSender ->
    Timber.d("Launching GPS activity for Thread client")
    threadClientLauncher.launch(IntentSenderRequest.Builder(threadClientIntentSender).build())
    // Reset IntentSender so we don't redo the launch on configuration change where data
    // is reset in the fragment.
    threadViewModel.setThreadClientIntentSender(null)
    threadViewModel.setActionDialogInfo(ActionType.None, ActionState.None)
  }

  LifecycleResumeEffect(Unit) {
    Timber.d("LifecycleResumeEffect")
    threadNetworkUiState.startServiceDiscovery()
    onPauseOrDispose {
      // do any needed clean up here
      Timber.d("LifecycleResumeEffect:onPauseOrDispose")
      threadNetworkUiState.stopServiceDiscovery()
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.menu_item_thread)) },
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
    ThreadScreen(
        uiState = uiState,
        onThreadNetworkAction,
        modifier = modifierWithInnerPadding,
    )
  }
}

@Composable
private fun ThreadScreen(
    uiState: ThreadUiState,
    onThreadNetworkAction: (ActionRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
  // The action dialogs.
  SimpleActionDialog(uiState.currentActionInfo, onThreadNetworkAction)
  OtbrActionDialog(uiState.currentActionInfo, onThreadNetworkAction)

  Box(modifier = modifier) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.paddingSurfaceContent)
    ) {
      // TODO: Hack to handle issue with Fragment's top appbar.
      Spacer(Modifier.padding(30.dp))

      // The Actions section is the main section with all possible Thread network actions
      // exposed as clickable cards.
      Box(
          modifier = Modifier.border(BorderStroke(1.dp, Color.Red), shape = RoundedCornerShape(4))
      ) {
        ActionsSection(onThreadNetworkAction)
      }

      Spacer(Modifier.padding(10.dp))

      // Section that shows the current Thread network credentials working dataset.
      WorkingDatasetSection(uiState.threadCredentialsInfo)
    }
  }
}

// -----------------------------------------------------------------------------------------------
// Actions grid Composables

@Composable
private fun ActionsSection(
    onThreadNetworkAction: (ActionRequest) -> Unit,
) {

  // These constants can only be declared in Composable.
  val gpsPrefColors =
      CardDefaults.cardColors(
          containerColor = Color(0, 99, 155, 255),
          contentColor = Color.White,
      )
  val rpiOtbrColors =
      CardDefaults.cardColors(
          containerColor = Color(62, 118, 109, 255),
          contentColor = Color.White,
      )
  val qrCodeColors =
      CardDefaults.cardColors(
          containerColor = Color(255, 133, 105, 255),
          contentColor = Color.White,
      )

  LazyVerticalGrid(
      modifier = Modifier.padding(10.dp),
      columns = GridCells.Fixed(3),
  ) {
    // Top to Bottom, Left to Right.
    item { ActionGroupHeader("GPS Preferred\nCredentials") }
    item { ActionGroupHeader("RPi OTBR\nCredentials") }
    item { ActionGroupHeader("QR Code\nCredentials") }
    item {
      ActionItem(
          "Exist?",
          gpsPrefColors,
          onClick = {
            onThreadNetworkAction(
                ActionRequest(ActionType.doGpsPreferredCredentialsExist, ActionTask.Process)
            )
          },
      )
    }
    item {
      ActionItem(
          "Get",
          rpiOtbrColors,
          onClick = {
            onThreadNetworkAction(
                ActionRequest(ActionType.getOtbrActiveThreadCredentials, ActionTask.Init)
            )
          },
      )
    }
    item {
      ActionItem(
          "Read",
          qrCodeColors,
          onClick = {
            onThreadNetworkAction(
                ActionRequest(ActionType.readQrCodeCredentials, ActionTask.Process)
            )
          },
      )
    }
    item {
      ActionItem(
          "Get",
          gpsPrefColors,
          onClick = {
            onThreadNetworkAction(
                ActionRequest(ActionType.getGpsPreferredCredentials, ActionTask.Process)
            )
          },
      )
    }
    item {
      ActionItem(
          "Set",
          rpiOtbrColors,
          onClick = {
            onThreadNetworkAction(
                ActionRequest(ActionType.setOtbrPendingThreadCredentials, ActionTask.Init)
            )
          },
      )
    }
    item {
      ActionItem(
          "Show",
          qrCodeColors,
          onClick = {
            onThreadNetworkAction(
                ActionRequest(ActionType.showQrCodeCredentials, ActionTask.Process)
            )
          },
      )
    }
    item {
      ActionItem(
          "Set",
          gpsPrefColors,
          onClick = {
            onThreadNetworkAction(
                ActionRequest(ActionType.setGpsPreferredCredentials, ActionTask.Process)
            )
          },
      )
    }
    item {} // empty slot
    item {} // empty slot
    item {
      ActionItem(
          "Clear",
          gpsPrefColors,
          onClick = {
            onThreadNetworkAction(
                ActionRequest(ActionType.clearGpsPreferredCredentials, ActionTask.Process)
            )
          },
      )
    }
  }
}

// An Action item shown in the Actions grid.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionItem(text: String, colors: CardColors, onClick: () -> Unit) {
  Card(
      modifier = Modifier.padding(4.dp),
      colors = colors,
      onClick = {
        Timber.d("Clicked text=$text")
        onClick()
        Timber.d("after calling onClick")
      },
  ) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(bottom = 12.dp, top = 12.dp).fillMaxWidth(),
    )
  }
}

/** The Header for an Action Group (e.g. GPS preferred credentials) */
@Composable
private fun ActionGroupHeader(text: String) {
  Text(
      text = text,
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.labelMedium,
  )
}

// -----------------------------------------------------------------------------------------------
// Thread Credentials Working Dataset Composables

@Composable
private fun WorkingDatasetSection(threadCredentialsInfo: ThreadCredentialsInfo) {
  if (threadCredentialsInfo.credentials != null) {
    Box(modifier = Modifier.border(BorderStroke(1.dp, Color.Red), shape = RoundedCornerShape(4))) {
      Column(modifier = Modifier.padding(10.dp)) {
        Text(
            text = "Thread Credentials Working Dataset",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.padding(4.dp))
        if (threadCredentialsInfo.selectedThreadBorderRouterId != null) {
          Text(
              text =
                  "Selected Thread Border Router: ${threadCredentialsInfo.selectedThreadBorderRouterId}",
              style = MaterialTheme.typography.bodyLarge,
          )
          Spacer(Modifier.padding(4.dp))
        }
        Text(
            text = threadNetworkInfo(threadCredentialsInfo.credentials),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.padding(4.dp))
        Text(
            text = threadTlv(threadCredentialsInfo.credentials),
            style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}

// -----------------------------------------------------------------------------------------------
// Action Dialogs Composables

/**
 * Dialog associated with the processing of "simple" actions, i.e. actions that do not require any
 * interactions with the user. The dialog lets the user know that the action is being processed, and
 * optionally, the result of that action.
 */
@Composable
private fun SimpleActionDialog(
    currentActionInfo: ActionDialogInfo,
    onThreadNetworkAction: (ActionRequest) -> Unit,
) {
  // Filter out actions that are not "simple".
  if (currentActionInfo.type == ActionType.None || isOtbrAction(currentActionInfo.type)) {
    return
  }

  AlertDialog(
      title = { Text(text = currentActionInfo.type.title) },
      text = {
        // Circular progress indicator while the action executes.
        if (currentActionInfo.state == ActionState.Processing) {
          CircularProgressIndicator(
              modifier = Modifier.width(64.dp).wrapContentSize(Alignment.Center),
              color = MaterialTheme.colorScheme.secondary,
              trackColor = MaterialTheme.colorScheme.surfaceVariant,
          )
        } else if (currentActionInfo.state == ActionState.Completed) {
          if (currentActionInfo.type == ActionType.doGpsPreferredCredentialsExist) {
            Text("Thread network credentials exist.")
          } else if (currentActionInfo.type == ActionType.clearGpsPreferredCredentials) {
            Text(clearGpsPreferredCredsMsg())
          } else if (currentActionInfo.type == ActionType.showQrCodeCredentials) {
            Image(
                bitmap = currentActionInfo.qrCodeBitmap!!.asImageBitmap(),
                contentDescription = "The QR Code",
            )
          }
        } else if (currentActionInfo.state == ActionState.Error) {
          Text(currentActionInfo.data)
        }
      },
      confirmButton = {
        if (
            currentActionInfo.state == ActionState.Completed ||
                currentActionInfo.state == ActionState.Error
        ) {
          TextButton(
              onClick = {
                onThreadNetworkAction(ActionRequest(ActionType.None, ActionTask.Complete))
              }
          ) {
            Text("OK")
          }
        }
      },
      onDismissRequest = {},
      dismissButton = {},
  )
}

/**
 * Dialog associated with the processing of an OTBR_related action.
 *
 * These actions first require the selection of a Border Router before they can then proceed with
 * their specific processing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtbrActionDialog(
    currentActionInfo: ActionDialogInfo,
    onThreadNetworkAction: (ActionRequest) -> Unit,
) {
  // Filter out any action other than OTBR-related action.
  if (currentActionInfo.type == ActionType.None || !isOtbrAction(currentActionInfo.type)) {
    return
  }

  val hasOtbrs = currentActionInfo.borderRoutersList.isNotEmpty()
  var isExpanded by remember { mutableStateOf(value = false) }
  var otbr: NsdServiceInfo? by remember { mutableStateOf(value = null) }

  AlertDialog(
      title = { Text(text = currentActionInfo.type.title) },
      text = {
        Column {
          if (
              currentActionInfo.type == ActionType.setOtbrPendingThreadCredentials &&
                  currentActionInfo.state == ActionState.Error &&
                  !hasOtbrs
          ) {
            Text(currentActionInfo.data)
          } else if (currentActionInfo.state == ActionState.BorderRoutersProvided && !hasOtbrs) {
            Text("No OpenThread Border Routers discovered.")
          } else {
            ExposedDropdownMenuBox(expanded = false, onExpandedChange = { isExpanded = it }) {
              TextField(
                  // The `menuAnchor` modifier must be passed to the text field for correctness.
                  modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                  readOnly = true,
                  value = if (otbr == null) "" else otbr!!.serviceName,
                  onValueChange = { Timber.d("Value changed") },
                  label = { Text("Select OTBR to use") },
                  trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded)
                  },
                  colors = ExposedDropdownMenuDefaults.textFieldColors(),
              )
              ExposedDropdownMenu(expanded = isExpanded, onDismissRequest = { /*TODO*/ }) {
                currentActionInfo.borderRoutersList.forEach {
                  Timber.d("OTBR value=$it")
                  DropdownMenuItem(
                      text = { Text(it.serviceName) },
                      onClick = {
                        otbr = it
                        isExpanded = false
                      },
                  )
                }
              }
            }
            if (currentActionInfo.state == ActionState.Processing) {
              CircularProgressIndicator(
                  modifier =
                      Modifier.width(64.dp)
                          // .fillMaxSize()
                          .wrapContentSize(Alignment.Center),
                  color = MaterialTheme.colorScheme.secondary,
                  trackColor = MaterialTheme.colorScheme.surfaceVariant,
              )
            }
            if (currentActionInfo.state == ActionState.Completed) {
              Text(currentActionInfo.data)
            }
            if (currentActionInfo.state == ActionState.Error) {
              Text(currentActionInfo.data)
            }
          }
        }
      },
      confirmButton = {
        if (!hasOtbrs) {
          TextButton(
              onClick = {
                onThreadNetworkAction(ActionRequest(ActionType.None, ActionTask.Complete))
              }
          ) {
            Text("OK")
          }
        } else {
          TextButton(
              onClick = {
                // Trigger the processing of the OTBR.
                onThreadNetworkAction(
                    ActionRequest(currentActionInfo.type, ActionTask.Process, serviceInfo = otbr)
                )
              }
          ) {
            Text("OK")
          }
        }
      },
      onDismissRequest = {
        onThreadNetworkAction(ActionRequest(ActionType.None, ActionTask.Complete))
      },
      dismissButton = {
        if (hasOtbrs) {
          TextButton(
              onClick = {
                onThreadNetworkAction(ActionRequest(ActionType.None, ActionTask.Complete))
              }
          ) {
            Text("Cancel")
          }
        }
      },
  )
}

// -----------------------------------------------------------------------------------------------
// Utility methods

private fun isOtbrAction(actionType: ActionType): Boolean {
  return actionType == ActionType.getOtbrActiveThreadCredentials ||
      actionType == ActionType.setOtbrPendingThreadCredentials
}

private fun threadNetworkInfo(credentials: ThreadNetworkCredentials): String {
  return "NetworkName: " +
      credentials.networkName +
      "\nChannel: " +
      credentials.channel +
      "\nPanID: " +
      credentials.panId +
      "\nExtendedPanID: " +
      BaseEncoding.base16().encode(credentials.extendedPanId) +
      "\nNetworkKey: " +
      BaseEncoding.base16().encode(credentials.networkKey) +
      "\nPskc:" +
      BaseEncoding.base16().encode(credentials.pskc) +
      "\nMesh Local Prefix: " +
      BaseEncoding.base16().encode(credentials.meshLocalPrefix)
}

private fun threadTlv(credentials: ThreadNetworkCredentials): String {
  return BaseEncoding.base16().encode(credentials.activeOperationalDataset)
}

private fun clearGpsPreferredCredsMsg(): String {
  return "You can't clear credentials programmatically.\n\n" +
      "If you would like to alter your GPS Preferred Thread credentials " +
      "after they have been set, you must:\n" +
      "1. Factory Reset all your Google Border Routers\n" +
      "2. Clear all GPS information via `adb -d shell pm clear com.google.android.gms`\n" +
      "3a. Get credentials from your OTBR or\n" +
      "3b. Create a new set\n" +
      "4. Set the credentials to a BR in GPS. First credential set will become preferred\n\n" +
      "Clearing all the GPS data might have unforeseen side effects on other Google " +
      "Services. Use cautiously on a phone and on an user dedicated to development purposes."
}

fun Context.getActivity(): ComponentActivity? =
    when (this) {
      is ComponentActivity -> this
      is ContextWrapper -> baseContext.getActivity()
      else -> null
    }
