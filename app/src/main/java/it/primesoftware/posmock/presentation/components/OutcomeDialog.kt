package it.primesoftware.posmock.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.primesoftware.posmock.domain.model.MockOutcome
import it.primesoftware.posmock.domain.model.PendingDecision

/**
 * Il dialog della modalita' manuale: mostra la richiesta arrivata e aspetta.
 *
 * Non e' chiudibile toccando fuori ne' col tasto indietro: dall'altra parte c'e'
 * una connessione aperta in attesa, e un dialog chiuso per sbaglio la
 * lascerebbe appesa senza che si capisca perche'.
 */
@Composable
fun OutcomeDialog(
    decision: PendingDecision,
    onOutcomeChosen: (MockOutcome) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* la connessione aspetta: si esce solo scegliendo */ },
        title = { Text("Pagamento in arrivo") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = decision.request.formattedAmount,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${decision.request.kind} da ${decision.request.peer}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MockOutcome.presets().forEach { outcome ->
                            TextButton(
                                onClick = { onOutcomeChosen(outcome) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(outcome.label, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onOutcomeChosen(decision.defaultOutcome) }) {
                Text("Usa il preset (${decision.defaultOutcome.label})")
            }
        },
    )
}
