// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.chip

import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipClusters.BasicInformationCluster
import chip.devicecontroller.ChipStructs
import chip.devicecontroller.ReportCallback
import chip.devicecontroller.model.ChipAttributePath
import chip.devicecontroller.model.ChipEventPath
import chip.devicecontroller.model.InvokeElement
import chip.devicecontroller.model.NodeState
import io.aether.android.CommissioningWindowStatus
import io.aether.android.matter.AttributeId
import io.aether.android.matter.ClusterId
import io.aether.android.matter.Clusters
import io.aether.android.matter.CommandId
import io.aether.android.matter.DeviceTypeId
import io.aether.android.matter.EndpointId
import io.aether.android.matter.EventId
import io.aether.android.matter.GenericAttributes
import io.aether.android.matter.NodeId
import io.aether.android.matter.ProductId
import io.aether.android.matter.ROOT_ENDPOINT_ID
import io.aether.android.matter.VendorId
import io.aether.android.matter.toAttributeId
import io.aether.android.matter.toClusterId
import io.aether.android.matter.toCommandId
import io.aether.android.matter.toDeviceTypeId
import io.aether.android.matter.toEndpointId
import io.aether.android.matter.toEventId
import io.aether.android.matter.toNodeId
import io.aether.android.matter.toProductId
import io.aether.android.matter.toVendorId
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import timber.log.Timber

/**
 * Encapsulates the information of interest when querying a Matter device just after it has been
 * commissioned.
 */
data class DeviceMatterInfo(
    val endpointId: EndpointId,
    val types: List<DeviceTypeId>,
    val serverClusters: List<ClusterId>,
    val clientClusters: List<ClusterId>,
    val parts: List<EndpointId>,
)

data class BasicInformationAttributes(
    val vendorId: VendorId? = null,
    val vendorName: String? = null,
    val productId: ProductId? = null,
    val productName: String? = null,
    val hardwareVersion: String? = null,
    val softwareVersion: String? = null,
    val nodeLabel: String? = null,
)

@Singleton
class ClustersHelper @Inject constructor(private val chipClient: ChipClient) {

  // -----------------------------------------------------------------------------------------------
  // Convenience functions

  /** Fetches MatterDeviceInfo for each endpointId supported by the device. */
  suspend fun fetchDeviceMatterInfo(nodeId: NodeId): List<DeviceMatterInfo> {
    Timber.d("Fetching device Matter info nodeId=$nodeId")
    val matterDeviceInfoList = arrayListOf<DeviceMatterInfo>()
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(nodeId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return emptyList()
        }
    fetchDeviceMatterInfo(nodeId, connectedDevicePtr, ROOT_ENDPOINT_ID, matterDeviceInfoList)
    return matterDeviceInfoList
  }

  suspend fun readClusterAttributeList(
      nodeId: NodeId,
      endpointId: EndpointId,
      clusterId: ClusterId,
  ): List<AttributeId> {
    return readGlobalListAttribute(
            nodeId = nodeId,
            endpointId = endpointId,
            clusterId = clusterId,
            globalAttributeId = GenericAttributes.AttributeList.ID,
        )
        .map { it.toAttributeId() }
  }

  suspend fun readClusterAcceptedCommandList(
      nodeId: NodeId,
      endpointId: EndpointId,
      clusterId: ClusterId,
  ): List<CommandId> {
    return readGlobalListAttribute(
            nodeId = nodeId,
            endpointId = endpointId,
            clusterId = clusterId,
            globalAttributeId = GenericAttributes.AcceptedCommandList.ID,
        )
        .map { it.toCommandId() }
  }

  suspend fun readClusterGeneratedCommandList(
      nodeId: NodeId,
      endpointId: EndpointId,
      clusterId: ClusterId,
  ): List<CommandId> {
    return readGlobalListAttribute(
            nodeId = nodeId,
            endpointId = endpointId,
            clusterId = clusterId,
            globalAttributeId = GenericAttributes.GeneratedCommandList.ID,
        )
        .map { it.toCommandId() }
  }

  suspend fun readClusterEventList(
      nodeId: NodeId,
      endpointId: EndpointId,
      clusterId: ClusterId,
  ): List<EventId> {
    return readGlobalListAttribute(
            nodeId = nodeId,
            endpointId = endpointId,
            clusterId = clusterId,
            globalAttributeId = GenericAttributes.EventList.ID,
        )
        .map { it.toEventId() }
  }

