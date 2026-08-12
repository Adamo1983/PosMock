package it.primesoftware.posmock.data.server

import it.primesoftware.posmock.domain.model.MockOutcome
import it.primesoftware.posmock.domain.model.PaymentRequest
import it.primesoftware.posmock.domain.model.PendingDecision
import it.primesoftware.posmock.domain.repository.IConfigStore
import it.primesoftware.posmock.domain.repository.IOutcomeProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Sceglie l'esito: preset, o dialog che aspetta l'utente.
 *
 * L'attesa e' un [CompletableDeferred]: la coroutine che serve la connessione si
 * **sospende**, non blocca un thread, e resta cancellabile — cosi' lo stop del
 * server non lascia in giro connessioni appese a una decisione che non arrivera'
 * mai.
 *
 * Il mutex serializza le richieste: un terminale fisico serve una transazione
 * per volta, e due dialog sovrapposti non si saprebbe nemmeno a quale pagamento
 * si riferiscono.
 */
class OutcomeProviderImpl(
    private val configStore: IConfigStore,
) : IOutcomeProvider {

    private val _pending = MutableStateFlow<PendingDecision?>(null)
    override val pending: StateFlow<PendingDecision?> = _pending.asStateFlow()

    private val waiters = ConcurrentHashMap<Long, CompletableDeferred<MockOutcome>>()
    private val decisionMutex = Mutex()

    override suspend fun decide(request: PaymentRequest): MockOutcome {
        val config = configStore.config.value
        if (!config.askEachTime) return config.defaultOutcome

        return decisionMutex.withLock {
            val deferred = CompletableDeferred<MockOutcome>()
            waiters[request.id] = deferred
            _pending.value = PendingDecision(request, config.defaultOutcome)
            try {
                deferred.await()
            } finally {
                waiters.remove(request.id)
                _pending.value = null
            }
        }
    }

    override fun resolve(requestId: Long, outcome: MockOutcome) {
        waiters[requestId]?.complete(outcome)
    }
}
