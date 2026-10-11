// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.common

import android.text.method.LinkMovementMethod
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.google.android.material.textview.MaterialTextView
import io.aether.android.R

data class DialogInfo(
    @field:StringRes val titleRes: Int? = null,
    val title: String? = null,
    @field:StringRes val messageRes: Int? = null,
    val messageArgs: List<Any> = emptyList(),
    val message: String? = null,
    val showConfirmButton: Boolean = true,
)

@Composable
fun MsgAlertDialog(dialogInfo: DialogInfo, onDismissMsgAlertDialog: () -> Unit) {
  val resolvedTitle = dialogInfo.titleRes?.let { stringResource(it) } ?: dialogInfo.title
  val resolvedMessage =
      dialogInfo.messageRes?.let { stringResource(it, *dialogInfo.messageArgs.toTypedArray()) }
          ?: dialogInfo.message
  AlertDialog(
      title = {
        if (!resolvedTitle.isNullOrEmpty()) {
          Text(resolvedTitle)
        }
      },
      text = {
        if (!resolvedMessage.isNullOrEmpty()) {
          Text(resolvedMessage)
        }
      },
      confirmButton = {
        if (dialogInfo.showConfirmButton) {
          TextButton(onClick = onDismissMsgAlertDialog) { Text(stringResource(R.string.ok)) }
        }
      },
      onDismissRequest = {},
      dismissButton = {},
  )
}

@Composable
fun HtmlInfoDialog(title: String, htmlInfo: String, onClick: () -> Unit) {
  val htmlText = HtmlCompat.fromHtml(htmlInfo, HtmlCompat.FROM_HTML_MODE_LEGACY)
  AlertDialog(
      title = { Text(text = title) },
      text = {
        // See https://developer.android.com/codelabs/jetpack-compose-migration
        AndroidView(
            update = { it.text = htmlText },
            factory = {
              MaterialTextView(it).apply { movementMethod = LinkMovementMethod.getInstance() }
            },
        )
      },
      confirmButton = { TextButton(onClick = onClick) { Text(stringResource(R.string.ok)) } },
      onDismissRequest = {},
      dismissButton = {},
  )
}
