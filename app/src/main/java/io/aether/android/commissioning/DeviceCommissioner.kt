// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.commissioning

import android.content.Intent
import android.content.IntentSender
import io.aether.android.matter.NodeId
import io.aether.android.matter.ProductId
import io.aether.android.matter.VendorId

data class CommissionedDevice(val nodeId: NodeId, val vendorId: VendorId, val productId: ProductId)

class CommissioningWindowExpiringException(val secondsLeft: Long) :
    Exception("Commissioning window expires in $secondsLeft seconds")

/** Starts Matter commissioning through an external commissioner (Google Play Services). */
interface DeviceCommissioner {
  /** Returns the sender of the activity driving the commissioning UI. */
  suspend fun createIntentSender(): IntentSender

  /**
   * Same as [createIntentSender] for a multi-admin [intent].
   *
   * @throws CommissioningWindowExpiringException if the window is too short to complete.
   */
  suspend fun createMultiAdminIntentSender(intent: Intent): IntentSender

  /** Parses the commissioning activity result; null if it has no commissioned device. */
  fun parseResult(resultCode: Int, data: Intent?): CommissionedDevice?
}
