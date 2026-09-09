// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.home

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.method.LinkMovementMethod
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.DeviceInfo
import com.google.android.gms.home.matter.commissioning.SharedDeviceData
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_COMMISSIONING_WINDOW_EXPIRATION
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_DEVICE_NAME
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_MANUAL_PAIRING_CODE
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_PRODUCT_ID
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_VENDOR_ID
import com.google.android.material.textview.MaterialTextView
import io.aether.android.MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS
import io.aether.android.R
import io.aether.android.TaskStatus
import io.aether.android.commissioning.AppCommissioningService
import io.aether.android.isMultiAdminCommissioning
import io.aether.android.isOnDisplayString
import io.aether.android.matter.NodeId
import io.aether.android.matter.getDeviceTypeIconId
import io.aether.android.screens.common.MsgAlertDialog
import io.aether.android.screens.thread.getActivity
import io.aether.android.spacing
import timber.log.Timber

/**
 * Home screen for the application.
 *
 * The Home screen features three sections:
 * 1. The list of devices currently commissioned into the app's fabric. When the user clicks on a
 *    device, app flow moves to the Device screen where one can get additional details on the device
 *    and perform actions on it. Devices are persisted in the DevicesRepository, a Proto Datastore.
 *    It's possible to hide the devices that are currently offline via a setting in the Settings
 *    screen.
 * 2. Top App Bar. Settings icon to navigate to the Settings screen.
 * 3. "Add Device" button. Triggers the commissioning of a new device. Note:
 * - The app currently only supports Matter devices with server attribute "ON/OFF".
 *
 * TODO:
 * - Finding out that a device is offline is not working very well. Much work needed there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeRoute(
    navigateToDevice: (nodeId: NodeId) -> Unit,
    onMenuClick: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
  // Launching GPS commissioning requires Activity.
  val activity = LocalContext.current.getActivity()
  val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
  val onCommissionedDeviceNameCaptured: (name: String) -> Unit = remember {
    { homeViewModel.onCommissionedDeviceNameCaptured(it) }
  }
  val onDismissMsgDialog: () -> Unit = remember { { homeViewModel.dismissMsgDialog() } }

  // Functions invoked when UI controls are clicked on a specific device in the list.
  val onDeviceClick: (deviceUiModel: DeviceUiModel) -> Unit = remember {
    { navigateToDevice(it.nodeId) }
  }
  val onOnOffClick: (nodeId: NodeId, value: Boolean) -> Unit = remember {
    { nodeId, value -> homeViewModel.updateDeviceStateOn(nodeId, value) }
  }

  // The device commissioning flow involves multiple steps as it is based on an Activity
  // that is launched on the Google Play Services (GPS).
  // Step 1 (here) is where An activity launcher is registered.
  // At step 2, the user triggers the "Commission Device" action by clicking on the
  // "Add device" button on this screen. This creates the proper IntentSender that is then
  // used in step 3 to call commissionDevicelauncher.launch().
  // Step 4 is when GPS takes over the commissioning flow.
  // Step 5 is when the GPS activity completes and the result is handled here.
  val commissionDeviceLauncher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartIntentSenderForResult()
      ) { result ->
        // Commission Device Step 5.
        // The Commission Device activity in GPS (step 4) has completed.
        val resultCode = result.resultCode
        if (resultCode == Activity.RESULT_OK) {
          Timber.d("Device commissioning succeeded")
          // We let the ViewModel know that GPS commissioning has completed successfully.
          // The ViewModel knows that we still need to capture the device name and will\
          // update UI state to trigger the NewDeviceAlertDialog.
          homeViewModel.gpsCommissioningDeviceSucceeded(result)
        } else {
          homeViewModel.commissionDeviceFailed(resultCode)
        }
      }
  val onCommissionDevice: () -> Unit = remember {
    {
      Timber.d("onAddDeviceClick")
      // fixme deviceAttestationFailureIgnored = false
      homeViewModel.stopMonitoringStateChanges()
      commissionDevice(activity!!.applicationContext, commissionDeviceLauncher)
    }
  }

  LifecycleResumeEffect(Unit) {
    Timber.d("Home screen resumed")
    val intent = activity!!.intent
    Timber.d("Received intent=$intent")
    if (isMultiAdminCommissioning(intent)) {
      Timber.d("Invocation multiAdminCommissioning")
      if (uiState.multiadminCommissionDeviceTaskStatus == TaskStatus.NotStarted) {
        Timber.d("TaskStatus.NotStarted so starting multiadmin commissioning")
        homeViewModel.setMultiadminCommissioningTaskStatus(TaskStatus.InProgress)
        multiAdminCommissionDevice(
            activity.applicationContext,
            intent,
            homeViewModel,
            commissionDeviceLauncher,
        )
      } else {
        Timber.d("Task status=${uiState.multiadminCommissionDeviceTaskStatus}")
      }
    } else {
      Timber.d("Invocation main")
      homeViewModel.startMonitoringStateChanges()
    }
    // FIXME[TJ]: I had this on fragment's create(). Anything similar to that for composables?
    // We need our own device attestation delegate as we currently only support attestation
    // of test Matter devices. This DeviceAttestationDelegate makes it possible to ignore device
    // attestation failures, which happen if commissioning production devices.
    // TODO: Look into supporting different Root CAs.
    // FIXME: This currently breaks commissioning. Removed for now.
    // homeViewModel.setDeviceAttestationDelegate()
    onPauseOrDispose {
      // do any needed clean up here
      Timber.d("LifecycleResumeEffect:onPauseOrDispose stopMonitoringStateChanges()")
      homeViewModel.stopMonitoringStateChanges()
      // FIXME[TJ]: I had this on fragment's destroy(). Anything similar to that for composables?
      // FIXME: This currently breaks commissioning. Removed for now.
      // homeViewModel.resetDeviceAttestationDelegate()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    if (uiState.devices.isEmpty()) {
      NoDevices()
    }
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
          TopAppBar(
              title = {},
              navigationIcon = {
                IconButton(onClick = onMenuClick) {
                  Icon(
                      imageVector = Icons.Filled.Menu,
                      contentDescription = stringResource(R.string.menu_button),
                      tint = MaterialTheme.colorScheme.onBackground,
                  )
                }
              },
              colors =
                  TopAppBarDefaults.topAppBarColors(
                      containerColor = Color.Transparent,
                      scrolledContainerColor = Color.Transparent,
                      navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                      titleContentColor = MaterialTheme.colorScheme.onBackground,
                  ),
          )
        },
        floatingActionButton = {
          FloatingActionButton(
              onClick = onCommissionDevice,
              modifier = Modifier.padding(16.dp),
          ) {
            Icon(Icons.Filled.Add, contentDescription = "Add")
          }
        },
    ) { innerPadding ->
      val modifierWithInnerPadding = Modifier.fillMaxSize().padding(innerPadding)
      HomeScreen(
          uiState = uiState,
          onConsumeMsgDialog = onDismissMsgDialog,
          onCommissionedDeviceNameCaptured,
          onCommissionDevice,
          onDeviceClick,
          onOnOffClick,
          modifier = modifierWithInnerPadding,
      )
    }
  }
}

fun getPlayServicesVersion(context: Context): Long {
  return PackageInfoCompat.getLongVersionCode(
      context.packageManager.getPackageInfo(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE, 0)
  )
}

@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onConsumeMsgDialog: () -> Unit,
    onCommissionedDeviceNameCaptured: (name: String) -> Unit,
    onCommissionDevice: () -> Unit,
    onDeviceClick: (deviceUiModel: DeviceUiModel) -> Unit,
    onOnOffClick: (nodeId: NodeId, value: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var showUpdateDialog by remember { mutableStateOf(false) }
  var canAdd by remember { mutableStateOf(false) }

  // Check when entering or resuming
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        showUpdateDialog = status != ConnectionResult.SUCCESS
        if (getPlayServicesVersion(context) < 223615000L) showUpdateDialog = true
        if (!showUpdateDialog) {
          canAdd = true
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  if (showUpdateDialog) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Update required") },
        text = { Text("Please update Google Play services to continue.") },
        confirmButton = {
          TextButton(onClick = { openPlayServicesInStore(context) }) { Text("Update") }
        },
    )
  }

  if (uiState.msgDialogInfo != null) {
    MsgAlertDialog(uiState.msgDialogInfo, onConsumeMsgDialog)
  }

  if (uiState.showNewDeviceNameAlertDialog) {
    NewDeviceAlertDialog(
        onCommissionedDeviceNameCaptured,
        uiState.deviceAttestationFailureIgnored,
    )
  }

  LazyColumn(modifier = modifier) {
    this.items(uiState.devices) { device ->
      val onDeviceItemClick: () -> Unit = { onDeviceClick(device) }
      DeviceItem(device = device, onOnOffClick = onOnOffClick, onDeviceClick = onDeviceItemClick)
    }
  }
}

fun openPlayServicesInStore(context: Context) {

  if (context is Activity) {
    Timber.d("context is Activity")
  } else {
    Timber.d("context is NOT Activity")
  }

  val intent =
      Intent(Intent.ACTION_VIEW).apply {
        data = "market://details?id=com.google.android.gms".toUri()
        setPackage("com.android.vending")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

  try {
    context.startActivity(intent)
  } catch (e: Exception) {
    val webIntent =
        Intent(Intent.ACTION_VIEW).apply {
          data = "https://play.google.com/store/apps/details?id=com.google.android.gms".toUri()
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    context.startActivity(webIntent)
  }
}

@Composable
private fun DeviceItem(
    device: DeviceUiModel,
    onOnOffClick: (nodeId: NodeId, value: Boolean) -> Unit,
    onDeviceClick: (() -> Unit),
) {
  val nodeId = device.nodeId
  val deviceTypeId = device.deviceTypeId
  val name = device.name
  val isOnline = device.isOnline
  val isOn = device.isOn
  val bgColor =
      if (isOnline && isOn) MaterialTheme.colorScheme.surfaceVariant
      else MaterialTheme.colorScheme.surface
  val contentColor =
      if (isOnline && isOn) MaterialTheme.colorScheme.onSurfaceVariant
      else MaterialTheme.colorScheme.onSurface
  val text = isOnDisplayString(isOn)
  val iconId = deviceTypeId.getDeviceTypeIconId()
  val onCheckedChange: (value: Boolean) -> Unit = { onOnOffClick(nodeId, it) }

  Surface(
      modifier =
          Modifier.padding(top = MaterialTheme.spacing.paddingSmall)
              .padding(PaddingValues(horizontal = MaterialTheme.spacing.paddingSmall)),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
      contentColor = contentColor,
      color = bgColor,
      shape = RoundedCornerShape(MaterialTheme.spacing.roundedCorner),
      onClick = onDeviceClick,
  ) {
    Column(modifier = Modifier.padding(MaterialTheme.spacing.paddingSurfaceContent)) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.paddingSmall),
      ) {
        Icon(
            painter = painterResource(id = iconId),
            contentDescription = null, // decorative element
        )
        Column {
          Text(text = name, style = MaterialTheme.typography.bodyLarge)
          Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.weight(1f))
        Switch(enabled = isOnline, checked = isOn, onCheckedChange = onCheckedChange)
      }
      if (!isOnline) {
        Text(
            text = stringResource(R.string.device_offline_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.paddingSmall),
        )
      }
    }
  }
}

@Composable
private fun NewDeviceAlertDialog(
    onCommissionedDeviceNameCaptured: (name: String) -> Unit,
    deviceAttestationFailureIgnored: Boolean,
) {
  var inputText by remember { mutableStateOf("") }
  AlertDialog(
      title = { Text(text = "Specify device name") },
      text = {
        Column {
          TextField(
              value = inputText,
              onValueChange = { inputText = it },
              label = { Text("Device name") },
              modifier = Modifier.fillMaxWidth(),
          )
          if (deviceAttestationFailureIgnored) {
            val htmlText =
                HtmlCompat.fromHtml(
                        stringResource(R.string.device_attestation_warning),
                        HtmlCompat.FROM_HTML_MODE_LEGACY,
                    )
                    .toString()
            AndroidView(
                modifier = Modifier.padding(top = 20.dp),
                update = { it.text = htmlText },
                factory = {
                  MaterialTextView(it).apply { movementMethod = LinkMovementMethod.getInstance() }
                },
            )
          }
        }
      },
      confirmButton = {
        Button(
            onClick = {
              // Process inputText
              onCommissionedDeviceNameCaptured(inputText)
            },
            enabled = inputText.isNotEmpty(),
        ) {
          Text("OK")
        }
      },
      onDismissRequest = {},
      dismissButton = {},
  )
}

@Composable
private fun NoDevices() {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    // Crop-scale fills the entire area on both axes without letterboxing —
    // the image is scaled uniformly to whichever dimension (width or height)
    // needs the least scaling, and the other axis is cropped.
    Image(
        painter = painterResource(R.drawable.bg_empty_dashboard),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    // Place the title/subtitle in a column whose bottom sits 20 % above the
    // bottom edge of the screen.
    Column(
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .padding(bottom = maxHeight * 0.2f)
                .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
          text = stringResource(R.string.empty_dashboard_title),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium,
      )
      Text(
          text = stringResource(R.string.empty_dashboard_subtitle),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

// ---------------------------------------------------------------------------
// Launch GPS Activity

fun commissionDevice(
    context: Context,
    commissionDeviceLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
) {
  Timber.d("Starting device commissioning")

  val commissionDeviceRequest =
      CommissioningRequest.builder()
          .setCommissioningService(ComponentName(context, AppCommissioningService::class.java))
          .build()

  // The call to commissionDevice() creates the IntentSender that will eventually be launched
  // in the fragment to trigger the commissioning activity in GPS.
  Matter.getCommissioningClient(context)
      .commissionDevice(commissionDeviceRequest)
      .addOnSuccessListener { result ->
        Timber.d("Got commissioning intent sender result=$result")
        commissionDeviceLauncher.launch(IntentSenderRequest.Builder(result).build())
      }
      .addOnFailureListener { error ->
        Timber.e(error)
        //      _commissionDeviceStatus.postValue(
        //        TaskStatus.Failed("Setting up the IntentSender failed", error))
      }
}

fun multiAdminCommissionDevice(
    context: Context,
    intent: Intent,
    homeViewModel: HomeViewModel,
    commissionDeviceLauncher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
) {
  Timber.d("Starting device commissioning")

  val sharedDeviceData = SharedDeviceData.fromIntent(intent)
  Timber.d("Starting multi-admin commissioning data=$sharedDeviceData")
  Timber.d("Multi-admin pairing code=${sharedDeviceData.manualPairingCode}")

  val commissionRequestBuilder =
      CommissioningRequest.builder()
          .setCommissioningService(ComponentName(context, AppCommissioningService::class.java))

  // Fill in the commissioning request...

  // EXTRA_COMMISSIONING_WINDOW_EXPIRATION is a hint of how much time is remaining in the
  // commissioning window for multi-admin. It is based on the current system uptime.
  // If the user takes too long to select the target commissioning app, then there's not
  // enougj time to complete the multi-admin commissioning and we message it to the user.
  val commissioningWindowExpirationMillis =
      intent.getLongExtra(EXTRA_COMMISSIONING_WINDOW_EXPIRATION, -1L)
  val currentUptimeMillis = SystemClock.elapsedRealtime()
  val timeLeftSeconds = (commissioningWindowExpirationMillis - currentUptimeMillis) / 1000
  Timber.d(
      "commissionDevice: TargetCommissioner for MultiAdmin. " +
          "uptimeMillis=$currentUptimeMillis " +
          "commissioningWindowExpirationMillis=$commissioningWindowExpirationMillis " +
          "-> expires in $timeLeftSeconds seconds"
  )

  if (commissioningWindowExpirationMillis == -1L) {
    Timber.e(
        "EXTRA_COMMISSIONING_WINDOW_EXPIRATION not specified in multi-admin call. " +
            "Still going ahead with the multi-admin though."
    )
  } else if (timeLeftSeconds < MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS) {
    homeViewModel.showMsgDialog(
        title = "Commissioning Window Expiration",
        msg =
            "The commissioning window will " +
                "expire in $timeLeftSeconds seconds, not long enough to complete the commissioning.\n\n" +
                "In the future, please select the target commissioning application faster to avoid this situation.",
    )
    return
  }

  val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
  commissionRequestBuilder.setDeviceNameHint(deviceName)

  val vendorId = intent.getIntExtra(EXTRA_VENDOR_ID, -1)
  val productId = intent.getIntExtra(EXTRA_PRODUCT_ID, -1)
  val deviceInfo = DeviceInfo.builder().setProductId(productId).setVendorId(vendorId).build()
  commissionRequestBuilder.setDeviceInfo(deviceInfo)

  val manualPairingCode = intent.getStringExtra(EXTRA_MANUAL_PAIRING_CODE)
  commissionRequestBuilder.setOnboardingPayload(manualPairingCode)

  val commissioningRequest = commissionRequestBuilder.build()

  Timber.d(
      "multiadmin: commissioningRequest " +
          "onboardingPayload=${commissioningRequest.onboardingPayload} " +
          "vendorId=${commissioningRequest.deviceInfo!!.vendorId} " +
          "productId=${commissioningRequest.deviceInfo!!.productId}"
  )

  Matter.getCommissioningClient(context)
      .commissionDevice(commissioningRequest)
      .addOnSuccessListener { result ->
        Timber.d("Got intent sender result=$result")
        commissionDeviceLauncher.launch(IntentSenderRequest.Builder(result).build())
      }
      .addOnFailureListener { error ->
        Timber.e(error)
        homeViewModel.showMsgDialog(
            title = "Failed to to get the IntentSender",
            msg = error.toString(),
        )
      }
}
