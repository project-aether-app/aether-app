// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.commissioning

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
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
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

class GmsDeviceCommissioner @Inject constructor(@param:ApplicationContext private val context: Context) :
  DeviceCommissioner {

  override suspend fun createIntentSender(): IntentSender =
    request(CommissioningRequest.builder().setCommissioningService(service()).build())

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
        .setCommissioningService(service())
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

  private fun service() = ComponentName(context, AppCommissioningService::class.java)

  private suspend fun request(request: CommissioningRequest): IntentSender =
    suspendCancellableCoroutine { cont ->
      Matter.getCommissioningClient(context)
        .commissionDevice(request)
        .addOnSuccessListener { cont.resumeWith(Result.success(it)) }
        .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
    }
}
