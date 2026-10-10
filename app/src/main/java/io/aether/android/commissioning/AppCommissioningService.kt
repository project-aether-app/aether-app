// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.commissioning

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.android.gms.home.matter.commissioning.CommissioningCompleteMetadata
import com.google.android.gms.home.matter.commissioning.CommissioningRequestMetadata
import com.google.android.gms.home.matter.commissioning.CommissioningService
import com.google.android.gms.home.matter.commissioning.CommissioningService.CommissioningError
import dagger.hilt.android.AndroidEntryPoint
import io.aether.android.APP_NAME
import io.aether.android.R
import io.aether.android.chip.ChipClient
import io.aether.android.generateNextDeviceId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The CommissioningService that's responsible for commissioning the device on the app's custom
 * fabric. AppCommissioningService is specified when building the
 * [com.google.android.gms.home.matter.commissioning.CommissioningRequest] in
 * [../screens.home.HomeViewModel].
 */
@AndroidEntryPoint
class AppCommissioningService : Service(), CommissioningService.Callback {

  @Inject internal lateinit var chipClient: ChipClient

  private val serviceJob = Job()
  private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

  private lateinit var commissioningServiceDelegate: CommissioningService

  override fun onCreate() {
    super.onCreate()
    // May be invoked without MainActivity being called to initialize APP_NAME.
    // So do it here as well.
    APP_NAME = getString(R.string.app_name)
    Timber.d("onCreate()")
    commissioningServiceDelegate = CommissioningService.Builder(this).setCallback(this).build()
  }

  override fun onBind(intent: Intent): IBinder {
    Timber.d("Binding service intent=$intent")
    return commissioningServiceDelegate.asBinder()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Timber.d("Starting service intent=$intent flags=$flags startId=$startId")
    return super.onStartCommand(intent, flags, startId)
  }

  override fun onDestroy() {
    super.onDestroy()
    Timber.d("onDestroy()")
    serviceJob.cancel()
  }

  override fun onCommissioningRequested(metadata: CommissioningRequestMetadata) {
    Timber.d(
        "*** onCommissioningRequested ***:\n" +
            "\tdeviceDescriptor: " +
            "vendorId=${metadata.deviceDescriptor.vendorId} " +
            "productId=${metadata.deviceDescriptor.productId}\n" +
            "\tnetworkLocation: " +
            "IP address=${metadata.networkLocation.ipAddress} " +
            "hostAddress=${metadata.networkLocation.ipAddress.hostAddress} " +
            "port=${metadata.networkLocation.port}\n" +
            "\tpassCode=${metadata.passcode}",
    )

    // Perform commissioning on custom fabric for the sample app.
    serviceScope.launch {
      val deviceId = generateNextDeviceId()
      try {
        Timber.d("Establishing PASE connection deviceId=$deviceId")
        chipClient.awaitEstablishPaseConnection(
            deviceId,
            metadata.networkLocation.ipAddress.hostAddress!!,
            metadata.networkLocation.port,
            metadata.passcode,
        )

        Timber.d("Commissioning deviceId=$deviceId")
        chipClient.awaitCommissionDevice(deviceId, null)
      } catch (e: Exception) {
        Timber.e(e, "onCommissioningRequested() failed")
        // No way to determine whether this was ATTESTATION_FAILED or DEVICE_UNREACHABLE.
        commissioningServiceDelegate
            .sendCommissioningError(CommissioningError.OTHER)
            .addOnSuccessListener {
              Timber.d(
                  "Commissioning: commissioningServiceDelegate.sendCommissioningError() succeeded",
              )
            }
            .addOnFailureListener { e2 ->
              Timber.e(
                  e2,
                  "Commissioning: commissioningServiceDelegate.sendCommissioningError() failed",
              )
            }
        return@launch
      }

      Timber.d("Sending commissioning complete")
      commissioningServiceDelegate
          .sendCommissioningComplete(
              CommissioningCompleteMetadata.builder().setToken(deviceId.toString()).build(),
          )
          .addOnSuccessListener {
            Timber.d(
                "Commissioning: commissioningServiceDelegate.sendCommissioningComplete() succeeded",
            )
          }
          .addOnFailureListener { e ->
            Timber.e(
                e,
                "Commissioning: commissioningServiceDelegate.sendCommissioningComplete() failed",
            )
          }
    }
  }
}
