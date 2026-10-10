// SPDX-FileCopyrightText: 2022 Google LLC
// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import io.aether.android.MatterFabricState
import java.io.InputStream
import java.io.OutputStream

/** Storage serializer for the persisted Matter fabric. */
object FabricStateSerializer : Serializer<MatterFabricState> {
  override val defaultValue: MatterFabricState = MatterFabricState.getDefaultInstance()

  override suspend fun readFrom(input: InputStream): MatterFabricState =
      try {
        MatterFabricState.parseFrom(input)
      } catch (exception: InvalidProtocolBufferException) {
        throw CorruptionException("Cannot read proto.", exception)
      }

  override suspend fun writeTo(t: MatterFabricState, output: OutputStream) = t.writeTo(output)
}

val Context.fabricDataStore: DataStore<MatterFabricState> by
    dataStore(
        fileName = "devices_state__store.proto",
        serializer = FabricStateSerializer,
        produceMigrations = { emptyList() },
    )
