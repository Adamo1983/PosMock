package it.primesoftware.posmock.domain.repository

import it.primesoftware.posmock.domain.model.ServerConfig

/** Persistenza della configurazione fra un avvio e l'altro. */
interface IPreferencesRepository {

    fun load(): ServerConfig

    fun save(config: ServerConfig)

    /**
     * Le porte gia' usate, quelle che la schermata Configurazione offre nella
     * tendina.
     *
     * Stanno fuori da [ServerConfig] apposta: non sono una scelta con cui il
     * server parte — quella e' una sola, `port` — ma la memoria delle scelte
     * fatte prima. Metterle li' dentro le avrebbe portate nel riepilogo della
     * schermata Stato, dove non dicono niente su come partira' il servizio.
     */
    fun loadKnownPorts(): List<Int>

    fun saveKnownPorts(ports: List<Int>)
}
