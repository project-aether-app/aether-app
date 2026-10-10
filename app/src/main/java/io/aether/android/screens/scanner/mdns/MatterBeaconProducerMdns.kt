// SPDX-FileCopyrightText: 2023 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.scanner.mdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.core.content.ContextCompat.getSystemService
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.discovery.DnsSdServiceInfo
import com.google.android.gms.home.matter.discovery.ResolveServiceRequest
import com.google.android.gms.home.matter.discovery.ResolveServiceRequest.SERVICE_TYPE_COMMISSIONABLE
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aether.android.chip.ChipClient
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId
import io.aether.android.screens.scanner.MatterBeacon
import io.aether.android.screens.scanner.MatterBeaconProducer
import io.aether.android.screens.scanner.Transport
import javax.inject.Inject
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/** [MatterBeaconProducer] which emits mDNS beacons as they are discovered. */
class MatterBeaconProducerMdns
@Inject
constructor(
    private val chipClient: ChipClient,
    @param:ApplicationContext private val context: Context,
) : MatterBeaconProducer {

  // Android's NSD Manager. Used to scan mDNS advertisements.
  private val nsdManager = getSystemService(context, NsdManager::class.java) as NsdManager

  // Google Home Discovery API. See
  // https://developers.home.google.com/reference/com/google/android/gms/home/matter/discovery/package-summary.
  private val discoveryClient = Matter.getDiscoveryClient(context)

  // The scanned mDNS beacons list that's currently active.
  private var beaconsList = mutableListOf<MatterBeacon>()

  // The producer that is set by the flow.
  private lateinit var producer: ProducerScope<MatterBeacon>

  // ---------------------------------------------------------------------------
  // DiscoveryListener for NsdManager.discoverServices().

  private val discoveryListener =
      object : NsdManager.DiscoveryListener {

        // Called as soon as service discovery begins.
        override fun onDiscoveryStarted(regType: String) {
          Timber.d("mDNS discovery started regType=$regType")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
          Timber.d("mDNS service found service=$service")
          if (service.serviceType != SERVICE_TYPE_ANDROID) {
            Timber.d(
                "Discarded serviceType=${service.serviceType} serviceName=${service.serviceName}",
            )
          } else {
            // Resolve the service.
            val resolveServiceRequest =
                ResolveServiceRequest.create(service.serviceName, SERVICE_TYPE_COMMISSIONABLE)
            discoveryClient
                .resolveService(resolveServiceRequest)
                .addOnSuccessListener { result ->
                  Timber.d("mDNS service resolved serviceInfo=${result.serviceInfo}")
                  resolvedDnsSdServiceInfo(result.serviceInfo)
                }
                .addOnFailureListener { error -> Timber.e(error, "Service resolution failed") }
          }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
          // When the network service is no longer available.
          Timber.d("mDNS service lost service=$service")
          lostNsdServiceInfo(service)
        }

        override fun onDiscoveryStopped(serviceType: String) {
          Timber.d("mDNS discovery stopped serviceType=$serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
          Timber.e("mDNS discovery failed serviceType=$serviceType errorCode=$errorCode")
          nsdManager.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
          Timber.e("mDNS discovery stop failed serviceType=$serviceType errorCode=$errorCode")
          nsdManager.stopServiceDiscovery(this)
        }
      }

  // ---------------------------------------------------------------------------
  // Utility methods to manage the discovered mDNS services.

  /**
   * The mDNS service has been resolved. We convert the service information into a MatterBeacon and
   * emit that beacon to the producer flow.
   */
  private fun resolvedDnsSdServiceInfo(dnsSdServiceInfo: DnsSdServiceInfo) {
    val discriminator =
        dnsSdServiceInfo
            .getTxtAttributeValue("D")
            ?.takeIf { it.length <= 4 }
            ?.takeIf { it.all { char -> char.isDigit() } }
            ?.toIntOrNull()

    val vidPid =
        dnsSdServiceInfo
            .getTxtAttributeValue("VP")
            .orEmpty()
            .split("+")
            .takeUnless { it.size > 2 }
            ?.takeIf { it.all { value -> value.all { char -> char.isDigit() } } }

    val address = dnsSdServiceInfo.networkLocations.get(0).formattedIpAddress
    val port = dnsSdServiceInfo.networkLocations.get(0).port

    val beacon =
        MatterBeacon(
            name = dnsSdServiceInfo.instanceName,
            vendorId = VendorId((vidPid?.getOrNull(0)?.toIntOrNull() ?: 0).toUShort()),
            productId = ProductId((vidPid?.getOrNull(1)?.toIntOrNull() ?: 0).toUShort()),
            discriminator = discriminator!!,
            transport = Transport.Mdns(address, port, true),
        )
    Timber.d("Resolved DNS-SD beacon=$beacon")
    producer.trySend(beacon)
  }

  /** The mDNS service is no longer advertising. */
  private fun lostNsdServiceInfo(nsdServiceInfo: NsdServiceInfo) {
    Timber.d("Lost DNS-SD serviceName=${nsdServiceInfo.serviceName}")
    val beacon =
        MatterBeacon(
            name = nsdServiceInfo.serviceName,
            vendorId = VendorId(0u.toUShort()),
            productId = ProductId(0u.toUShort()),
            discriminator = 0,
            Transport.Mdns("0.0.0.0", 0 /* fixme */, false),
        )
    producer.trySend(beacon)
  }

  // ---------------------------------------------------------------------------
  // MatterBeaconProducer interface functions

  override fun getBeaconsFlow(): Flow<MatterBeacon> = callbackFlow {
    Timber.d("Starting mDNS discovery -- NATIVE")
    producer = this
    nsdManager.discoverServices(SERVICE_TYPE_ANDROID, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

    awaitClose {
      Timber.d("Stopping mDNS discovery")
      nsdManager.stopServiceDiscovery(discoveryListener)
    }
  }

  // ---------------------------------------------------------------------------
  // Companion

  companion object {
    private const val SERVICE_TYPE_GMSCORE = SERVICE_TYPE_COMMISSIONABLE
    private const val SERVICE_TYPE_ANDROID = SERVICE_TYPE_GMSCORE + "."
  }
}
