// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.commissioning

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.CommissioningResult
import com.google.android.gms.home.matter.commissioning.DeviceInfo
import com.google.android.gms.home.matter.commissioning.SharedDeviceData
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_COMMISSIONING_WINDOW_EXPIRATION
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_DEVICE_NAME
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_MANUAL_PAIRING_CODE
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_PRODUCT_ID
import com.google.android.gms.home.matter.commissioning.SharedDeviceData.EXTRA_VENDOR_ID
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aether.android.MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS
import io.aether.android.matter.toNodeId
import io.aether.android.matter.toProductId
import io.aether.android.matter.toVendorId
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

class GmsDeviceCommissioner
@Inject
constructor(@param:ApplicationContext private val context: Context) : DeviceCommissioner {

  override val isNetworkValidFlow: Flow<Boolean> = callbackFlow {
    // Listener for system changes.
    val networkCallback =
      object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
          trySend(checkNetworkValidity())
        }

        override fun onLost(network: Network) {
          trySend(checkNetworkValidity())
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
          trySend(checkNetworkValidity())
        }
      }
    // Send the current state when a ViewModel starts collecting this flow.
    send(checkNetworkValidity())
    // Register the OS callback.
    connectivityManager.registerDefaultNetworkCallback(networkCallback)
    // Clean up and unregister when the ViewModel stops collecting.
    awaitClose { connectivityManager.unregisterNetworkCallback(networkCallback) }
  }

  override suspend fun createIntentSender(): IntentSender =
    request(CommissioningRequest.builder().setCommissioningService(commissioningService).build())

  override suspend fun createMultiAdminIntentSender(intent: Intent): IntentSender {
    Timber.d("Starting multi-admin commissioning data=${SharedDeviceData.fromIntent(intent)}")
    val expirationMillis = intent.getLongExtra(EXTRA_COMMISSIONING_WINDOW_EXPIRATION, -1L)
    val timeLeftSeconds = (expirationMillis - SystemClock.elapsedRealtime()) / 1000
    if (expirationMillis == -1L) {
      Timber.e("EXTRA_COMMISSIONING_WINDOW_EXPIRATION not specified in multi-admin call.")
    } else if (timeLeftSeconds < MIN_COMMISSIONING_WINDOW_EXPIRATION_SECONDS) {
      throw CommissioningWindowExpiringException(timeLeftSeconds)
    }
    val deviceInfo =
      DeviceInfo.builder()
        .setProductId(intent.getIntExtra(EXTRA_PRODUCT_ID, -1))
        .setVendorId(intent.getIntExtra(EXTRA_VENDOR_ID, -1))
        .build()
    return request(
      CommissioningRequest.builder()
        .setCommissioningService(commissioningService)
        .setDeviceNameHint(intent.getStringExtra(EXTRA_DEVICE_NAME))
        .setDeviceInfo(deviceInfo)
        .setOnboardingPayload(intent.getStringExtra(EXTRA_MANUAL_PAIRING_CODE))
        .build(),
    )
  }

  override fun parseResult(resultCode: Int, data: Intent?): CommissionedDevice? {
    val result = CommissioningResult.fromIntentSenderResult(resultCode, data)
    val nodeId = result.token?.toLong()?.toNodeId() ?: return null
    val descriptor = result.commissionedDeviceDescriptor
    return CommissionedDevice(
      nodeId = nodeId,
      vendorId = descriptor.vendorId.toVendorId(),
      productId = descriptor.productId.toProductId(),
    )
  }

  private fun checkNetworkValidity(): Boolean {
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    // Block commissioning if on a VPN which will break mDNS functionality.
    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false
    // Ensure we are actually on a local Wi-Fi or Ethernet connection.
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
  }

  private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
  private val commissioningService = ComponentName(context, AppCommissioningService::class.java)

  private suspend fun request(request: CommissioningRequest): IntentSender =
    suspendCancellableCoroutine { cont ->
      Matter.getCommissioningClient(context)
        .commissionDevice(request)
        .addOnSuccessListener { cont.resumeWith(Result.success(it)) }
        .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
    }
}
