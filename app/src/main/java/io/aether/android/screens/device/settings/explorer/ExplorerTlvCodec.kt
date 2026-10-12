// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.annotation.StringRes
import io.aether.android.R
import io.aether.android.matter.DataType
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

internal class ExplorerInputValidationException(
  @field:StringRes @param:StringRes val messageRes: Int,
) : IllegalArgumentException()

internal class ExplorerUnsupportedValueException : IllegalArgumentException()

internal object ExplorerTlvCodec {
  private const val TLV_STRUCTURE_START = 0x15
  private const val TLV_CONTAINER_END = 0x18
  private const val TLV_CONTEXT_SIGNED_1 = 0x20
  private const val TLV_CONTEXT_SIGNED_2 = 0x21
  private const val TLV_CONTEXT_SIGNED_4 = 0x22
  private const val TLV_CONTEXT_SIGNED_8 = 0x23
  private const val TLV_CONTEXT_UNSIGNED_1 = 0x24
  private const val TLV_CONTEXT_UNSIGNED_2 = 0x25
  private const val TLV_CONTEXT_UNSIGNED_4 = 0x26
  private const val TLV_CONTEXT_UNSIGNED_8 = 0x27
  private const val TLV_CONTEXT_BOOL_FALSE = 0x28
  private const val TLV_CONTEXT_BOOL_TRUE = 0x29
  private const val TLV_CONTEXT_STRING_1 = 0x2C

  private const val TLV_ANON_SIGNED_1 = 0x00
  private const val TLV_ANON_SIGNED_2 = 0x01
  private const val TLV_ANON_SIGNED_4 = 0x02
  private const val TLV_ANON_SIGNED_8 = 0x03
  private const val TLV_ANON_UNSIGNED_1 = 0x04
  private const val TLV_ANON_UNSIGNED_2 = 0x05
  private const val TLV_ANON_UNSIGNED_4 = 0x06
  private const val TLV_ANON_UNSIGNED_8 = 0x07
  private const val TLV_ANON_BOOL_FALSE = 0x08
  private const val TLV_ANON_BOOL_TRUE = 0x09
  private const val TLV_ANON_STRING_1 = 0x0C

