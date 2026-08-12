package it.primesoftware.posmock.data.server.protocol.iae37

import java.util.Calendar

/**
 * Le risposte del terminale IAE37 simulato.
 *
 * ⚠️ **Da dove viene questo layout.** La specifica Ingenico
 * (`ING_DOC_DT_IAE37_DLL_…`) documenta la **DLL**, non il formato sul filo:
 * quello e' stato ricostruito dai trace `*iaedll-log*` di terminali veri. Le
 * lunghezze dei campi pero' non sono indovinate — combaciano con le strutture
 * dichiarate nella specifica (`TransactionResult[2+1]`, `KODescription[24+1]`,
 * P/N 33, S/N 18, InfoRelease 80) e la loro somma da' **esattamente** i 225 byte
 * del payload di status catturato. La conferma incrociata e' il motivo per cui
 * ci si puo' fidare di questa ricostruzione.
 *
 * Come lo ZVT, il protocollo e' a posizione fissa: un campo lungo un carattere
 * di troppo non da' errore, sposta tutti quelli dopo.
 */
object Iae37Messages {

    // ─── Status esteso ('t') — e' il pre-flight `StatoPos` del middleware ─────
    //
    // Composizione del payload (225 byte, verificata campo per campo):
    //   terminalId 8 | riempitivo 1 | comando 1 | contatore 10 | data 6 (GGMMAA)
    //   | ora 4 (HHMM) | P/N 33 | S/N 18 | InfoRelease 80 | IP 12 | gateway 12
    //   | 2 | contratto 13 | esercente 24 | statoPos 1
    private const val PRODUCT_NUMBER = "P/NDESK3200EM4"          // padded a 33
    private const val SERIAL_NUMBER = "S/N182592207401594"        // 18 esatti
    private const val INFO_RELEASE =
        "MAN16.08EMV3242AREVFt___ECR47.00SSL31.71EXT35.62EDL39.00" // padded a 80
    private const val CONTRACT = "0257573388006"                   // 13 esatti
    private const val MERCHANT = "PRIME SOFTWARE SRL"              // padded a 24

    /** `2` = terminale pronto, "after first DLL" (specifica, §StatoPos). */
    private const val STATUS_READY = "2"

    /** Versione applicativa riportata nello status breve. */
    private const val APP_VERSION = "01.00.14_B2778_2778"

    fun statusExtended(terminalId: String, ip: String, gateway: String): String = buildString {
        append(header(terminalId, 't'))
        append("0".repeat(10))
        append(dateTime())
        append(PRODUCT_NUMBER.padEnd(33))
        append(SERIAL_NUMBER.padEnd(18).take(18))
        append(INFO_RELEASE.padEnd(80))
        append(packIp(ip))
        append(packIp(gateway))
        append("01")
        append(CONTRACT.padEnd(13).take(13))
        append(MERCHANT.padEnd(24))
        append(STATUS_READY)
    }

    /** Status breve ('s'): stessa testa, poi stato testuale e versione. */
    fun statusShort(terminalId: String): String = buildString {
        append(header(terminalId, 's'))
        append("0".repeat(10))
        append(dateTime())
        append("OPERATIVE")
        append(APP_VERSION)
    }

    // ─── Esito del pagamento ('E') ────────────────────────────────────────────
    //
    // Dopo `TransactionResult` il tracciato cambia a seconda dell'esito: su OK
    // segue il PAN mascherato, su KO la descrizione dell'errore. Per questo i
    // due casi hanno due code diverse, prese da altrettanti frame reali.

    /** `00` = transazione autorizzata. */
    private const val RESULT_OK = "00"

    /** `01` = transazione non autorizzata. */
    private const val RESULT_KO = "01"

    /**
     * Coda del frame di esito positivo, da una cattura reale: PAN mascherato,
     * codice autorizzazione, data/ora, STAN e importi.
     *
     * ⚠️ Non e' parametrizzata sull'importo: nella coda ci sono piu' campi
     * numerici e senza due catture con importi diversi non si puo' dire quale
     * sia quale. Sbagliare campo scriverebbe cifre plausibili nel posto
     * sbagliato — peggio che lasciare il valore della cattura, che almeno e'
     * coerente. Il middleware non usa questi campi per decidere l'esito: li
     * archivia come diagnostica.
     */
    private const val OK_TAIL =
        "000************9993CLE40384 1871120288105100003000003000039000000000050000000000"

    /** Coda del frame di esito negativo, da cattura reale. */
    private const val KO_TAIL = "000000000002           000005000034"

    fun paymentApproved(terminalId: String): String =
        header(terminalId, 'E') + RESULT_OK + OK_TAIL

    fun paymentDeclined(terminalId: String, description: String): String =
        header(terminalId, 'E') + RESULT_KO + description.padEnd(24).take(24) + KO_TAIL

    // ─── Lettura della richiesta ──────────────────────────────────────────────

    /**
     * Importo in centesimi dalla richiesta di pagamento ('P').
     *
     * L'importo e' il campo `Amount` di `TECRData`, 8 cifre. Nell'unica cattura
     * disponibile (0,05 €) cade all'offset [AMOUNT_OFFSET] del payload. Se un
     * giorno il log mostrasse un importo diverso da quello chiesto dalla cassa,
     * e' questa costante da correggere — e non serve toccare altro.
     */
    fun parseAmountCents(frame: Iae37Frame): Long? {
        val payload = frame.payload
        if (payload.length < AMOUNT_OFFSET + AMOUNT_LENGTH) return null
        val field = payload.substring(AMOUNT_OFFSET, AMOUNT_OFFSET + AMOUNT_LENGTH)
        if (!field.all { it.isDigit() }) return null
        return field.toLongOrNull()
    }

    private const val AMOUNT_OFFSET = 23
    private const val AMOUNT_LENGTH = 8

    // ─── Mattoncini ───────────────────────────────────────────────────────────

    /** `<terminalId 8><riempitivo 0><comando>` — la testa di ogni messaggio. */
    private fun header(terminalId: String, command: Char): String =
        terminalId.padStart(8, '0').take(8) + "0" + command

    /** Data GGMMAA + ora HHMM, come le manda il terminale. */
    private fun dateTime(): String {
        val now = Calendar.getInstance()
        return "%02d%02d%02d%02d%02d".format(
            now.get(Calendar.DAY_OF_MONTH),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.YEAR) % 100,
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
        )
    }

    /** `192.168.1.106` → `192168001106`: ottetti a tre cifre, senza punti. */
    private fun packIp(ip: String): String {
        val octets = ip.split(".")
        if (octets.size != 4) return "0".repeat(12)
        return octets.joinToString("") { (it.toIntOrNull() ?: 0).toString().padStart(3, '0') }
    }
}
