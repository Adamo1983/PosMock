package it.primesoftware.posmock.data.server.protocol

import java.net.Socket

/**
 * Serve una connessione aperta dal middleware, dall'inizio alla fine.
 *
 * L'implementazione gira in una coroutine per connessione. Puo' sospendere
 * quanto vuole (attesa di una decisione manuale, ritardi voluti): a chiuderla e'
 * il server, che allo stop chiude le socket e fa saltare le letture in corso.
 */
interface ProtocolHandler {

    suspend fun handle(socket: Socket, peer: String)
}
