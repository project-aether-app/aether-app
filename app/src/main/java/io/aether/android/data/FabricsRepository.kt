// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data

import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipStructs
import chip.devicecontroller.ReportCallback
import chip.devicecontroller.model.ChipAttributePath
import chip.devicecontroller.model.ChipEventPath
import chip.devicecontroller.model.NodeState
import io.aether.android.chip.ChipClient
import io.aether.android.data.models.ManagedFabric
import io.aether.android.matter.Clusters
import io.aether.android.matter.NodeId
import io.aether.android.matter.ROOT_ENDPOINT_ID
import io.aether.android.matter.toFabricId
import io.aether.android.matter.toNodeId
import io.aether.android.matter.toVendorId
import javax.inject.Inject
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

class FabricsRepository @Inject constructor(private val chipClient: ChipClient) {

  @OptIn(ExperimentalAtomicApi::class)
  suspend fun readManagedFabrics(nodeId: NodeId): List<ManagedFabric> = runCatching {
    val completed = AtomicBoolean(false)
    val devicePtr = chipClient.getConnectedDevicePointer(nodeId)
    val currentIdx = getCurrentFabricIndex(nodeId)
    suspendCancellableCoroutine { continuation ->
      val readPaths =
        listOf(
          ChipAttributePath.newInstance(
            ROOT_ENDPOINT_ID.toLong(),
            Clusters.OperationalCredentials.ID.toLong(),
            Clusters.OperationalCredentials.Attributes.Fabrics.ID.toLong(),
          ),
        )
      chipClient.chipDeviceController.readPath(
        object : ReportCallback {
          override fun onError(
            attributePath: ChipAttributePath?,
            eventPath: ChipEventPath?,
            ex: Exception,
          ) {
            if (!completed.compareAndSet(expectedValue = false, newValue = true)) return
            continuation.resumeWithException(ex)
          }

          override fun onReport(nodeState: NodeState) {
            if (!completed.compareAndSet(expectedValue = false, newValue = true)) return
            continuation.resume(extractManagedFabrics(nodeState, currentIdx))
          }
        },
        devicePtr,
        readPaths,
        null,
        false,
      )
    }
  }
    .onFailure { e -> Timber.e(e, "Failed to read fabrics for nodeId=$nodeId") }
    .getOrThrow()

  private fun extractManagedFabrics(nodeState: NodeState, currentIdx: Int): List<ManagedFabric> {
    return nodeState
      .getEndpointState(ROOT_ENDPOINT_ID.toInt())
      ?.getClusterState(Clusters.OperationalCredentials.ID.toLong())
      ?.getAttributeState(Clusters.OperationalCredentials.Attributes.Fabrics.ID.toLong())
      ?.value
      .let { it as? List<*> }
      ?.filterIsInstance<ChipStructs.OperationalCredentialsClusterFabricDescriptorStruct>()
      ?.sortedBy { it.fabricIndex }
      ?.map { struct ->
        ManagedFabric(
          fabricIndex = struct.fabricIndex,
          rootPublicKey = struct.rootPublicKey.toList(),
          vendorId = struct.vendorID.toVendorId(),
          fabricId = struct.fabricID.toFabricId(),
          nodeId = struct.nodeID.toNodeId(),
          label = struct.label,
          isCurrentFabric = struct.fabricIndex == currentIdx,
        )
      } ?: emptyList()
  }

  @OptIn(ExperimentalAtomicApi::class)
  suspend fun removeFabric(nodeId: NodeId, fabricIndex: Int) = runCatching {
    Timber.d("Removing fabricIndex=$fabricIndex for nodeId=$nodeId")
    val completed = AtomicBoolean(false)
    val devicePtr = chipClient.getConnectedDevicePointer(nodeId)
    suspendCancellableCoroutine { continuation ->
      val cluster = ChipClusters.OperationalCredentialsCluster(devicePtr, ROOT_ENDPOINT_ID.toInt())
      cluster.removeFabric(
        object : ChipClusters.OperationalCredentialsCluster.NOCResponseCallback {
          override fun onSuccess(
            statusCode: Int,
            fabricIndex: java.util.Optional<Int>,
            debugText: java.util.Optional<String>,
          ) {
            if (!completed.compareAndSet(expectedValue = false, newValue = true)) return
            if (statusCode == 0) {
              continuation.resume(Unit)
            } else {
              val msg = debugText.orElse("Unknown error")
              continuation.resumeWithException(
                IllegalStateException("removeFabric status $statusCode: $msg"),
              )
            }
          }

          override fun onError(ex: Exception) {
            if (!completed.compareAndSet(expectedValue = false, newValue = true)) return
            continuation.resumeWithException(ex)
          }
        },
        fabricIndex,
        500, // Wait for 500ms to allow the device to process the removal.
      )
    }
  }
    .onFailure { e -> Timber.e(e, "Failed to remove fabricIndex=$fabricIndex for nodeId=$nodeId") }
    .getOrNull()

  suspend fun getCurrentFabricIndex(nodeId: NodeId): Int {
    return readCurrentFabricIndexAttribute(nodeId)
      ?: chipClient.chipDeviceController.getFabricIndex()
  }

  @OptIn(ExperimentalAtomicApi::class)
  private suspend fun readCurrentFabricIndexAttribute(nodeId: NodeId): Int? = runCatching {
    val completed = AtomicBoolean(false)
    val devicePtr = chipClient.getConnectedDevicePointer(nodeId)
    suspendCancellableCoroutine { continuation ->
      val readPaths =
        listOf(
          ChipAttributePath.newInstance(
            ROOT_ENDPOINT_ID.toLong(),
            Clusters.OperationalCredentials.ID.toLong(),
            Clusters.OperationalCredentials.Attributes.CurrentFabricIndex.ID.toLong(),
          ),
        )
      chipClient.chipDeviceController.readPath(
        object : ReportCallback {
          override fun onError(
            path: ChipAttributePath?,
            event: ChipEventPath?,
            ex: Exception,
          ) {
            if (!completed.compareAndSet(expectedValue = false, newValue = true)) return
            continuation.resumeWithException(ex)
          }

          override fun onReport(nodeState: NodeState) {
            if (!completed.compareAndSet(expectedValue = false, newValue = true)) return
            continuation.resume(extractCurrentFabricIndex(nodeState))
          }
        },
        devicePtr,
        readPaths,
        null,
        false,
      )
    }
  }
    .onFailure { e -> Timber.e(e, "Failed to read current fabric index for nodeId=$nodeId") }
    .getOrNull()

  private fun extractCurrentFabricIndex(nodeState: NodeState): Int? {
    return nodeState
      .getEndpointState(ROOT_ENDPOINT_ID.toInt())
      ?.getClusterState(Clusters.OperationalCredentials.ID.toLong())
      ?.getAttributeState(Clusters.OperationalCredentials.Attributes.CurrentFabricIndex.ID.toLong())
      ?.value
      .let { it as? Number }
      ?.toInt()
  }
}
