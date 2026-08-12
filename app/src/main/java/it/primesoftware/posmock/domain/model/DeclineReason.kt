package it.primesoftware.posmock.domain.model

// KODescription viste su terminali veri. `TRANSAZIONE RIFIUTATA` viene dal JSON
// di PosBridge del 12/08/2026 (carta scaduta, transaction_result "01"); le altre
// tre dai trace della DLL del 2024-2025.
private const val IAE37_OK = "OK"
private const val IAE37_DECLINED = "TRANSAZIONE RIFIUTATA"
private const val IAE37_ABORTED = "ABORTED_MANUALLY"
private const val IAE37_CARD_TIMEOUT = "CARD_SEARCH_TIMEOUT"
private const val IAE37_ERROR = "ERROR_UNKNOWN"

/**
 * Motivo di rifiuto, con la sua traduzione in **entrambi** i protocolli.
 *
 * - [zvtCode] e' l'error id ZVT (`ErrorIDEnum` della CardTerminalLibrary usata
 *   da UniquePosManager e da PosManagerKtorDe): viaggia come result code nello
 *   Status Information e nell'Abort.
 * - [iae37] e' la `KODescription` del protocollo italiano, 24 caratteri di testo
 *   che il terminale mette nel frame di esito.
 *
 * Una sola enum per due protocolli perche' la scelta e' dell'utente ("carta
 * scaduta"), non del filo: e' il traduttore a sapere come si dice di la'.
 *
 * ⚠️ **Le descrizioni IAE37 sono solo quattro, e sono tutte osservate su
 * terminali veri.** Nessuna e' inventata, ed e' una scelta: il terminale
 * italiano non espone il motivo del rifiuto come fa lo ZVT. Su un rifiuto
 * autorizzativo manda `TRANSAZIONE RIFIUTATA` — in italiano, cosi' com'e' —
 * qualunque sia la ragione, e tiene i codici in inglese per gli abort locali
 * (annullo, carta mai avvicinata, errore generico). Quindi carta scaduta e
 * credito insufficiente arrivano al middleware **indistinguibili**: e' il
 * protocollo a essere fatto cosi', e un mock che inventasse `CARD_EXPIRED`
 * mostrerebbe una precisione che in campo non esiste.
 *
 * Nessuna conseguenza sull'esito: il middleware non interpreta queste stringhe,
 * le rigira come testo in `protocolSpecificErrorDescription`. A decidere e'
 * `TransactionResult` (`00` autorizzato / tutto il resto rifiutato).
 */
enum class DeclineReason(
    val zvtCode: Int,
    val label: String,
    val iae37: String,
) {
    NoError(0x00, "Nessun errore", IAE37_OK),
    CardNotReadable(0x64, "Carta illeggibile", IAE37_DECLINED),
    ProcessingError(0x66, "Errore di elaborazione", IAE37_ERROR),
    TimeoutOrAbort(0x6C, "Timeout o annullo", IAE37_ABORTED),
    CardSearchTimeout(0x6D, "Carta mai avvicinata", IAE37_CARD_TIMEOUT),
    CreditNotSufficient(0x71, "Credito insufficiente", IAE37_DECLINED),
    ChipError(0x72, "Errore chip", IAE37_ERROR),
    CardExpired(0x78, "Carta scaduta", IAE37_DECLINED),
    CardUnknown(0x7A, "Carta sconosciuta", IAE37_DECLINED),
    CommunicationError(0x7D, "Errore di comunicazione", IAE37_ERROR),
    FunctionNotPossible(0x83, "Funzione non possibile", IAE37_DECLINED),
    ReceiverNotReady(0xA0, "Ricevente non pronto", IAE37_ERROR),
    MaximumAmountExceeded(0xC3, "Importo massimo superato", IAE37_DECLINED),
    AmountTooSmall(0xC8, "Importo troppo piccolo", IAE37_DECLINED),
    Unknownerror(0xFF, "Errore sconosciuto", IAE37_ERROR);

    val zvtByte: Byte get() = zvtCode.toByte()

    companion object {
        /** Motivi proponibili come rifiuto nella UI. */
        fun declinable(): List<DeclineReason> = entries.filter { it != NoError }
    }
}
