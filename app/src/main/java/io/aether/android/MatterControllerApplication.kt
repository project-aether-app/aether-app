// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MatterControllerApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    Timber.plant(
        object : Timber.DebugTree() {
          override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            super.log(priority, "${APP_NAME}-$tag", message, t)
          }
        },
    )
  }
}
