// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data

import chip.devicecontroller.ChipStructs
import chip.devicecontroller.ReportCallback
import chip.devicecontroller.model.ChipAttributePath
import chip.devicecontroller.model.ChipEventPath
import chip.devicecontroller.model.NodeState
import io.aether.android.chip.ChipClient
import io.aether.android.data.models.EthernetNetworkDiagnosticsData
import io.aether.android.data.models.GeneralDiagnosticsData
import io.aether.android.data.models.NetworkInterface
import io.aether.android.data.models.SoftwareDiagnosticsData
import io.aether.android.data.models.ThreadMetrics
import io.aether.android.data.models.ThreadNetworkDiagnosticsData
import io.aether.android.data.models.WiFiNetworkDiagnosticsData
import io.aether.android.matter.AttributeId
import io.aether.android.matter.Clusters
import io.aether.android.matter.Enums
import io.aether.android.matter.NodeId
import io.aether.android.matter.ROOT_ENDPOINT_ID
import io.aether.android.matter.WILDCARD_ATTRIBUTE_ID
import java.net.Inet6Address
import java.net.InetAddress
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import timber.log.Timber

class DeviceDiagnosticsRepository @Inject constructor(private val chipClient: ChipClient) {

  private val readTimeoutDuration = 5.seconds

  suspend fun readGeneralDiagnostics(nodeId: NodeId): GeneralDiagnosticsData? = runCatching {
    withTimeout(readTimeoutDuration) {
      val devicePtr = chipClient.getConnectedDevicePointer(nodeId)
      suspendCancellableCoroutine { continuation ->
        val readCallback =
          object : ReportCallback {
            override fun onError(
              path: ChipAttributePath?,
              event: ChipEventPath?,
              ex: Exception,
            ) {
              if (continuation.isActive) continuation.resumeWithException(ex)
            }

            override fun onReport(nodeState: NodeState) {
              if (continuation.isActive) {
                continuation.resume(extractGeneralDiagnosticsData(nodeState))
              }
            }
          }
        val readPaths =
          listOf(
            ChipAttributePath.newInstance(
              ROOT_ENDPOINT_ID.toLong(),
              Clusters.GeneralDiagnostics.ID.toLong(),
              WILDCARD_ATTRIBUTE_ID.toLong(),
            ),
          )
        chipClient.chipDeviceController.readAttributePath(
          readCallback,
          devicePtr,
          readPaths,
        )
      }
    }
  }
    .onFailure { e -> Timber.e(e, "Failed to read General Diagnostics for nodeId=$nodeId") }
    .getOrNull()

  private fun extractGeneralDiagnosticsData(nodeState: NodeState): GeneralDiagnosticsData {
    val clusterState =
      nodeState
        .getEndpointState(ROOT_ENDPOINT_ID.toInt())
        ?.getClusterState(Clusters.GeneralDiagnostics.ID.toLong())
        ?: return GeneralDiagnosticsData(networkInterfaces = emptyList(), rebootCount = 0u)

    fun attr(id: AttributeId) = clusterState.getAttributeState(id.toLong())?.value
    return GeneralDiagnosticsData(
      networkInterfaces =
        attr(Clusters.GeneralDiagnostics.Attributes.NetworkInterfaces.ID).toNetworkInterfaces(),
      rebootCount =
        attr(Clusters.GeneralDiagnostics.Attributes.RebootCount.ID).toUShortOrNull() ?: 0u,
      upTime = attr(Clusters.GeneralDiagnostics.Attributes.UpTime.ID).toULongOrNull(),
      totalOperationalHours =
        attr(Clusters.GeneralDiagnostics.Attributes.TotalOperationalHours.ID).toUIntOrNull(),
      bootReason = attr(Clusters.GeneralDiagnostics.Attributes.BootReason.ID).toBootReasonOrNull(),
      activeHardwareFaults =
        attr(Clusters.GeneralDiagnostics.Attributes.ActiveHardwareFaults.ID).toHardwareFaults(),
      activeRadioFaults =
        attr(Clusters.GeneralDiagnostics.Attributes.ActiveRadioFaults.ID).toRadioFaults(),
      activeNetworkFaults =
        attr(Clusters.GeneralDiagnostics.Attributes.ActiveNetworkFaults.ID).toNetworkFaults(),
      testEventTriggersEnabled =
        attr(Clusters.GeneralDiagnostics.Attributes.TestEventTriggersEnabled.ID).toBooleanOrNull(),
      deviceLoadStatus =
        attr(Clusters.GeneralDiagnostics.Attributes.DeviceLoadStatus.ID).toString(),
    )
  }

