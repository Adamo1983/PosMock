package it.primesoftware.posmock.data.server.protocol.raw

import it.primesoftware.posmock.data.server.protocol.ProtocolHandler
import it.primesoftware.posmock.data.server.protocol.zvt.ZvtCodec
import it.primesoftware.posmock.domain.model.LogDirection
import it.primesoftware.posmock.domain.model.MockOutcome
import it.primesoftware.posmock.domain.model.MockProtocol
import it.primesoftware.posmock.domain.model.PaymentRequest
import it.primesoftware.posmock.domain.model.RawReplyMode
import it.primesoftware.posmock.domain.model.ServerConfig
import it.primesoftware.posmock.domain.repository.ILogRepository
import it.primesoftware.posmock.domain.repository.IOutcomeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

/**
 * Modalita' raw: nessuna logica di protocollo, si registra e basta.
 *
 * E' lo strumento con cui IAE37 e' stato ricostruito — esadecimale e ASCII
 * affiancati, che e' quanto serve per ricavare un tracciato a posizione fissa —
 * e resta qui per la prossima volta: un protocollo sconosciuto, una versione di
 * terminale che si comporta diversamente, o semplicemente il bisogno di vedere
 * in chiaro cosa manda il middleware quando qualcosa non torna.
 *
 * Sa fare anche le due cose che non richiedono di capire il protocollo: tacere e
 * chiudere la connessione.
 */
class RawProtocolHandler(
    private val log: ILogRepository,
    private val outcomeProvider: IOutcomeProvider,
    private val configProvider: () -> ServerConfig,
) : ProtocolHandler {

    private val nextRequestId = AtomicLong(1)

    override suspend fun handle(socket: Socket, peer: String) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)

        while (true) {
            val read = withContext(Dispatchers.IO) {
                socket.soTimeout = 0
                input.read(buffer)
            }
            if (read < 0) break

            val chunk = buffer.copyOf(read)
            log.log(
                LogDirection.RX,
                "$read byte da $peer\n${printable(chunk)}",
                ZvtCodec.toHex(chunk),
            )

            val config = configProvider()
            val request = PaymentRequest(
                id = nextRequestId.getAndIncrement(),
                protocol = MockProtocol.RAW,
                peer = peer,
                kind = "$read byte grezzi",
                amountCents = null,
                timestampMs = System.currentTimeMillis(),
            )

            if (config.responseDelayMs > 0 && !config.askEachTime) {
                delay(config.responseDelayMs)
            }

            // In manuale la scelta dell'utente comanda; col preset comandano il
            // modo di risposta raw e l'esito configurato, che qui possono solo
            // significare "rispondi", "taci" o "chiudi".
            val outcome = outcomeProvider.decide(request)
            when (outcome) {
                MockOutcome.DropConnection -> {
                    log.log(LogDirection.INFO, "Chiudo la connessione")
                    socket.close()
                    return
                }

                MockOutcome.NoAck, MockOutcome.HangAfterAck -> {
                    log.log(LogDirection.INFO, "Nessuna risposta: resto in ascolto in silenzio")
                    awaitCancellation()
                }

                else -> reply(socket, output, chunk, config)
            }
        }
    }

    private suspend fun reply(
        socket: Socket,
        output: java.io.OutputStream,
        received: ByteArray,
        config: ServerConfig,
    ) {
        when (config.rawReplyMode) {
            RawReplyMode.SILENT ->
                log.log(LogDirection.INFO, "Modalita' silenzio: nessuna risposta")

            RawReplyMode.ECHO -> {
                withContext(Dispatchers.IO) {
                    output.write(received)
                    output.flush()
                }
                log.log(LogDirection.TX, "Echo di ${received.size} byte", ZvtCodec.toHex(received))
            }

            RawReplyMode.FIXED_HEX -> {
                val bytes = runCatching { ZvtCodec.fromHex(config.rawReplyHex) }.getOrNull()
                if (bytes == null || bytes.isEmpty()) {
                    log.log(
                        LogDirection.ERROR,
                        "Risposta fissa non valida: '${config.rawReplyHex}' non e' esadecimale",
                    )
                } else {
                    withContext(Dispatchers.IO) {
                        output.write(bytes)
                        output.flush()
                    }
                    log.log(LogDirection.TX, "Risposta fissa", ZvtCodec.toHex(bytes))
                }
            }

            RawReplyMode.CLOSE -> {
                log.log(LogDirection.INFO, "Chiudo la connessione (modalita' raw)")
                socket.close()
            }
        }
    }

    /** Rende leggibile la parte stampabile: molti protocolli POS sono ASCII. */
    private fun printable(bytes: ByteArray): String =
        bytes.joinToString("") { b ->
            val c = b.toInt().toChar()
            if (c.code in 0x20..0x7E) c.toString() else "."
        }

    private companion object {
        const val BUFFER_SIZE = 4096
    }
}
