// SPDX-FileCopyrightText: 2026 The Authors
// SPDX-License-Identifier: Apache-2.0

package io.aether.android.screens.device.settings.explorer

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.aether.android.R
import io.aether.android.chip.ClustersHelper
import io.aether.android.chip.DeviceMatterInfo
import io.aether.android.matter.AttributeId
import io.aether.android.matter.ClusterId
import io.aether.android.matter.CommandId
import io.aether.android.matter.DataType
import io.aether.android.matter.EndpointId
import io.aether.android.matter.EventId
import io.aether.android.matter.NodeId
import io.aether.android.matter.Privilege
import io.aether.android.screens.common.DialogInfo
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

enum class ExplorerTab(@field:StringRes @param:StringRes val titleRes: Int) {
  ATTRIBUTES(R.string.device_explorer_tab_attributes),
  COMMANDS(R.string.device_explorer_tab_commands),
  EVENTS(R.string.device_explorer_tab_events),
}

data class ExplorerClusterKey(val endpointId: EndpointId, val clusterId: ClusterId)

data class ExplorerAttributeUiItem(
    val id: AttributeId,
    val name: String? = null,
    val type: DataType = DataType.UNKNOWN,
    val readPrivilege: Privilege = Privilege.NONE,
    val writePrivilege: Privilege = Privilege.NONE,
    val isSupported: Boolean = true,
)

data class ExplorerCommandUiItem(
    val id: CommandId,
    val name: String? = null,
    val arguments: List<ExplorerCommandArgumentDefinition> = emptyList(),
    val isSupported: Boolean = true,
)

data class ExplorerEventUiItem(
    val id: EventId,
    val name: String? = null,
)

data class ExplorerClusterDetails(
    val attributes: List<ExplorerAttributeUiItem> = emptyList(),
    val commands: List<ExplorerCommandUiItem> = emptyList(),
    val events: List<ExplorerEventUiItem> = emptyList(),
)

data class ExplorerUiState(
    // Lifecycle Flags
    val isFirstTimeLoading: Boolean = true,
    val isBackgroundRefreshing: Boolean = false,
    val isOnline: Boolean = false,
    @field:StringRes val errorMessageRes: Int? = null,

    // Core Screen Data
    val deviceMatterInfoList: List<DeviceMatterInfo> = emptyList(),
    val navStack: List<ExplorerLevel> = listOf(ExplorerLevel.EndpointList),
    val endpointSearchQuery: String = "",
    val clusterSearchQuery: String = "",
    val attributeSearchQuery: String = "",
    val commandSearchQuery: String = "",
    val eventSearchQuery: String = "",
    val loadingClusterKeys: Set<ExplorerClusterKey> = emptySet(),
    val clusterDetailsByKey: Map<ExplorerClusterKey, ExplorerClusterDetails> = emptyMap(),
    val attributeValueByKey: Map<String, String> = emptyMap(),
    val attributeReadSuccessCount: Int = 0,
    val attributeWriteSuccessCount: Int = 0,
    val commandInvokeSuccessCount: Int = 0,
    val knownClustersById: Map<ClusterId, ExplorerClusterDefinition> = emptyMap(),

    // UI Overlay / Dialog States
    val msgDialogInfo: DialogInfo? = null,
)

private data class ExplorerCoreData(
    val deviceMatterInfoList: List<DeviceMatterInfo> = emptyList(),
    val isFirstTimeLoading: Boolean = true,
    val isBackgroundRefreshing: Boolean = false,
    val errorMessageRes: Int? = null,
)

sealed class ExplorerLevel {
  object EndpointList : ExplorerLevel()

  data class ClusterList(val endpointId: EndpointId) : ExplorerLevel()

  data class ClusterDetail(
      val endpointId: EndpointId,
      val clusterId: ClusterId,
      val tab: ExplorerTab = ExplorerTab.ATTRIBUTES,
  ) : ExplorerLevel()

  data class AttributeDetail(
      val endpointId: EndpointId,
      val clusterId: ClusterId,
      val attribute: ExplorerAttributeUiItem,
  ) : ExplorerLevel()

  data class CommandInvoke(
      val endpointId: EndpointId,
      val clusterId: ClusterId,
      val command: ExplorerCommandUiItem,
  ) : ExplorerLevel()
}

