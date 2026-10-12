// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

// Module-level build file.
// This file t lets you configure build settings for the specific module it is located in.
// Configuring these build settings lets you provide custom packaging options,
// such as additional build types and product flavors, and override settings in the
// main/ app manifest or top-level build script.

import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.ksp)
  alias(libs.plugins.google.protobuf)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ktfmt.plugin)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.paparazzi)
}

ktfmt { googleStyle() }

/**
 * Locate (and possibly download) a JDK used to build your kotlin source code. This also acts as a
 * default for sourceCompatibility, targetCompatibility and jvmTarget. Note that this does not
 * affect which JDK is used to run the Gradle build itself, and does not need to take into account
 * the JDK version required by Gradle plugins (such as the Android Gradle Plugin)
 */
kotlin { jvmToolchain(25) }

/** The android block is where you configure all your Android-specific build options. */
android {
  /** The app's namespace. Used primarily to access app resources. */
  namespace = "io.aether.android"

  /**
   * compileSdk specifies the Android API level Gradle should use to compile your app. This means
   * your app can use the API features included in this API level and lower.
   */
  compileSdk = 37

  /**
   * The defaultConfig block encapsulates default settings and entries for all build variants and
   * can override some attributes in main/AndroidManifest.xml dynamically from the build system. You
   * can configure product flavors to override these values for different versions of your app.
   */
  defaultConfig {
    // Uniquely identifies the package for publishing.
    applicationId = "io.aether.android"

    // Defines the minimum API level required to run the app.
    minSdk = 27

    // Specifies the API level used to test the app.
    targetSdk = 37

    // Defines the version number of your app.
    versionCode = 1

    // Defines a user-friendly version name for your app.
    versionName = "1.0.0"
  }

  signingConfigs {
    create("persistentDebug") {
      storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
      signingConfig = signingConfigs.getByName(findProperty("signingConfig") as? String ?: "debug")
    }
  }

  buildFeatures { compose = true }

  compileOptions { isCoreLibraryDesugaringEnabled = true }

  // Gradle will use the NDK that"s associated by default with its plugin.
  // If it"s not available (from the SDK Manager), then stripping the .so"s will not happen
  // (message: Unable to strip library...)
  // See https://github.com/google-home/sample-app-for-matter-android/issues/82.
  // https://developer.android.com/studio/projects/install-ndk
  // If you want to use a specific NDK, then uncomment the statement below with the proper
  // NDK version.
  // ndkVersion = "25.2.9519653"

  splits {
    abi {
      val selectedAbi = findProperty("selectedAbi") as? String
      isEnable = selectedAbi != null
      if (selectedAbi != null) {
        reset()
        include(selectedAbi)
      }
    }
  }
}

dependencies {
  // Connected Home
  implementation(libs.play.services.base)
  implementation(libs.play.services.home)

  // Core library desugaring (required by play-services-threadnetwork)
  coreLibraryDesugaring(libs.desugar.jdk.libs)

  // Matter Android Demo SDK
  implementation(libs.matter.android.demo.sdk)

  // Thread Network
  implementation(libs.play.services.threadnetwork)
  // Thread QR Code Scanning
  implementation(libs.code.scanner)
  // Thread QR Code Generation
  implementation(libs.zxing)

  // AndroidX
  implementation(libs.appcompat)
  implementation(libs.core.ktx)
  implementation(libs.preference)

  // Compose
  // Bill of Materials: https://developer.android.com/jetpack/compose/bom
  // The Compose Bill of Materials (BOM) lets you manage all of your Compose library versions by
  // specifying only the BOM’s version. The BOM itself has links to the stable versions of the
  // different Compose libraries, in such a way that they work well together. When using the BOM
  // in your app, you don't need to add any version to the Compose library dependencies
  // themselves. When you update the BOM version, all the libraries that you're using are
  // automatically updated to their new versions.
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.foundation.layout)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.runtime.livedata)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.activity.compose)

  // Navigation
  implementation(libs.navigation.compose)

  // Lifecycle
  implementation(libs.lifecycle.livedata.ktx)
  implementation(libs.lifecycle.runtime.ktx)
  implementation(libs.lifecycle.viewmodel.ktx)

  // Datastore
  implementation(libs.datastore)
  implementation(libs.datastore.core)
  implementation(libs.protobuf.javalite)

  // Hilt
  // https://dagger.dev/hilt/gradle-setup
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)

  // Task.await()
  implementation(libs.kotlinx.coroutines.play.services)

  // Preferences/Settings for Jetpack Compose
  implementation(libs.zhanghai.compose.preference)

  // Other
  implementation(libs.material)
  implementation(libs.timber)
  // Needed for using BaseEncoding class
  implementation(libs.guava)
}

// Issue with androidx.test.espresso:espresso-contrib
// https://github.com/android/android-test/issues/999
configurations.configureEach { exclude(group = "com.google.protobuf", module = "protobuf-lite") }

tasks.register<KtfmtCheckTask>("ktfmtCheckSources") {
  source = project.fileTree(projectDir) { include("src/**/*.kt") }
}

tasks.register<KtfmtFormatTask>("ktfmtFormatSources") {
  source = project.fileTree(projectDir) { include("src/**/*.kt") }
}

tasks.named("ktfmtCheck") { dependsOn("ktfmtCheckSources") }

tasks.named("ktfmtFormat") { dependsOn("ktfmtFormatSources") }

protobuf {
  protoc {
    // Choose the right protoc binary for the current OS and CPU architecture
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val protocDepSuffix =
        if (osName.contains("mac")) {
          if (osArch == "aarch64" || osArch == "arm64") ":osx-aarch_64" else ":osx-x86_64"
        } else {
          ""
        }
    artifact = "com.google.protobuf:protoc:4.35.0" + protocDepSuffix
  }

  // Generates the java Protobuf-lite code for the Protobufs in this project. See
  // https://github.com/google/protobuf-gradle-plugin#customizing-protobuf-compilation
  // for more information.
  generateProtoTasks {
    all().forEach { task -> task.builtins { create("java") { option("lite") } } }
  }
}
