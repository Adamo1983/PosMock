package it.primesoftware.posmock.presentation.screen.status.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.primesoftware.posmock.domain.model.ServerConfig
import it.primesoftware.posmock.domain.model.ServerState
import it.primesoftware.posmock.domain.repository.IConfigStore
import it.primesoftware.posmock.domain.repository.INetworkInfoProvider
import it.primesoftware.posmock.domain.repository.IServerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatusUiState(
    val config: ServerConfig = ServerConfig.DEFAULT,
    val serverState: ServerState = ServerState.Stopped,
    val activeConnections: Int = 0,
    val localIp: String? = null,
) {
    val isRunning: Boolean get() = serverState.isActive

    /** Quello che va scritto in `posList.cfg` lato middleware. */
    val terminalAddress: String
        get() = "${localIp ?: "IP non disponibile"}:${config.port}"
}

/**
 * La schermata di comando: riepiloga la configurazione **effettiva** e accende
 * o spegne il terminale.
 *
 * Legge dallo stesso [IConfigStore] su cui scrive la schermata Configurazione,
 * quindi mostra sempre valori gia' validati e persistiti — mai il testo a meta'
 * che si sta ancora digitando di la'.
 */
class StatusViewModel(
    private val configStore: IConfigStore,
    private val serverController: IServerController,
    networkInfoProvider: INetworkInfoProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StatusUiState(
            config = configStore.config.value,
            localIp = networkInfoProvider.localIpAddress(),
        )
    )
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                configStore.config,
                serverController.state,
                serverController.activeConnections,
            ) { config, state, connections -> Triple(config, state, connections) }
                .collect { (config, state, connections) ->
                    _uiState.update {
                        it.copy(
                            config = config,
                            serverState = state,
                            activeConnections = connections,
                        )
                    }
                }
        }
    }

    fun onToggleServerClicked() {
        if (serverController.state.value.isActive) {
            // L'arresto aspetta che l'accept loop sia finito, quindi e' sospendibile.
            // Se lo scope muore prima — schermo ruotato, app chiusa — la pulizia va
            // avanti lo stesso: se ne occupa il controller.
            viewModelScope.launch { serverController.stop() }
        } else {
            serverController.start()
        }
    }
}
