package it.primesoftware.posmock.data.server.protocol.zvt

import it.primesoftware.posmock.domain.model.DeclineReason
import java.util.Calendar

/**
 * Costruzione dei pacchetti che manda il terminale e lettura di quelli che
 * arrivano dalla cassa.
 *
 * ⚠️ Le lunghezze dei parametri (BMP) **non stanno sul filo**: chi legge le
 * conosce a memoria e le usa per trovare il parametro successivo. Un campo di
 * lunghezza sbagliata non da' un errore, disallinea tutto quello che segue e fa
 * leggere valori casuali. Le lunghezze qui sotto sono quelle della
 * CardTerminalLibrary usata da UniquePosManager (e dal suo port Kotlin in
 * PosManagerKtorDe), da cui i commenti byte per byte.
 */
object ZvtMessages {

    // Control field dei pacchetti che manda il terminale.
    private const val CLASS_PT = 0x04
    private const val CLASS_ECR = 0x06
    private const val INSTR_STATUS_INFORMATION = 0x0F
    private const val INSTR_INTERMEDIATE_STATUS = 0xFF
    private const val INSTR_COMPLETION = 0x0F
    private const val INSTR_ABORT = 0x1E

    // BMP dei parametri usati nello Status Information.
    private const val BMP_RESULT_CODE = 0x27
    private const val BMP_AMOUNT = 0x04
    private const val BMP_TRACE_NR = 0x0B
    private const val BMP_TIME = 0x0C
    private const val BMP_DATE = 0x0D
    private const val BMP_CURRENCY = 0x49
    private const val BMP_TERMINAL_ID = 0x29
    private const val BMP_RECEIPT_NR = 0x87
    private const val BMP_CARD_TYPE = 0x8A
    private const val BMP_STATUS_BYTE = 0x19

    /** Codice valuta EUR come lo scrive la libreria: `09 78`. */
    private val CURRENCY_EUR = byteArrayOf(0x09, 0x78.toByte())

    /** Terminal id di comodo del terminale simulato (8 cifre BCD, 4 byte). */
    private const val TERMINAL_ID = 52_000_001L

    /** `80 00 00` — il riscontro positivo, l'unica risposta che la cassa attende entro 2s. */
    fun ack(): ByteArray = ZvtCodec.build(0x80, 0x00)

    /**
     * `04 FF` — stato intermedio ("attendere prego"). Serve a due cose: e' cio'
     * che un terminale vero manda mentre il cliente appoggia la carta, e tiene
     * viva la conversazione mentre qui si aspetta la decisione dell'utente.
     */
    fun intermediateStatus(status: Int = INTERMEDIATE_PROCESSING): ByteArray =
        ZvtCodec.build(CLASS_PT, INSTR_INTERMEDIATE_STATUS, status)

    /** "In elaborazione, attendere" (`IntermediateStatusEnum.ProcessingPleaseWait`). */
    const val INTERMEDIATE_PROCESSING = 0x0E

