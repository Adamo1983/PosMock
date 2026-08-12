package it.primesoftware.posmock.domain.model

/**
 * Riga del log mostrata nella schermata Traffico. La stessa riga finisce anche
 * su Logcat e sul file di log.
 */
data class LogEntry(
    val id: Long,
    val timestampMs: Long,
    val direction: LogDirection,
    val text: String,
    /** Byte grezzi, se la riga descrive un pacchetto. */
    val hex: String? = null,
)

enum class LogDirection {
    /** Byte arrivati dal middleware. */
    RX,

    /** Byte mandati al middleware. */
    TX,

    /** Vita del server: avvio, arresto, connessioni. */
    INFO,

    /** Qualcosa e' andato storto. */
    ERROR,
}
