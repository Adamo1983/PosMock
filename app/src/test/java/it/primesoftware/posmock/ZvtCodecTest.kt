package it.primesoftware.posmock

import it.primesoftware.posmock.data.server.protocol.zvt.ZvtApdu
import it.primesoftware.posmock.data.server.protocol.zvt.ZvtCodec
import it.primesoftware.posmock.data.server.protocol.zvt.ZvtMessages
import it.primesoftware.posmock.domain.model.DeclineReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Prove sul formato dei pacchetti: e' l'unica parte che deve essere esatta al
 * byte, ed e' anche l'unica verificabile senza un middleware dall'altra parte.
 */
class ZvtCodecTest {

    @Test
    fun `authorization di 12,34 euro viene letta come 1234 centesimi`() {
        // 06 01 0A | 04 <6 byte BCD> | 49 09 78 — come lo costruisce la cassa.
        val raw = ZvtCodec.build(
            0x06.toByte(),
            0x01.toByte(),
            byteArrayOf(0x04) + ZvtCodec.toBcd(1234, 6) + byteArrayOf(0x49, 0x09, 0x78.toByte()),
        )
        val apdu = ZvtCodec.read(ByteArrayInputStream(raw))!!

        assertEquals(1234L, ZvtMessages.parseAmountCents(apdu))
    }

    @Test
    fun `un parametro sconosciuto ferma la lettura invece di inventare un importo`() {
        val apdu = ZvtApdu(0x06, 0x01, byteArrayOf(0x77, 0x00, 0x04) + ZvtCodec.toBcd(999, 6))

        assertNull(ZvtMessages.parseAmountCents(apdu))
    }

    @Test
    fun `la lunghezza oltre i 254 byte passa alla forma estesa FF ll ll`() {
        val payload = ByteArray(300) { 0x41 }
        val raw = ZvtCodec.build(0x04.toByte(), 0x0F.toByte(), payload)

        assertEquals(0xFF, raw[2].toInt() and 0xFF)
        assertEquals(0x2C, raw[3].toInt() and 0xFF) // 300 = 0x012C, little endian
        assertEquals(0x01, raw[4].toInt() and 0xFF)

        val decoded = ZvtCodec.read(ByteArrayInputStream(raw))!!
        assertEquals(300, decoded.data.size)
    }

    @Test
    fun `lo status information mette il result code come primo parametro`() {
        val bytes = ZvtMessages.statusInformation(
            result = DeclineReason.CreditNotSufficient,
            amountCents = 500,
            traceNr = 7,
            receiptNr = 7,
        )

        assertEquals(0x04, bytes[0].toInt() and 0xFF)
        assertEquals(0x0F, bytes[1].toInt() and 0xFF)
        assertEquals(0x27, bytes[3].toInt() and 0xFF)
        assertEquals(DeclineReason.CreditNotSufficient.zvtCode, bytes[4].toInt() and 0xFF)
    }

    @Test
    fun `l'ACK e' esattamente 80 00 00`() {
        assertEquals("80 00 00", ZvtCodec.toHex(ZvtMessages.ack()))
    }

    @Test
    fun `BCD andata e ritorno`() {
        val bcd = ZvtCodec.toBcd(1234, 6)
        assertEquals("00 00 00 00 12 34", ZvtCodec.toHex(bcd))
        assertEquals(1234L, ZvtCodec.fromBcd(bcd, 0, 6))
    }
}
