// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.chip

import android.content.Context
import chip.devicecontroller.*
import chip.devicecontroller.GetConnectedDeviceCallbackJni.GetConnectedDeviceCallback
import chip.devicecontroller.model.*
import chip.platform.AndroidBleManager
import chip.platform.AndroidChipPlatform
import chip.platform.ChipMdnsCallbackImpl
import chip.platform.DiagnosticDataProviderImpl
import chip.platform.NsdManagerServiceBrowser
import chip.platform.NsdManagerServiceResolver
import chip.platform.PreferencesConfigurationManager
import chip.platform.PreferencesKeyValueStoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aether.android.matter.NodeId
import io.aether.android.matter.toNodeId
import io.aether.android.stripLinkLocalInIpAddress
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import timber.log.Timber

/** Singleton to interact with the CHIP APIs. */
@Singleton
class ChipClient @Inject constructor(@ApplicationContext context: Context) {

  /* 0xFFF4 is a test vendor ID, replace with your assigned company ID */
  private val VENDOR_ID = 0xFFF4

  private val DEFAULT_TIMEOUT = 1000

  // Lazily instantiate [ChipDeviceController] and hold a reference to it.
  val chipDeviceController: ChipDeviceController by lazy {
    ChipDeviceController.loadJni()
    AndroidChipPlatform(
        AndroidBleManager(),
        PreferencesKeyValueStoreManager(context),
        PreferencesConfigurationManager(context),
        NsdManagerServiceResolver(context),
        NsdManagerServiceBrowser(context),
        ChipMdnsCallbackImpl(),
        DiagnosticDataProviderImpl(context),
    )
    ChipDeviceController(
        ControllerParams.newBuilder().setUdpListenPort(0).setControllerVendorId(VENDOR_ID).build(),
    )
  }

  /**
   * Wrapper around [ChipDeviceController.getConnectedDevicePointer] to return the value directly.
   */
  suspend fun getConnectedDevicePointer(nodeId: NodeId): Long {
    return suspendCoroutine { continuation ->
      chipDeviceController.getConnectedDevicePointer(
          nodeId.toLong(),
          object : GetConnectedDeviceCallback {
            override fun onDeviceConnected(devicePointer: Long) {
              Timber.d("Got connected device pointer")
              continuation.resume(devicePointer)
            }

            override fun onConnectionFailure(id: Long, error: Exception) {
              val errorMessage = "Unable to get connected device with nodeId ${id.toNodeId()}."
              Timber.e(error, errorMessage)
              continuation.resumeWithException(IllegalStateException(errorMessage))
            }
          },
      )
    }
  }

  suspend fun getConnectedDevicePointer(id: Long): Long = getConnectedDevicePointer(id.toNodeId())

  /**
   * Removes the app's fabric from the device.
   *
   * @param nodeId node identifier
   */
  suspend fun awaitUnpairDevice(nodeId: NodeId) {
    return suspendCoroutine { continuation ->
      Timber.d("Calling chipDeviceController.unpair")
      val callback: UnpairDeviceCallback =
          object : UnpairDeviceCallback {
            override fun onError(status: Int, id: Long) {
              continuation.resumeWithException(
                  java.lang.IllegalStateException("Failed to unpair deviceId=$id status=$status"),
              )
            }

            override fun onSuccess(id: Long) {
              Timber.d("Device unpaired deviceId=$id")
              continuation.resume(Unit)
            }
          }
      chipDeviceController.unpairDeviceCallback(nodeId.toLong(), callback)
    }
  }

  suspend fun awaitUnpairDevice(id: Long) {
    awaitUnpairDevice(id.toNodeId())
  }

  fun computePaseVerifier(
      devicePtr: Long,
      pinCode: Long,
      iterations: Long,
      salt: ByteArray,
  ): PaseVerifierParams {
    Timber.d(
        "Computing PASE verifier devicePtr=$devicePtr pinCode=$pinCode iterations=$iterations salt=$salt",
    )
    return chipDeviceController.computePaseVerifier(devicePtr, pinCode, iterations, salt)
  }

