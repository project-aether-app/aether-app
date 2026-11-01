// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.matter

object GenericAttributes {
  object GeneratedCommandList {
    val ID = AttributeId(0xFFF8u)
  }

  object AcceptedCommandList {
    val ID = AttributeId(0xFFF9u)
  }

  object EventList {
    val ID = AttributeId(0xFFFAu)
  }

  object AttributeList {
    val ID = AttributeId(0xFFFBu)
  }

  object FeatureMap {
    val ID = AttributeId(0xFFFCu)
  }

  object ClusterRevision {
    val ID = AttributeId(0xFFFDu)
  }
}

val GENERIC_ATTRIBUTES =
    mapOf<AttributeId, AttributeInfo>(
        GenericAttributes.GeneratedCommandList.ID to
            AttributeInfo(
                name = "GeneratedCommandList",
                type = DataType.LIST_U_INT32,
                readPrivilege = Privilege.VIEW,
            ),
        GenericAttributes.AcceptedCommandList.ID to
            AttributeInfo(
                name = "AcceptedCommandList",
                type = DataType.LIST_U_INT32,
                readPrivilege = Privilege.VIEW,
            ),
        GenericAttributes.EventList.ID to
            AttributeInfo(
                name = "EventList",
                type = DataType.LIST_U_INT32,
                readPrivilege = Privilege.VIEW,
            ),
        GenericAttributes.AttributeList.ID to
            AttributeInfo(
                name = "AttributeList",
                type = DataType.LIST_U_INT32,
                readPrivilege = Privilege.VIEW,
            ),
        GenericAttributes.FeatureMap.ID to
            AttributeInfo(
                name = "FeatureMap",
                type = DataType.MAP32,
                readPrivilege = Privilege.VIEW,
            ),
        GenericAttributes.ClusterRevision.ID to
            AttributeInfo(
                name = "ClusterRevision",
                type = DataType.U_INT16,
                readPrivilege = Privilege.VIEW,
            ),
    )
