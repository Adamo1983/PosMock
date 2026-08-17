package it.primesoftware.posmock.presentation.screen.config.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.primesoftware.posmock.domain.model.MockOutcome
import it.primesoftware.posmock.domain.model.MockProtocol
import it.primesoftware.posmock.domain.model.RawReplyMode
import it.primesoftware.posmock.presentation.components.OptionRow
import it.primesoftware.posmock.presentation.components.SectionCard
import it.primesoftware.posmock.presentation.screen.config.viewModel.ConfigAction
import it.primesoftware.posmock.presentation.screen.config.viewModel.ConfigUiState
import it.primesoftware.posmock.presentation.screen.config.viewModel.ConfigViewModel
import it.primesoftware.posmock.presentation.theme.PosMockTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun ConfigScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: ConfigViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ConfigScreen(uiState = uiState, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun ConfigScreen(
    uiState: ConfigUiState,
    onAction: (ConfigAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            // L'activity e' edge-to-edge, quindi `adjustResize` del manifest non
            // rimpicciolisce piu' la finestra all'apertura della tastiera: senza
            // questo, l'IME copre il campo del ritardo invece di farlo scorrere
            // in alto. Con la lista accorciata, il campo che prende il fuoco ci
            // scorre da solo.
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        if (uiState.isServerRunning) {
            item { RunningNotice() }
        }
        item { ProtocolCard(uiState, onAction) }
        item { OutcomeCard(uiState, onAction) }
        if (uiState.config.protocol == MockProtocol.RAW) {
            item { RawModeCard(uiState, onAction) }
        }
    }
}

/**
 * Le modifiche non vengono bloccate col servizio acceso — si puo' preparare la
 * prossima prova mentre gira quella in corso — ma va detto chiaro che porta e
 * protocollo valgono dal prossimo avvio, altrimenti si cambia porta e ci si
 * chiede perche' il middleware bussa ancora alla vecchia.
 */
@Composable
private fun RunningNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Text(
            text = "Il servizio e' acceso. Esito e ritardo valgono subito; porta e " +
                "protocollo dal prossimo avvio.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ProtocolCard(uiState: ConfigUiState, onAction: (ConfigAction) -> Unit) {
    SectionCard(title = "Protocollo da simulare") {
        MockProtocol.entries.forEach { protocol ->
            OptionRow(
                selected = uiState.config.protocol == protocol,
                onClick = { onAction(ConfigAction.ProtocolSelected(protocol)) },
                title = protocol.label,
                subtitle = protocol.description,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        OutlinedTextField(
            value = uiState.portText,
            onValueChange = { onAction(ConfigAction.PortChanged(it)) },
            label = { Text("Porta in ascolto") },
            supportingText = {
                Text(
                    if (uiState.isPortValid) {
                        "Ingenico usa la 5577, CCV la 20007"
                    } else {
                        "Porta non valida (1-65535): resta quella di prima"
                    }
                )
            },
            isError = !uiState.isPortValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OutcomeCard(uiState: ConfigUiState, onAction: (ConfigAction) -> Unit) {
    SectionCard(title = "Come rispondere") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Chiedi a ogni pagamento", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Il terminale manda subito l'ACK e poi aspetta la tua scelta",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = uiState.config.askEachTime,
                onCheckedChange = { onAction(ConfigAction.AskEachTimeChanged(it)) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = if (uiState.config.askEachTime) {
                "Esito proposto nel dialog"
            } else {
                "Esito automatico"
            },
            style = MaterialTheme.typography.titleSmall,
        )
        MockOutcome.presets().forEach { outcome ->
            OptionRow(
                selected = uiState.config.defaultOutcome == outcome,
                onClick = { onAction(ConfigAction.OutcomeSelected(outcome)) },
                title = outcome.label,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        OutlinedTextField(
            value = uiState.delayText,
            onValueChange = { onAction(ConfigAction.DelayChanged(it)) },
            label = { Text("Ritardo prima di rispondere (ms)") },
            supportingText = {
                Text(
                    if (uiState.isDelayValid) {
                        "Serve a provare i timeout: il middleware molla a 60s, il palmare a 90s"
                    } else {
                        "Valore non valido (0-600000): resta quello di prima"
                    }
                )
            },
            isError = !uiState.isDelayValid,
            enabled = !uiState.config.askEachTime,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        // Sta in fondo e con un avviso perche' e' l'unica opzione che agisce PRIMA del
        // pagamento: lasciata accesa per sbaglio, ogni prova successiva fallisce alla
        // registrazione e sembra un guasto della cassa.
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Muto dopo l'ACK di registrazione", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Solo ZVT. Il terminale ACKa la registrazione e poi tace: " +
                        "la cassa si blocca prima ancora di mandare il pagamento",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (uiState.config.hangAfterRegistrationAck) {
                    Text(
                        text = "Acceso: nessun pagamento partira'. Spegnilo per gli altri test.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Switch(
                checked = uiState.config.hangAfterRegistrationAck,
                onCheckedChange = { onAction(ConfigAction.HangAfterRegistrationAckChanged(it)) },
            )
        }
    }
}

@Composable
private fun RawModeCard(uiState: ConfigUiState, onAction: (ConfigAction) -> Unit) {
    SectionCard(title = "Risposta in modalita' raw") {
        Text(
            text = "Nessuna logica di protocollo: i byte si registrano e basta. " +
                "Il log della schermata Traffico mostra esadecimale e ASCII.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RawReplyMode.entries.forEach { mode ->
            OptionRow(
                selected = uiState.config.rawReplyMode == mode,
                onClick = { onAction(ConfigAction.RawModeSelected(mode)) },
                title = mode.label,
                subtitle = mode.description,
            )
        }
        if (uiState.config.rawReplyMode == RawReplyMode.FIXED_HEX) {
            OutlinedTextField(
                value = uiState.config.rawReplyHex,
                onValueChange = { onAction(ConfigAction.RawHexChanged(it)) },
                label = { Text("Risposta (esadecimale)") },
                placeholder = { Text("02 30 30 03") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ConfigScreenPreview() {
    PosMockTheme {
        ConfigScreen(uiState = ConfigUiState(isServerRunning = true), onAction = {})
    }
}
