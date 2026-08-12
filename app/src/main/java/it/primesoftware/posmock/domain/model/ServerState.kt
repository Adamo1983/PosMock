package it.primesoftware.posmock.domain.model

/** Stato osservabile del server TCP, riflesso nella status bar e nel pulsante. */
sealed interface ServerState {
    data object Stopped : ServerState
    data object Starting : ServerState
    data class Running(val protocol: MockProtocol, val port: Int) : ServerState
    data class Error(val message: String) : ServerState

    val isActive: Boolean get() = this is Starting || this is Running
}
