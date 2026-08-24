package it.primesoftware.posmock.presentation.screen.config.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.primesoftware.posmock.domain.model.MockOutcome
import it.primesoftware.posmock.domain.model.MockProtocol
import it.primesoftware.posmock.domain.model.RawReplyMode
import it.primesoftware.posmock.domain.model.ServerConfig
import it.primesoftware.posmock.domain.model.TerminalBrand
import it.primesoftware.posmock.domain.repository.IConfigStore
import it.primesoftware.posmock.domain.repository.IServerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Porta valida secondo TCP: la stessa soglia in validazione e in memorizzazione. */
private val PORT_RANGE = 1..65535
private val DELAY_RANGE = 0L..600_000L

data class ConfigUiState(
    val config: ServerConfig = ServerConfig.DEFAULT,
    val portText: String = ServerConfig.DEFAULT.port.toString(),
    val delayText: String = ServerConfig.DEFAULT.responseDelayMs.toString(),
    /** Le porte proposte nella tendina, in ordine crescente. */
    val knownPorts: List<Int> = TerminalBrand.entries.map { it.defaultPort }.sorted(),
    val isServerRunning: Boolean = false,
) {
    val isPortValid: Boolean
        get() = portText.toIntOrNull()?.let { it in PORT_RANGE } == true

    val isDelayValid: Boolean
        get() = delayText.toLongOrNull()?.let { it in DELAY_RANGE } == true
}

sealed interface ConfigAction {
    data class ProtocolSelected(val protocol: MockProtocol) : ConfigAction
    data class PortChanged(val text: String) : ConfigAction

    /** Porta scelta dalla tendina invece che battuta. */
    data class PortPicked(val port: Int) : ConfigAction

    /**
     * La porta scritta a mano si considera finita: il campo ha perso il fuoco
     * oppure e' arrivato "Fine" dalla tastiera. Da qui entra nella tendina.
     */
    data object PortCommitted : ConfigAction

    /** Porta tolta dalla tendina con la X. */
    data class PortForgotten(val port: Int) : ConfigAction

    data class OutcomeSelected(val outcome: MockOutcome) : ConfigAction
    data class DelayChanged(val text: String) : ConfigAction
    data class AskEachTimeChanged(val enabled: Boolean) : ConfigAction
    data class HangAfterRegistrationAckChanged(val enabled: Boolean) : ConfigAction
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
 *
 * La porta ha due momenti distinti, e vanno tenuti separati: **cambia** a ogni
 * cifra (perche' la config deve essere sempre valida) ma **entra nella tendina**
 * solo quando la scrittura e' finita. Fossero lo stesso momento, digitare 5577
 * lascerebbe in elenco anche 5, 55 e 557.
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
                knownPorts = configStore.knownPorts.value,
            )
        }
    )
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                configStore.config,
                configStore.knownPorts,
                serverController.state,
            ) { config, ports, state ->
                Triple(config, ports, state.isActive)
            }.collect { (config, ports, isRunning) ->
                _uiState.update {
                    it.copy(config = config, knownPorts = ports, isServerRunning = isRunning)
                }
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
                digits.toIntOrNull()?.takeIf { it in PORT_RANGE }?.let { port ->
                    persist { it.copy(port = port) }
                }
            }

            is ConfigAction.PortPicked -> {
                _uiState.update { it.copy(portText = action.port.toString()) }
                persist { it.copy(port = action.port) }
            }

            ConfigAction.PortCommitted -> commitPort()

            is ConfigAction.PortForgotten -> configStore.forgetPort(action.port)

            is ConfigAction.OutcomeSelected -> persist { it.copy(defaultOutcome = action.outcome) }

            is ConfigAction.DelayChanged -> {
                val digits = action.text.filter { it.isDigit() }.take(6)
                _uiState.update { it.copy(delayText = digits) }
                digits.toLongOrNull()?.takeIf { it in DELAY_RANGE }?.let { delay ->
                    persist { it.copy(responseDelayMs = delay) }
                }
            }

            is ConfigAction.AskEachTimeChanged -> persist { it.copy(askEachTime = action.enabled) }

            is ConfigAction.HangAfterRegistrationAckChanged ->
                persist { it.copy(hangAfterRegistrationAck = action.enabled) }

            is ConfigAction.RawModeSelected -> persist { it.copy(rawReplyMode = action.mode) }

            is ConfigAction.RawHexChanged -> persist { it.copy(rawReplyHex = action.text) }
        }
    }

    /**
     * Ultima rete di sicurezza: chi scrive una porta e chiude l'app senza
     * togliere il fuoco dal campo se la ritroverebbe comunque in tendina.
     */
    override fun onCleared() {
        commitPort()
    }

    private fun commitPort() {
        _uiState.value.portText.toIntOrNull()
            ?.takeIf { it in PORT_RANGE }
            ?.let(configStore::rememberPort)
    }

    private fun persist(transform: (ServerConfig) -> ServerConfig) {
        configStore.update(transform(configStore.config.value))
    }
}
