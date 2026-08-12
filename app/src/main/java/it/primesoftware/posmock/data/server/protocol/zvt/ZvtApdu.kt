package it.primesoftware.posmock.data.server.protocol.zvt

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Un APDU ZVT: due byte di control field e un blocco dati.
 *
 * Sul trasporto TCP il TPDU **e'** l'APDU: niente STX/ETX, niente checksum
 * (quelli servono sulla seriale). L'unica finezza e' la lunghezza: un byte, e se
 * vale `0xFF` seguono due byte little-endian con la lunghezza vera.
 */
data class ZvtApdu(
    val classByte: Byte,
    val instruction: Byte,
    val data: ByteArray,
) {
    fun isControl(cls: Int, instr: Int): Boolean =
        classByte == cls.toByte() && instruction == instr.toByte()

    /** Byte come viaggiano sul filo, lunghezza inclusa. */
    fun toBytes(): ByteArray = ZvtCodec.build(classByte, instruction, data)

    val controlFieldHex: String
        get() = "%02X %02X".format(classByte, instruction)

    /** Nome leggibile del comando, per il log. */
    val name: String
        get() = NAMES[controlFieldHex]
            ?: if (classByte == 0x84.toByte()) "NACK (${"%02X".format(instruction)})" else "Sconosciuto"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZvtApdu) return false
        return classByte == other.classByte &&
            instruction == other.instruction &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = classByte.toInt()
        result = 31 * result + instruction
        result = 31 * result + data.contentHashCode()
        return result
    }

    private companion object {
        val NAMES: Map<String, String> = mapOf(
            "06 00" to "Registration",
            "06 01" to "Authorization",
            "06 0F" to "Completion",
            "06 1E" to "Abort",
            "06 1B" to "Reset",
            "06 50" to "End of day",
            "06 70" to "Diagnosis",
            "06 93" to "Initialisation",
            "06 D1" to "Print line",
            "06 D3" to "Print block",
            "04 0F" to "Status information",
            "04 FF" to "Intermediate status",
            "05 01" to "Status enquiry",
            "80 00" to "ACK",
        )
    }
}

/** Codifica e decodifica dei frame ZVT su TCP. */
object ZvtCodec {

    /** Lunghezza oltre la quale si passa alla forma estesa `FF ll ll`. */
    private const val EXTENDED_LENGTH_MARKER = 0xFF

    fun build(classByte: Byte, instruction: Byte, data: ByteArray): ByteArray {
        val header = if (data.size < EXTENDED_LENGTH_MARKER) {
            byteArrayOf(classByte, instruction, data.size.toByte())
        } else {
            byteArrayOf(
                classByte,
                instruction,
                EXTENDED_LENGTH_MARKER.toByte(),
                (data.size and 0xFF).toByte(),
                ((data.size shr 8) and 0xFF).toByte(),
            )
        }
        return header + data
    }

    fun build(classByte: Int, instruction: Int, vararg data: Int): ByteArray =
        build(classByte.toByte(), instruction.toByte(), data.map { it.toByte() }.toByteArray())

    /**
     * Legge un APDU completo, bloccando finche' non e' arrivato tutto.
     * Torna null a fine stream (il middleware ha chiuso).
     */
    fun read(input: InputStream): ZvtApdu? {
        val classByte = input.read()
        if (classByte < 0) return null
        val instruction = readOrThrow(input)
        val firstLength = readOrThrow(input)
        val length = if (firstLength == EXTENDED_LENGTH_MARKER) {
            val low = readOrThrow(input)
            val high = readOrThrow(input)
            low or (high shl 8)
        } else {
            firstLength
        }
        val data = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(data, read, length - read)
            if (n < 0) throw EOFException("Stream chiuso a meta' APDU")
            read += n
        }
        return ZvtApdu(classByte.toByte(), instruction.toByte(), data)
    }

    fun write(output: OutputStream, bytes: ByteArray) {
        output.write(bytes)
        output.flush()
    }

    private fun readOrThrow(input: InputStream): Int {
        val value = input.read()
        if (value < 0) throw EOFException("Stream chiuso a meta' APDU")
        return value
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    fun fromHex(hex: String): ByteArray {
        val cleaned = hex.filter { !it.isWhitespace() && it != ':' && it != ',' }
        require(cleaned.length % 2 == 0) { "Numero di cifre esadecimali dispari" }
        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /** Numero decimale su [byteCount] byte BCD, due cifre per byte. */
    fun toBcd(value: Long, byteCount: Int): ByteArray {
        var remaining = value
        val out = ByteArray(byteCount)
        for (i in byteCount - 1 downTo 0) {
            val low = (remaining % 10).toInt()
            remaining /= 10
            val high = (remaining % 10).toInt()
            remaining /= 10
            out[i] = ((high shl 4) or low).toByte()
        }
        return out
    }

    fun fromBcd(bytes: ByteArray, offset: Int, length: Int): Long {
        var value = 0L
        for (i in offset until offset + length) {
            val b = bytes[i].toInt()
            value = value * 10 + ((b shr 4) and 0x0F)
            value = value * 10 + (b and 0x0F)
        }
        return value
    }
}
