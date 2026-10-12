// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Google Play Services availability and store navigation. */
interface PlayServices {
  fun isUpdateRequired(): Boolean

  fun openInStore()
}

class AndroidPlayServices @Inject constructor(@param:ApplicationContext private val context: Context) :
  PlayServices {

  override fun isUpdateRequired(): Boolean {
    val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    return status != ConnectionResult.SUCCESS || version() < MIN_VERSION
  }

  private fun version(): Long = runCatching {
    PackageInfoCompat.getLongVersionCode(
      context.packageManager.getPackageInfo(
        GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE,
        0,
      ),
    )
  }
    .getOrDefault(0L)

  override fun openInStore() {
    val nativeIntent =
      Intent(Intent.ACTION_VIEW).apply {
        data = "market://details?id=$PACKAGE".toUri()
        setPackage("com.android.vending")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    try {
      context.startActivity(nativeIntent)
    } catch (e: Exception) {
      val webIntent =
        Intent(Intent.ACTION_VIEW).apply {
          data = "https://play.google.com/store/apps/details?id=$PACKAGE".toUri()
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(webIntent)
    }
  }

  private companion object {
    const val PACKAGE = "com.google.android.gms"
    const val MIN_VERSION = 223615000L
  }
}
