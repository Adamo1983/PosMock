package it.primesoftware.posmock.domain.repository

import it.primesoftware.posmock.domain.model.ServerState
import kotlinx.coroutines.flow.StateFlow

/**
 * Accensione e spegnimento del terminale simulato.
 *
 * L'implementazione e' un singleton di processo: il foreground service serve
 * solo a tenere vivo quel processo e a mostrare la notifica, non a ospitare il
 * server. Cosi' la UI osserva direttamente gli StateFlow, senza IPC.
 */
interface IServerController {

    val state: StateFlow<ServerState>

    /** Connessioni attualmente aperte dal middleware. */
    val activeConnections: StateFlow<Int>

    /** Si mette in ascolto con la configurazione corrente di [IConfigStore]. */
    fun start()

    fun stop()
}
