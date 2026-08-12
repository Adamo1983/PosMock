package it.primesoftware.posmock.presentation.activity

import androidx.lifecycle.ViewModel
import it.primesoftware.posmock.domain.model.MockOutcome
import it.primesoftware.posmock.domain.model.PendingDecision
import it.primesoftware.posmock.domain.repository.IOutcomeProvider
import kotlinx.coroutines.flow.StateFlow

/**
 * Stato che vale per tutta l'app: oggi la richiesta in attesa di una decisione
 * manuale, che deve poter comparire sopra qualunque schermata.
 */
class MainViewModel(
    private val outcomeProvider: IOutcomeProvider,
) : ViewModel() {

    val pendingDecision: StateFlow<PendingDecision?> = outcomeProvider.pending

    fun resolve(requestId: Long, outcome: MockOutcome) {
        outcomeProvider.resolve(requestId, outcome)
    }
}
