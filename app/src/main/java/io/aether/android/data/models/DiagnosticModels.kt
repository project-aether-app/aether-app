// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data.models

import io.aether.android.matter.Enums
import java.net.Inet6Address
import java.net.InetAddress

data class NetworkInterface(
    val type: Enums.DiagnosticsGeneralClusterInterfaceType,
    val hardwareAddress: ByteArray,
    val name: String,
    val offPremiseServicesReachableIPv4: Boolean? = null,
    val offPremiseServicesReachableIPv6: Boolean? = null,
    val ipv4Addresses: List<InetAddress> = emptyList(),
    val ipv6Addresses: List<Inet6Address> = emptyList(),
    val isOperational: Boolean = false,
)

data class GeneralDiagnosticsData(
    val networkInterfaces: List<NetworkInterface>,
    val rebootCount: UShort,
    val upTime: ULong? = null,
    val totalOperationalHours: UInt? = null,
    val bootReason: Enums.DiagnosticsGeneralClusterBootReason? = null,
    val activeHardwareFaults: List<Enums.DiagnosticsGeneralClusterHardwareFault> = emptyList(),
    val activeRadioFaults: List<Enums.DiagnosticsGeneralClusterRadioFault> = emptyList(),
    val activeNetworkFaults: List<Enums.DiagnosticsGeneralClusterNetworkFault> = emptyList(),
    val testEventTriggersEnabled: Boolean? = null,
    val deviceLoadStatus: String? = null,
)

data class ThreadMetrics(
    val id: ULong,
    val name: String? = null,
    val stackFreeCurrent: UInt? = null,
    val stackFreeMinimum: UInt? = null,
    val stackSize: UInt? = null,
)

data class SoftwareDiagnosticsData(
    val threadMetrics: List<ThreadMetrics>,
    val currentHeapFree: ULong? = null,
    val currentHeapUsed: ULong? = null,
    val currentHeapHighWatermark: ULong? = null,
)

data class EthernetNetworkDiagnosticsData(
    val phyRate: Enums.DiagnosticsEthernetClusterPHYRate? = null,
    val fullDuplex: Boolean? = null,
    val packetRxCount: ULong? = null,
    val packetTxCount: ULong? = null,
    val txErrCount: ULong? = null,
    val collisionCount: ULong? = null,
    val overrunCount: ULong? = null,
    val carrierDetect: Boolean? = null,
    val timeSinceReset: ULong? = null,
)

data class WiFiNetworkDiagnosticsData(
    val bssid: ByteArray? = null,
    val securityType: Enums.DiagnosticsWiFiClusterSecurityType? = null,
    val wifiVersion: Enums.DiagnosticsWiFiClusterWiFiVersion? = null,
    val channelNumber: UShort? = null,
    val rssi: Int? = null,
    val beaconLostCount: UInt? = null,
    val beaconRxCount: UInt? = null,
    val packetMulticastRxCount: UInt? = null,
    val packetMulticastTxCount: UInt? = null,
    val packetUnicastRxCount: UInt? = null,
    val packetUnicastTxCount: UInt? = null,
    val currentMaxRate: ULong? = null,
    val overrunCount: ULong? = null,
)

data class ThreadNetworkDiagnosticsData(
    val channel: UShort? = null,
    val routingRole: Enums.DiagnosticsThreadClusterRoutingRole? = null,
    val networkName: String? = null,
    val panId: UShort? = null,
    val extendedPanId: ULong? = null,
    val meshLocalPrefix: String? = null, // TODO: type ipv6pre
    val overrunCount: ULong? = null,
    val neighborTable: List<String> = emptyList(), // TODO: type NeighborTableStruct
    val routeTable: List<String> = emptyList(), // TODO: type RouteTableStruct
    val partitionId: UShort? = null,
    val weighting: UShort? = null,
    val dataVersion: UShort? = null,
    val stableDataVersion: UShort? = null,
    val leaderRouterId: UByte? = null,
    val detachedRoleCount: UShort? = null,
    val childRoleCount: UShort? = null,
    val routerRoleCount: UShort? = null,
    val leaderRoleCount: UShort? = null,
    val attachAttemptCount: UShort? = null,
    val partitionIdChangeCount: UShort? = null,
    val betterPartitionAttachAttemptCount: UShort? = null,
    val parentChangeCount: UShort? = null,
    val txTotalCount: UInt? = null,
    val txUnicastCount: UInt? = null,
    val txBroadcastCount: UInt? = null,
    val txAckRequestedCount: UInt? = null,
    val txAckedCount: UInt? = null,
    val txNoAckRequestedCount: UInt? = null,
    val txDataCount: UInt? = null,
    val txDataPollCount: UInt? = null,
    val txBeaconCount: UInt? = null,
    val txBeaconRequestCount: UInt? = null,
    val txOtherCount: UInt? = null,
    val txRetryCount: UInt? = null,
    val txDirectMaxRetryExpiryCount: UInt? = null,
    val txIndirectMaxRetryExpiryCount: UInt? = null,
    val txErrCcaCount: UInt? = null,
    val txErrAbortCount: UInt? = null,
    val txErrBusyChannelCount: UInt? = null,
    val rxTotalCount: UInt? = null,
    val rxUnicastCount: UInt? = null,
    val rxBroadcastCount: UInt? = null,
    val rxDataCount: UInt? = null,
    val rxDataPollCount: UInt? = null,
    val rxBeaconCount: UInt? = null,
    val rxBeaconRequestCount: UInt? = null,
    val rxOtherCount: UInt? = null,
    val rxAddressFilteredCount: UInt? = null,
    val rxDestAddrFilteredCount: UInt? = null,
    val rxDuplicatedCount: UInt? = null,
    val rxErrNoFrameCount: UInt? = null,
    val rxErrUnknownNeighborCount: UInt? = null,
    val rxErrInvalidSrcAddrCount: UInt? = null,
    val rxErrSecCount: UInt? = null,
    val rxErrFcsCount: UInt? = null,
    val rxErrOtherCount: UInt? = null,
    val activeTimestamp: ULong? = null,
    val pendingTimestamp: ULong? = null,
    val delay: UInt? = null,
    val securityPolicy: String? = null, // TODO: type SecurityPolicyStruct
    val channelPage0Mask: ByteArray? = null,
    val operationalDatasetComponents: String? = null, // TODO: OperationalDatasetComponents
    val activeNetworkFaultsList: List<Enums.DiagnosticsThreadClusterNetworkFault> = emptyList(),
    val extAddress: ULong? = null,
    val rloc16: UShort? = null,
)
