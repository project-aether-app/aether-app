// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.chip

import java.util.Locale
import java.util.concurrent.TimeoutException

fun Throwable.isCommunicationTimeoutError(): Boolean {
  var current: Throwable? = this
  while (current != null) {
    if (current is TimeoutException) {
      return true
    }
    val className = current::class.java.simpleName.lowercase(Locale.US)
    if (className.contains("timeout")) {
      return true
    }
    val message = current.message?.lowercase(Locale.US).orEmpty()
    if (
      message.contains("timeout") ||
        message.contains("timed out") ||
        message.contains("time out") ||
        message.contains("deadline")
    ) {
      return true
    }
    current = current.cause
  }
  return false
}
