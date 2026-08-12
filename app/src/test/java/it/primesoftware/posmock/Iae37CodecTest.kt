package it.primesoftware.posmock

import it.primesoftware.posmock.data.server.protocol.iae37.Iae37Codec
import it.primesoftware.posmock.data.server.protocol.iae37.Iae37Frame
import it.primesoftware.posmock.data.server.protocol.iae37.Iae37Messages
import it.primesoftware.posmock.domain.model.DeclineReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Prove sul formato IAE37, con **frame veri** presi dai trace della DLL
 * (`*iaedll-log*`) come riferimento.
 *
 * Il tracciato e' a posizione fissa e l'LRC non perdona: sono esattamente le due
 * cose che si rompono in silenzio, e le uniche verificabili senza un terminale
 * dall'altra parte.
 */
class Iae37CodecTest {

    /** Richiesta di status, catturata sul filo: `02 "000000000" "t" 03 3A`. */
    private val realStatusRequest = byteArrayOf(
        0x02,
        0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
        0x74,
        0x03,
        0x3A,
    )

    @Test
    fun `l'LRC della richiesta di status catturata torna`() {
        val body = realStatusRequest.copyOf(realStatusRequest.size - 1)

        assertEquals(0x3A.toByte(), Iae37Codec.lrc(body))
    }

    @Test
    fun `ACK e NAK sono quelli visti sul filo`() {
        assertEquals("06 03 7A", Iae37Codec.toHex(Iae37Codec.ack()))
        assertEquals("15 03 69", Iae37Codec.toHex(Iae37Codec.nak()))
    }

    @Test
    fun `la richiesta catturata viene letta come comando status`() {
        val frame = Iae37Codec.read(ByteArrayInputStream(realStatusRequest))!!

        assertTrue(frame.isMessage)
        assertTrue(frame.lrcValid)
        assertEquals("00000000", frame.terminalId)
        assertEquals('t', frame.command)
    }

    @Test
    fun `un LRC sbagliato viene riconosciuto invece di passare per buono`() {
        val corrupted = realStatusRequest.copyOf()
        corrupted[corrupted.size - 1] = 0x00

        val frame = Iae37Codec.read(ByteArrayInputStream(corrupted))!!

        assertTrue(frame.isMessage)
        assertEquals(false, frame.lrcValid)
    }

    @Test
    fun `andata e ritorno di un frame costruito da noi`() {
        val payload = Iae37Messages.statusShort("02575733")
        val frame = Iae37Codec.read(ByteArrayInputStream(Iae37Codec.frame(payload)))!!

        assertTrue(frame.lrcValid)
        assertEquals(payload, frame.payload)
        assertEquals('s', frame.command)
    }

    /**
     * I 225 byte non sono un numero a caso: sono la somma dei campi dichiarati
     * nella specifica Ingenico, ed e' la lunghezza del payload catturato da un
     * terminale vero. Se questo test cade, un campo ha cambiato lunghezza e
     * tutti quelli dopo sono fuori posto.
     */
    @Test
    fun `lo status esteso e' lungo esattamente quanto quello di un terminale vero`() {
        val payload = Iae37Messages.statusExtended(
            terminalId = "02575733",
            ip = "192.168.1.106",
            gateway = "192.168.1.1",
        )

        assertEquals(225, payload.length)
        assertEquals("02575733", payload.take(8))
        assertEquals('t', payload[9])
        assertEquals("2", payload.takeLast(1)) // terminale pronto
        assertTrue(payload.contains("192168001106"))
    }

    /**
     * `01` + descrizione: e' la coppia che il middleware traduce in
     * `PaymentFailed` (`return_code == IAE_OK && transaction_result == "00"` e'
     * l'unica combinazione che vale come incasso).
     */
    @Test
    fun `l'esito negativo porta TransactionResult 01 e la descrizione a lunghezza fissa`() {
        val payload = Iae37Messages.paymentDeclined("02575733", DeclineReason.CardExpired.iae37)

        assertEquals('E', payload[9])
        assertEquals("01", payload.substring(10, 12))
        assertEquals("TRANSAZIONE RIFIUTATA".padEnd(24), payload.substring(12, 36))
    }

    /**
     * Il terminale italiano non dice **perche'** ha rifiutato: carta scaduta e
     * credito insufficiente arrivano con la stessa descrizione. Se un giorno
     * questo test cadesse perche' qualcuno ha differenziato le stringhe, la
     * domanda da farsi e' se quella differenza sia mai stata osservata in campo.
     */
    @Test
    fun `i rifiuti autorizzativi sono indistinguibili come sul terminale vero`() {
        assertEquals(
            DeclineReason.CardExpired.iae37,
            DeclineReason.CreditNotSufficient.iae37,
        )
        assertEquals("TRANSAZIONE RIFIUTATA", DeclineReason.CardExpired.iae37)
    }

    @Test
    fun `l'esito positivo porta TransactionResult 00`() {
        val payload = Iae37Messages.paymentApproved("02575733")

        assertEquals('E', payload[9])
        assertEquals("00", payload.substring(10, 12))
    }

    /**
     * Richiesta di pagamento catturata (0,05 €): l'importo sta nel campo
     * `Amount` di 8 cifre. E' l'unico dato della richiesta che serve al mock, ed
     * e' quello che finisce nel dialog davanti all'utente.
     */
    @Test
    fun `l'importo si legge dalla richiesta di pagamento catturata`() {
        val payload = "000000000P000000000000000000005" + " ".repeat(120)
        val frame = Iae37Codec.read(ByteArrayInputStream(Iae37Codec.frame(payload)))!!

        assertEquals(5L, Iae37Messages.parseAmountCents(frame))
    }

    @Test
    fun `una richiesta troppo corta non produce un importo inventato`() {
        val frame = Iae37Frame(
            kind = Iae37Codec.STX,
            payload = "000000000P",
            raw = ByteArray(0),
            lrcValid = true,
        )

        assertNull(Iae37Messages.parseAmountCents(frame))
    }
}