  suspend fun awaitEstablishPaseConnection(
      deviceId: Long,
      ipAddress: String,
      port: Int,
      setupPinCode: Long,
  ) {
    return suspendCoroutine { continuation ->
      chipDeviceController.setCompletionListener(
          object : BaseCompletionListener() {
            override fun onConnectDeviceComplete() {
              super.onConnectDeviceComplete()
              continuation.resume(Unit)
            }

            // Note that an error in processing is not necessarily communicated via onError().
            // onCommissioningComplete with a "code != 0" also denotes an error in processing.
            override fun onPairingComplete(code: Int) {
              super.onPairingComplete(code)
              if (code != 0) {
                continuation.resumeWithException(
                    IllegalStateException("Pairing failed errorCode=$code"),
                )
              } else {
                continuation.resume(Unit)
              }
            }

            override fun onError(error: Throwable) {
              super.onError(error)
              continuation.resumeWithException(error)
            }

            override fun onReadCommissioningInfo(
                vendorId: Int,
                productId: Int,
                wifiEndpointId: Int,
                threadEndpointId: Int,
            ) {
              super.onReadCommissioningInfo(vendorId, productId, wifiEndpointId, threadEndpointId)
              continuation.resume(Unit)
            }

            override fun onCommissioningStatusUpdate(id: Long, stage: String?, errorCode: Int) {
              super.onCommissioningStatusUpdate(id, stage, errorCode)
              continuation.resume(Unit)
            }
          },
      )

      // Temporary workaround to remove interface indexes from ipAddress
      // due to https://github.com/project-chip/connectedhomeip/pull/19394/files
      chipDeviceController.establishPaseConnection(
          deviceId,
          stripLinkLocalInIpAddress(ipAddress),
          port,
          setupPinCode,
      )
    }
  }

  suspend fun awaitCommissionDevice(deviceId: Long, networkCredentials: NetworkCredentials?) {
    return suspendCoroutine { continuation ->
      chipDeviceController.setCompletionListener(
          object : BaseCompletionListener() {
            // Note that an error in processing is not necessarily communicated via onError().
            // onCommissioningComplete with an "errorCode != 0" also denotes an error in processing.
            override fun onCommissioningComplete(id: Long, errorCode: Int) {
              super.onCommissioningComplete(id, errorCode)
              if (errorCode != 0) {
                continuation.resumeWithException(
                    IllegalStateException("Commissioning failed errorCode=$errorCode"),
                )
              } else {
                continuation.resume(Unit)
              }
            }

            override fun onError(error: Throwable) {
              super.onError(error)
              continuation.resumeWithException(error)
            }
          },
      )
      chipDeviceController.commissionDevice(deviceId, networkCredentials)
    }
  }

  suspend fun awaitOpenPairingWindowWithPIN(
      connectedDevicePointer: Long,
      duration: Int,
      iteration: Long,
      discriminator: Int,
      setupPinCode: Long,
  ) {
    return suspendCoroutine { continuation ->
      Timber.d("Calling chipDeviceController.openPairingWindowWithPIN")
      val callback: OpenCommissioningCallback =
          object : OpenCommissioningCallback {
            override fun onError(status: Int, deviceId: Long) {
              Timber.e("Failed to open pairing window status=$status deviceId=$deviceId")
              continuation.resumeWithException(
                  java.lang.IllegalStateException("Failed to open pairing window status=$status"),
              )
            }

            override fun onSuccess(deviceId: Long, manualPairingCode: String?, qrCode: String?) {
              Timber.d("Pairing window opened deviceId=$deviceId")
              continuation.resume(Unit)
            }
          }
      chipDeviceController.openPairingWindowWithPINCallback(
          connectedDevicePointer,
          duration,
          iteration,
          discriminator,
          setupPinCode,
          callback,
      )
    }
  }

  /**
   * Wrapper around [ChipDeviceController.getConnectedDevicePointer] to return the value directly.
   */
  suspend fun awaitGetConnectedDevicePointer(nodeId: NodeId): Long {
    return suspendCoroutine { continuation ->
      chipDeviceController.getConnectedDevicePointer(
          nodeId.toLong(),
          object : GetConnectedDeviceCallback {
            override fun onDeviceConnected(devicePointer: Long) {
              Timber.d("Got connected device pointer")
              continuation.resume(devicePointer)
            }

            override fun onConnectionFailure(id: Long, error: Exception) {
              val errorMessage = "Unable to get connected device with nodeId ${id.toNodeId()}"
              Timber.e(error, errorMessage)
              continuation.resumeWithException(IllegalStateException(errorMessage))
            }
          },
      )
    }
  }

  suspend fun awaitGetConnectedDevicePointer(id: Long): Long =
      awaitGetConnectedDevicePointer(id.toNodeId())

  // ---------------------------------------------------------------------------
  // We use our own mDNS discovery code, but interesting to note that
  // ChipDeviceController also offers that feature.

  fun getCommissionableNodes() {
    chipDeviceController.discoverCommissionableNodes()
  }

  fun getDiscoveredDevice(index: Int): DiscoveredDevice? {
    Timber.d("Getting discovered device index=$index")
    return chipDeviceController.getDiscoveredDevice(index)
  }

  // ---------------------------------------------------------------------------
  // Access clusters via numeric ids. Useful to access manufacturer specific clusters.

  suspend fun writeAttribute(
      devicePtr: Long,
      attributePath: ChipAttributePath,
      tlv: ByteArray,
      timedRequestTimeoutMs: Int = DEFAULT_TIMEOUT,
      imTimeoutMs: Int = DEFAULT_TIMEOUT,
  ) {
    return writeAttributes(
        devicePtr,
        mapOf(attributePath to tlv),
        timedRequestTimeoutMs,
        imTimeoutMs,
    )
  }

