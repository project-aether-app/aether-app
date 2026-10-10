// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.protobuf.Timestamp
import io.aether.android.matter.DEVICES
import io.aether.android.matter.DeviceTypeId
import java.io.File
import java.lang.Long.max
import java.security.SecureRandom
import java.time.Instant
import kotlin.math.abs
import timber.log.Timber

// -------------------------------------------------------------------------------------------------
// Various constants

// Not using lateinit so Timber can be used with Compose previews
var VERSION_NAME = "v?.?"
var APP_NAME = "APP_NAME"

// -------------------------------------------------------------------------------------------------
// Display helper functions

/** Enumeration of statuses for an asynchronous [com.google.android.gms.tasks.Task]. */
sealed class TaskStatus {
  /** The task has not been started. */
  object NotStarted : TaskStatus()

  /** The task has been started, and has not yet completed with a result. */
  object InProgress : TaskStatus()

  /**
   * The task completed with an exception.
   *
   * @param cause the cause of the failure
   */
  class Failed(val message: String, val cause: Throwable?) : TaskStatus()

  /**
   * The task completed successfully.
   *
   * @param statusMessage a message to be displayed in the UI
   */
  class Completed(val statusMessage: String) : TaskStatus()
}

fun getDeviceTypeDisplayStringId(deviceTypeId: DeviceTypeId): String {
  return DEVICES[deviceTypeId] ?: "Unknown (${deviceTypeId})"
}

/** Converts the "isOn" boolean into a proper string for the UI. */
fun isOnDisplayString(isOn: Boolean): String {
  return if (isOn) "ON" else "OFF"
}

// -------------------------------------------------------------------------------------------------
// System helper functions

fun isMultiAdminCommissioning(intent: Intent): Boolean {
  return intent.action == "com.google.android.gms.home.matter.ACTION_COMMISSION_DEVICE"
}

/**
 * The Matter APIs make use of SharedPreferences. Useful to print what they are when the app starts.
 */
fun displayPreferences(context: Context) {
  val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
  if (prefsDir.exists() && prefsDir.isDirectory) {
    Timber.d("*** Preference Files ***")
    val list = prefsDir.list().orEmpty()
    for (element in list) {
      Timber.d("Preference file=$element")
      val sharedPreferencesFileKey = element.substringBefore(".xml")
      Timber.d("Preference fileKey=$sharedPreferencesFileKey")
      val sharedPreferences =
          context.getSharedPreferences(sharedPreferencesFileKey, Context.MODE_PRIVATE)
      val allPreferences = sharedPreferences.all
      for ((key, value) in allPreferences.entries) Timber.d("Preference key=$key value=$value")
    }
    return
  } else {
    Timber.d("Preferences directory does not exist path=$prefsDir")
    return
  }
}

/** Returns a com.google.protobuf.Timestamp for the current time. */
fun getTimestampForNow(): Timestamp {
  val now = Instant.now()
  return Timestamp.newBuilder().setSeconds(now.epochSecond).setNanos(now.nano).build()
}

/**
 * Formats a com.google.protobuf.Timestamp using Android date/time formatters so user 12/24-hour
 * settings are respected.
 */
fun formatTimestamp(context: Context, timestamp: Timestamp): String {
  return formatTimestamp(context, Instant.ofEpochSecond(timestamp.seconds, timestamp.nanos.toLong()))
}

/** Formats an [Instant] using Android date/time formatters. */
fun formatTimestamp(context: Context, instant: Instant): String {
  val date = java.util.Date.from(instant)
  val dateFormatter = android.text.format.DateFormat.getMediumDateFormat(context)
  val timeFormatter = android.text.format.DateFormat.getTimeFormat(context)
  return "${dateFormatter.format(date)} ${timeFormatter.format(date)}"
}

/** Create a com.google.protobuf.Timestamp from a Long representing epoch seconds. */
fun Long.toTimestamp(): Timestamp {
  return Timestamp.newBuilder().setSeconds(this).build()
}

/** Generates a random number to be used as a device identifier during device commissioning */
fun generateNextDeviceId(): Long {
  val secureRandom =
      try {
        SecureRandom.getInstance("SHA1PRNG")
      } catch (ex: Exception) {
        Timber.w(ex, "Failed to instantiate SecureRandom with SHA1PRNG")
        // instantiate with the default algorithm
        SecureRandom()
      }

  return max(abs(secureRandom.nextLong()), 1)
}