  fun encodeCommandPayload(
    definitions: List<ExplorerCommandArgumentDefinition>,
    argumentValues: Map<String, String>,
  ): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(TLV_STRUCTURE_START)
    definitions.forEachIndexed { index, definition ->
      encodeContextValue(out, index, definition, argumentValues[definition.key])
    }
    out.write(TLV_CONTAINER_END)
    return out.toByteArray()
  }

  fun encodeAnonymousValue(
    type: DataType,
    rawValue: String,
    @StringRes invalidNumberMessageRes: Int,
  ): ByteArray? {
    if (type == DataType.UNKNOWN) {
      return null
    }
    val out = ByteArrayOutputStream()
    when (type) {
      DataType.BOOLEAN -> {
        val parsed = rawValue.trim().toBooleanStrictOrNull()
        when (parsed) {
          true -> out.write(TLV_ANON_BOOL_TRUE)
          false -> out.write(TLV_ANON_BOOL_FALSE)
          null ->
            throw ExplorerInputValidationException(R.string.device_explorer_error_invalid_boolean)
        }
      }
      DataType.STRING,
      DataType.OCTET_STRING,
      DataType.I_PV6_ADDRESS,
      DataType.I_PV6_PREFIX,
      DataType.HARDWARE_ADDRESS -> {
        val bytes = rawValue.toByteArray(StandardCharsets.UTF_8)
        requireStringLength(bytes)
        out.write(TLV_ANON_STRING_1)
        out.write(bytes.size)
        out.write(bytes)
      }
      DataType.U_INT8,
      DataType.ENUM8,
      DataType.MAP8 ->
        writeAnonymousUnsigned(
          out,
          TLV_ANON_UNSIGNED_1,
          parseUnsigned(rawValue, 0xFF, invalidNumberMessageRes),
          1,
        )
      DataType.U_INT16,
      DataType.ENUM16,
      DataType.MAP16 ->
        writeAnonymousUnsigned(
          out,
          TLV_ANON_UNSIGNED_2,
          parseUnsigned(rawValue, 0xFFFF, invalidNumberMessageRes),
          2,
        )
      DataType.U_INT24,
      DataType.U_INT32,
      DataType.CLUSTER_ID,
      DataType.ENDPOINT_ID,
      DataType.GROUP_ID,
      DataType.VENDOR_ID,
      DataType.MESSAGE_ID,
      DataType.TLS_ENDPOINT_ID ->
        writeAnonymousUnsigned(
          out,
          TLV_ANON_UNSIGNED_4,
          parseUnsigned(rawValue, 0xFFFFFFFFL, invalidNumberMessageRes),
          4,
        )
      DataType.U_INT64,
      DataType.EPOCH_SECONDS,
      DataType.EPOCH_MICROSECONDS,
      DataType.FABRIC_INDEX,
      DataType.NODE_ID,
      DataType.SUBJECT_ID,
      DataType.TLSCAID,
      DataType.TLSCCDID ->
        writeAnonymousUnsigned(
          out,
          TLV_ANON_UNSIGNED_8,
          parseUnsigned64(rawValue, invalidNumberMessageRes),
          8,
        )
      DataType.INT8 ->
        writeAnonymousSigned(
          out,
          TLV_ANON_SIGNED_1,
          parseSigned(rawValue, -128, 127, invalidNumberMessageRes),
          1,
        )
      DataType.INT16 ->
        writeAnonymousSigned(
          out,
          TLV_ANON_SIGNED_2,
          parseSigned(rawValue, -32768, 32767, invalidNumberMessageRes),
          2,
        )
      DataType.INT32 ->
        writeAnonymousSigned(
          out,
          TLV_ANON_SIGNED_4,
          parseSigned(
            rawValue,
            Int.MIN_VALUE.toLong(),
            Int.MAX_VALUE.toLong(),
            invalidNumberMessageRes,
          ),
          4,
        )
      DataType.INT64 ->
        writeAnonymousSigned(
          out,
          TLV_ANON_SIGNED_8,
          parseSigned(rawValue, Long.MIN_VALUE, Long.MAX_VALUE, invalidNumberMessageRes),
          8,
        )
      else -> return null
    }
    return out.toByteArray()
  }

  private fun encodeContextValue(
    out: ByteArrayOutputStream,
    tag: Int,
    definition: ExplorerCommandArgumentDefinition,
    rawValue: String?,
  ) {
    val requiredValue = rawValue?.trim().orEmpty()
    when (definition.type) {
      DataType.BOOLEAN -> {
        val parsed = requiredValue.toBooleanStrictOrNull()
        when (parsed) {
          true -> out.write(TLV_CONTEXT_BOOL_TRUE)
          false -> out.write(TLV_CONTEXT_BOOL_FALSE)
          null ->
            throw ExplorerInputValidationException(R.string.device_explorer_error_invalid_boolean)
        }
        out.write(tag)
      }
      DataType.STRING,
      DataType.OCTET_STRING,
      DataType.I_PV6_ADDRESS,
      DataType.I_PV6_PREFIX,
      DataType.HARDWARE_ADDRESS -> {
        val bytes = requiredValue.toByteArray(StandardCharsets.UTF_8)
        requireStringLength(bytes)
        out.write(TLV_CONTEXT_STRING_1)
        out.write(tag)
        out.write(bytes.size)
        out.write(bytes)
      }
      DataType.U_INT8,
      DataType.ENUM8,
      DataType.MAP8 ->
        writeContextUnsigned(
          out,
          tag,
          TLV_CONTEXT_UNSIGNED_1,
          parseUnsigned(requiredValue, 0xFF, R.string.device_explorer_error_invalid_number),
          1,
        )
      DataType.U_INT16,
      DataType.ENUM16,
      DataType.MAP16 ->
        writeContextUnsigned(
          out,
          tag,
          TLV_CONTEXT_UNSIGNED_2,
          parseUnsigned(requiredValue, 0xFFFF, R.string.device_explorer_error_invalid_number),
          2,
        )
      DataType.U_INT24,
      DataType.U_INT32,
      DataType.CLUSTER_ID,
      DataType.ENDPOINT_ID,
      DataType.GROUP_ID,
      DataType.VENDOR_ID,
      DataType.MESSAGE_ID,
      DataType.TLS_ENDPOINT_ID ->
        writeContextUnsigned(
          out,
          tag,
          TLV_CONTEXT_UNSIGNED_4,
          parseUnsigned(
            requiredValue,
            0xFFFFFFFFL,
            R.string.device_explorer_error_invalid_number,
          ),
          4,
        )
      DataType.U_INT64,
      DataType.EPOCH_SECONDS,
      DataType.EPOCH_MICROSECONDS,
      DataType.FABRIC_INDEX,
      DataType.NODE_ID,
      DataType.SUBJECT_ID,
      DataType.TLSCAID,
      DataType.TLSCCDID ->
        writeContextUnsigned(
          out,
          tag,
          TLV_CONTEXT_UNSIGNED_8,
          parseUnsigned64(requiredValue, R.string.device_explorer_error_invalid_number),
          8,
        )
      DataType.INT8 ->
        writeContextSigned(
          out,
          tag,
          TLV_CONTEXT_SIGNED_1,
          parseSigned(
            requiredValue,
            -128,
            127,
            R.string.device_explorer_error_invalid_number,
          ),
          1,
        )
      DataType.INT16 ->
        writeContextSigned(
          out,
          tag,
          TLV_CONTEXT_SIGNED_2,
          parseSigned(
            requiredValue,
            -32768,
            32767,
            R.string.device_explorer_error_invalid_number,
          ),
          2,
        )
      DataType.INT32 ->
        writeContextSigned(
          out,
          tag,
          TLV_CONTEXT_SIGNED_4,
          parseSigned(
            requiredValue,
            Int.MIN_VALUE.toLong(),
            Int.MAX_VALUE.toLong(),
            R.string.device_explorer_error_invalid_number,
          ),
          4,
        )
      DataType.INT64 ->
        writeContextSigned(
          out,
          tag,
          TLV_CONTEXT_SIGNED_8,
          parseSigned(
            requiredValue,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            R.string.device_explorer_error_invalid_number,
          ),
          8,
        )
      else -> throw ExplorerUnsupportedValueException()
    }
  }

  private fun writeContextUnsigned(
    out: ByteArrayOutputStream,
    tag: Int,
    controlByte: Int,
    value: Long,
    sizeBytes: Int,
  ) {
    out.write(controlByte)
    out.write(tag)
    writeLittleEndian(out, value, sizeBytes)
  }

  private fun writeContextSigned(
    out: ByteArrayOutputStream,
    tag: Int,
    controlByte: Int,
    value: Long,
    sizeBytes: Int,
  ) {
    out.write(controlByte)
    out.write(tag)
    writeLittleEndian(out, value, sizeBytes)
  }

  private fun writeAnonymousUnsigned(
    out: ByteArrayOutputStream,
    controlByte: Int,
    value: Long,
    sizeBytes: Int,
  ) {
    out.write(controlByte)
    writeLittleEndian(out, value, sizeBytes)
  }

  private fun writeAnonymousSigned(
    out: ByteArrayOutputStream,
    controlByte: Int,
    value: Long,
    sizeBytes: Int,
  ) {
    out.write(controlByte)
    writeLittleEndian(out, value, sizeBytes)
  }

  private fun writeLittleEndian(out: ByteArrayOutputStream, value: Long, sizeBytes: Int) {
    repeat(sizeBytes) { shiftByte -> out.write(((value ushr (shiftByte * 8)) and 0xFF).toInt()) }
  }

  private fun parseUnsigned64(
    value: String,
    @StringRes invalidNumberMessageRes: Int,
  ): Long {
    val normalized = value.trim()
    if (normalized.isBlank()) {
      throw ExplorerInputValidationException(invalidNumberMessageRes)
    }
    val ulong =
      when {
        normalized.startsWith("0x", ignoreCase = true) ->
          normalized.substring(2).toULongOrNull(16)
            ?: throw ExplorerInputValidationException(invalidNumberMessageRes)
        else ->
          normalized.toULongOrNull()
            ?: throw ExplorerInputValidationException(invalidNumberMessageRes)
      }
    // Reinterpret the ULong bits as Long; writeLittleEndian uses ushr which correctly
    // serializes all 8 bytes regardless of the sign bit.
    return ulong.toLong()
  }

  private fun parseUnsigned(
    value: String,
    max: Long,
    @StringRes invalidNumberMessageRes: Int,
  ): Long {
    val parsed = parseFlexibleLong(value, invalidNumberMessageRes)
    if (parsed < 0 || parsed > max) {
      throw ExplorerInputValidationException(invalidNumberMessageRes)
    }
    return parsed
  }

  private fun parseSigned(
    value: String,
    min: Long,
    max: Long,
    @StringRes invalidNumberMessageRes: Int,
  ): Long {
    val parsed = parseFlexibleLong(value, invalidNumberMessageRes)
    if (parsed < min || parsed > max) {
      throw ExplorerInputValidationException(invalidNumberMessageRes)
    }
    return parsed
  }

  private fun parseFlexibleLong(value: String, @StringRes invalidNumberMessageRes: Int): Long {
    val normalized = value.trim()
    if (normalized.isBlank()) {
      throw ExplorerInputValidationException(invalidNumberMessageRes)
    }
    return when {
      normalized.startsWith("0x", ignoreCase = true) ->
        normalized.substring(2).toLongOrNull(16)
          ?: throw ExplorerInputValidationException(invalidNumberMessageRes)
      normalized.startsWith("-0x", ignoreCase = true) ->
        -(normalized.substring(3).toLongOrNull(16)
          ?: throw ExplorerInputValidationException(invalidNumberMessageRes))
      else ->
        normalized.toLongOrNull() ?: throw ExplorerInputValidationException(invalidNumberMessageRes)
    }
  }

  private fun requireStringLength(bytes: ByteArray) {
    if (bytes.size > 0xFF) {
      throw ExplorerUnsupportedValueException()
    }
  }
}
