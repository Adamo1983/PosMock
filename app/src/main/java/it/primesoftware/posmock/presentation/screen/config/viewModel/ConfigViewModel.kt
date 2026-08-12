package it.primesoftware.posmock.presentation.screen.config.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.primesoftware.posmock.domain.model.MockOutcome
import it.primesoftware.posmock.domain.model.MockProtocol
import it.primesoftware.posmock.domain.model.RawReplyMode
import it.primesoftware.posmock.domain.model.ServerConfig
import it.primesoftware.posmock.domain.repository.IConfigStore
import it.primesoftware.posmock.domain.repository.IServerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConfigUiState(
    val config: ServerConfig = ServerConfig.DEFAULT,
    val portText: String = ServerConfig.DEFAULT.port.toString(),
    val delayText: String = ServerConfig.DEFAULT.responseDelayMs.toString(),
    val isServerRunning: Boolean = false,
) {
    val isPortValid: Boolean
        get() = portText.toIntOrNull()?.let { it in 1..65535 } == true

    val isDelayValid: Boolean
        get() = delayText.toLongOrNull()?.let { it in 0..600_000 } == true
}

sealed interface ConfigAction {
    data class ProtocolSelected(val protocol: MockProtocol) : ConfigAction
    data class PortChanged(val text: String) : ConfigAction
    data class OutcomeSelected(val outcome: MockOutcome) : ConfigAction
    data class DelayChanged(val text: String) : ConfigAction
    data class AskEachTimeChanged(val enabled: Boolean) : ConfigAction
    data class RawModeSelected(val mode: RawReplyMode) : ConfigAction
    data class RawHexChanged(val text: String) : ConfigAction
}

/**
 * La schermata delle scelte. Non accende niente: scrive su [IConfigStore], da
 * cui la schermata Stato legge il riepilogo.
 *
 * Un valore non valido resta **solo** nel campo di testo e non viene persistito:
 * cosi' una porta digitata a meta' non puo' arrivare al server, e il riepilogo
 * mostra sempre qualcosa con cui si puo' davvero partire.
 */
class ConfigViewModel(
    private val configStore: IConfigStore,
    serverController: IServerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        configStore.config.value.let { config ->
            ConfigUiState(
                config = config,
                portText = config.port.toString(),
                delayText = config.responseDelayMs.toString(),
            )
        }
    )
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(configStore.config, serverController.state) { config, state ->
                config to state.isActive
            }.collect { (config, isRunning) ->
                _uiState.update { it.copy(config = config, isServerRunning = isRunning) }
            }
        }
    }

    fun onAction(action: ConfigAction) {
        when (action) {
            is ConfigAction.ProtocolSelected -> {
                // Cambiando protocollo si porta dietro la porta di default: e'
                // quasi sempre quella giusta, e chi ne vuole un'altra la scrive.
                _uiState.update { it.copy(portText = action.protocol.defaultPort.toString()) }
                persist { it.copy(protocol = action.protocol, port = action.protocol.defaultPort) }
            }

            is ConfigAction.PortChanged -> {
                val digits = action.text.filter { it.isDigit() }.take(5)
                _uiState.update { it.copy(portText = digits) }
                digits.toIntOrNull()?.takeIf { it in 1..65535 }?.let { port ->
                    persist { it.copy(port = port) }
                }
            }

            is ConfigAction.OutcomeSelected -> persist { it.copy(defaultOutcome = action.outcome) }

            is ConfigAction.DelayChanged -> {
                val digits = action.text.filter { it.isDigit() }.take(6)
                _uiState.update { it.copy(delayText = digits) }
                digits.toLongOrNull()?.takeIf { it in 0..600_000 }?.let { delay ->
                    persist { it.copy(responseDelayMs = delay) }
                }
            }

            is ConfigAction.AskEachTimeChanged -> persist { it.copy(askEachTime = action.enabled) }

            is ConfigAction.RawModeSelected -> persist { it.copy(rawReplyMode = action.mode) }

            is ConfigAction.RawHexChanged -> persist { it.copy(rawReplyHex = action.text) }
        }
    }

    private fun persist(transform: (ServerConfig) -> ServerConfig) {
        configStore.update(transform(configStore.config.value))
    }
}
