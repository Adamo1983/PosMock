package it.primesoftware.posmock.domain.repository

import it.primesoftware.posmock.domain.model.ServerConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Unica sorgente di verita' della configurazione.
 *
 * Sta per conto suo, e non dentro il controller del server, perche' gliela
 * chiedono in due: il controller (per sapere su che porta mettersi in ascolto) e
 * chi decide gli esiti. Con la config dentro il controller quei due si
 * dipenderebbero a vicenda e il grafo Koin sarebbe circolare.
 */
interface IConfigStore {

    val config: StateFlow<ServerConfig>

    /** Aggiorna e persiste. Ha effetto sulla porta solo al prossimo avvio. */
    fun update(config: ServerConfig)
}
