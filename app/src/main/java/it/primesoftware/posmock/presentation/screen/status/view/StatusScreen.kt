package it.primesoftware.posmock.presentation.screen.status.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.primesoftware.posmock.domain.model.MockProtocol
import it.primesoftware.posmock.domain.model.ServerState
import it.primesoftware.posmock.presentation.components.InfoRow
import it.primesoftware.posmock.presentation.components.SectionCard
import it.primesoftware.posmock.presentation.screen.status.viewModel.StatusUiState
import it.primesoftware.posmock.presentation.screen.status.viewModel.StatusViewModel
import it.primesoftware.posmock.presentation.theme.PosMockTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun StatusScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: StatusViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StatusScreen(
        uiState = uiState,
        onToggleServerClicked = viewModel::onToggleServerClicked,
        modifier = modifier,
    )
}

@Composable
fun StatusScreen(
    uiState: StatusUiState,
    onToggleServerClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { StateCard(uiState) }
        item { StartStopButton(uiState, onToggleServerClicked) }
        item { SummaryCard(uiState) }
    }
}

/** Lo stato del server, grande e colorato: si deve capire da lontano. */
@Composable
private fun StateCard(uiState: StatusUiState) {
    val state = uiState.serverState
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is ServerState.Running -> MaterialTheme.colorScheme.primaryContainer
                is ServerState.Error -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when (state) {
                    is ServerState.Running -> "In ascolto"
                    ServerState.Starting -> "Avvio in corso…"
                    ServerState.Stopped -> "Fermo"
                    is ServerState.Error -> "Errore"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (state is ServerState.Error) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }

            // L'indirizzo e' la riga che serve davvero: e' quella da ribattere in
            // posList.cfg lato middleware.
            InfoRow("Indirizzo del terminale", uiState.terminalAddress)
            if (uiState.isRunning) {
                InfoRow("Connessioni attive", uiState.activeConnections.toString())
            }
        }
    }
}

@Composable
private fun StartStopButton(uiState: StatusUiState, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        colors = if (uiState.isRunning) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Icon(
            imageVector = if (uiState.isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = if (uiState.isRunning) "  Ferma il servizio" else "  Avvia il servizio",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** Riepilogo di sola lettura: le scelte fatte nella schermata Configurazione. */
@Composable
private fun SummaryCard(uiState: StatusUiState) {
    val config = uiState.config
    SectionCard(title = "Configurazione") {
        InfoRow("Protocollo", config.protocol.label)
        InfoRow("Porta", config.port.toString())
        InfoRow(
            label = "Esito",
            value = if (config.askEachTime) "Lo scegli tu" else config.defaultOutcome.label,
        )
        if (!config.askEachTime) {
            InfoRow("Ritardo", "${config.responseDelayMs} ms")
        }
        if (config.protocol == MockProtocol.RAW) {
            InfoRow("Risposta raw", config.rawReplyMode.label)
        }
        // Si mostra solo quando e' acceso, e proprio per quello: e' l'unica opzione che
        // impedisce ai pagamenti di partire, e da qui si capisce subito perche'.
        if (config.hangAfterRegistrationAck && config.protocol == MockProtocol.ZVT) {
            InfoRow("Registrazione", "Muta dopo l'ACK")
        }
        Text(
            text = if (uiState.isRunning) {
                "Il servizio e' acceso: le modifiche alla porta e al protocollo " +
                    "valgono dal prossimo avvio."
            } else {
                "Si cambia dalla schermata Configurazione."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun StatusScreenPreview() {
    PosMockTheme {
        StatusScreen(
            uiState = StatusUiState(localIp = "192.168.1.50"),
            onToggleServerClicked = {},
        )
    }
}
