// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.aether.android.success
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private val SUCCESS_HIGHLIGHT_DURATION = 2.seconds

internal enum class FieldHighlight {
  STANDARD,
  EDITED,
  SUCCESS,
}

@Composable
internal fun highlightedFieldColors(highlight: FieldHighlight): TextFieldColors {
  val successColor = MaterialTheme.colorScheme.success
  val editedColor = MaterialTheme.colorScheme.error
  val standardFocused = MaterialTheme.colorScheme.primary
  val standardUnfocused = MaterialTheme.colorScheme.outline
  val durationMs = if (highlight == FieldHighlight.STANDARD) 500 else 150
  val animSpec = tween<Color>(durationMillis = durationMs)
  val targetFocused =
    when (highlight) {
      FieldHighlight.SUCCESS -> successColor
      FieldHighlight.EDITED -> editedColor
      FieldHighlight.STANDARD -> standardFocused
    }
  val targetUnfocused =
    when (highlight) {
      FieldHighlight.SUCCESS -> successColor
      FieldHighlight.EDITED -> editedColor
      FieldHighlight.STANDARD -> standardUnfocused
    }
  val focusedColor by
    animateColorAsState(
      targetValue = targetFocused,
      animationSpec = animSpec,
      label = "focusedBorderColor",
    )
  val unfocusedColor by
    animateColorAsState(
      targetValue = targetUnfocused,
      animationSpec = animSpec,
      label = "unfocusedBorderColor",
    )
  return OutlinedTextFieldDefaults.colors(
    focusedBorderColor = focusedColor,
    unfocusedBorderColor = unfocusedColor,
  )
}

internal data class HighlightedOutlinedComboOption(
  val value: String,
  val label: String,
)

/**
 * An [OutlinedTextField] that visually signals interaction results via border color transitions:
 * - Turns **red** after the user has made local edits (resets on success or navigation).
 * - Turns **green** (then fades back to the standard color) when [successTrigger] increments,
 *   indicating that the last operation succeeded.
 *
 * @param successTrigger Increment this value to trigger the green-success animation.
 * @param resetKey When this value changes all internal highlight state is reset (e.g. when
 *   navigating to a different attribute or command).
 */
@Composable
fun HighlightedOutlinedTextField(
  value: String,
  onValueChange: (String) -> Unit,
  successTrigger: Int,
  resetKey: Any,
  modifier: Modifier = Modifier,
  label: @Composable (() -> Unit)? = null,
  enabled: Boolean = true,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
  var lastSeen by remember(resetKey) { mutableIntStateOf(successTrigger) }
  var highlight by remember(resetKey) { mutableStateOf(FieldHighlight.STANDARD) }

  LaunchedEffect(successTrigger) {
    if (successTrigger > lastSeen) {
      lastSeen = successTrigger
      highlight = FieldHighlight.SUCCESS
      delay(SUCCESS_HIGHLIGHT_DURATION)
      if (highlight == FieldHighlight.SUCCESS) highlight = FieldHighlight.STANDARD
    }
  }

  OutlinedTextField(
    value = value,
    onValueChange = {
      onValueChange(it)
      highlight = FieldHighlight.EDITED
    },
    modifier = modifier,
    label = label,
    enabled = enabled,
    keyboardOptions = keyboardOptions,
    colors = highlightedFieldColors(highlight),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HighlightedOutlinedComboField(
  value: String,
  onValueChange: (String) -> Unit,
  options: List<HighlightedOutlinedComboOption>,
  successTrigger: Int,
  resetKey: Any,
  modifier: Modifier = Modifier,
  label: @Composable (() -> Unit)? = null,
  enabled: Boolean = true,
) {
  var expanded by remember(resetKey) { mutableStateOf(false) }
  var lastSeen by remember(resetKey) { mutableIntStateOf(successTrigger) }
  var highlight by remember(resetKey) { mutableStateOf(FieldHighlight.STANDARD) }

  LaunchedEffect(successTrigger) {
    if (successTrigger > lastSeen) {
      lastSeen = successTrigger
      highlight = FieldHighlight.SUCCESS
      delay(SUCCESS_HIGHLIGHT_DURATION)
      if (highlight == FieldHighlight.SUCCESS) highlight = FieldHighlight.STANDARD
    }
  }

  val displayedValue = options.firstOrNull { it.value == value }?.label ?: value

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { if (enabled) expanded = it },
  ) {
    OutlinedTextField(
      value = displayedValue,
      onValueChange = {},
      readOnly = true,
      enabled = enabled,
      modifier =
        modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
      label = label,
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      colors = highlightedFieldColors(highlight),
    )

    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
    ) {
      options.forEach { option ->
        DropdownMenuItem(
          text = { Text(option.label) },
          onClick = {
            onValueChange(option.value)
            highlight = FieldHighlight.EDITED
            expanded = false
          },
        )
      }
    }
  }
}