    /**
     * `04 0F` — Status Information, il pacchetto che porta l'esito vero.
     * Il primo parametro e' il result code: se non e' `00` la cassa considera la
     * transazione fallita, qualunque cosa arrivi dopo.
     */
    fun statusInformation(
        result: DeclineReason,
        amountCents: Long,
        traceNr: Long,
        receiptNr: Long,
    ): ByteArray {
        val now = Calendar.getInstance()
        val data = buildList {
            add(BMP_RESULT_CODE.toByte()); add(result.zvtByte)                       // 1 byte
            add(BMP_AMOUNT.toByte()); addAll(ZvtCodec.toBcd(amountCents, 6).toList())   // 6 byte
            add(BMP_TRACE_NR.toByte()); addAll(ZvtCodec.toBcd(traceNr, 3).toList())     // 3 byte
            add(BMP_TIME.toByte())                                                       // 3 byte HHMMSS
            addAll(
                byteArrayOf(
                    ZvtCodec.toBcd(now.get(Calendar.HOUR_OF_DAY).toLong(), 1)[0],
                    ZvtCodec.toBcd(now.get(Calendar.MINUTE).toLong(), 1)[0],
                    ZvtCodec.toBcd(now.get(Calendar.SECOND).toLong(), 1)[0],
                ).toList()
            )
            add(BMP_DATE.toByte())                                                       // 2 byte MMDD
            addAll(
                byteArrayOf(
                    ZvtCodec.toBcd((now.get(Calendar.MONTH) + 1).toLong(), 1)[0],
                    ZvtCodec.toBcd(now.get(Calendar.DAY_OF_MONTH).toLong(), 1)[0],
                ).toList()
            )
            add(BMP_CURRENCY.toByte()); addAll(CURRENCY_EUR.toList())                    // 2 byte
            add(BMP_RECEIPT_NR.toByte()); addAll(ZvtCodec.toBcd(receiptNr, 2).toList())  // 2 byte
            add(BMP_CARD_TYPE.toByte()); add(CARD_TYPE_GIROCARD)                         // 1 byte
            add(BMP_TERMINAL_ID.toByte()); addAll(ZvtCodec.toBcd(TERMINAL_ID, 4).toList()) // 4 byte
        }.toByteArray()
        return ZvtCodec.build(CLASS_PT.toByte(), INSTR_STATUS_INFORMATION.toByte(), data)
    }

    /** girocard: valore innocuo, serve solo a rendere realistico il pacchetto. */
    private const val CARD_TYPE_GIROCARD: Byte = 0x05

    /**
     * `06 0F` — Completion: chiude il comando. Per la registrazione porta lo
     * status byte, che dice alla cassa se il terminale ha bisogno di
     * inizializzazione o diagnosi: a `00` non chiede nient'altro e il dialogo
     * finisce li'.
     */
    fun completion(statusByte: Int? = null): ByteArray {
        val data = statusByte?.let {
            byteArrayOf(BMP_STATUS_BYTE.toByte(), it.toByte())
        } ?: ByteArray(0)
        return ZvtCodec.build(CLASS_ECR.toByte(), INSTR_COMPLETION.toByte(), data)
    }

    /** `06 1E` — Abort: il comando termina male, e il primo byte dice perche'. */
    fun abort(error: DeclineReason): ByteArray =
        ZvtCodec.build(CLASS_ECR, INSTR_ABORT, error.zvtCode)

    /**
     * Legge l'importo dall'Authorization `06 01`.
     *
     * Scorre i parametri con la tabella delle lunghezze note e si ferma al primo
     * BMP sconosciuto: meglio "importo non letto" che un importo inventato letto
     * a offset sbagliato.
     */
    fun parseAmountCents(apdu: ZvtApdu): Long? {
        var index = 0
        val data = apdu.data
        while (index < data.size) {
            val bmp = data[index].toInt() and 0xFF
            val length = PARAMETER_LENGTHS[bmp] ?: return null
            if (index + 1 + length > data.size) return null
            if (bmp == BMP_AMOUNT) {
                return ZvtCodec.fromBcd(data, index + 1, length)
            }
            index += 1 + length
        }
        return null
    }

    /** Lunghezza in byte dei parametri a dimensione fissa che ci interessano. */
    private val PARAMETER_LENGTHS: Map<Int, Int> = mapOf(
        BMP_AMOUNT to 6,
        BMP_TRACE_NR to 3,
        BMP_TIME to 3,
        BMP_DATE to 2,
        0x0E to 2,   // data di scadenza
        0x17 to 2,   // numero sequenza
        BMP_STATUS_BYTE to 1,
        BMP_RESULT_CODE to 1,
        BMP_TERMINAL_ID to 4,
        0x3B to 8,   // authorisation attribute
        BMP_CURRENCY to 2,
        BMP_RECEIPT_NR to 2,
        BMP_CARD_TYPE to 1,
        0x8C to 1,   // card type id
        0xA0 to 1,   // result code binario
    )
}