@HiltViewModel
class ExplorerViewModel
@Inject
constructor(
    private val clustersHelper: ClustersHelper,
) : ViewModel() {

  private val refreshTrigger = MutableSharedFlow<NodeId>(replay = 1)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val explorerCoreDataState: StateFlow<ExplorerCoreData> =
      refreshTrigger
          .flatMapLatest { nodeId ->
            flow {
              emit(ExplorerCoreData(isFirstTimeLoading = true))
              runCatching {
                clustersHelper.fetchDeviceMatterInfo(nodeId).sortedBy { it.endpointId }
              }
                  .onSuccess { list ->
                    emit(
                        ExplorerCoreData(
                            deviceMatterInfoList = list,
                            isFirstTimeLoading = false,
                            isBackgroundRefreshing = false,
                        )
                    )
                  }
                  .onFailure {
                    Timber.e(it, "loadExplorer failed")
                    emit(
                        ExplorerCoreData(
                            isFirstTimeLoading = false,
                            isBackgroundRefreshing = false,
                            errorMessageRes = R.string.device_explorer_error_action_failed,
                        )
                    )
                  }
            }
          }
          .stateIn(
              viewModelScope,
              SharingStarted.WhileSubscribed(5000),
              ExplorerCoreData(),
          )

  fun loadExplorer(nodeId: NodeId) = refreshTrigger.tryEmit(nodeId)

  private val _navStack = MutableStateFlow<List<ExplorerLevel>>(listOf(ExplorerLevel.EndpointList))
  val navStack: StateFlow<List<ExplorerLevel>> = _navStack.asStateFlow()

  private val _endpointSearchQuery = MutableStateFlow("")
  val endpointSearchQuery: StateFlow<String> = _endpointSearchQuery.asStateFlow()

  private val _clusterSearchQuery = MutableStateFlow("")
  val clusterSearchQuery: StateFlow<String> = _clusterSearchQuery.asStateFlow()

  private val _attributeSearchQuery = MutableStateFlow("")
  val attributeSearchQuery: StateFlow<String> = _attributeSearchQuery.asStateFlow()

  private val _commandSearchQuery = MutableStateFlow("")
  val commandSearchQuery: StateFlow<String> = _commandSearchQuery.asStateFlow()

  private val _eventSearchQuery = MutableStateFlow("")
  val eventSearchQuery: StateFlow<String> = _eventSearchQuery.asStateFlow()

  private val _loadingClusterKeys = MutableStateFlow<Set<ExplorerClusterKey>>(emptySet())
  val loadingClusterKeys: StateFlow<Set<ExplorerClusterKey>> = _loadingClusterKeys.asStateFlow()

  private val _clusterDetailsByKey =
      MutableStateFlow<Map<ExplorerClusterKey, ExplorerClusterDetails>>(emptyMap())
  val clusterDetailsByKey: StateFlow<Map<ExplorerClusterKey, ExplorerClusterDetails>> =
      _clusterDetailsByKey.asStateFlow()

  private val _attributeValueByKey = MutableStateFlow<Map<String, String>>(emptyMap())
  val attributeValueByKey: StateFlow<Map<String, String>> = _attributeValueByKey.asStateFlow()

  private val _attributeReadSuccessCount = MutableStateFlow(0)
  val attributeReadSuccessCount: StateFlow<Int> = _attributeReadSuccessCount.asStateFlow()

  private val _attributeWriteSuccessCount = MutableStateFlow(0)
  val attributeWriteSuccessCount: StateFlow<Int> = _attributeWriteSuccessCount.asStateFlow()

  private val _commandInvokeSuccessCount = MutableStateFlow(0)

  private val _msgDialogInfo = MutableStateFlow<DialogInfo?>(null)

  private val _knownClustersById =
      MutableStateFlow<Map<ClusterId, ExplorerClusterDefinition>>(emptyMap())

  @Suppress("UNCHECKED_CAST")
  val uiState: StateFlow<ExplorerUiState> =
      combine(
              explorerCoreDataState,
              _navStack.asStateFlow(),
              _endpointSearchQuery.asStateFlow(),
              _clusterSearchQuery.asStateFlow(),
              _attributeSearchQuery.asStateFlow(),
              _commandSearchQuery.asStateFlow(),
              _eventSearchQuery.asStateFlow(),
              _loadingClusterKeys.asStateFlow(),
              _clusterDetailsByKey.asStateFlow(),
              _attributeValueByKey.asStateFlow(),
              _attributeReadSuccessCount.asStateFlow(),
              _attributeWriteSuccessCount.asStateFlow(),
              _commandInvokeSuccessCount.asStateFlow(),
              _msgDialogInfo.asStateFlow(),
              _knownClustersById.asStateFlow(),
          ) { array ->
            var coreData = array[0] as ExplorerCoreData
            var navStack = array[1] as List<ExplorerLevel>
            var endpointSearchQuery = array[2] as String
            var clusterSearchQuery = array[3] as String
            var attributeSearchQuery = array[4] as String
            var commandSearchQuery = array[5] as String
            var eventSearchQuery = array[6] as String
            var loadingClusterKeys = array[7] as Set<ExplorerClusterKey>
            var clusterDetailsByKey = array[8] as Map<ExplorerClusterKey, ExplorerClusterDetails>
            var attributeValueByKey = array[9] as Map<String, String>
            var attributeReadSuccessCount = array[10] as Int
            var attributeWriteSuccessCount = array[11] as Int
            var commandInvokeSuccessCount = array[12] as Int
            var msgDialogInfo = array[13] as DialogInfo?
            var knownClustersById = array[14] as Map<ClusterId, ExplorerClusterDefinition>

            ExplorerUiState(
                isFirstTimeLoading = coreData.isFirstTimeLoading,
                isBackgroundRefreshing = coreData.isBackgroundRefreshing,
                isOnline = false,
                errorMessageRes = coreData.errorMessageRes,
                deviceMatterInfoList = coreData.deviceMatterInfoList,
                navStack = navStack,
                endpointSearchQuery = endpointSearchQuery,
                clusterSearchQuery = clusterSearchQuery,
                attributeSearchQuery = attributeSearchQuery,
                commandSearchQuery = commandSearchQuery,
                eventSearchQuery = eventSearchQuery,
                loadingClusterKeys = loadingClusterKeys,
                clusterDetailsByKey = clusterDetailsByKey,
                attributeValueByKey = attributeValueByKey,
                attributeReadSuccessCount = attributeReadSuccessCount,
                attributeWriteSuccessCount = attributeWriteSuccessCount,
                commandInvokeSuccessCount = commandInvokeSuccessCount,
                msgDialogInfo = msgDialogInfo,
                knownClustersById = knownClustersById,
            )
          }
          .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExplorerUiState())

  init {
    viewModelScope.launch(Dispatchers.IO) {
      _knownClustersById.value = ExplorerSchema.buildKnownClustersById()
    }
  }

  fun navigateBack() {
    val stack = _navStack.value
    if (stack.size <= 1) return
    val newStack = stack.dropLast(1)
    _navStack.value = newStack
    clearSearchForLevel(newStack.last())
  }

  fun popToIndex(index: Int) {
    val stack = _navStack.value
    if (index < 0 || index >= stack.size) return
    val newStack = stack.subList(0, index + 1)
    _navStack.value = newStack
    clearSearchForLevel(newStack.last())
  }

  fun selectEndpoint(endpointId: EndpointId) {
    _clusterSearchQuery.value = ""
    _navStack.update { it + ExplorerLevel.ClusterList(endpointId) }
  }

  fun selectCluster(nodeId: NodeId, endpointId: EndpointId, clusterId: ClusterId) {
    _attributeSearchQuery.value = ""
    _commandSearchQuery.value = ""
    _eventSearchQuery.value = ""
    _navStack.update { it + ExplorerLevel.ClusterDetail(endpointId, clusterId) }
    ensureClusterDetails(nodeId, endpointId, clusterId)
  }

  fun setClusterDetailTab(endpointId: EndpointId, clusterId: ClusterId, tab: ExplorerTab) {
    _navStack.update { stack ->
      stack.map { level ->
        if (
            level is ExplorerLevel.ClusterDetail &&
                level.endpointId == endpointId &&
                level.clusterId == clusterId
        ) {
          level.copy(tab = tab)
        } else {
          level
        }
      }
    }
  }

  fun onEndpointSearchQueryChange(query: String) {
    _endpointSearchQuery.value = query
  }

  fun onClusterSearchQueryChange(query: String) {
    _clusterSearchQuery.value = query
  }

  fun onAttributeSearchQueryChange(query: String) {
    _attributeSearchQuery.value = query
  }

  fun onCommandSearchQueryChange(query: String) {
    _commandSearchQuery.value = query
  }

  fun onEventSearchQueryChange(query: String) {
    _eventSearchQuery.value = query
  }

  fun openAttributeDetail(
      endpointId: EndpointId,
      clusterId: ClusterId,
      attribute: ExplorerAttributeUiItem,
  ) {
    _navStack.update { it + ExplorerLevel.AttributeDetail(endpointId, clusterId, attribute) }
  }

  fun openCommandInvoke(
      endpointId: EndpointId,
      clusterId: ClusterId,
      command: ExplorerCommandUiItem,
  ) {
    _navStack.update { it + ExplorerLevel.CommandInvoke(endpointId, clusterId, command) }
  }

  private fun ensureClusterDetails(nodeId: NodeId, endpointId: EndpointId, clusterId: ClusterId) {
    val key = ExplorerClusterKey(endpointId, clusterId)
    if (_clusterDetailsByKey.value.containsKey(key) || _loadingClusterKeys.value.contains(key)) {
      return
    }

    _loadingClusterKeys.update { it + key }
    viewModelScope.launch {
      try {
        val knownSchema = _knownClustersById.value[clusterId]

        val attributesFromDevice = runCatching {
          clustersHelper.readClusterAttributeList(nodeId, endpointId, clusterId)
        }
            .getOrElse {
              Timber.w(
                  it,
                  "readClusterAttributeList failed endpoint=%s cluster=%s",
                  endpointId,
                  clusterId,
              )
              emptyList()
            }
        val commandsFromDevice = runCatching {
          clustersHelper.readClusterAcceptedCommandList(
              nodeId,
              endpointId,
              clusterId,
          )
        }
            .getOrElse {
              Timber.w(
                  it,
                  "readClusterAcceptedCommandList failed endpoint=%s cluster=%s",
                  endpointId,
                  clusterId,
              )
              emptyList()
            }
        val generatedCommandsFromDevice = runCatching {
          clustersHelper.readClusterGeneratedCommandList(
              nodeId,
              endpointId,
              clusterId,
          )
        }
            .getOrElse {
              Timber.w(
                  it,
                  "readClusterGeneratedCommandList failed endpoint=%s cluster=%s",
                  endpointId,
                  clusterId,
              )
              emptyList()
            }
        val eventsFromDevice = runCatching {
          clustersHelper.readClusterEventList(nodeId, endpointId, clusterId)
        }
            .getOrElse {
              Timber.w(
                  it,
                  "readClusterEventList failed endpoint=%s cluster=%s",
                  endpointId,
                  clusterId,
              )
              emptyList()
            }

        val knownAttributes = knownSchema?.attributes.orEmpty().associateBy { it.id }
        val knownCommands = knownSchema?.commands.orEmpty().associateBy { it.id }
        val knownEvents = knownSchema?.events.orEmpty().associateBy { it.id }
        val supportedAttributeIds = attributesFromDevice.toSet()
        val supportedCommandIds = (commandsFromDevice + generatedCommandsFromDevice).toSet()

        val attributes =
            (attributesFromDevice + knownAttributes.keys).toSet().sorted().map { id ->
              val known = knownAttributes[id]
              ExplorerAttributeUiItem(
                  id = id,
                  name = known?.name,
                  type = known?.type ?: DataType.UNKNOWN,
                  readPrivilege = known?.readPrivilege ?: Privilege.NONE,
                  writePrivilege = known?.writePrivilege ?: Privilege.NONE,
                  isSupported = id in supportedAttributeIds,
              )
            }
        val commands =
            (supportedCommandIds + knownCommands.keys).toList().sorted().map { id ->
              val known = knownCommands[id]
              ExplorerCommandUiItem(
                  id = id,
                  name = known?.name,
                  arguments = known?.arguments.orEmpty(),
                  isSupported = id in supportedCommandIds,
              )
            }
        val events =
            (eventsFromDevice + knownEvents.keys).toSet().sorted().map { id ->
              val known = knownEvents[id]
              ExplorerEventUiItem(id = id, name = known?.name)
            }

        _clusterDetailsByKey.update { current ->
          current + (key to ExplorerClusterDetails(attributes, commands, events))
        }
      } finally {
        _loadingClusterKeys.update { it - key }
      }
    }
  }

  fun readAttribute(
      nodeId: NodeId,
      endpointId: EndpointId,
      clusterId: ClusterId,
      attributeId: AttributeId,
  ) {
    viewModelScope.launch {
      try {
        val value = clustersHelper.readAttributeValue(nodeId, endpointId, clusterId, attributeId)
        _attributeValueByKey.update {
          it + (attributeKey(endpointId, clusterId, attributeId) to value)
        }
        _attributeReadSuccessCount.update { it + 1 }
      } catch (e: Exception) {
        Timber.e(e, "readAttribute failed")
        showMsgDialog(
            R.string.device_settings_admin_explorer,
            R.string.device_explorer_error_action_failed,
        )
      }
    }
  }

  fun writeAttribute(
      nodeId: NodeId,
      endpointId: EndpointId,
      clusterId: ClusterId,
      attributeId: AttributeId,
      value: String,
  ) {
    viewModelScope.launch {
      try {
        val attributeType =
            _knownClustersById.value[clusterId]
                ?.attributes
                ?.firstOrNull { it.id == attributeId }
                ?.type ?: DataType.UNKNOWN
        val payload =
            ExplorerTlvCodec.encodeAnonymousValue(
                type = attributeType,
                rawValue = value,
                invalidNumberMessageRes = R.string.device_explorer_error_invalid_number,
            )
                ?: run {
                  showMsgDialog(
                      R.string.device_settings_admin_explorer,
                      R.string.device_explorer_error_unsupported_attribute_write,
                  )
                  return@launch
                }

        clustersHelper.writeGenericAttribute(
            nodeId,
            endpointId,
            clusterId,
            attributeId,
            payload,
        )
        _attributeValueByKey.update {
          it + (attributeKey(endpointId, clusterId, attributeId) to value)
        }
        _attributeWriteSuccessCount.update { it + 1 }
      } catch (e: ExplorerInputValidationException) {
        showMsgDialog(R.string.device_settings_admin_explorer, e.messageRes)
      } catch (e: Exception) {
        Timber.e(e, "writeAttribute failed")
        showMsgDialog(
            R.string.device_settings_admin_explorer,
            R.string.device_explorer_error_action_failed,
        )
      }
    }
  }

  fun invokeCommand(
      nodeId: NodeId,
      endpointId: EndpointId,
      clusterId: ClusterId,
      commandId: CommandId,
      argumentValues: Map<String, String>,
  ) {
    viewModelScope.launch {
      try {
        val arguments =
            _knownClustersById.value[clusterId]
                ?.commands
                ?.firstOrNull { it.id == commandId }
                ?.arguments
                .orEmpty()
        val payload = ExplorerTlvCodec.encodeCommandPayload(arguments, argumentValues)
        clustersHelper.invokeGenericCommand(
            nodeId,
            endpointId,
            clusterId,
            commandId,
            payload,
        )
        _commandInvokeSuccessCount.update { it + 1 }
      } catch (e: ExplorerInputValidationException) {
        showMsgDialog(R.string.device_settings_admin_explorer, e.messageRes)
      } catch (e: Exception) {
        Timber.e(e, "invokeCommand failed")
        showMsgDialog(
            R.string.device_settings_admin_explorer,
            R.string.device_explorer_error_action_failed,
        )
      }
    }
  }

  internal fun attributeKey(
      endpointId: EndpointId,
      clusterId: ClusterId,
      attributeId: AttributeId,
  ): String = "$endpointId-$clusterId-$attributeId"

  fun dismissMsgDialog() {
    _msgDialogInfo.value = null
  }

  private fun showMsgDialog(@StringRes titleRes: Int, message: String?) {
    _msgDialogInfo.value = DialogInfo(titleRes = titleRes, message = message)
  }

  private fun showMsgDialog(@StringRes titleRes: Int, @StringRes messageRes: Int) {
    _msgDialogInfo.value = DialogInfo(titleRes = titleRes, messageRes = messageRes)
  }

  private fun clearSearchForLevel(level: ExplorerLevel) {
    if (level is ExplorerLevel.ClusterList || level is ExplorerLevel.EndpointList) {
      _attributeSearchQuery.value = ""
      _commandSearchQuery.value = ""
      _eventSearchQuery.value = ""
    }
    if (level is ExplorerLevel.EndpointList) {
      _clusterSearchQuery.value = ""
    }
  }
}
