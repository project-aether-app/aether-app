// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import io.aether.android.screens.common.DialogInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Application-level error notifier. Singletons that need to surface error dialogs regardless of
 * which screen the user is on can call [notify];
 * [io.aether.android.screens.settings.DeveloperUtilitiesViewModel] collects from [errors] and
 * routes them to the global dialog in [AppLayout].
 */
@Singleton
class AppErrorNotifier @Inject constructor() {
  private val _errors =
    MutableSharedFlow<DialogInfo>(
      extraBufferCapacity = 16,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
  val errors: SharedFlow<DialogInfo> = _errors.asSharedFlow()

  fun notify(error: DialogInfo) {
    _errors.tryEmit(error)
  }
}
