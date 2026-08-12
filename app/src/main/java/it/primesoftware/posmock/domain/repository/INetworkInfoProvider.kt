package it.primesoftware.posmock.domain.repository

/**
 * Indirizzo con cui il middleware deve raggiungere il mock: e' quello da
 * scrivere in `posList.cfg`, quindi va mostrato ben visibile.
 */
interface INetworkInfoProvider {

    fun localIpAddress(): String?
}
