package it.primesoftware.posmock.domain.repository

import it.primesoftware.posmock.domain.model.MockOutcome
import it.primesoftware.posmock.domain.model.PaymentRequest
import it.primesoftware.posmock.domain.model.PendingDecision
import kotlinx.coroutines.flow.StateFlow

/**
 * Decide come rispondere a una richiesta.
 *
 * Con `askEachTime` spento applica il preset; acceso, pubblica la richiesta su
 * [pending] e **sospende** finche' la UI non chiama [resolve]. Sospende, non
 * blocca: la coroutine che serve la connessione resta cancellabile, cosi' lo
 * stop del server non lascia thread appesi.
 */
interface IOutcomeProvider {

    val pending: StateFlow<PendingDecision?>

    suspend fun decide(request: PaymentRequest): MockOutcome

    fun resolve(requestId: Long, outcome: MockOutcome)
}
