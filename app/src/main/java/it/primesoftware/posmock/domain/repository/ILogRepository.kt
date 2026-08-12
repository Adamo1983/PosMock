package it.primesoftware.posmock.domain.repository

import it.primesoftware.posmock.domain.model.LogDirection
import it.primesoftware.posmock.domain.model.LogEntry
import kotlinx.coroutines.flow.StateFlow

/**
 * Log del traffico. Tiene in memoria le ultime righe per la UI e le scrive su
 * file: quando un test non torna, il file e' cio' che si rilegge dopo.
 */
interface ILogRepository {

    val entries: StateFlow<List<LogEntry>>

    fun log(direction: LogDirection, text: String, hex: String? = null)

    /**
     * Sospende finche' quanto loggato finora non e' su disco.
     *
     * La scrittura su file e' asincrona: senza questa barriera un export fatto
     * subito dopo una transazione copierebbe un file a cui mancano le ultime
     * righe — cioe' proprio quelle che si voleva guardare.
     */
    suspend fun flush()

    fun clear()

    /** Percorso del file di log del giorno, da mostrare nella UI. */
    fun currentLogFilePath(): String?
}
