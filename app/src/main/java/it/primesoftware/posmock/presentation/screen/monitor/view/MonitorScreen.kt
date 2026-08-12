package it.primesoftware.posmock.presentation.screen.monitor.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onExportClicked, enabled = !isExporting) {
                    Text(if (isExporting) "Esporto…" else "Esporta")
                }
                TextButton(onClick = onClearClicked) { Text("Pulisci") }
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