  suspend fun readSoftwareDiagnostics(nodeId: NodeId): SoftwareDiagnosticsData? = runCatching {
    withTimeout(readTimeoutDuration) {
      val devicePtr = chipClient.getConnectedDevicePointer(nodeId)
      suspendCancellableCoroutine { continuation ->
        val readCallback =
          object : ReportCallback {
            override fun onError(
              path: ChipAttributePath?,
              event: ChipEventPath?,
              ex: Exception,
            ) {
              if (continuation.isActive) continuation.resumeWithException(ex)
            }

            override fun onReport(nodeState: NodeState) {
              if (continuation.isActive) {
                continuation.resume(extractSoftwareDiagnosticsData(nodeState))
              }
            }
          }
        val readPaths =
          listOf(
            ChipAttributePath.newInstance(
              ROOT_ENDPOINT_ID.toLong(),
              Clusters.SoftwareDiagnostics.ID.toLong(),
              WILDCARD_ATTRIBUTE_ID.toLong(),
            ),
          )
        chipClient.chipDeviceController.readAttributePath(
          readCallback,
          devicePtr,
          readPaths,
        )
      }
    }
  }
    .onFailure { e -> Timber.e(e, "Failed to read Software Diagnostics for nodeId=$nodeId") }
    .getOrNull()

  private fun extractSoftwareDiagnosticsData(nodeState: NodeState): SoftwareDiagnosticsData {
    val clusterState =
      nodeState
        .getEndpointState(ROOT_ENDPOINT_ID.toInt())
        ?.getClusterState(Clusters.SoftwareDiagnostics.ID.toLong())
        ?: return SoftwareDiagnosticsData(threadMetrics = emptyList())

    fun attr(id: AttributeId) = clusterState.getAttributeState(id.toLong())?.value
    return SoftwareDiagnosticsData(
      threadMetrics =
        attr(Clusters.SoftwareDiagnostics.Attributes.ThreadMetrics.ID).toThreadMetrics(),
      currentHeapFree =
        attr(Clusters.SoftwareDiagnostics.Attributes.CurrentHeapFree.ID).toULongOrNull(),
      currentHeapUsed =
        attr(Clusters.SoftwareDiagnostics.Attributes.CurrentHeapUsed.ID).toULongOrNull(),
      currentHeapHighWatermark =
        attr(Clusters.SoftwareDiagnostics.Attributes.CurrentHeapHighWatermark.ID).toULongOrNull(),
    )
  }

  suspend fun readEthernetNetworkDiagnostics(nodeId: NodeId): EthernetNetworkDiagnosticsData? =
    runCatching {
      withTimeout(readTimeoutDuration) {
        val devicePtr = chipClient.getConnectedDevicePointer(nodeId)
        suspendCancellableCoroutine { continuation ->
          val readCallback =
            object : ReportCallback {
              override fun onError(
                path: ChipAttributePath?,
                event: ChipEventPath?,
                ex: Exception,
              ) {
                if (continuation.isActive) continuation.resumeWithException(ex)
              }

              override fun onReport(nodeState: NodeState) {
                if (continuation.isActive) {
                  continuation.resume(extractEthernetNetworkDiagnosticsData(nodeState))
                }
              }
            }
          val readPaths =
            listOf(
              ChipAttributePath.newInstance(
                ROOT_ENDPOINT_ID.toLong(),
                Clusters.EthernetNetworkDiagnostics.ID.toLong(),
                WILDCARD_ATTRIBUTE_ID.toLong(),
              ),
            )
          chipClient.chipDeviceController.readAttributePath(
            readCallback,
            devicePtr,
            readPaths,
          )
        }
      }
    }
    .onFailure { e ->
      Timber.e(e, "Failed to read Ethernet Network Diagnostics for nodeId=$nodeId")
    }
    .getOrNull()

