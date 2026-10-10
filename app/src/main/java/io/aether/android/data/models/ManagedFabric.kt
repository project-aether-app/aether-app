// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data.models

import io.aether.android.matter.FabricId
import io.aether.android.matter.NodeId
import io.aether.android.matter.VendorId

data class ManagedFabric(
    val fabricIndex: Int,
    val rootPublicKey: ByteArray,
    val vendorId: VendorId,
    val fabricId: FabricId,
    val nodeId: NodeId,
    val label: String = "",
    val isCurrentFabric: Boolean = false,
)
