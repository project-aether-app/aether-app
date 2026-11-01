// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.matter

/**
 * Known Matter Vendor IDs from the CSA Distributed Compliance Ledger (DCL).
 *
 * Source: https://webui.dcl.csa-iot.org/vendors
 */
val VENDORS: Map<VendorId, String> =
    mapOf(
        VendorId(0x0000u) to "Unspecified",
        VendorId(0x1011u) to "Google",
        VendorId(0x1135u) to "Amazon",
        VendorId(0x1217u) to "Apple",
        VendorId(0x1341u) to "Apple",
        VendorId(0x100Bu) to "Philips Hue (Signify)",
        VendorId(0x100Cu) to "IKEA",
        VendorId(0x1321u) to "Samsung SmartThings",
        VendorId(0x10D9u) to "Legrand",
        VendorId(0x117Cu) to "Eve Systems",
        VendorId(0x1020u) to "Comcast",
        VendorId(0x1049u) to "Wulian",
        VendorId(0x1037u) to "Tuya",
        VendorId(0x111Du) to "Belkin (Wemo)",
        VendorId(0x1349u) to "Apple Home",
        VendorId(0x1384u) to "Apple Keychain",
        VendorId(0x6006u) to "Google LLC",
        VendorId(0xFFF1u) to "Test Vendor 1",
        VendorId(0xFFF2u) to "Test Vendor 2",
        VendorId(0xFFF3u) to "Test Vendor 3",
        VendorId(0xFFF4u) to "Test Vendor 4",
    )

/** Returns a human-readable vendor label for a Matter VID, including the hex code. */
fun vendorLabel(vendorId: VendorId, providedLabel: String? = null): String {
  val name = providedLabel?.takeIf { it.isNotBlank() } ?: VENDORS[vendorId]
  val hex = vendorId.toString()
  return if (name != null) "$name ($hex)" else hex
}
