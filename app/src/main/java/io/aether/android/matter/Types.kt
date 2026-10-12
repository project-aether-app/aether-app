// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.matter

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aether.android.R

internal val hexFormat = HexFormat {
  upperCase = true
  number.prefix = "0x"
}

/** A Matter Fabric ID (uint64 per spec). */
@JvmInline
value class FabricId(val value: ULong) : Comparable<FabricId> {
  override fun toString(): String = value.toHexString(hexFormat)

  override fun compareTo(other: FabricId): Int = value.compareTo(other.value)

  fun toLong(): Long = value.toLong()
}

/** A Matter Node ID (uint64 per spec). */
@JvmInline
value class NodeId(val value: ULong) : Comparable<NodeId> {
  override fun toString(): String = value.toHexString(hexFormat)

  override fun compareTo(other: NodeId): Int = value.compareTo(other.value)

  fun toLong(): Long = value.toLong()
}

/** A Matter Endpoint ID (uint16 per spec). */
@JvmInline
value class EndpointId(val value: UShort) : Comparable<EndpointId> {
  override fun toString(): String = value.toHexString(hexFormat)

  override fun compareTo(other: EndpointId): Int = value.compareTo(other.value)

  fun toInt(): Int = value.toInt()

  fun toUInt(): UInt = value.toUInt()

  fun toLong(): Long = value.toLong()
}

/** A Matter Cluster ID (uint32 per spec). */
@JvmInline
value class ClusterId(val value: UInt) : Comparable<ClusterId> {
  override fun toString(): String =
    when {
      value <= 0xFFFFu -> value.toUShort().toHexString(hexFormat)
      else -> value.toHexString(hexFormat)
    }

  override fun compareTo(other: ClusterId): Int = value.compareTo(other.value)

  fun toLong(): Long = value.toLong()

  fun toUInt(): UInt = value
}

/** A Matter Attribute ID (uint32 per spec). */
@JvmInline
value class AttributeId(val value: UInt) : Comparable<AttributeId> {
  override fun toString(): String =
    when {
      value <= 0xFFFFu -> value.toUShort().toHexString(hexFormat)
      else -> value.toHexString(hexFormat)
    }

  override fun compareTo(other: AttributeId): Int = value.compareTo(other.value)

  fun toLong(): Long = value.toLong()

  fun toUInt(): UInt = value
}

/** A Matter Command ID (uint32 per spec). */
@JvmInline
value class CommandId(val value: UInt) : Comparable<CommandId> {
  override fun toString(): String =
    when {
      value <= 0xFFFFu -> value.toUShort().toHexString(hexFormat)
      else -> value.toHexString(hexFormat)
    }

  override fun compareTo(other: CommandId): Int = value.compareTo(other.value)

  fun toLong(): Long = value.toLong()

  fun toUInt(): UInt = value
}

/** A Matter Event ID (uint32 per spec). */
@JvmInline
value class EventId(val value: UInt) : Comparable<EventId> {
  override fun toString(): String =
    when {
      value <= 0xFFFFu -> value.toUShort().toHexString(hexFormat)
      else -> value.toHexString(hexFormat)
    }

  override fun compareTo(other: EventId): Int = value.compareTo(other.value)

  fun toLong(): Long = value.toLong()

  fun toUInt(): UInt = value
}

/** A Matter Device Type ID (uint32 per spec). */
@JvmInline
value class DeviceTypeId(val value: UInt) : Comparable<DeviceTypeId> {
  override fun toString(): String =
    when {
      value <= 0xFFFFu -> value.toUShort().toHexString(hexFormat)
      else -> value.toHexString(hexFormat)
    }

  override fun compareTo(other: DeviceTypeId): Int = value.compareTo(other.value)

  fun toLong(): Long = value.toLong()

  fun toInt(): Int = value.toInt()

  fun toUInt(): UInt = value
}

/** A Matter Vendor ID (uint16 per spec). */
@JvmInline
value class VendorId(val value: UShort) : Comparable<VendorId> {
  override fun toString(): String = value.toHexString(hexFormat)

  override fun compareTo(other: VendorId): Int = value.compareTo(other.value)

  fun toInt(): Int = value.toInt()
}

/** A Matter Product ID (uint16 per spec). */
@JvmInline
value class ProductId(val value: UShort) : Comparable<ProductId> {
  override fun toString(): String = value.toHexString(hexFormat)

  override fun compareTo(other: ProductId): Int = value.compareTo(other.value)

  fun toInt(): Int = value.toInt()
}

// ---------------------------------------------------------------------------
// Conversion extensions from Long/Int (used at Chip SDK and navigation boundaries)
// ---------------------------------------------------------------------------

fun Long.toFabricId(): FabricId = FabricId(toULong())

fun Long.toNodeId(): NodeId = NodeId(toULong())

fun Int.toEndpointId(): EndpointId = EndpointId(toUShort())

fun UInt.toEndpointId(): EndpointId = EndpointId(toUShort())

fun Long.toClusterId(): ClusterId = ClusterId(toUInt())

fun Long.toAttributeId(): AttributeId = AttributeId(toUInt())

fun Long.toCommandId(): CommandId = CommandId(toUInt())

fun Long.toEventId(): EventId = EventId(toUInt())

fun Long.toDeviceTypeId(): DeviceTypeId = DeviceTypeId(toUInt())

fun Int.toVendorId(): VendorId = VendorId(toUShort())

fun Int.toProductId(): ProductId = ProductId(toUShort())

/** Data class representing Matter cluster from data model. */
data class ClusterInfo(
  val name: String,
  val attributes: Map<AttributeId, AttributeInfo>,
  val commandsIncoming: Map<CommandId, CommandInfo>,
  val commandsOutgoing: Map<CommandId, CommandInfo>,
  val events: Map<EventId, EventInfo>,
)

/** Data class representing Matter attribute from data model. */
data class AttributeInfo(
  val name: String,
  val type: DataType,
  val readPrivilege: Privilege = Privilege.NONE,
  val writePrivilege: Privilege = Privilege.NONE,
)

data class ParameterInfo(
  val name: String,
  val type: DataType,
)

data class CommandInfo(
  val name: String,
  val parameters: Map<UInt, ParameterInfo>,
  val privilege: Privilege = Privilege.NONE,
)

data class EventInfo(val name: String)

/** Matter data model access privilege for attributes and commands. */
enum class Privilege(val label: UInt) {
  NONE(0u),
  VIEW(1u),
  OPERATE(3u),
  MANAGE(4u),
  ADMINISTER(5u);

  @Composable
  fun toLabel(): String {
    return when (this) {
      NONE -> stringResource(R.string.attr_access_privilege_none)
      VIEW -> stringResource(R.string.attr_access_privilege_view)
      OPERATE -> stringResource(R.string.attr_access_privilege_operate)
      MANAGE -> stringResource(R.string.attr_access_privilege_manage)
      ADMINISTER -> stringResource(R.string.attr_access_privilege_administer)
    }
  }
}
