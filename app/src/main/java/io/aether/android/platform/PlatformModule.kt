// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.platform

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.aether.android.commissioning.DeviceCommissioner
import io.aether.android.commissioning.GmsDeviceCommissioner

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {
  @Binds abstract fun bindsPlayServices(impl: AndroidPlayServices): PlayServices

  @Binds abstract fun bindsDeviceCommissioner(impl: GmsDeviceCommissioner): DeviceCommissioner
}
