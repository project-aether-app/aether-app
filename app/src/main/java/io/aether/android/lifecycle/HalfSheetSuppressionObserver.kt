// SPDX-FileCopyrightText: 2023 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.lifecycle

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.home.matter.Matter
import dagger.hilt.android.qualifiers.ApplicationContext
import io.aether.android.data.UserPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class HalfSheetSuppressionObserver
@Inject
internal constructor(
  @param:ApplicationContext private val context: Context,
  private val preferencesRepository: UserPreferencesRepository,
) : AppLifecycleObserver {

  private val scope = CoroutineScope(Dispatchers.Main)

  /**
   * Handle user's preference for suppressing HalfSheet Notifications (proactive commissionable
   * discovery notifications for Matter devices).
   * https://developers.home.google.com/reference/com/google/android/gms/home/matter/commissioning/CommissioningClient#suppressHalfSheetNotification().
   */
  override fun onStart(owner: LifecycleOwner) {
    Timber.d("onStart()")
    scope.launch {
      val suppressHalfSheetNotification =
        !preferencesRepository.shouldShowGpsMatterDiscoveryNotification()
      if (suppressHalfSheetNotification) {
        try {
          Matter.getCommissioningClient(context).suppressHalfSheetNotification().await()
          Timber.d("Half-sheet notification suppressed")
        } catch (e: Exception) {
          Timber.e(e, "Error on suppressHalfSheetNotification")
        }
      }
    }
  }

  override fun onStop(owner: LifecycleOwner) {
    scope.cancel()
  }
}
