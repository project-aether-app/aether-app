// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import io.aether.android.matter.AttributeId
import io.aether.android.matter.CLUSTERS
import io.aether.android.matter.ClusterId
import io.aether.android.matter.CommandId
import io.aether.android.matter.DataType
import io.aether.android.matter.EventId
import io.aether.android.matter.GENERIC_ATTRIBUTES
import io.aether.android.matter.Privilege

data class ExplorerAttributeDefinition(
  val id: AttributeId,
  val type: DataType = DataType.UNKNOWN,
  val name: String,
  val readPrivilege: Privilege = Privilege.NONE,
  val writePrivilege: Privilege = Privilege.NONE,
)

data class ExplorerCommandArgumentDefinition(
  val key: String,
  val name: String,
  val type: DataType = DataType.UNKNOWN,
)

data class ExplorerCommandDefinition(
  val id: CommandId,
  val name: String,
  val arguments: List<ExplorerCommandArgumentDefinition> = emptyList(),
)

data class ExplorerEventDefinition(
  val id: EventId,
  val name: String,
)

data class ExplorerClusterDefinition(
  val clusterId: ClusterId,
  val attributes: List<ExplorerAttributeDefinition> = emptyList(),
  val commands: List<ExplorerCommandDefinition> = emptyList(),
  val events: List<ExplorerEventDefinition> = emptyList(),
)

object ExplorerSchema {
  fun buildKnownClustersById(): Map<ClusterId, ExplorerClusterDefinition> =
    CLUSTERS.entries.associate { (clusterId, clusterInfo) ->
      clusterId to
        ExplorerClusterDefinition(
          clusterId = clusterId,
          attributes =
            (clusterInfo.attributes.map { (attributeId, attributeInfo) ->
                ExplorerAttributeDefinition(
                  id = attributeId,
                  type = attributeInfo.type,
                  name = attributeInfo.name,
                  readPrivilege = attributeInfo.readPrivilege,
                  writePrivilege = attributeInfo.writePrivilege,
                )
              } +
                GENERIC_ATTRIBUTES.map { (attributeId, attributeInfo) ->
                  ExplorerAttributeDefinition(
                    id = attributeId,
                    type = attributeInfo.type,
                    name = attributeInfo.name,
                    readPrivilege = attributeInfo.readPrivilege,
                    writePrivilege = attributeInfo.writePrivilege,
                  )
                })
              .associateBy { it.id }
              .values
              .sortedBy { it.id },
          commands =
            (clusterInfo.commandsIncoming + clusterInfo.commandsOutgoing)
              .map { (commandId, commandInfo) ->
                ExplorerCommandDefinition(
                  id = commandId,
                  name = commandInfo.name,
                  arguments =
                    commandInfo.parameters.values.map { arg ->
                      ExplorerCommandArgumentDefinition(
                        key = arg.name,
                        name = arg.name,
                        type = arg.type,
                      )
                    },
                )
              }
              .sortedBy { it.id },
          events =
            clusterInfo.events
              .map { (eventId, eventInfo) ->
                ExplorerEventDefinition(id = eventId, name = eventInfo.name)
              }
              .sortedBy { it.id },
        )
    }
}
