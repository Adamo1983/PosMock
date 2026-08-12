package it.primesoftware.posmock.data.local

import it.primesoftware.posmock.domain.model.ServerConfig
import it.primesoftware.posmock.domain.repository.IConfigStore
import it.primesoftware.posmock.domain.repository.IPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Configurazione in memoria, riletta all'avvio e riscritta a ogni modifica. */
class ConfigStore(private val preferences: IPreferencesRepository) : IConfigStore {

    private val _config = MutableStateFlow(preferences.load())
    override val config: StateFlow<ServerConfig> = _config.asStateFlow()

    override fun update(config: ServerConfig) {
        _config.value = config
        preferences.save(config)
    }
}
