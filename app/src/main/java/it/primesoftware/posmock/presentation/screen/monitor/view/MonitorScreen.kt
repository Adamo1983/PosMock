package it.primesoftware.posmock.presentation.screen.monitor.view

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.primesoftware.posmock.domain.model.LogDirection
import it.primesoftware.posmock.domain.model.LogEntry
import it.primesoftware.posmock.presentation.screen.monitor.viewModel.ExportStatus
import it.primesoftware.posmock.presentation.screen.monitor.viewModel.MonitorViewModel
import it.primesoftware.posmock.presentation.theme.PosMockTheme
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitorScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: MonitorViewModel = koinViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()
    MonitorScreen(
        entries = entries,
        logFilePath = viewModel.logFilePath,
        isExporting = isExporting,
        exportStatus = exportStatus,
        onClearClicked = viewModel::onClearClicked,
        onExportClicked = viewModel::onExportClicked,
        modifier = modifier,
    )
}

/**
 * Pulsante d'azione della schermata Traffico: icona, angoli arrotondati e un colore
 * che dice cosa fa.
 *
 * La luminosita' e' l'unico canale di animazione usato, e porta due informazioni
 * diverse: alla pressione il colore schiarisce di scatto e torna (riscontro
 * immediato, utile su un telefono tenuto in mano mentre si cronometra una prova);
 * con [pulsing] acceso schiarisce e si spegne di continuo, e vuol dire che
 * l'operazione e' ancora in corso. Si schiarisce verso il bianco invece di cambiare
 * tinta perche' cosi' funziona con qualunque colore le si passi, tema chiaro o scuro.
 */
@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val pulseTransition = rememberInfiniteTransition(label = "pulsazione")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f,
        // A zero quando non pulsa: la transizione continua a girare ma non muove niente.
        targetValue = if (pulsing) 0.30f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "luminosita-pulsazione",
    )

    // La pressione si anima a parte: se passasse anche lei dalla transizione infinita
    // la risposta al dito arriverebbe smorzata, e il riscontro immediato e' il punto.
    val pressLift by animateFloatAsState(
        targetValue = if (pressed) 0.28f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "luminosita-pressione",
    )

    val container = lerp(color, Color.White, (pressLift + pulse).coerceIn(0f, 1f))

    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColor,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        interactionSource = interactionSource,
    ) {
        // contentDescription null: l'etichetta accanto dice gia' tutto, e ripeterla
        // farebbe leggere due volte la stessa cosa da TalkBack.
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text)
    }
}

@Composable
fun MonitorScreen(
    entries: List<LogEntry>,
    logFilePath: String?,
    isExporting: Boolean,
    exportStatus: ExportStatus?,
    onClearClicked: () -> Unit,
    onExportClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Segue la coda del log da solo: durante una prova si guarda l'ultimo
    // pacchetto, non si scorre a mano.
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${entries.size} righe",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Non si disabilita durante l'export: un pulsante spento perde il
                // colore e con esso la pulsazione, che e' proprio il segnale che
                // sta lavorando. I click ripetuti li ignora gia' il view-model.
                ActionButton(
                    text = if (isExporting) "Esporto…" else "Esporta",
                    icon = Icons.Filled.FileDownload,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    pulsing = isExporting,
                    onClick = onExportClicked,
                )
                // Rosso perche' cancella, e cancellare a meta' di una prova vuol dire
                // rifarla: il colore e' un avviso, non una decorazione.
                ActionButton(
                    text = "Pulisci",
                    icon = Icons.Filled.DeleteSweep,
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    onClick = onClearClicked,
                )
            }
        }

        if (logFilePath != null) {
            Text(
                text = logFilePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // L'esito resta a video finche' non se ne fa un altro: e' il percorso da
        // cercare col cavo collegato al PC, e una notifica che sparisce dopo due
        // secondi costringerebbe a riesportare per rileggerlo.
        exportStatus?.let { status ->
            Text(
                text = status.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (entries.isEmpty()) {
            Text(
                text = "Nessun traffico. Avvia il servizio e fai partire un pagamento " +
                    "dal middleware.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(entries, key = { it.id }) { entry -> LogRow(entry) }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = entry.direction.containerColor()),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = TIME_FORMAT.format(Date(entry.timestampMs)),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = entry.direction.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(text = entry.text, style = MaterialTheme.typography.bodyMedium)
            if (!entry.hex.isNullOrBlank()) {
                Text(
                    text = entry.hex,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Colori tenui e distinti per direzione: durante una prova si guarda il flusso a
 * colpo d'occhio, e serve distinguere subito cosa arriva da cosa parte.
 */
@Composable
private fun LogDirection.containerColor(): Color = when (this) {
    LogDirection.RX -> MaterialTheme.colorScheme.surfaceVariant
    LogDirection.TX -> MaterialTheme.colorScheme.secondaryContainer
    LogDirection.INFO -> MaterialTheme.colorScheme.surface
    LogDirection.ERROR -> MaterialTheme.colorScheme.errorContainer
}

private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.ITALY)

@Preview(showBackground = true)
@Composable
private fun MonitorScreenPreview() {
    PosMockTheme {
        MonitorScreen(
            entries = listOf(
                LogEntry(0, System.currentTimeMillis(), LogDirection.INFO, "Connessione da 192.168.1.10"),
                LogEntry(1, System.currentTimeMillis(), LogDirection.RX, "Authorization (06 01)", "06 01 0A 04 00 00 00 00 12 34 49 09 78"),
                LogEntry(2, System.currentTimeMillis(), LogDirection.TX, "ACK", "80 00 00"),
            ),
            logFilePath = "/storage/emulated/0/Android/data/…/posmock_2026-08-12.log",
            isExporting = false,
            exportStatus = ExportStatus("Log copiato in Download/PosMock/posmock_2026-08-12.log", false),
            onClearClicked = {},
            onExportClicked = {},
        )
    }
}

/**
 * Anteprima dei due pulsanti da soli, uno dei quali in pulsazione: la loro
 * animazione si guarda qui, senza dover far partire un export vero sul telefono.
 */
@Preview(showBackground = true, name = "Pulsanti — export in corso")
@Composable
private fun ActionButtonsPreview() {
    PosMockTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                text = "Esporto…",
                icon = Icons.Filled.FileDownload,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                pulsing = true,
                onClick = {},
            )
            ActionButton(
                text = "Pulisci",
                icon = Icons.Filled.DeleteSweep,
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                onClick = {},
            )
        }
    }
}
