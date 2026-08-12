package it.primesoftware.posmock.data.server.protocol.iae37

import java.io.EOFException
import java.io.InputStream

/**
 * Framing del protocollo IAE37 (Ingenico Italia), ricavato dai trace della DLL.
 *
 * ```
 * messaggio:  02 <payload ASCII> 03 <LRC>
 * ACK:        06 03 7A
 * NAK:        15 03 69
 * display:    01 <testo>          (solo POS → cassa, righe tipo "ATTENDERE")
 * ```
 *
 * **LRC = XOR di tutti i byte del frame — primo byte e ETX inclusi — XOR 0x7F.**
 * Formula verificata su tutti i frame delle catture disponibili, ACK e NAK
 * compresi (`06^03^7F = 7A`, `15^03^7F = 69`).
 *
 * Tutto e' ASCII stampabile, quindi `0x03` non puo' comparire dentro il payload:
 * leggere fino al primo ETX e' sicuro.
 */
object Iae37Codec {

    const val SOH: Byte = 0x01
    const val STX: Byte = 0x02
    const val ETX: Byte = 0x03
    const val ACK: Byte = 0x06
    const val NAK: Byte = 0x15

    fun lrc(bytes: ByteArray): Byte {
        var x = 0
        for (b in bytes) x = x xor (b.toInt() and 0xFF)
        return (x xor 0x7F).toByte()
    }

    /** `02 <payload> 03 <LRC>` */
    fun frame(payload: String): ByteArray {
        val body = byteArrayOf(STX) + payload.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(ETX)
        return body + lrc(body)
    }

    /** `06 03 7A` */
    fun ack(): ByteArray = control(ACK)

    /** `15 03 69` */
    fun nak(): ByteArray = control(NAK)

    private fun control(kind: Byte): ByteArray {
        val body = byteArrayOf(kind, ETX)
        return body + lrc(body)
    }

    /**
     * Legge un frame completo. Torna null a fine stream.
     *
     * Il primo byte dice il tipo; da li' in poi la forma e' sempre la stessa —
     * si va avanti fino a ETX e si prende l'LRC.
     */
    fun read(input: InputStream): Iae37Frame? {
        val first = input.read()
        if (first < 0) return null

        val body = mutableListOf<Byte>(first.toByte())
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("Stream chiuso a meta' frame")
            body.add(b.toByte())
            if (b == ETX.toInt()) break
            if (body.size > MAX_FRAME) throw EOFException("Frame senza ETX oltre $MAX_FRAME byte")
        }
        val receivedLrc = input.read()
        if (receivedLrc < 0) throw EOFException("Stream chiuso prima dell'LRC")

        val raw = body.toByteArray()
        val expected = lrc(raw)
        val payload = String(raw, 1, raw.size - 2, Charsets.ISO_8859_1)
        return Iae37Frame(
            kind = raw[0],
            payload = payload,
            raw = raw + receivedLrc.toByte(),
            lrcValid = expected == receivedLrc.toByte(),
        )
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    private const val MAX_FRAME = 8192
}

/**
 * Un frame IAE37 letto dal filo.
 *
 * Nei messaggi ([Iae37Codec.STX]) il payload comincia sempre con
 * `<terminalId 8><riempitivo 1><comando 1>`: il terminal id e' quello che manda
 * la cassa (`00000000` da PosBridge) e il comando e' la lettera che decide tutto
 * il resto.
 */
data class Iae37Frame(
    val kind: Byte,
    val payload: String,
    val raw: ByteArray,
    val lrcValid: Boolean,
) {
    val isMessage: Boolean get() = kind == Iae37Codec.STX
    val isAck: Boolean get() = kind == Iae37Codec.ACK
    val isNak: Boolean get() = kind == Iae37Codec.NAK

    val terminalId: String get() = payload.take(8)

    /** Lettera del comando: 't' status esteso, 's' status breve, 'P' pagamento… */
    val command: Char? get() = payload.getOrNull(9)

    /** Tutto quello che viene dopo terminal id, riempitivo e comando. */
    val body: String get() = if (payload.length > 10) payload.substring(10) else ""

    val commandName: String
        get() = when (command) {
            't' -> "Status esteso"
            's' -> "Status breve"
            'P' -> "Pagamento"
            'X' -> "Pagamento esteso"
            'E' -> "Esito"
            'S' -> "Scontrino"
            'C' -> "Chiusura"
            'D' -> "Diagnostica"
            'B' -> "Blocco"
            'W' -> "Pagamento (legacy)"
            null -> "Frame corto"
            else -> "Comando '${command}'"
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Iae37Frame) return false
        return kind == other.kind && payload == other.payload && lrcValid == other.lrcValid
    }

    override fun hashCode(): Int {
        var result = kind.toInt()
        result = 31 * result + payload.hashCode()
        result = 31 * result + lrcValid.hashCode()
        return result
    }
}
