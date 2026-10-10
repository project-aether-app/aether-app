// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android

import io.aether.android.data.models.Endpoint
import io.aether.android.matter.Devices

/** Returns true if the device exposes a Level Control (dimmable) cluster. */
fun supportsLevelControl(endpoint: Endpoint): Boolean {
  val primaryType = endpoint.deviceTypeId.toLong()
  return endpoint.supportsLevelControl ||
      primaryType == Devices.DimmableLight.ID.toLong() ||
      primaryType == Devices.ColorTemperatureLight.ID.toLong() ||
      primaryType == Devices.ExtendedColorLight.ID.toLong()
}

/**
 * Returns true if the device exposes the Color Temperature attribute of the Color Control cluster.
 * The endpoint capability flag is set at commissioning time only when the Color Control cluster's
 * AttributeList confirms that the color temperature attribute (id 7) is present. A device type
 * fallback is also kept for legacy commissioned devices.
 */
fun supportsColorTemperature(endpoint: Endpoint): Boolean {
  val primaryType = endpoint.deviceTypeId.toLong()
  return endpoint.supportsColorTemperature ||
      primaryType == Devices.ColorTemperatureLight.ID.toLong() ||
      primaryType == Devices.ExtendedColorLight.ID.toLong()
}
