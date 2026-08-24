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

    /**
     * Le porte gia' usate, in ordine crescente: quelle proposte nella tendina.
     *
     * Sono un elenco a parte da [config] perche' cambiano in momenti diversi —
     * la porta si aggiorna a ogni cifra digitata, la tendina solo quando la
     * scelta e' finita — e tenerle insieme farebbe finire in elenco ogni
     * prefisso battuto per strada.
     */
    val knownPorts: StateFlow<List<Int>>

    /** Aggiunge una porta all'elenco. Idempotente: una porta gia' nota non lo cambia. */
    fun rememberPort(port: Int)

    /** Toglie una porta dall'elenco. Le porte di default dei protocolli non si tolgono. */
    fun forgetPort(port: Int)
}
