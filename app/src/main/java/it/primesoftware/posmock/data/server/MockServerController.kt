package it.primesoftware.posmock.data.server

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import it.primesoftware.posmock.data.server.protocol.ProtocolHandler
import it.primesoftware.posmock.data.server.protocol.iae37.Iae37TerminalHandler
//import it.primesoftware.posmock.data.server.protocol.raw.RawProtocolHandler
import it.primesoftware.posmock.data.server.protocol.zvt.ZvtTerminalHandler
import it.primesoftware.posmock.domain.model.LogDirection
import it.primesoftware.posmock.domain.model.MockProtocol
import it.primesoftware.posmock.domain.model.ServerState
import it.primesoftware.posmock.domain.repository.IConfigStore
import it.primesoftware.posmock.domain.repository.ILogRepository
import it.primesoftware.posmock.domain.repository.INetworkInfoProvider
import it.primesoftware.posmock.domain.repository.IOutcomeProvider
import it.primesoftware.posmock.domain.repository.IServerController
import it.primesoftware.posmock.service.MockServerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds

/**
 * Il server TCP che fa da terminale.
 *
 * Vive come singleton di processo, non dentro il [MockServerService]: il service
 * serve solo a tenere vivo il processo e a mostrare la notifica, cosi' la UI
 * osserva questi StateFlow direttamente, senza binder ne' broadcast.
 *
 * Wake lock e wifi lock sono tenuti per tutta la durata del server, come in
 * Ermes: senza, con lo schermo spento il Wi-Fi va in risparmio energetico e il
 * middleware trova un terminale che non risponde — un finto bug, prodotto dal
 * banco di prova invece che dal codice sotto esame.
 */