  private fun extractEthernetNetworkDiagnosticsData(
    nodeState: NodeState,
  ): EthernetNetworkDiagnosticsData {
    val clusterState =
      nodeState
        .getEndpointState(ROOT_ENDPOINT_ID.toInt())
        ?.getClusterState(Clusters.EthernetNetworkDiagnostics.ID.toLong())
        ?: return EthernetNetworkDiagnosticsData()

    fun attr(id: AttributeId) = clusterState.getAttributeState(id.toLong())?.value
    return EthernetNetworkDiagnosticsData(
      phyRate = attr(Clusters.EthernetNetworkDiagnostics.Attributes.PHYRate.ID).toPhyRateOrNull(),
      fullDuplex =
        attr(Clusters.EthernetNetworkDiagnostics.Attributes.FullDuplex.ID).toBooleanOrNull(),
      packetRxCount =
        attr(Clusters.EthernetNetworkDiagnostics.Attributes.PacketRxCount.ID).toULongOrNull(),
      packetTxCount =
        attr(Clusters.EthernetNetworkDiagnostics.Attributes.PacketTxCount.ID).toULongOrNull(),
      txErrCount =
        attr(Clusters.EthernetNetworkDiagnostics.Attributes.TxErrCount.ID).toULongOrNull(),
      collisionCount =
        attr(Clusters.EthernetNetworkDiagnostics.Attributes.CollisionCount.ID).toULongOrNull(),
      overrunCount =
        attr(Clusters.EthernetNetworkDiagnostics.Attributes.OverrunCount.ID).toULongOrNull(),
      carrierDetect =
        attr(Clusters.EthernetNetworkDiagnostics.Attributes.CarrierDetect.ID).toBooleanOrNull(),
      timeSinceReset =
        attr(Clusters.EthernetNetworkDiagnostics.Attributes.TimeSinceReset.ID).toULongOrNull(),
    )
  }

  suspend fun readWiFiNetworkDiagnostics(nodeId: NodeId): WiFiNetworkDiagnosticsData? =
    runCatching {
      withTimeout(readTimeoutDuration) {
        val devicePtr = chipClient.getConnectedDevicePointer(nodeId)
        suspendCancellableCoroutine { continuation ->
          val readCallback =
            object : ReportCallback {
              override fun onError(
                path: ChipAttributePath?,
                event: ChipEventPath?,
                ex: Exception,
              ) {
                if (continuation.isActive) continuation.resumeWithException(ex)
              }

              override fun onReport(nodeState: NodeState) {
                if (continuation.isActive) {
                  continuation.resume(extractWiFiNetworkDiagnosticsData(nodeState))
                }
              }
            }
          val readPaths =
            listOf(
              ChipAttributePath.newInstance(
                ROOT_ENDPOINT_ID.toLong(),
                Clusters.WiFiNetworkDiagnostics.ID.toLong(),
                WILDCARD_ATTRIBUTE_ID.toLong(),
              ),
            )
          chipClient.chipDeviceController.readAttributePath(
            readCallback,
            devicePtr,
            readPaths,
          )
        }
      }
    }
    .onFailure { e -> Timber.e(e, "Failed to read WiFi Network Diagnostics for nodeId=$nodeId") }
    .getOrNull()

