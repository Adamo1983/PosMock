package it.primesoftware.posmock.data.local.logging

import android.content.Context
import android.util.Log
import it.primesoftware.posmock.domain.model.LogDirection
import it.primesoftware.posmock.domain.model.LogEntry
import it.primesoftware.posmock.domain.repository.ILogRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Log in memoria per la UI + file su disco.
 *
 * Come in Ermes la riga si **formatta subito** sul thread che logga e viene
 * accodata su un [Channel]: a scriverla e' un'unica coroutine su IO. Un solo
 * consumatore significa scritture seriali nell'ordine di accodamento, e il
 * timestamp resta quello dell'evento e non quello della scrittura.
 *
 * In memoria si tengono le ultime [MAX_ENTRIES] righe: una sessione di prova
 * lunga produce migliaia di pacchetti e la lista finirebbe per mangiarsi la RAM
 * e la fluidita' dello scroll. Il file, invece, tiene tutto.
 */
class LogRepositoryImpl(context: Context) : ILogRepository {

    private val appContext = context.applicationContext
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    override val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val nextId = AtomicLong(0)
    private val writeChannel = Channel<WriteCommand>(capacity = Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.ITALY)

    /**
     * Comandi della coda di scrittura. La barriera esiste perche' la scrittura e'
     * asincrona: senza, un export fatto subito dopo una transazione copierebbe un
     * file a cui mancano proprio le ultime righe — quelle che interessano.
     */
    private sealed interface WriteCommand {
        data class Line(val text: String, val file: File?) : WriteCommand
        data class Barrier(val done: CompletableDeferred<Unit>) : WriteCommand
    }

    init {
        scope.launch {
            for (command in writeChannel) {
                when (command) {
                    is WriteCommand.Line ->
                        runCatching { command.file?.appendText(command.text + "\n") }

                    is WriteCommand.Barrier -> command.done.complete(Unit)
                }
            }
        }
    }

    override fun log(direction: LogDirection, text: String, hex: String?) {
        val entry = LogEntry(
            id = nextId.getAndIncrement(),
            timestampMs = System.currentTimeMillis(),
            direction = direction,
            text = text,
            hex = hex,
        )
        _entries.update { current ->
            val appended = current + entry
            if (appended.size > MAX_ENTRIES) appended.takeLast(MAX_ENTRIES) else appended
        }

        val formatted = buildString {
            append(timeFormat.format(Date(entry.timestampMs)))
            append(" [").append(direction.name).append("] ")
            append(text)
            if (!hex.isNullOrBlank()) append("  ").append(hex)
        }
        when (direction) {
            LogDirection.ERROR -> Log.e(TAG, formatted)
            else -> Log.i(TAG, formatted)
        }

        // Il file del giorno si sceglie qui, non nel consumatore: una riga
        // prodotta a cavallo di mezzanotte resta nel file del giorno a cui
        // appartiene.
        val file = runCatching { LogFiles.fileFor(appContext, Date(entry.timestampMs)) }.getOrNull()
        writeChannel.trySend(WriteCommand.Line(formatted, file))
    }

    override suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        writeChannel.trySend(WriteCommand.Barrier(done))
        // Come il drain di Ermes: con un disco bloccato e' meglio esportare un
        // file quasi completo che lasciare un pulsante che non risponde.
        withTimeoutOrNull(FLUSH_TIMEOUT_MS) { done.await() }
    }

    override fun clear() {
        _entries.value = emptyList()
    }

    override fun currentLogFilePath(): String? =
        runCatching { LogFiles.fileFor(appContext, Date()).absolutePath }.getOrNull()

    private companion object {
        const val TAG = "PosMock"
        const val MAX_ENTRIES = 500
        const val FLUSH_TIMEOUT_MS = 2_000L
    }
}