  suspend fun readAttributeValue(
      nodeId: NodeId,
      endpointId: EndpointId,
      clusterId: ClusterId,
      attributeId: AttributeId,
  ): String {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(nodeId)
        } catch (e: IllegalStateException) {
          Timber.e(e, "Can't get connectedDevicePointer for readAttributeValue.")
          throw e
        }
    val attributeState =
        chipClient.readAttribute(
            connectedDevicePtr,
            ChipAttributePath.newInstance(
                endpointId.toLong(),
                clusterId.toLong(),
                attributeId.toLong(),
            ),
        ) ?: throw IllegalStateException("readAttributeValue returned no state")
    return when {
      attributeState.value != null -> attributeState.value.toString()
      attributeState.json != null -> attributeState.json.toString()
      else -> throw IllegalStateException("readAttributeValue returned empty state")
    }
  }

  suspend fun invokeGenericCommand(
      nodeId: NodeId,
      endpointId: EndpointId,
      clusterId: ClusterId,
      commandId: CommandId,
      tlvPayload: ByteArray,
  ) {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(nodeId)
        } catch (e: IllegalStateException) {
          Timber.e(e, "Can't get connectedDevicePointer for invokeGenericCommand.")
          throw e
        }

    chipClient.invoke(
        connectedDevicePtr,
        InvokeElement.newInstance(
            endpointId.toLong(),
            clusterId.toLong(),
            commandId.toLong(),
            tlvPayload,
            "",
        ),
    )
  }

  suspend fun writeGenericAttribute(
      nodeId: NodeId,
      endpointId: EndpointId,
      clusterId: ClusterId,
      attributeId: AttributeId,
      tlvPayload: ByteArray,
  ) {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(nodeId)
        } catch (e: IllegalStateException) {
          Timber.e(e, "Can't get connectedDevicePointer for writeGenericAttribute.")
          throw e
        }

    chipClient.writeAttribute(
        connectedDevicePtr,
        ChipAttributePath.newInstance(
            endpointId.toLong(),
            clusterId.toLong(),
            attributeId.toLong(),
        ),
        tlvPayload,
    )
  }

  private suspend fun readGlobalListAttribute(
      nodeId: NodeId,
      endpointId: EndpointId,
      clusterId: ClusterId,
      globalAttributeId: AttributeId,
  ): List<Long> {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(nodeId)
        } catch (e: IllegalStateException) {
          Timber.e(e, "Can't get connectedDevicePointer for readGlobalListAttribute.")
          return emptyList()
        }
    val attributeState =
        try {
          chipClient.readAttribute(
              connectedDevicePtr,
              ChipAttributePath.newInstance(
                  endpointId.toLong(),
                  clusterId.toLong(),
                  globalAttributeId.toLong(),
              ),
          )
        } catch (e: IllegalStateException) {
          if (isUnsupportedAttributeError(e)) {
            Timber.d(
                e,
                "Unsupported global attribute endpointId=$endpointId clusterId=${clusterId.toLong()} attributeId=${globalAttributeId.toLong()}",
            )
            return emptyList()
          }
          throw e
        } ?: return emptyList()
    return readLongList(attributeState.value)
  }

  private fun isUnsupportedAttributeError(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
      val message = current.message.orEmpty()
      if (message.contains("IM Status: 134") || message.contains("IM Status: 0x86")) {
        return true
      }
      current = current.cause
    }
    return false
  }

  private fun readLongList(value: Any?): List<Long> {
    return when (value) {
      is List<*> -> {
        val numericValues = value.mapNotNull { (it as? Number)?.toLong() }
        if (numericValues.size != value.size) {
          throw IllegalStateException(
              "readLongList: received non-numeric items in global list attribute",
          )
        }
        numericValues
      }
      is LongArray -> value.toList()
      is IntArray -> value.map { it.toLong() }
      else -> emptyList()
    }
  }

  /** Fetches MatterDeviceInfo for a specific endpointId. */
  suspend fun fetchDeviceMatterInfo(
      nodeId: NodeId,
      connectedDevicePtr: Long,
      endpointId: EndpointId,
      matterDeviceInfoList: ArrayList<DeviceMatterInfo>,
  ) {
    Timber.d("Fetching device Matter info nodeId=$nodeId endpointId=$endpointId")

    val partsListAttribute = readDescriptorClusterPartsListAttribute(connectedDevicePtr, endpointId)
    Timber.d("Parts list=$partsListAttribute")
    val parts = partsListAttribute.orEmpty()

    // DeviceListAttribute
    val deviceListAttribute =
        readDescriptorClusterDeviceListAttribute(connectedDevicePtr, endpointId)
    val types = arrayListOf<DeviceTypeId>()
    deviceListAttribute.forEach { types.add(it.deviceType.toDeviceTypeId()) }

    // ServerListAttribute
    val serverListAttribute =
        readDescriptorClusterServerListAttribute(connectedDevicePtr, endpointId)
    val serverClusters = arrayListOf<ClusterId>()
    serverListAttribute.forEach { serverClusters.add(it) }

    // ClientListAttribute
    val clientListAttribute =
        readDescriptorClusterClientListAttribute(connectedDevicePtr, endpointId)
    val clientClusters = arrayListOf<ClusterId>()
    clientListAttribute.forEach { clientClusters.add(it) }

    // Build the DeviceMatterInfo
    val deviceMatterInfo =
        DeviceMatterInfo(endpointId, types, serverClusters, clientClusters, parts)
    matterDeviceInfoList.add(deviceMatterInfo)

    // Recursive call for the parts supported by the endpointId.
    // For each part (endpointId)
    parts.forEach { part ->
      Timber.d("Processing part=$part")
      val childEndpointId = part
      fetchDeviceMatterInfo(nodeId, connectedDevicePtr, childEndpointId, matterDeviceInfoList)
    }
  }

  // -----------------------------------------------------------------------------------------------
  // DescriptorCluster functions

  /**
   * PartsListAttribute. These are the endpoints supported.
   *
   * ```
   * For example, on endpointId 0:
   *     sendReadPartsListAttribute part: [1]
   *     sendReadPartsListAttribute part: [2]
   * ```
   */
  suspend fun readDescriptorClusterPartsListAttribute(
      devicePtr: Long,
      endpointId: EndpointId,
  ): List<EndpointId>? {
    return suspendCoroutine { continuation ->
      getDescriptorClusterForDevice(devicePtr, endpointId)
          .readPartsListAttribute(
              object : ChipClusters.DescriptorCluster.PartsListAttributeCallback {
                override fun onSuccess(values: MutableList<Int>?) {
                  continuation.resume(values?.map { it.toEndpointId() })
                }

                override fun onError(ex: Exception) {
                  continuation.resumeWithException(ex)
                }
              },
          )
    }
  }

  /**
   * DeviceListAttribute
   *
   * ```
   * For example, on endpointId 0:
   *   device: [long type: 22, int revision: 1] -> maps to Root node (0x0016) (utility device type)
   * on endpointId 1:
   *   device: [long type: 256, int revision: 1] -> maps to On/Off Light (0x0100)
   * ```
   */
  suspend fun readDescriptorClusterDeviceListAttribute(
      devicePtr: Long,
      endpointId: EndpointId,
  ): List<ChipStructs.DescriptorClusterDeviceTypeStruct> {
    return suspendCoroutine { continuation ->
      getDescriptorClusterForDevice(devicePtr, endpointId)
          .readDeviceTypeListAttribute(
              object : ChipClusters.DescriptorCluster.DeviceTypeListAttributeCallback {
                override fun onSuccess(
                    values: List<ChipStructs.DescriptorClusterDeviceTypeStruct>,
                ) {
                  continuation.resume(values)
                }

                override fun onError(ex: Exception) {
                  continuation.resumeWithException(ex)
                }
              },
          )
    }
  }

  /**
   * ServerListAttribute See
   * https://github.com/project-chip/connectedhomeip/blob/master/zzz_generated/app-common/app-common/zap-generated/ids/Clusters.h
   *
   * ```
   * For example: on endpointId 0
   *     sendReadServerListAttribute: [3]
   *     sendReadServerListAttribute: [4]
   *     sendReadServerListAttribute: [29]
   *     ... and more ...
   * on endpointId 1:
   *     sendReadServerListAttribute: [3]
   *     sendReadServerListAttribute: [4]
   *     sendReadServerListAttribute: [5]
   *     sendReadServerListAttribute: [6]
   *     sendReadServerListAttribute: [7]
   *     ... and more ...
   * on endpointId 2:
   *     sendReadServerListAttribute: [4]
   *     sendReadServerListAttribute: [6]
   *     sendReadServerListAttribute: [29]
   *     sendReadServerListAttribute: [1030]
   *
   * Some mappings:
   *     namespace Groups = 0x00000004 (4)
   *     namespace OnOff = 0x00000006 (6)
   *     namespace Descriptor = 0x0000001D (29)
   *     namespace OccupancySensing = 0x00000406 (1030)
   * ```
   */
  suspend fun readDescriptorClusterServerListAttribute(
      devicePtr: Long,
      endpointId: EndpointId,
  ): List<ClusterId> {
    return suspendCoroutine { continuation ->
      getDescriptorClusterForDevice(devicePtr, endpointId)
          .readServerListAttribute(
              object : ChipClusters.DescriptorCluster.ServerListAttributeCallback {
                override fun onSuccess(values: MutableList<Long>) {
                  continuation.resume(values.map { it.toClusterId() })
                }

                override fun onError(ex: Exception) {
                  continuation.resumeWithException(ex)
                }
              },
          )
    }
  }

  /** ClientListAttribute */
  suspend fun readDescriptorClusterClientListAttribute(
      devicePtr: Long,
      endpointId: EndpointId,
  ): List<ClusterId> {
    return suspendCoroutine { continuation ->
      getDescriptorClusterForDevice(devicePtr, endpointId)
          .readClientListAttribute(
              object : ChipClusters.DescriptorCluster.ClientListAttributeCallback {
                override fun onSuccess(values: MutableList<Long>) {
                  continuation.resume(values.map { it.toClusterId() })
                }

                override fun onError(ex: Exception) {
                  continuation.resumeWithException(ex)
                }
              },
          )
    }
  }

  private fun getDescriptorClusterForDevice(
      devicePtr: Long,
      endpointId: EndpointId,
  ): ChipClusters.DescriptorCluster {
    return ChipClusters.DescriptorCluster(devicePtr, endpointId.toInt())
  }

  // -----------------------------------------------------------------------------------------------
  // ApplicationCluster functions

  suspend fun readApplicationBasicClusterAttributeList(
      deviceId: Long,
      endpointId: EndpointId,
  ): List<AttributeId> {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId.toNodeId())
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return emptyList()
        }
    return suspendCoroutine { continuation ->
      getApplicationBasicClusterForDevice(connectedDevicePtr, endpointId)
          .readAttributeListAttribute(
              object : ChipClusters.ApplicationBasicCluster.AttributeListAttributeCallback {
                override fun onSuccess(value: MutableList<Long>) {
                  continuation.resume(value.map { it.toAttributeId() })
                }

                override fun onError(ex: Exception) {
                  continuation.resumeWithException(ex)
                }
              },
          )
    }
  }

  private fun getApplicationBasicClusterForDevice(
      devicePtr: Long,
      endpointId: EndpointId,
  ): ChipClusters.ApplicationBasicCluster {
    return ChipClusters.ApplicationBasicCluster(devicePtr, endpointId.toInt())
  }

  // -----------------------------------------------------------------------------------------------
  // BasicCluster functions

  suspend fun readBasicClusterVendorIDAttribute(deviceId: Long, endpointId: EndpointId): Int? {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId.toNodeId())
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return null
        }
    return suspendCoroutine { continuation ->
      getBasicClusterForDevice(connectedDevicePtr, endpointId)
          .readVendorIDAttribute(
              object : ChipClusters.ApplicationBasicCluster.VendorIDAttributeCallback {
                override fun onSuccess(value: Int?) {
                  continuation.resume(value)
                }

                override fun onError(ex: Exception) {
                  continuation.resumeWithException(ex)
                }
              },
          )
    }
  }

  suspend fun readBasicClusterAttributeList(
      deviceId: Long,
      endpointId: EndpointId,
  ): List<AttributeId> {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId.toNodeId())
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return emptyList()
        }

    return suspendCoroutine { continuation ->
      getBasicClusterForDevice(connectedDevicePtr, endpointId)
          .readAttributeListAttribute(
              object : ChipClusters.ApplicationBasicCluster.AttributeListAttributeCallback {
                override fun onSuccess(values: MutableList<Long>) {
                  continuation.resume(values.map { it.toAttributeId() })
                }

                override fun onError(ex: Exception) {
                  continuation.resumeWithException(ex)
                }
              },
          )
    }
  }

  private fun getBasicClusterForDevice(
      devicePtr: Long,
      endpointId: EndpointId,
  ): ChipClusters.ApplicationBasicCluster {
    return ChipClusters.ApplicationBasicCluster(devicePtr, endpointId.toInt())
  }

  /**
   * Writes NodeLabel attribute. See spec section "11.1.6.3. Attributes" of the "Basic Information
   * Cluster".
   *
   * @param deviceId device identifier
   * @param nodeLabel device name/node label
   */
  suspend fun writeBasicClusterNodeLabelAttribute(deviceId: Long, nodeLabel: String) {
    val connectedDevicePtr = chipClient.getConnectedDevicePointer(deviceId.toNodeId())

    return suspendCoroutine { continuation ->
      val callback =
          object : ChipClusters.DefaultClusterCallback {
            override fun onSuccess() {
              continuation.resume(Unit)
            }

            override fun onError(ex: Exception) {
              continuation.resumeWithException(ex)
            }
          }

      BasicInformationCluster(connectedDevicePtr, 0).writeNodeLabelAttribute(callback, nodeLabel)
    }
  }

  /**
   * Reads the vendor name attribute. See spec section "11.1.6.3. Attributes" of the "Basic
   * Information Cluster".
   *
   * @param deviceId the device identifier.
   * @return the vendor name
   */
  suspend fun readBasicClusterVendorNameAttribute(deviceId: Long): String {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId.toNodeId())
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return ""
        }

    return suspendCoroutine { continuation ->
      val callback =
          object : ChipClusters.CharStringAttributeCallback {
            override fun onSuccess(value: String) {
              continuation.resume(value)
            }

            override fun onError(ex: Exception) {
              continuation.resumeWithException(ex)
            }
          }

      BasicInformationCluster(connectedDevicePtr, 0).readVendorNameAttribute(callback)
    }
  }

  /**
   * Reads node's product name attribute. See spec section "11.1.6.3. Attributes" of the "Basic
   * Information Cluster".
   *
   * @param deviceId the device identifier
   * @return the product name
   */
  suspend fun readBasicClusterProductNameAttribute(deviceId: Long): String {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId.toNodeId())
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return ""
        }

    return suspendCoroutine { continuation ->
      val callback =
          object : ChipClusters.CharStringAttributeCallback {
            override fun onSuccess(value: String) {
              continuation.resume(value)
            }

            override fun onError(ex: Exception) {
              continuation.resumeWithException(ex)
            }
          }

      BasicInformationCluster(connectedDevicePtr, 0).readProductNameAttribute(callback)
    }
  }

  // -----------------------------------------------------------------------------------------------
  // OnOffCluster functions

  suspend fun toggleDeviceStateOnOffCluster(deviceId: Long, endpointId: EndpointId) {
    Timber.d("toggleDeviceStateOnOffCluster())")
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId.toNodeId())
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return
        }
    return suspendCoroutine { continuation ->
      getOnOffClusterForDevice(connectedDevicePtr, endpointId)
          .toggle(
              object : ChipClusters.DefaultClusterCallback {
                override fun onSuccess() {
                  continuation.resume(Unit)
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "readOnOffAttribute command failure")
                  continuation.resumeWithException(ex)
                }
              },
          )
    }
  }

  suspend fun setOnOffDeviceStateOnOffCluster(
      deviceId: Long,
      isOn: Boolean,
      endpointId: EndpointId,
  ) {
    Timber.d("Setting on/off state deviceId=$deviceId isOn=$isOn endpointId=$endpointId")
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId.toNodeId())
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return
        }
    if (isOn) {
      // ON
      return suspendCoroutine { continuation ->
        getOnOffClusterForDevice(connectedDevicePtr, endpointId)
            .on(
                object : ChipClusters.DefaultClusterCallback {
                  override fun onSuccess() {
                    Timber.d("Success for setOnOffDeviceStateOnOffCluster")
                    continuation.resume(Unit)
                  }

                  override fun onError(ex: Exception) {
                    Timber.e(ex, "Failure for setOnOffDeviceStateOnOffCluster")
                    continuation.resumeWithException(ex)
                  }
                },
            )
      }
    } else {
      // OFF
      return suspendCoroutine { continuation ->
        getOnOffClusterForDevice(connectedDevicePtr, endpointId)
            .off(
                object : ChipClusters.DefaultClusterCallback {
                  override fun onSuccess() {
                    Timber.d("Success for getOnOffDeviceStateOnOffCluster")
                    continuation.resume(Unit)
                  }

                  override fun onError(ex: Exception) {
                    Timber.e(ex, "Failure for getOnOffDeviceStateOnOffCluster")
                    continuation.resumeWithException(ex)
                  }
                },
            )
      }
    }
  }

  suspend fun getDeviceStateOnOffCluster(deviceId: Long, endpointId: EndpointId): Boolean? {
    Timber.d("getDeviceStateOnOffCluster())")
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId.toNodeId())
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return null
        }
    return suspendCoroutine { continuation ->
      getOnOffClusterForDevice(connectedDevicePtr, endpointId)
          .readOnOffAttribute(
              object : ChipClusters.BooleanAttributeCallback {
                override fun onSuccess(value: Boolean) {
                  Timber.d("Read on/off attribute value=$value")
                  continuation.resume(value)
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "readOnOffAttribute command failure")
                  continuation.resumeWithException(ex)
                }
              },
          )
    }
  }

  private fun getOnOffClusterForDevice(
      devicePtr: Long,
      endpointId: EndpointId,
  ): ChipClusters.OnOffCluster {
    return ChipClusters.OnOffCluster(devicePtr, endpointId.toInt())
  }

  // -----------------------------------------------------------------------------------------------
  // LevelControlCluster functions

  suspend fun setLevelStateLevelControlCluster(deviceId: Long, level: Int, endpointId: EndpointId) {
    Timber.d("Setting level deviceId=$deviceId level=$level endpointId=$endpointId")
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId.toNodeId())
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return
        }
    return suspendCoroutine { continuation ->
      getLevelControlClusterForDevice(connectedDevicePtr, endpointId)
          .moveToLevel(
              object : ChipClusters.DefaultClusterCallback {
                override fun onSuccess() {
                  Timber.d("Success for setLevelStateLevelControlCluster")
                  continuation.resume(Unit)
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "Failure for setLevelStateLevelControlCluster")
                  continuation.resumeWithException(ex)
                }
              },
              level,
              0,
              0,
              0,
          )
    }
  }

  suspend fun getDeviceStateLevelControlCluster(deviceId: Long, endpointId: EndpointId): Int? {
    Timber.d("getDeviceStateLevelControlCluster())")
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId.toNodeId())
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return null
        }
    return suspendCoroutine { continuation ->
      getLevelControlClusterForDevice(connectedDevicePtr, endpointId)
          .readCurrentLevelAttribute(
              object : ChipClusters.LevelControlCluster.CurrentLevelAttributeCallback {
                override fun onSuccess(value: Int?) {
                  Timber.d("Read level attribute value=$value")
                  continuation.resume(value)
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "readLevelControlAttribute command failure")
                  continuation.resumeWithException(ex)
                }
              },
          )
    }
  }

  private fun getLevelControlClusterForDevice(
      devicePtr: Long,
      endpointId: EndpointId,
  ): ChipClusters.LevelControlCluster {
    return ChipClusters.LevelControlCluster(devicePtr, endpointId.toInt())
  }

  // -----------------------------------------------------------------------------------------------
  // ColorControlCluster functions

  suspend fun setColorTemperatureColorControlCluster(
      deviceId: Long,
      colorTemperature: Int,
      endpointId: EndpointId,
  ) {
    Timber.d(
        "Setting color temperature deviceId=$deviceId colorTemperature=$colorTemperature endpointId=$endpointId",
    )
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId.toNodeId())
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return
        }
    return suspendCoroutine { continuation ->
      getColorControlClusterForDevice(connectedDevicePtr, endpointId)
          .moveToColorTemperature(
              object : ChipClusters.DefaultClusterCallback {
                override fun onSuccess() {
                  Timber.d("Success for setColorTemperatureColorControlCluster")
                  continuation.resume(Unit)
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "Failure for setColorTemperatureColorControlCluster")
                  continuation.resumeWithException(ex)
                }
              },
              colorTemperature,
              0,
              0,
              0,
          )
    }
  }

  suspend fun getColorTemperatureColorControlCluster(deviceId: Long, endpointId: EndpointId): Int? {
    Timber.d("getDeviceStateColorControlCluster())")
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId.toNodeId())
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer.")
          return null
        }
    return suspendCoroutine { continuation ->
      getColorControlClusterForDevice(connectedDevicePtr, endpointId)
          .readColorTemperatureMiredsAttribute(
              object : ChipClusters.IntegerAttributeCallback {
                override fun onSuccess(value: Int) {
                  Timber.d("Read color temperature value=$value")
                  continuation.resume(value)
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "readColorTemperatureMiredsAttribute command failure")
                  continuation.resumeWithException(ex)
                }
              },
          )
    }
  }

  /**
   * Reads the AttributeList of the Color Control cluster for the given [nodeId] and [endpointId].
   * Returns the list of supported attribute IDs, or an empty list on error. Use this to check
   * whether the optional Color Temperature attribute (id 7) is present before flagging a device as
   * supporting color temperature control.
   */
  suspend fun readColorControlClusterAttributeList(
      nodeId: NodeId,
      endpointId: EndpointId,
  ): List<AttributeId> {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(nodeId)
        } catch (e: IllegalStateException) {
          Timber.e("Can't get connectedDevicePointer for readColorControlClusterAttributeList.")
          return emptyList()
        }
    return suspendCoroutine { continuation ->
      getColorControlClusterForDevice(connectedDevicePtr, endpointId)
          .readAttributeListAttribute(
              object : ChipClusters.ColorControlCluster.AttributeListAttributeCallback {
                override fun onSuccess(value: MutableList<Long>) {
                  continuation.resume(value.map { it.toAttributeId() })
                }

                override fun onError(ex: Exception) {
                  Timber.e(ex, "readColorControlClusterAttributeList failure")
                  continuation.resume(emptyList())
                }
              },
          )
    }
  }

  private fun getColorControlClusterForDevice(
      devicePtr: Long,
      endpointId: EndpointId,
  ): ChipClusters.ColorControlCluster {
    return ChipClusters.ColorControlCluster(devicePtr, endpointId.toInt())
  }

  // -----------------------------------------------------------------------------------------------
  // Administrator Commissioning Cluster (11.19)

  suspend fun openCommissioningWindowAdministratorCommissioningCluster(
      deviceId: Long,
      endpointId: EndpointId,
      timeoutSeconds: Int,
      pakeVerifier: ByteArray,
      discriminator: Int,
      iterations: Long,
      salt: ByteArray,
      timedInvokeTimeoutMs: Int,
  ) {
    Timber.d("openCommissioningWindowAdministratorCommissioningCluster())")
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(deviceId.toNodeId())
        } catch (e: IllegalStateException) {
          Timber.e(e, "Can't get connectedDevicePointer.")
          return
        }

    /*
    ChipClusters.DefaultClusterCallback var1, Integer var2, byte[] var3, Integer var4, Long var5, byte[] var6, int var7
     */
    return suspendCoroutine { continuation ->
      getAdministratorCommissioningClusterForDevice(connectedDevicePtr, endpointId)
          .openCommissioningWindow(
              object : ChipClusters.DefaultClusterCallback {
                override fun onSuccess() {
                  continuation.resume(Unit)
                }

                override fun onError(ex: java.lang.Exception?) {
                  Timber.e(
                      ex,
                      "getAdministratorCommissioningClusterForDevice.openCommissioningWindow command failure",
                  )
                  continuation.resumeWithException(ex!!)
                }
              },
              timeoutSeconds,
              pakeVerifier,
              discriminator,
              iterations,
              salt,
              timedInvokeTimeoutMs,
          )
    }
  }

  /**
   * Closes a node's commissioning window. See spec section "11.18.8.3. RevokeCommissioning
   * Command".
   *
   * @param devicePtr connected device pointer.
   */
  suspend fun closeCommissioningWindow(devicePtr: Long) {
    return suspendCoroutine { continuation ->
      val callback =
          object : ChipClusters.DefaultClusterCallback {
            override fun onSuccess() {
              Timber.d("Window is closed successfully")
              continuation.resume(Unit)
            }

            override fun onError(ex: Exception) {
              Timber.e(ex, "Failed to close window")
            }
          }
      ChipClusters.AdministratorCommissioningCluster(devicePtr, 0)
          .revokeCommissioning(callback, 100)
    }
  }

  /**
   * Checks if a device has an open commissioning window. See spec section "11.18.7. Attributes" of
   * the "Administrator Commissioning Cluster".
   *
   * @param devicePtr connected device pointer.
   * @return true if a window is open, false otherwise.
   */
  suspend fun isCommissioningWindowOpen(devicePtr: Long): Boolean {
    return suspendCoroutine { continuation ->
      val callback =
          object : ChipClusters.IntegerAttributeCallback {
            override fun onSuccess(value: Int) {
              when (value) {
                CommissioningWindowStatus.WindowNotOpen.status -> {
                  continuation.resume(false)
                }
                CommissioningWindowStatus.EnhancedWindowOpen.status,
                CommissioningWindowStatus.BasicWindowOpen.status -> {
                  continuation.resume(true)
                }
              }
            }

            override fun onError(ex: Exception) {
              Timber.e(ex, "Failed to check commissioning window status")
              continuation.resumeWithException(ex)
            }
          }

      ChipClusters.AdministratorCommissioningCluster(devicePtr, 0)
          .readWindowStatusAttribute(callback)
    }
  }

  /**
   * Reads vendor/manufacturer fields and version strings from Basic Information in a single read
   * request limited to the required attributes.
   */
  suspend fun readBasicInformationAttributes(nodeId: NodeId): BasicInformationAttributes? {
    val connectedDevicePtr =
        try {
          chipClient.getConnectedDevicePointer(nodeId)
        } catch (e: IllegalStateException) {
          Timber.e(e, "Can't get connected device pointer nodeId=$nodeId")
          return null
        }

    return try {
      suspendCoroutine { continuation ->
        val completed = AtomicBoolean(false)
        val basicInfoPaths =
            listOf(
                ChipAttributePath.newInstance(
                    ROOT_ENDPOINT_ID.toInt().toLong(),
                    Clusters.BasicInformation.ID.toLong(),
                    Clusters.BasicInformation.Attributes.VendorID.ID.toLong(),
                ),
                ChipAttributePath.newInstance(
                    ROOT_ENDPOINT_ID.toInt().toLong(),
                    Clusters.BasicInformation.ID.toLong(),
                    Clusters.BasicInformation.Attributes.VendorName.ID.toLong(),
                ),
                ChipAttributePath.newInstance(
                    ROOT_ENDPOINT_ID.toInt().toLong(),
                    Clusters.BasicInformation.ID.toLong(),
                    Clusters.BasicInformation.Attributes.ProductID.ID.toLong(),
                ),
                ChipAttributePath.newInstance(
                    ROOT_ENDPOINT_ID.toInt().toLong(),
                    Clusters.BasicInformation.ID.toLong(),
                    Clusters.BasicInformation.Attributes.ProductName.ID.toLong(),
                ),
                ChipAttributePath.newInstance(
                    ROOT_ENDPOINT_ID.toInt().toLong(),
                    Clusters.BasicInformation.ID.toLong(),
                    Clusters.BasicInformation.Attributes.HardwareVersionString.ID.toLong(),
                ),
                ChipAttributePath.newInstance(
                    ROOT_ENDPOINT_ID.toInt().toLong(),
                    Clusters.BasicInformation.ID.toLong(),
                    Clusters.BasicInformation.Attributes.SoftwareVersionString.ID.toLong(),
                ),
                ChipAttributePath.newInstance(
                    ROOT_ENDPOINT_ID.toInt().toLong(),
                    Clusters.BasicInformation.ID.toLong(),
                    Clusters.BasicInformation.Attributes.NodeLabel.ID.toLong(),
                ),
            )

        chipClient.chipDeviceController.readPath(
            object : ReportCallback {
              override fun onError(
                  attributePath: ChipAttributePath?,
                  eventPath: ChipEventPath?,
                  ex: Exception,
              ) {
                if (completed.compareAndSet(false, true)) {
                  continuation.resumeWithException(ex)
                }
              }

              override fun onReport(nodeState: NodeState) {
                if (completed.compareAndSet(false, true)) {
                  continuation.resume(extractBasicInformationAttributes(nodeState))
                }
              }
            },
            connectedDevicePtr,
            basicInfoPaths,
            emptyList(),
            false,
        )
      }
    } catch (e: Exception) {
      Timber.e(e, "readBasicInformationAttributes failed")
      null
    }
  }

  private fun extractBasicInformationAttributes(nodeState: NodeState): BasicInformationAttributes {
    val clusterState =
        nodeState
            .getEndpointState(ROOT_ENDPOINT_ID.toInt())
            ?.getClusterState(Clusters.BasicInformation.ID.toLong())
            ?: return BasicInformationAttributes()

    val vendorId =
        clusterState
            .getAttributeState(Clusters.BasicInformation.Attributes.VendorID.ID.toLong())
            ?.value
            .asInt()
            ?.toVendorId()
    val vendorName =
        clusterState
            .getAttributeState(Clusters.BasicInformation.Attributes.VendorName.ID.toLong())
            ?.value
            .asString()
    val productId =
        clusterState
            .getAttributeState(Clusters.BasicInformation.Attributes.ProductID.ID.toLong())
            ?.value
            .asInt()
            ?.toProductId()
    val productName =
        clusterState
            .getAttributeState(Clusters.BasicInformation.Attributes.ProductName.ID.toLong())
            ?.value
            .asString()
    val hardwareVersion =
        clusterState
            .getAttributeState(
                Clusters.BasicInformation.Attributes.HardwareVersionString.ID.toLong(),
            )
            ?.value
            .asString()
    val softwareVersion =
        clusterState
            .getAttributeState(
                Clusters.BasicInformation.Attributes.SoftwareVersionString.ID.toLong(),
            )
            ?.value
            .asString()
    val nodeLabel =
        clusterState
            .getAttributeState(Clusters.BasicInformation.Attributes.NodeLabel.ID.toLong())
            ?.value
            .asString()

    return BasicInformationAttributes(
        vendorId = vendorId,
        vendorName = vendorName,
        productId = productId,
        productName = productName,
        hardwareVersion = hardwareVersion,
        softwareVersion = softwareVersion,
        nodeLabel = nodeLabel,
    )
  }

  private fun Any?.asInt(): Int? =
      when (this) {
        is Number -> toInt()
        is String -> toIntOrNull() ?: removePrefix("0x").toIntOrNull(16)
        else -> null
      }

  private fun Any?.asLong(): Long? =
      when (this) {
        is Number -> toLong()
        is String -> toLongOrNull() ?: removePrefix("0x").toLongOrNull(16)
        else -> null
      }

  private fun Any?.asString(): String? =
      when (this) {
        is String -> this
        else -> null
      }

  private fun Any?.asDisplayString(): String? =
      when (this) {
        is String -> this
        is Number -> toString()
        else -> null
      }

  private fun getAdministratorCommissioningClusterForDevice(
      devicePtr: Long,
      endpointId: EndpointId,
  ): ChipClusters.AdministratorCommissioningCluster {
    return ChipClusters.AdministratorCommissioningCluster(devicePtr, endpointId.toInt())
  }
}
