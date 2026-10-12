// SPDX-FileCopyrightText: 2023 Google LLC
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.scanner.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.aether.android.screens.scanner.MatterBeaconInject
import io.aether.android.screens.scanner.MatterBeaconProducer

/**
 * If this is instantiated, then all permissions have been cleared and Bluetooth is enabled. See
 * [SettingsDeveloperUtilitiesNestedFragment].
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ModuleBle {
  @Binds
  @IntoSet
  abstract fun bindsMatterBeaconProducer(impl: MatterBeaconProducerBle): MatterBeaconProducer

  companion object {
    @Provides
    @MatterBeaconInject
    fun providesBluetoothLeScanner(@ApplicationContext context: Context): BluetoothLeScanner? {
      val bluetoothAdapter =
        (context.getSystemService(BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)
          ?.adapter
      return bluetoothAdapter?.takeIf { it.state == BluetoothAdapter.STATE_ON }?.bluetoothLeScanner
    }
  }
}
