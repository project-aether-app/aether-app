// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.aether.android.R
import io.aether.android.data.models.SoftwareDiagnosticsData
import io.aether.android.data.models.ThreadMetrics

@Composable
fun SoftwareDiagnostics(data: SoftwareDiagnosticsData) {
  DiagnosticsSection(stringResource(R.string.device_diagnostics_section_memory)) {
    val used = data.currentHeapUsed ?: 0u
    val free = data.currentHeapFree ?: 0u
    val total = used + free
    if (total > 0u) {
      DiagnosticsInfoRow(stringResource(R.string.device_diagnostics_label_current_heap_usage)) {
        val progress = used.toFloat() / total.toFloat()
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color =
                if (progress > 0.9f) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(
              stringResource(
                  R.string.device_diagnostics_current_heap_used,
                  (used / 1024u).toString(),
              ),
          )
          Text(
              stringResource(
                  R.string.device_diagnostics_current_heap_free,
                  (free / 1024u).toString(),
              ),
          )
        }
      }
    }
    data.currentHeapHighWatermark?.let {
      DiagnosticsInfoRow(
          stringResource(R.string.device_diagnostics_label_current_heap_high_watermark),
      ) {
        Text(
            stringResource(
                R.string.device_diagnostics_current_heap_high_watermark,
                (it / 1024u).toString(),
            ),
        )
      }
    }
  }
  if (data.threadMetrics.isNotEmpty()) {
    val maxStackSizeAcrossThreads = data.threadMetrics.maxOfOrNull { it.stackSize ?: 0u } ?: 0u
    DiagnosticsSection(stringResource(R.string.device_diagnostics_section_threads)) {
      data.threadMetrics
          .sortedBy { it.id }
          .forEach { SoftwareDiagnosticsThread(it, maxStackSizeAcrossThreads) }
    }
  }
}

@Composable
private fun SoftwareDiagnosticsThread(thread: ThreadMetrics, maxStackSizeAcrossThreads: UInt) {
  DiagnosticsInfoRow(
      stringResource(
          R.string.device_diagnostics_thread_label,
          thread.name ?: stringResource(R.string.device_diagnostics_thread_unnamed),
          thread.id.toString(),
      ),
  ) {
    if (thread.stackSize != null && thread.stackFreeCurrent != null) {
      val used = (thread.stackSize - thread.stackFreeCurrent).coerceAtLeast(0u)
      val progress = used.toFloat() / thread.stackSize.toFloat()
      LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier.fillMaxWidth(),
          color =
              if (progress > 0.9f) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.primary,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(
                R.string.device_diagnostics_thread_stack_used,
                (used / 1024u).toString(),
            ),
        )
        Text(
            stringResource(
                R.string.device_diagnostics_thread_stack_free,
                (thread.stackFreeCurrent / 1024u).toString(),
            ),
        )
      }
    }
  }
}
