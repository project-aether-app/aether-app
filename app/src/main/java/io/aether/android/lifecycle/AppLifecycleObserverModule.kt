// SPDX-FileCopyrightText: 2023 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.lifecycle

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(ActivityComponent::class)
abstract class AppLifecycleObserverModule {
  @Binds
  @IntoSet
  abstract fun bindsHalfSheetSuppressionObserver(
    impl: HalfSheetSuppressionObserver,
  ): AppLifecycleObserver
}
