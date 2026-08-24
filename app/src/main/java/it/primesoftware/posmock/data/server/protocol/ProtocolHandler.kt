package it.primesoftware.posmock.data.server.protocol

import it.primesoftware.posmock.domain.model.LogDirection
import it.primesoftware.posmock.domain.model.ServerConfig
import it.primesoftware.posmock.domain.repository.ILogRepository
import kotlinx.coroutines.delay
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

/**
 * Serve una connessione aperta dal middleware, dall'inizio alla fine.
 *
 * L'implementazione gira in una coroutine per connessione. Puo' sospendere
 * quanto vuole (attesa di una decisione manuale, ritardi voluti): a chiuderla e'
 * il server, che allo stop chiude le socket e fa saltare le letture in corso.
 *
 * ⚠️ Chi la implementa vedra' l'ispezione *Possibly blocking call in non-blocking
 * context* su `socket.getInputStream()`, `getOutputStream()` e `close()`, ed e'
 * un **falso positivo** su tutti e tre: i due getter controllano lo stato del
 * socket e restituiscono lo stream, e `close()` chiude il descrittore e torna —
 * aspetterebbe solo con `SO_LINGER`, che qui non viene mai impostato. Sono
 * marcati perche' dichiarano `IOException`, che e' tutto quello che l'ispezione
 * guarda. In piu' `handle()` gira sempre sullo scope `Dispatchers.IO` del
 * `MockServerController`, cosa che l'ispezione non puo' vedere perche' legge il
 * contesto lessicale, non da dove parte la coroutine.
 *
 * Da qui il `@file:Suppress` in testa alle implementazioni: i punti da zittire
 * sono sparsi in mezza classe, e cinque annotazioni sparpagliate si leggono
 * peggio di una riga sola. Il prezzo e' che li' dentro l'IDE non fa piu' da rete:
 * a bloccare sono `read` e `write`, e **quelle vanno dentro
 * `withContext(Dispatchers.IO)`** — per come funzionano, non per far tacere
 * l'ispezione, che ormai tace comunque.
 */
interface ProtocolHandler {

    suspend fun handle(socket: Socket, peer: String)
}

/**
 * L'attesa configurata, da consumare **dopo** che l'esito e' stato deciso e
 * prima di mandarlo sul filo.
 *
 * Il "dopo" conta solo in modalita' manuale, ed e' voluto: il dialog deve
 * comparire subito, cosi' si sceglie mentre si ricorda ancora quale prova si sta
 * facendo, e il ritardo diventa una coda misurabile che parte da un istante
 * noto. Col preset non cambia niente — li' l'esito e' gia' deciso e `decide()`
 * torna senza sospendere.
 *
 * Sta qui e non nei due handler perche' e' la stessa attesa per tutti i
 * protocolli; dove metterla dentro il dialogo, invece, e' affare di ciascuno
 * (sullo ZVT va coperta dal keep-alive, su IAE37 non c'e' niente da coprire).
 */
internal suspend fun awaitResponseDelay(config: ServerConfig, log: ILogRepository) {
    if (config.responseDelayMs <= 0) return
    log.log(LogDirection.INFO, "Attendo ${config.responseDelayMs} ms prima di rispondere")
    delay(config.responseDelayMs.milliseconds)
}