  private fun extractWiFiNetworkDiagnosticsData(nodeState: NodeState): WiFiNetworkDiagnosticsData {
    val clusterState =
      nodeState
        .getEndpointState(ROOT_ENDPOINT_ID.toInt())
        ?.getClusterState(Clusters.WiFiNetworkDiagnostics.ID.toLong())
        ?: return WiFiNetworkDiagnosticsData()

    fun attr(id: AttributeId) = clusterState.getAttributeState(id.toLong())?.value
    return WiFiNetworkDiagnosticsData(
      bssid = attr(Clusters.WiFiNetworkDiagnostics.Attributes.BSSID.ID).toBSSID(),
      securityType =
        attr(Clusters.WiFiNetworkDiagnostics.Attributes.SecurityType.ID).toSecurityTypeOrNull(),
      wifiVersion =
        attr(Clusters.WiFiNetworkDiagnostics.Attributes.WiFiVersion.ID).toWiFiVersionOrNull(),
      channelNumber =
        attr(Clusters.WiFiNetworkDiagnostics.Attributes.ChannelNumber.ID).toUShortOrNull(),
      rssi = attr(Clusters.WiFiNetworkDiagnostics.Attributes.RSSI.ID).toIntOrNull(),
      beaconLostCount =
        attr(Clusters.WiFiNetworkDiagnostics.Attributes.BeaconLostCount.ID).toUIntOrNull(),
      beaconRxCount =
        attr(Clusters.WiFiNetworkDiagnostics.Attributes.BeaconRxCount.ID).toUIntOrNull(),
      packetMulticastRxCount =
        attr(Clusters.WiFiNetworkDiagnostics.Attributes.PacketMulticastRxCount.ID).toUIntOrNull(),
      packetMulticastTxCount =
        attr(Clusters.WiFiNetworkDiagnostics.Attributes.PacketMulticastTxCount.ID).toUIntOrNull(),
      packetUnicastRxCount =
        attr(Clusters.WiFiNetworkDiagnostics.Attributes.PacketUnicastRxCount.ID).toUIntOrNull(),
      packetUnicastTxCount =
        attr(Clusters.WiFiNetworkDiagnostics.Attributes.PacketUnicastTxCount.ID).toUIntOrNull(),
      currentMaxRate =
        attr(Clusters.WiFiNetworkDiagnostics.Attributes.CurrentMaxRate.ID).toULongOrNull(),
      overrunCount =
        attr(Clusters.WiFiNetworkDiagnostics.Attributes.OverrunCount.ID).toULongOrNull(),
    )
  }

  suspend fun readThreadNetworkDiagnostics(nodeId: NodeId): ThreadNetworkDiagnosticsData? =
    runCatching {
      withTimeout(readTimeoutDuration) {
        val devicePtr = chipClient.getConnectedDevicePointer(nodeId)
        suspendCancellableCoroutine { continuation ->
          val readCallback =
            object : ReportCallback {
              override fun onError(
                path: ChipAttributePath?,
                event: ChipEventPath?,
                ex: Exception,
              ) {
                if (continuation.isActive) continuation.resumeWithException(ex)
              }

              override fun onReport(nodeState: NodeState) {
                if (continuation.isActive) {
                  continuation.resume(extractThreadNetworkDiagnosticsData(nodeState))
                }
              }
            }
          val readPaths =
            listOf(
              ChipAttributePath.newInstance(
                ROOT_ENDPOINT_ID.toLong(),
                Clusters.ThreadNetworkDiagnostics.ID.toLong(),
                WILDCARD_ATTRIBUTE_ID.toLong(),
              ),
            )
          chipClient.chipDeviceController.readAttributePath(
            readCallback,
            devicePtr,
            readPaths,
          )
        }
      }
    }
    .onFailure { e -> Timber.e(e, "Failed to read Thread Network Diagnostics for nodeId=$nodeId") }
    .getOrNull()

  private fun extractThreadNetworkDiagnosticsData(
    nodeState: NodeState,
  ): ThreadNetworkDiagnosticsData {
    val clusterState =
      nodeState
        .getEndpointState(ROOT_ENDPOINT_ID.toInt())
        ?.getClusterState(Clusters.ThreadNetworkDiagnostics.ID.toLong())
        ?: return ThreadNetworkDiagnosticsData()

    fun attr(id: AttributeId) = clusterState.getAttributeState(id.toLong())?.value
    return ThreadNetworkDiagnosticsData()
  }

  private val interfaceTypeMap =
    Enums.DiagnosticsGeneralClusterInterfaceType.entries.associateBy { it.value }

  private fun Any?.toNetworkInterfaces(): List<NetworkInterface> =
    when (this) {
      is List<*> ->
        this.filterIsInstance<ChipStructs.GeneralDiagnosticsClusterNetworkInterface>().map {
          NetworkInterface(
            name = it.name,
            isOperational = it.isOperational,
            offPremiseServicesReachableIPv4 = it.offPremiseServicesReachableIPv4,
            offPremiseServicesReachableIPv6 = it.offPremiseServicesReachableIPv6,
            hardwareAddress = it.hardwareAddress?.toList() ?: emptyList(),
            ipv4Addresses = it.IPv4Addresses.map { addr -> InetAddress.getByAddress(addr) },
            ipv6Addresses =
              it.IPv6Addresses.map { addr -> Inet6Address.getByAddress(addr) as Inet6Address },
            type =
              interfaceTypeMap[it.type] ?: Enums.DiagnosticsGeneralClusterInterfaceType.Unspecified,
          )
        }

      else -> emptyList()
    }

