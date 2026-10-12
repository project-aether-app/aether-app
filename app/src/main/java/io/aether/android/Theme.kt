// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val ColorScheme.success: Color
  @Composable get() = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF4CAF50)

data class Spacing(
  val paddingNormal: Dp = 16.dp,
  val paddingSmall: Dp = 8.dp,
  val paddingTiny: Dp = 4.dp,
  val paddingSurfaceContent: Dp = 12.dp,
  val roundedCorner: Dp = 12.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

val MaterialTheme.spacing: Spacing
  @Composable @ReadOnlyComposable get() = LocalSpacing.current

@Composable
fun AetherTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val colorScheme =
    remember(darkTheme, context) {
      when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
          if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
      }
    }
  CompositionLocalProvider(LocalSpacing provides Spacing()) {
    MaterialTheme(colorScheme = colorScheme, content = content)
  }
}
