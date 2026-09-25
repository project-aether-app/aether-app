// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

// This file typically defines the common versions of plugins used by modules in the project.
plugins {
  /**
   * Use `apply false` in the top-level build.gradle file to add a Gradle plugin as a build
   * dependency but not apply it to the current (root) project. Don't use `apply false` in
   * sub-projects. For more information, see Applying external plugins with same version to
   * subprojects.
   */

  // Gradle Plugin
  alias(libs.plugins.android.application) apply false

  // Kotlin plugin
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.ksp) apply false

  // Hilt
  alias(libs.plugins.hilt) apply false

  // Paparazzi (screen offline rendering)
  alias(libs.plugins.paparazzi) apply false
}
