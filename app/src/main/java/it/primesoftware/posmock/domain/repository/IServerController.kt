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

    /**
     * Chiude tutto e torna [ServerState.Stopped].
     *
     * E' sospendibile perche' aspetta davvero che l'accept loop sia finito: al
     * ritorno la porta e' libera per un riavvio immediato. Chi la chiama non
     * puo' interromperla a meta' — l'implementazione protegge la pulizia — ma
     * deve avere uno scope in cui lanciarla.
     */
    suspend fun stop()
}