  private val bootReasonMap =
    Enums.DiagnosticsGeneralClusterBootReason.entries.associateBy { it.value }

  private fun Any?.toBootReasonOrNull(): Enums.DiagnosticsGeneralClusterBootReason? =
    when (this) {
      is Int -> bootReasonMap[this]
      else -> null
    }

  private val hardwareFaultMap =
    Enums.DiagnosticsGeneralClusterHardwareFault.entries.associateBy { it.value }

  private fun Any?.toHardwareFaults(): List<Enums.DiagnosticsGeneralClusterHardwareFault> =
    when (this) {
      is List<*> -> this.filterIsInstance<Int>().mapNotNull { hardwareFaultMap[it] }
      else -> emptyList()
    }

  private val radioFaultMap =
    Enums.DiagnosticsGeneralClusterRadioFault.entries.associateBy { it.value }

  private fun Any?.toRadioFaults(): List<Enums.DiagnosticsGeneralClusterRadioFault> =
    when (this) {
      is List<*> -> this.filterIsInstance<Int>().mapNotNull { radioFaultMap[it] }
      else -> emptyList()
    }

  private val networkFaultMap =
    Enums.DiagnosticsGeneralClusterNetworkFault.entries.associateBy { it.value }

  private fun Any?.toNetworkFaults(): List<Enums.DiagnosticsGeneralClusterNetworkFault> =
    when (this) {
      is List<*> -> this.filterIsInstance<Int>().mapNotNull { networkFaultMap[it] }
      else -> emptyList()
    }

  private fun Any?.toThreadMetrics(): List<ThreadMetrics> =
    when (this) {
      is List<*> ->
        this.filterIsInstance<ChipStructs.SoftwareDiagnosticsClusterThreadMetricsStruct>().map {
          ThreadMetrics(
            id = it.id.toULong(),
            name = it.name.orElse(null),
            stackFreeCurrent = it.stackFreeCurrent?.toUIntOrNull(),
            stackFreeMinimum = it.stackFreeMinimum?.toUIntOrNull(),
            stackSize = it.stackSize?.toUIntOrNull(),
          )
        }

      else -> emptyList()
    }

  private fun Any?.toBSSID(): List<Byte>? =
    when (this) {
      is ByteArray -> this.toList()
      else -> null
    }

  private val phyRateMap = Enums.DiagnosticsEthernetClusterPHYRate.entries.associateBy { it.value }

  private fun Any?.toPhyRateOrNull(): Enums.DiagnosticsEthernetClusterPHYRate? =
    when (this) {
      is Int -> phyRateMap[this]
      else -> null
    }

  private val securityTypeMap =
    Enums.DiagnosticsWiFiClusterSecurityType.entries.associateBy { it.value }

  private fun Any?.toSecurityTypeOrNull(): Enums.DiagnosticsWiFiClusterSecurityType? =
    when (this) {
      is Int -> securityTypeMap[this]
      else -> null
    }

  private val wifiVersionMap =
    Enums.DiagnosticsWiFiClusterWiFiVersion.entries.associateBy { it.value }

  private fun Any?.toWiFiVersionOrNull(): Enums.DiagnosticsWiFiClusterWiFiVersion? =
    when (this) {
      is Int -> wifiVersionMap[this]
      else -> null
    }

  private fun Any?.toBooleanOrNull(): Boolean? =
    when (this) {
      is Boolean -> this
      else -> null
    }

  private fun Any?.toUShortOrNull(): UShort? =
    when (this) {
      is Int -> this.toUShort()
      else -> null
    }

  private fun Any?.toIntOrNull(): Int? =
    when (this) {
      is Int -> this
      is Long -> this.toInt()
      else -> null
    }

  private fun Any?.toUIntOrNull(): UInt? =
    when (this) {
      is Int -> this.toUInt()
      is Long -> this.toUInt()
      else -> null
    }

  private fun Any?.toULongOrNull(): ULong? =
    when (this) {
      is Int -> this.toULong()
      is Long -> this.toULong()
      else -> null
    }
}
