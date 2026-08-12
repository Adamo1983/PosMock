package it.primesoftware.posmock.domain.model

/**
 * Cosa fa il terminale simulato quando gli arriva una richiesta.
 *
 * Le voci non sono solo "ok" e "rifiutato": i casi che interessano davvero sono
 * quelli in cui il terminale **non risponde**, perche' sono quelli da cui nasce
 * il pagamento orfano (il POS incassa, la cassa non lo sa). Vedi
 * `doc/pagamenti-orfani.md` di UniquePosManager.
 */
sealed interface MockOutcome {

    /** Etichetta breve per la UI e per il log. */
    val label: String

    /** Transazione autorizzata: il terminale risponde con esito positivo. */
    data object Approve : MockOutcome {
        override val label = "Approvato"
    }

    /**
     * Carta rifiutata: esito negativo **certo**. Il middleware lo mappa su
     * `PaymentFailed`, cioe' "i soldi non sono stati presi" — la distinzione che
     * tiene in piedi tutta la gestione degli incassi orfani.
     */
    data class Decline(val error: DeclineReason) : MockOutcome {
        override val label = "Rifiutato (${error.label})"
    }

    /**
     * Nessuna risposta, nemmeno l'ACK di protocollo: il terminale e' come se
     * fosse occupato dal cassiere o spento. Lato ZVT il middleware ci mette il
     * timeout di registrazione (5s) e dichiara `PosBusy`.
     */
    data object NoAck : MockOutcome {
        override val label = "Nessuna risposta (POS occupato)"
    }

    /**
     * ACK e poi silenzio: il caso peggiore, ed e' il motivo per cui questa app
     * esiste. Il middleware resta in attesa, il palmare va in timeout e nessuno
     * sa se la carta e' stata addebitata: e' lo scenario dell'incasso orfano.
     */
    data object HangAfterAck : MockOutcome {
        override val label = "ACK e poi silenzio (incasso orfano)"
    }

    /** Connessione chiusa a meta' dialogo: cavo staccato, Wi-Fi che cade. */
    data object DropConnection : MockOutcome {
        override val label = "Chiude la connessione"
    }

    companion object {
        /** Esiti proposti nella UI, nell'ordine in cui vale la pena provarli. */
        fun presets(): List<MockOutcome> = listOf(
            Approve,
            Decline(DeclineReason.CreditNotSufficient),
            Decline(DeclineReason.CardExpired),
            Decline(DeclineReason.TimeoutOrAbort),
            NoAck,
            HangAfterAck,
            DropConnection,
        )
    }
}
