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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.primesoftware.posmock.domain.model.MockOutcome
import it.primesoftware.posmock.domain.model.MockProtocol
import it.primesoftware.posmock.domain.model.TerminalBrand
//import it.primesoftware.posmock.domain.model.RawReplyMode
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
//        if (uiState.config.protocol == MockProtocol.RAW) {
//            item { RawModeCard(uiState, onAction) }
//        }
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

        PortField(uiState, onAction)
    }
}

/**
 * Campo della porta: si scrive a mano **oppure** si sceglie dalla tendina.
 *
 * Le due strade sono lo stesso gesto in due momenti diversi del banco — la prima
 * volta una porta si batte, dalla seconda in poi si ripesca — quindi un solo
 * campo, editabile, con l'elenco appeso alla freccia. Una tendina e basta non
 * basterebbe: la porta della prossima prova puo' essere una qualsiasi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortField(uiState: ConfigUiState, onAction: (ConfigAction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = uiState.portText,
            onValueChange = { onAction(ConfigAction.PortChanged(it)) },
            label = { Text("Porta in ascolto") },
            supportingText = {
                Text(
                    if (uiState.isPortValid) {
                        "Ingenico usa la 5577, CCV la 20007. La freccia apre le porte " +
                            "gia' usate; una porta nuova ci entra da sola."
                    } else {
                        "Porta non valida (1-65535): resta quella di prima"
                    }
                )
            },
            isError = !uiState.isPortValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            // "Fine" chiude la tastiera e toglie il fuoco: il salvataggio in
            // tendina passa da li', senza doverlo scrivere due volte.
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                    // Ancora secondaria: sul campo editabile il tocco serve a
                    // scrivere, quindi la tendina la apre solo la freccia.
                    modifier = Modifier.menuAnchor(
                        ExposedDropdownMenuAnchorType.SecondaryEditable
                    ),
                )
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth()
                // Il fuoco che se ne va e' il momento in cui la porta battuta a
                // mano si considera scelta. Non prima: la config si aggiorna a
                // ogni cifra, e memorizzare li' riempirebbe la tendina di 5, 55
                // e 557 sulla strada per 5577.
                .onFocusChanged { if (!it.isFocused) onAction(ConfigAction.PortCommitted) },
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            uiState.knownPorts.forEach { port ->
                val brand = TerminalBrand.entries.firstOrNull { it.defaultPort == port }
                val trailing: (@Composable () -> Unit)? = when {
                    // Le porte di serie restano riconoscibili e non si possono
                    // togliere: sono quelle di posList.cfg e della pagina
                    // hardware di Giano. L'etichetta e' la **marca**, non il
                    // protocollo: un Ingenico sta sulla 5577 anche parlando ZVT.
                    brand != null -> {
                        {
                            Text(
                                text = brand.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Nemmeno la porta in uso: il campo la rimetterebbe in
                    // elenco al primo cambio di fuoco, e una X che non toglie
                    // niente sembra un difetto. Prima se ne sceglie un'altra.
                    port == uiState.config.port -> null

                    else -> {
                        {
                            IconButton(onClick = { onAction(ConfigAction.PortForgotten(port)) }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Togli la porta $port dall'elenco",
                                )
                            }
                        }
                    }
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = port.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (port == uiState.config.port) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    },
                    onClick = {
                        onAction(ConfigAction.PortPicked(port))
                        expanded = false
                    },
                    trailingIcon = trailing,
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
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
                // In manuale il ritardo vale lo stesso, ma dopo la scelta: senza
                // dirlo, si tocca il dialog e sembra che non sia successo niente.
                val quando = if (uiState.config.askEachTime) {
                    "In manuale parte dopo la scelta: prima compare il dialog, poi il mock " +
                        "aspetta e risponde. "
                } else {
                    ""
                }
                Text(
                    if (uiState.isDelayValid) {
                        quando +
                            "Serve alla transazione lenta ma viva. Chi aspetta quanto: " +
                            "UniquePosManager l'attesa configurata sul POS (default 180 s), " +
                            "Ermes la promessa del middleware (GET /pos?v=2) + 15 s, " +
                            "Giano 180 s default alla registrazione del dispositivo."
                    } else {
                        "Valore non valido (0-600000): resta quello di prima"
                    }
                )
            },
            isError = !uiState.isDelayValid,
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

//@Composable
//private fun RawModeCard(uiState: ConfigUiState, onAction: (ConfigAction) -> Unit) {
//    SectionCard(title = "Risposta in modalita' raw") {
//        Text(
//            text = "Nessuna logica di protocollo: i byte si registrano e basta. " +
//                "Il log della schermata Traffico mostra esadecimale e ASCII.",
//            style = MaterialTheme.typography.bodySmall,
//            color = MaterialTheme.colorScheme.onSurfaceVariant,
//        )
//        RawReplyMode.entries.forEach { mode ->
//            OptionRow(
//                selected = uiState.config.rawReplyMode == mode,
//                onClick = { onAction(ConfigAction.RawModeSelected(mode)) },
//                title = mode.label,
//                subtitle = mode.description,
//            )
//        }
//        if (uiState.config.rawReplyMode == RawReplyMode.FIXED_HEX) {
//            OutlinedTextField(
//                value = uiState.config.rawReplyHex,
//                onValueChange = { onAction(ConfigAction.RawHexChanged(it)) },
//                label = { Text("Risposta (esadecimale)") },
//                placeholder = { Text("02 30 30 03") },
//                singleLine = true,
//                modifier = Modifier.fillMaxWidth(),
//            )
//        }
//    }
//}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ConfigScreenPreview() {
    PosMockTheme {
        ConfigScreen(uiState = ConfigUiState(isServerRunning = true), onAction = {})
    }
}