/**
 * Strip the link-local portion of an IP Address. Was needed to handle
 * https://github.com/google-home/sample-app-for-matter-android/issues/15. For example:
 * ```
 *    "fe80::84b1:c2f6:b1b7:67d4%wlan0"
 * ```
 *
 * becomes
 *
 * ```
 *    ""fe80::84b1:c2f6:b1b7:67d4"
 * ```
 *
 * The "%wlan0" at the end of the link-local ip address is stripped.
 */
fun stripLinkLocalInIpAddress(ipAddress: String): String {
  return ipAddress.replace("%.*".toRegex(), "")
}

// -------------------------------------------------------------------------------------------------
// Constants

// -------------------------------------------------------------------------------------------------
// Constants used when creating devices on the app's fabric.

// Shared device creation
const val SHARED_DEVICE_NAME_PREFIX = "Shared-"
const val SHARED_DEVICE_NAME_SUFFIX = ""
const val SHARED_DEVICE_ROOM_PREFIX = "Room-"

// Temporary device name used when commissioning the device to the 3P fabric.
const val REAL_DEVICE_NAME_PREFIX = "Real-"

// -------------------------------------------------------------------------------------------------
// Device Sharing constants

// How long a commissioning window for Device Sharing should be open.
const val OPEN_COMMISSIONING_WINDOW_DURATION_SECONDS = 180

// Discriminator
const val DISCRIMINATOR = 123

// Iteration
const val ITERATION = 10000L

// Iteration
const val SETUP_PIN_CODE = 11223344L

// Minimum time required to handle the multi-admin commissioning
// intent just received.
const val MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS = 20

// -------------------------------------------------------------------------------------------------
// Constants to modify the behavior of the app.

// Whether the on/off switch is disabled when the device is offline.
const val ON_OFF_SWITCH_DISABLED_WHEN_DEVICE_OFFLINE = false

// ----- Periodic monitoring of device state changes -----

// Modes supported for monitoring state changes.
enum class StateChangesMonitoringMode {
  // Subscription is what should normally be used.
  Subscription,
  // Left for historical reasons when we had issues with Subscription.
  PeriodicRead,
}

val STATE_CHANGES_MONITORING_MODE = StateChangesMonitoringMode.Subscription

// Intervals for PeriodicRead mode.
const val PERIODIC_READ_INTERVAL_HOME_SCREEN_SECONDS = 10
const val PERIODIC_READ_INTERVAL_DEVICE_SCREEN_SECONDS = 2

// ----- Device Sharing -----

// Whether DeviceSharing does commissioning with GPS.
// Alternative is using DNS-SD to discover the device and get its IP address, and then
// do the standard 3P commissioning.
const val DEVICE_SHARING_WITH_GPS = true

// Which API should be used for opening the commissioning window for DeviceSharing.
enum class OpenCommissioningWindowApi {
  ChipDeviceController,
  AdministratorCommissioningCluster,
}

/**
 * Indicates the status of a node's commissioning window. Useful in the context of "multi-admin"
 * when a temporary commissioning window must be open for a target commissioner. That's because
 * sometimes multi-admin may fail with the target commissioner (especially in a testing environment)
 * and the temporary commissioning window can then stay open for a substantial amount of time (e.g.
 * 3 minutes) preventing a new "multi-admin" to fail until that temporary commissioning window is
 * closed. Checking on the status of the commissioning window beforehand makes it possible to close
 * the currently open temporary commissioning window before trying to open a new one. [status] is
 * the enum value returned by reading the WindowStatusAttribute of the "Administrator Commissioning
 * Cluster". (See spec section "11.18.6.1. CommissioningWindowStatus enum").
 */
enum class CommissioningWindowStatus(val status: Int) {
  /** Commissioning window not open */
  WindowNotOpen(0),

  /** An Enhanced Commissioning Method window is open */
  EnhancedWindowOpen(1),

  /** A Basic Commissioning Method window is open */
  BasicWindowOpen(2),
}

val OPEN_COMMISSIONING_WINDOW_API = OpenCommissioningWindowApi.ChipDeviceController

/**
 * ToastTimber logs the same message on both Timber and Toast, thus giving some feedback to the user
 * that doesn't have ADB connected
 */
object ToastTimber {
  fun d(msg: String, activity: FragmentActivity) {
    Timber.d(msg)
    checkLooper()
    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
  }

  fun e(msg: String, activity: FragmentActivity) {
    Timber.e(msg)
    checkLooper()
    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
  }

  /**
   * Asserts Looper is running in the current thread. Important when using Timber in coroutine
   * Threads that don't have a Looper running
   */
  private fun checkLooper() {
    if (Looper.myLooper() == null) Looper.prepare()
  }
}
