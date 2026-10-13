// SPDX-FileCopyrightText: 2024 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.aether.android.lifecycle.AppLifecycleObserver
import javax.inject.Inject
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import timber.log.Timber

/** Main Activity for Æther Matter Controller. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject internal lateinit var lifecycleObservers: Set<@JvmSuppressWildcards AppLifecycleObserver>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    initContextDependentConstants()
    Timber.d("onCreate()")

    // See package "io.aether.android.lifecycle" for all the lifecycle observers
    // defined for the application.
    Timber.d("Lifecycle observers=$lifecycleObservers")
    lifecycleObservers.forEach { lifecycle.addObserver(it) }

    // Useful to see which preferences are set under the hood by Matter libraries.
    displayPreferences(this)

    setContent {
      AetherTheme {
        ProvidePreferenceLocals {
          val navController = rememberNavController()
          AppLayout(navController)
        }
      }
    }
  }

  /**
   * Constants we access from Utils, but that depend on the Activity context to be set to their
   * values.
   */
  private fun initContextDependentConstants() {
    // versionName is set in build.gradle.
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    VERSION_NAME = packageInfo.versionName ?: "v?.?"
    APP_NAME = getString(R.string.app_name)
    packageInfo.packageName
    Timber.i(
      "====================================\n" +
        "Version ${VERSION_NAME}\n" +
        "App     ${APP_NAME}\n" +
        "====================================",
    )
  }
}