class MockServerController(
    context: Context,
    private val configStore: IConfigStore,
    private val log: ILogRepository,
    private val outcomeProvider: IOutcomeProvider,
    private val networkInfoProvider: INetworkInfoProvider,
) : IServerController {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    override val state: StateFlow<ServerState> = _state.asStateFlow()

    private val _activeConnections = MutableStateFlow(0)
    override val activeConnections: StateFlow<Int> = _activeConnections.asStateFlow()

    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private val openSockets: MutableSet<Socket> = Collections.synchronizedSet(mutableSetOf())

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun start() {
        if (serverJob?.isActive == true) return
        _state.value = ServerState.Starting

        val config = configStore.config.value
        val handler = createHandler(config.protocol)

        serverJob = scope.launch {
            var socket: ServerSocket? = null
            try {
                socket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(config.port))
                }
                serverSocket = socket
                acquireLocks()
                startService()
                _state.value = ServerState.Running(config.protocol, config.port)
                log.log(
                    LogDirection.INFO,
                    "Server ${config.protocol.label} in ascolto sulla porta ${config.port}",
                )

                while (isActive) {
                    val client = socket.accept()
                    openSockets.add(client)
                    _activeConnections.value = openSockets.size
                    val peer = client.inetAddress?.hostAddress ?: "sconosciuto"
                    log.log(LogDirection.INFO, "Connessione da $peer")
                    launch { serveConnection(client, peer, handler) }
                }
            } catch (e: Throwable) {
                // La close() del socket in stop() fa saltare l'accept: e' l'arresto
                // voluto, non un errore da mostrare in rosso.
                if (isActive) {
                    val message = e.message ?: e.javaClass.simpleName
                    // Qui il loop e' saltato per conto suo — l'interfaccia di rete
                    // caduta, tipicamente. Nessuno serve piu' la porta, quindi lock
                    // e connessioni vanno lasciati andare adesso: senza, restavano
                    // presi fino alla morte del processo, con il pulsante in app
                    // tornato a "Avvia" e il wake lock ancora in mano. Il foreground
                    // service si ferma da solo, che lo stato Error lo vede passare.
                    closeAllSockets()
                    releaseLocks()
                    _state.value = ServerState.Error(message)
                    log.log(LogDirection.ERROR, "Server fermato: $message")
                }
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    /**
     * NonCancellable: una pulizia interrotta a meta' e' peggio di nessuna pulizia.
     *
     * Chi chiama vive in scope che muoiono per conto loro — il `viewModelScope`
     * alla rotazione dello schermo, quello del service quando il service si
     * ferma. Senza questa protezione, chiudere l'app subito dopo aver premuto
     * "Ferma" lascerebbe il wake lock in mano fino alla morte del processo.
     */
    override suspend fun stop(): Unit = withContext(NonCancellable) {
        val job = serverJob ?: run {
            _state.value = ServerState.Stopped
            return@withContext
        }
        serverJob = null

        runCatching { serverSocket?.close() }
        serverSocket = null
        closeAllSockets()

        // L'accept e' gia' saltato con la close qui sopra, quindi l'attesa e'
        // istantanea e ci garantisce che al ritorno da stop() la porta sia davvero
        // libera per un riavvio immediato. Il tetto resta per il caso in cui un
        // handler non esca comunque: meglio una porta ancora occupata al prossimo
        // avvio, purche' scritto nel log — cosi' l'"Address already in use" che
        // seguirebbe ha una riga che lo spiega.
        if (withTimeoutOrNull(STOP_JOIN_TIMEOUT_MS.milliseconds) { job.cancelAndJoin() } == null) {
            log.log(
                LogDirection.ERROR,
                "Arresto non completato entro $STOP_JOIN_TIMEOUT_MS ms: " +
                    "la porta potrebbe risultare ancora occupata al prossimo avvio",
            )
        }

        releaseLocks()
        _activeConnections.value = 0
        _state.value = ServerState.Stopped
        log.log(LogDirection.INFO, "Server fermato")
        // Per ultimo: fermare il service fa partire il suo onDestroy, che cancella
        // lo scope da cui questa stessa stop() puo' essere stata lanciata.
        stopService()
    }

    private suspend fun serveConnection(socket: Socket, peer: String, handler: ProtocolHandler) {
        try {
            handler.handle(socket, peer)
            log.log(LogDirection.INFO, "Connessione con $peer chiusa dal middleware")
        } catch (e: Throwable) {
            if (serverJob?.isActive == true) {
                log.log(
                    LogDirection.ERROR,
                    "Connessione con $peer interrotta: ${e.message ?: e.javaClass.simpleName}",
                )
            }
        } finally {
            runCatching { socket.close() }
            openSockets.remove(socket)
            _activeConnections.value = openSockets.size
        }
    }

    private fun createHandler(protocol: MockProtocol): ProtocolHandler = when (protocol) {
        MockProtocol.ZVT ->
            ZvtTerminalHandler(log, outcomeProvider) { configStore.config.value }

        MockProtocol.IAE37 ->
            Iae37TerminalHandler(log, outcomeProvider, networkInfoProvider) { configStore.config.value }

//        MockProtocol.RAW ->
//            RawProtocolHandler(log, outcomeProvider) { configStore.config.value }
    }

    private fun closeAllSockets() {
        synchronized(openSockets) {
            openSockets.forEach { runCatching { it.close() } }
            openSockets.clear()
        }
    }

    @SuppressLint("WakelockTimeout")
    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        // Un lock preso due volte perde il riferimento al primo, che resta in mano
        // fino alla morte del processo: rilasciare prima costa nulla se non c'e'
        // niente da rilasciare, e chiude la porta a quel caso.
        releaseLocks()
        runCatching {
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                .apply { acquire() }

            val wifiManager =
                appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lockType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager.createWifiLock(lockType, WIFI_LOCK_TAG).apply { acquire() }
        }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        wifiLock = null
    }

    private fun startService() {
        appContext.startForegroundService(Intent(appContext, MockServerService::class.java))
    }

    private fun stopService() {
        appContext.stopService(Intent(appContext, MockServerService::class.java))
    }

    private companion object {
        const val WAKE_LOCK_TAG = "PosMock::server"
        const val WIFI_LOCK_TAG = "PosMock::wifi"

        /** Come il drain() di Ermes: oltre, meglio un pulsante che risponde. */
        const val STOP_JOIN_TIMEOUT_MS = 2_000L
    }
}
