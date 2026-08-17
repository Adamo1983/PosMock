package it.primesoftware.posmock.domain.model

/**
 * Configurazione con cui parte il server: e' quello che l'utente sceglie nella
 * schermata di avvio.
 */
data class ServerConfig(
    val protocol: MockProtocol,
    val port: Int,
    /** Esito applicato quando [askEachTime] e' spento. */
    val defaultOutcome: MockOutcome,
    /** Attesa prima di rispondere, per provare i timeout delle varie tratte. */
    val responseDelayMs: Long,
    /** Se acceso, ogni richiesta apre il dialog e aspetta la scelta dell'utente. */
    val askEachTime: Boolean,
    /**
     * Solo ZVT. Se acceso, il terminale ACKa la **registrazione** e poi tace, invece
     * di mandare la Completion.
     *
     * E' un silenzio diverso da tutti gli altri, e riguarda la fase prima del
     * pagamento. La libreria passa in slave mode gia' sull'ACK della registrazione
     * (`InternalIsCompletionPacket`: risposta `80 00` -> `MasterMode = false`), quindi
     * da li' in poi vale l'attesa lunga e chi ha chiamato resta fermo dentro l'init,
     * non dentro il pagamento. E' il caso in cui il vecchio `Timeout.Infinite` faceva
     * piu' danno, perche' il middleware aspetta l'initTask **prima** di dichiarare
     * `PosBusy`: senza un tetto non rispondeva affatto.
     *
     * Sta a parte da [defaultOutcome] apposta: quello descrive come finisce un
     * pagamento, e un pagamento qui non comincia nemmeno. Tenerli separati permette
     * anche di provare la registrazione muta lasciando l'esito su "Approvato".
     */
    val hangAfterRegistrationAck: Boolean,
    /** Come rispondere in modalita' raw (solo [MockProtocol.RAW]). */
    val rawReplyMode: RawReplyMode,
    /** Risposta in esadecimale usata da [RawReplyMode.FIXED_HEX]. */
    val rawReplyHex: String,
) {
    companion object {
        val DEFAULT = ServerConfig(
            protocol = MockProtocol.ZVT,
            port = MockProtocol.ZVT.defaultPort,
            defaultOutcome = MockOutcome.Approve,
            responseDelayMs = 2_000L,
            askEachTime = false,
            hangAfterRegistrationAck = false,
            rawReplyMode = RawReplyMode.SILENT,
            rawReplyHex = "",
        )
    }
}

/** Cosa risponde il mock in modalita' raw, dove il protocollo non lo conosciamo. */
enum class RawReplyMode(val label: String, val description: String) {
    SILENT("Silenzio", "Accetta la connessione, registra e non risponde"),
    ECHO("Echo", "Rimanda indietro gli stessi byte ricevuti"),
    FIXED_HEX("Risposta fissa", "Risponde con la sequenza esadecimale configurata"),
    CLOSE("Chiude subito", "Chiude la connessione appena arrivano dei byte"),
}
