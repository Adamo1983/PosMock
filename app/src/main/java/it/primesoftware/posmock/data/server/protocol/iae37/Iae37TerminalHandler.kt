package it.primesoftware.posmock.data.server.protocol.iae37

import it.primesoftware.posmock.data.server.protocol.ProtocolHandler
import it.primesoftware.posmock.domain.model.LogDirection
import it.primesoftware.posmock.domain.model.MockOutcome
import it.primesoftware.posmock.domain.model.MockProtocol
import it.primesoftware.posmock.domain.model.PaymentRequest
import it.primesoftware.posmock.domain.model.ServerConfig
import it.primesoftware.posmock.domain.repository.ILogRepository
import it.primesoftware.posmock.domain.repository.INetworkInfoProvider
import it.primesoftware.posmock.domain.repository.IOutcomeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong

/**
 * Il terminale IAE37 simulato (protocollo italiano, Ingenico).
 *
 * Il dialogo, ricostruito dai trace della DLL (cassa → | ← terminale):
 *
 * ```
 * → 02 …0t 03 LRC   status         ← 06 03 7A ACK
 *                                  ← 02 …0t <stato> 03 LRC   → 06 03 7A
 * → 02 …0P … 03 LRC pagamento      ← 06 03 7A ACK
 *                                  ← 02 …0E <esito> 03 LRC   → 06 03 7A
 * ```
 *
 * Rispetto allo ZVT cambia tutto sul filo — ASCII invece di BCD, LRC invece di
 * niente — ma la forma e' la stessa: riscontro immediato, poi la risposta vera,
 * poi il riscontro dell'altro.
 *
 * ⚠️ **Lo status non e' un dettaglio: e' il guardiano.** Il bridge italiano
 * chiama `IAE37AX_StatoPos` con un timeout di 5s **prima** di ogni pagamento, e
 * se non riceve una risposta valida dichiara `PosBusy` e il pagamento non parte
 * nemmeno. Finche' il mock non rispondeva a `t`, di IAE37 non si poteva provare
 * niente altro.
 */
