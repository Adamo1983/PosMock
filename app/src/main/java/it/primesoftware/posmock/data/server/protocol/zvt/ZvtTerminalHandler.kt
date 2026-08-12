package it.primesoftware.posmock.data.server.protocol.zvt

import it.primesoftware.posmock.data.server.protocol.ProtocolHandler
import it.primesoftware.posmock.domain.model.LogDirection
import it.primesoftware.posmock.domain.model.MockOutcome
import it.primesoftware.posmock.domain.model.MockProtocol
import it.primesoftware.posmock.domain.model.PaymentRequest
import it.primesoftware.posmock.domain.model.ServerConfig
import it.primesoftware.posmock.domain.model.DeclineReason
import it.primesoftware.posmock.domain.repository.ILogRepository
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
 * Il terminale ZVT simulato: legge i comandi della cassa e risponde come
 * risponderebbe un POS vero.
 *
 * Il dialogo che conta e' questo (cassa → | ← terminale):
 *
 * ```
 * → 06 00 Registration          ← 80 00 00 ACK
 *                               ← 06 0F Completion (status byte)   → 80 00 00
 * → 06 01 Authorization         ← 80 00 00 ACK
 *                               ← 04 FF Intermediate status        → 80 00 00
 *                               ← 04 0F Status information (esito) → 80 00 00
 *                               ← 06 0F Completion                 → 80 00 00
 * ```
 *
 * Sul rifiuto l'ultimo pacchetto e' un `06 1E` Abort al posto della Completion:
 * e' cosi' che la cassa distingue "carta rifiutata" da "andata a buon fine".
 *
 * ⚠️ Il dettaglio che rende utile questo mock: **dopo il primo ACK la cassa
 * smette di applicare timeout** e resta in attesa a tempo indeterminato (nella
 * libreria e' il passaggio `masterMode = false`). Prima dell'ACK invece la
 * finestra e' di pochi secondi. Da qui i due silenzi simulabili, che sono cose
 * diverse: [MockOutcome.NoAck] fa dichiarare "POS occupato" in 5 secondi,
 * [MockOutcome.HangAfterAck] lascia tutti appesi ed e' il modo in cui nasce un
 * incasso orfano.
 */
class ZvtTerminalHandler(
    private val log: ILogRepository,
    private val outcomeProvider: IOutcomeProvider,
    private val configProvider: () -> ServerConfig,
) : ProtocolHandler {

    private val nextRequestId = AtomicLong(1)
    private val nextTraceNr = AtomicLong(1)

    override suspend fun handle(socket: Socket, peer: String) {
        val input = socket.getInputStream().buffered()
        val output = socket.getOutputStream()

        while (true) {
            val apdu = withContext(Dispatchers.IO) {
                socket.soTimeout = 0
                ZvtCodec.read(input)
            } ?: break

            log.log(
                LogDirection.RX,
                "${apdu.name} (${apdu.controlFieldHex}) da $peer",
                ZvtCodec.toHex(apdu.toBytes()),
            )

            when {
                apdu.isControl(0x06, 0x00) -> handleRegistration(socket, input, output, peer)
                apdu.isControl(0x06, 0x01) -> handleAuthorization(socket, input, output, peer, apdu)
                apdu.isControl(0x80, 0x00) -> Unit // ACK fuori sequenza: niente da fare
                else -> handleGenericCommand(socket, input, output, apdu)
            }
        }
    }

    // ─── Registrazione ────────────────────────────────────────────────────────
    //
    // Non e' un pagamento: non si chiede niente all'utente e non si applica il
    // ritardo configurato. Il ritardo qui non simulerebbe un terminale lento, lo
    // farebbe solo dichiarare occupato ogni volta (il middleware molla la
    // registrazione dopo 5s), nascondendo i test veri.
    private suspend fun handleRegistration(
        socket: Socket,
        input: InputStream,
        output: OutputStream,
        peer: String,
    ) {
        val config = configProvider()
        when (config.defaultOutcome) {
            MockOutcome.NoAck -> {
                log.log(
                    LogDirection.INFO,
                    "Registrazione ignorata: nessun ACK. Il middleware dichiarera' POS occupato.",
                )
                awaitCancellation()
            }

            MockOutcome.DropConnection -> {
                log.log(LogDirection.INFO, "Registrazione: chiudo la connessione senza rispondere")
                socket.close()
                return
            }

            else -> Unit
        }

        send(output, ZvtMessages.ack(), "ACK")
        send(output, ZvtMessages.completion(statusByte = 0x00), "Completion (nessuna init/diagnosi richiesta)")
        awaitAck(socket, input)
        log.log(LogDirection.INFO, "Terminale registrato da $peer")
    }

    // ─── Autorizzazione ───────────────────────────────────────────────────────
    private suspend fun handleAuthorization(
        socket: Socket,
        input: InputStream,
        output: OutputStream,
        peer: String,
        apdu: ZvtApdu,
    ) {
        val config = configProvider()
        val amountCents = ZvtMessages.parseAmountCents(apdu)
        val request = PaymentRequest(
            id = nextRequestId.getAndIncrement(),
            protocol = MockProtocol.ZVT,
            peer = peer,
            kind = "Autorizzazione",
            amountCents = amountCents,
            timestampMs = System.currentTimeMillis(),
        )
        log.log(LogDirection.INFO, "Richiesta pagamento ${request.formattedAmount} da $peer")

        // Con il preset, il silenzio totale e' possibile: si decide prima di
        // rispondere. In modalita' manuale no — l'ACK deve partire entro pochi
        // secondi, altrimenti il middleware chiude prima ancora che l'utente
        // arrivi a toccare lo schermo. Percio' li' l'ACK parte subito e "nessuna
        // risposta" scelto a mano diventa "silenzio dopo l'ACK".
        if (!config.askEachTime && config.defaultOutcome == MockOutcome.NoAck) {
            log.log(LogDirection.INFO, "Nessuna risposta: il terminale resta muto (POS occupato)")
            awaitCancellation()
        }

        send(output, ZvtMessages.ack(), "ACK")
        send(
            output,
            ZvtMessages.intermediateStatus(),
            "Intermediate status: elaborazione in corso",
        )
        awaitAck(socket, input)

        if (config.responseDelayMs > 0 && !config.askEachTime) {
            log.log(LogDirection.INFO, "Attendo ${config.responseDelayMs} ms prima di rispondere")
            delay(config.responseDelayMs)
        }

        val outcome = outcomeProvider.decide(request)
        log.log(LogDirection.INFO, "Esito scelto: ${outcome.label}")

        when (outcome) {
            MockOutcome.Approve -> {
                sendStatusInformation(output, DeclineReason.NoError, amountCents ?: 0L)
                awaitAck(socket, input)
                send(output, ZvtMessages.completion(), "Completion: pagamento autorizzato")
                awaitAck(socket, input)
            }

            is MockOutcome.Decline -> {
                sendStatusInformation(output, outcome.error, amountCents ?: 0L)
                awaitAck(socket, input)
                send(output, ZvtMessages.abort(outcome.error), "Abort: ${outcome.error.label}")
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

    // ─── Altri comandi (diagnosi, inizializzazione, chiusura giornaliera…) ────
    private suspend fun handleGenericCommand(
        socket: Socket,
        input: InputStream,
        output: OutputStream,
        apdu: ZvtApdu,
    ) {
        send(output, ZvtMessages.ack(), "ACK")
        send(output, ZvtMessages.completion(), "Completion per ${apdu.name}")
        awaitAck(socket, input)
    }

    private suspend fun sendStatusInformation(
        output: OutputStream,
        result: DeclineReason,
        amountCents: Long,
    ) {
        val trace = nextTraceNr.getAndIncrement()
        send(
            output,
            ZvtMessages.statusInformation(
                result = result,
                amountCents = amountCents,
                traceNr = trace,
                receiptNr = trace,
            ),
            "Status information: ${result.label} (result code ${"%02X".format(result.zvtByte)})",
        )
    }

    private suspend fun send(output: OutputStream, bytes: ByteArray, description: String) {
        withContext(Dispatchers.IO) { ZvtCodec.write(output, bytes) }
        log.log(LogDirection.TX, description, ZvtCodec.toHex(bytes))
    }

    /**
     * Attende l'ACK della cassa. Se non arriva si va avanti lo stesso: qui
     * l'interesse e' provare il middleware, e piantarsi su un ACK mancante
     * significherebbe perdere il resto del dialogo invece di vederlo nel log.
     */
    private suspend fun awaitAck(socket: Socket, input: InputStream) {
        val received = withContext(Dispatchers.IO) {
            try {
                socket.soTimeout = ACK_TIMEOUT_MS
                ZvtCodec.read(input)
            } catch (_: SocketTimeoutException) {
                null
            } finally {
                runCatching { socket.soTimeout = 0 }
            }
        }
        when {
            received == null ->
                log.log(LogDirection.ERROR, "ACK non ricevuto entro ${ACK_TIMEOUT_MS} ms")

            received.isControl(0x80, 0x00) ->
                log.log(LogDirection.RX, "ACK", ZvtCodec.toHex(received.toBytes()))

            else ->
                log.log(
                    LogDirection.RX,
                    "Atteso ACK, ricevuto ${received.name} (${received.controlFieldHex})",
                    ZvtCodec.toHex(received.toBytes()),
                )
        }
    }

    private companion object {
        const val ACK_TIMEOUT_MS = 5_000
    }
}
