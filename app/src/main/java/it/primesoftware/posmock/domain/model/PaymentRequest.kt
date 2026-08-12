package it.primesoftware.posmock.domain.model

/**
 * Una richiesta arrivata dal middleware e in attesa di esito.
 *
 * [amountCents] e' null quando il protocollo non ce lo fa leggere (modalita' raw)
 * o quando la richiesta non e' un pagamento (registrazione ZVT).
 */
data class PaymentRequest(
    val id: Long,
    val protocol: MockProtocol,
    val peer: String,
    val kind: String,
    val amountCents: Long?,
    val timestampMs: Long,
) {
    /** Importo formattato, o "—" se il protocollo non lo espone. */
    val formattedAmount: String
        get() = amountCents?.let {
            val euro = it / 100
            val cents = it % 100
            String.format("%d,%02d €", euro, cents)
        } ?: "—"
}

/**
 * Richiesta in attesa di una decisione manuale. Vive finche' la UI non chiama
 * `resolve`: la coroutine che serve la connessione e' sospesa sul deferred.
 */
data class PendingDecision(
    val request: PaymentRequest,
    val defaultOutcome: MockOutcome,
)