class Iae37TerminalHandler(
    private val log: ILogRepository,
    private val outcomeProvider: IOutcomeProvider,
    private val networkInfoProvider: INetworkInfoProvider,
    private val configProvider: () -> ServerConfig,
) : ProtocolHandler {

    private val nextRequestId = AtomicLong(1)

    override suspend fun handle(socket: Socket, peer: String) {
        val input = socket.getInputStream().buffered()
        val output = socket.getOutputStream()

        while (true) {
            val frame = withContext(Dispatchers.IO) {
                socket.soTimeout = 0
                Iae37Codec.read(input)
            } ?: break

            when {
                frame.isAck -> {
                    log.log(LogDirection.RX, "ACK", Iae37Codec.toHex(frame.raw))
                    continue
                }

                frame.isNak -> {
                    log.log(LogDirection.RX, "NAK", Iae37Codec.toHex(frame.raw))
                    continue
                }

                !frame.isMessage -> {
                    log.log(
                        LogDirection.RX,
                        "Frame non riconosciuto da $peer",
                        Iae37Codec.toHex(frame.raw),
                    )
                    continue
                }
            }

            log.log(
                LogDirection.RX,
                "${frame.commandName} ('${frame.command}') da $peer",
                Iae37Codec.toHex(frame.raw),
            )

            // LRC sbagliato: si risponde NAK e si aspetta la ritrasmissione, che
            // e' quello che fa un terminale vero (nei trace il NAK compare
            // proprio cosi'). Rispondere ACK a un frame corrotto significherebbe
            // dare per buono un importo letto male.
            if (!frame.lrcValid) {
                send(output, Iae37Codec.nak(), "NAK: LRC non valido")
                continue
            }

            when (frame.command) {
                't' -> handleStatus(socket, input, output, frame, extended = true)
                's' -> handleStatus(socket, input, output, frame, extended = false)
                'P', 'X', 'W' -> handlePayment(socket, input, output, peer, frame)
                'E' -> send(output, Iae37Codec.ack(), "ACK")
                else -> handleUnsupported(output, frame)
            }
        }
    }

    // ─── Status ('t' esteso, 's' breve) ───────────────────────────────────────
    //
    // Come nello ZVT, il ritardo configurato **non** si applica qui: il
    // middleware molla lo status dopo 5s, quindi un ritardo lo trasformerebbe in
    // un "POS occupato" a ogni prova, nascondendo il test vero.
    private suspend fun handleStatus(
        socket: Socket,
        input: InputStream,
        output: OutputStream,
        frame: Iae37Frame,
        extended: Boolean,
    ) {
        val config = configProvider()
        when (config.defaultOutcome) {
            MockOutcome.NoAck -> {
                log.log(
                    LogDirection.INFO,
                    "Status ignorato: nessun ACK. Il middleware dichiarera' POS occupato.",
                )
                awaitCancellation()
            }

            MockOutcome.DropConnection -> {
                log.log(LogDirection.INFO, "Status: chiudo la connessione senza rispondere")
                socket.close()
                return
            }

            else -> Unit
        }

        send(output, Iae37Codec.ack(), "ACK")

        val ip = networkInfoProvider.localIpAddress() ?: "0.0.0.0"
        val payload = if (extended) {
            Iae37Messages.statusExtended(
                terminalId = TERMINAL_ID,
                ip = ip,
                gateway = defaultGateway(ip),
            )
        } else {
            Iae37Messages.statusShort(TERMINAL_ID)
        }
        send(output, Iae37Codec.frame(payload), "Status: terminale pronto")
        awaitAck(socket, input)
    }

    // ─── Pagamento ('P') ──────────────────────────────────────────────────────
    private suspend fun handlePayment(
        socket: Socket,
        input: InputStream,
        output: OutputStream,
        peer: String,
        frame: Iae37Frame,
    ) {
        val config = configProvider()
        val amountCents = Iae37Messages.parseAmountCents(frame)
        val request = PaymentRequest(
            id = nextRequestId.getAndIncrement(),
            protocol = MockProtocol.IAE37,
            peer = peer,
            kind = frame.commandName,
            amountCents = amountCents,
            timestampMs = System.currentTimeMillis(),
        )
        log.log(LogDirection.INFO, "Richiesta pagamento ${request.formattedAmount} da $peer")

        // Stessa regola dello ZVT: col preset il silenzio totale e' possibile
        // perche' si decide prima di rispondere; in manuale l'ACK deve partire
        // subito, altrimenti la DLL molla prima che l'utente tocchi lo schermo.
        if (!config.askEachTime && config.defaultOutcome == MockOutcome.NoAck) {
            log.log(LogDirection.INFO, "Nessuna risposta: il terminale resta muto (POS occupato)")
            awaitCancellation()
        }

        send(output, Iae37Codec.ack(), "ACK")

        if (config.responseDelayMs > 0 && !config.askEachTime) {
            log.log(LogDirection.INFO, "Attendo ${config.responseDelayMs} ms prima di rispondere")
            delay(config.responseDelayMs)
        }

        val outcome = outcomeProvider.decide(request)
        log.log(LogDirection.INFO, "Esito scelto: ${outcome.label}")

        when (outcome) {
            MockOutcome.Approve -> {
                send(
                    output,
                    Iae37Codec.frame(Iae37Messages.paymentApproved(TERMINAL_ID)),
                    "Esito: transazione autorizzata (TransactionResult 00)",
                )
                awaitAck(socket, input)
            }

            is MockOutcome.Decline -> {
                send(
                    output,
                    Iae37Codec.frame(
                        Iae37Messages.paymentDeclined(TERMINAL_ID, outcome.error.iae37)
                    ),
                    "Esito: ${outcome.error.label} (TransactionResult 01, ${outcome.error.iae37})",
                )
                awaitAck(socket, input)
            }

            MockOutcome.NoAck, MockOutcome.HangAfterAck -> {
                log.log(
                    LogDirection.ERROR,
                    "Silenzio dopo l'ACK: la cassa non sapra' mai come e' finita. " +
                        "E' lo scenario dell'incasso orfano.",
                )
                awaitCancellation()
            }

            MockOutcome.DropConnection -> {
                log.log(LogDirection.ERROR, "Chiudo la connessione a meta' transazione")
                socket.close()
            }
        }
    }

    /**
     * Comandi che non simuliamo (scontrino, chiusura giornaliera, diagnostica…).
     * Si risponde NAK invece di tacere: il NAK e' una risposta prevista dal
     * protocollo, il silenzio manderebbe la DLL in timeout facendo sembrare il
     * terminale morto quando invece si e' solo chiesta una cosa non gestita.
     */
    private suspend fun handleUnsupported(output: OutputStream, frame: Iae37Frame) {
        send(output, Iae37Codec.nak(), "NAK: comando '${frame.command}' non simulato")
    }

    private suspend fun send(output: OutputStream, bytes: ByteArray, description: String) {
        withContext(Dispatchers.IO) {
            output.write(bytes)
            output.flush()
        }
        log.log(LogDirection.TX, description, Iae37Codec.toHex(bytes))
    }

    /**
     * Attende il riscontro della cassa. Come nello ZVT, se non arriva si tira
     * dritto: qui interessa vedere il resto del dialogo nel log, non piantarsi.
     */
    private suspend fun awaitAck(socket: Socket, input: InputStream) {
        val received = withContext(Dispatchers.IO) {
            try {
                socket.soTimeout = ACK_TIMEOUT_MS
                Iae37Codec.read(input)
            } catch (_: SocketTimeoutException) {
                null
            } finally {
                runCatching { socket.soTimeout = 0 }
            }
        }
        when {
            received == null ->
                log.log(LogDirection.ERROR, "ACK non ricevuto entro $ACK_TIMEOUT_MS ms")

            received.isAck ->
                log.log(LogDirection.RX, "ACK", Iae37Codec.toHex(received.raw))

            else ->
                log.log(
                    LogDirection.RX,
                    "Atteso ACK, ricevuto altro",
                    Iae37Codec.toHex(received.raw),
                )
        }
    }

    /** Gateway di comodo: la .1 della stessa sottorete. Campo informativo. */
    private fun defaultGateway(ip: String): String =
        ip.substringBeforeLast('.', "").let { if (it.isEmpty()) "0.0.0.0" else "$it.1" }

    private companion object {
        /** Terminal id del terminale simulato, come lo riporta nelle risposte. */
        const val TERMINAL_ID = "02575733"
        const val ACK_TIMEOUT_MS = 5_000
    }
}