  /** Wrapper around [ChipDeviceController.write] */
  suspend fun writeAttributes(
      devicePtr: Long,
      attributes: Map<ChipAttributePath, ByteArray>,
      timedRequestTimeoutMs: Int = DEFAULT_TIMEOUT,
      imTimeoutMs: Int = DEFAULT_TIMEOUT,
  ) {
    return suspendCoroutine { continuation ->
      val requests: List<AttributeWriteRequest> =
          attributes.toList().map {
            AttributeWriteRequest.newInstance(
                it.first.endpointId,
                it.first.clusterId,
                it.first.attributeId,
                it.second,
            )
          }
      val callback: WriteAttributesCallback =
          object : WriteAttributesCallback {
            override fun onError(attributePath: ChipAttributePath?, e: java.lang.Exception?) {
              continuation.resumeWithException(IllegalStateException("writeAttributes failed", e))
            }

            override fun onResponse(attributePath: ChipAttributePath?) {
              if (
                  attributePath!! ==
                      ChipAttributePath.newInstance(
                          requests.last().endpointId,
                          requests.last().clusterId,
                          requests.last().attributeId,
                      )
              ) {
                continuation.resume(Unit)
              }
            }
          }

      chipDeviceController.write(callback, devicePtr, requests, timedRequestTimeoutMs, imTimeoutMs)
    }
  }

  suspend fun readAttribute(devicePtr: Long, attributePath: ChipAttributePath): AttributeState? {
    return readAttributes(devicePtr, listOf(attributePath))[attributePath]
  }

  /** Wrapper around [ChipDeviceController.readAttributePath] */
  suspend fun readAttributes(
      devicePtr: Long,
      attributePaths: List<ChipAttributePath>,
  ): Map<ChipAttributePath, AttributeState> {
    return suspendCoroutine { continuation ->
      val completed = AtomicBoolean(false)
      val callback: ReportCallback =
          object : ReportCallback {
            override fun onError(
                attributePath: ChipAttributePath?,
                eventPath: ChipEventPath?,
                e: Exception?,
            ) {
              if (completed.compareAndSet(false, true)) {
                continuation.resumeWithException(
                    IllegalStateException(
                        "readAttributes failed",
                        e ?: IllegalStateException("Unknown readAttributes error"),
                    ),
                )
              }
            }

            override fun onReport(nodeState: NodeState?) {
              if (!completed.compareAndSet(false, true)) {
                return
              }

              try {
                val states: HashMap<ChipAttributePath, AttributeState> = HashMap()
                val reportState =
                    nodeState
                        ?: run {
                          continuation.resume(states)
                          return
                        }

                for (path in attributePaths) {
                  val endpoint = path.endpointId.id.toInt()
                  val attributeState =
                      reportState
                          .getEndpointState(endpoint)
                          ?.getClusterState(path.clusterId.id)
                          ?.getAttributeState(path.attributeId.id)
                  if (attributeState != null) {
                    states[path] = attributeState
                  }
                }

                continuation.resume(states)
              } catch (ex: Exception) {
                continuation.resumeWithException(IllegalStateException("readAttributes failed", ex))
              }
            }

            override fun onDone() {
              super.onDone()
            }
          }
      chipDeviceController.readAttributePath(callback, devicePtr, attributePaths)
    }
  }

  /** Wrapper around [ChipDeviceController.subscribeToAttributePath] */
  suspend fun subscribeToAttribute(
      devicePtr: Long,
      attributePath: ChipAttributePath,
      minInterval: Int,
      maxInterval: Int,
      callback: ReportCallback,
  ) {
    return suspendCoroutine { continuation ->
      chipDeviceController.subscribeToAttributePath(
          { continuation.resume(Unit) },
          callback,
          devicePtr,
          listOf(attributePath),
          minInterval,
          maxInterval,
      )
    }
  }

  /** Wrapper around [ChipDeviceController.invoke] */
  suspend fun invoke(
      devicePtr: Long,
      invokeElement: InvokeElement,
      timedRequestTimeoutMs: Int = DEFAULT_TIMEOUT,
      imTimeoutMs: Int = DEFAULT_TIMEOUT,
  ): Long {
    return suspendCoroutine { continuation ->
      val invokeCallback: InvokeCallback =
          object : InvokeCallback {
            override fun onError(e: java.lang.Exception?) {
              continuation.resumeWithException(IllegalStateException("invoke failed", e))
            }

            override fun onResponse(invokeElement: InvokeElement?, successCode: Long) {
              continuation.resume(successCode)
            }
          }
      chipDeviceController.invoke(
          invokeCallback,
          devicePtr,
          invokeElement,
          timedRequestTimeoutMs,
          imTimeoutMs,
      )
    }
  }
}
