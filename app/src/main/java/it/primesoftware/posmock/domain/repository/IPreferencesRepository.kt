package it.primesoftware.posmock.domain.repository

import it.primesoftware.posmock.domain.model.ServerConfig

/** Persistenza della configurazione fra un avvio e l'altro. */
interface IPreferencesRepository {

    fun load(): ServerConfig

    fun save(config: ServerConfig)
}
