package it.primesoftware.posmock.data.local

import it.primesoftware.posmock.domain.model.TerminalBrand
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

    private val _knownPorts = MutableStateFlow(withDefaults(preferences.loadKnownPorts()))
    override val knownPorts: StateFlow<List<Int>> = _knownPorts.asStateFlow()

    override fun update(config: ServerConfig) {
        _config.value = config
        preferences.save(config)
    }

    override fun rememberPort(port: Int) {
        if (port !in VALID_PORTS) return
        val updated = withDefaults(_knownPorts.value + port)
        if (updated == _knownPorts.value) return
        _knownPorts.value = updated
        preferences.saveKnownPorts(updated)
    }

    override fun forgetPort(port: Int) {
        if (port in DEFAULT_PORTS) return
        val updated = _knownPorts.value - port
        if (updated == _knownPorts.value) return
        _knownPorts.value = updated
        preferences.saveKnownPorts(updated)
    }

    /**
     * Le porte di serie dei terminali ci sono sempre, anche se non le si e' mai
     * battute: sono quelle scritte in `posList.cfg` e nella pagina hardware di
     * Giano, e una tendina da cui manca la 5577 o la 20007 costringerebbe a
     * ribatterle proprio nel caso piu' comune. Per lo stesso motivo [forgetPort]
     * si rifiuta di toglierle.
     *
     * Vengono da [TerminalBrand] e non dai protocolli: la porta e' della marca
     * del terminale, non del protocollo che ci parla sopra.
     */
    private fun withDefaults(ports: List<Int>): List<Int> =
        (ports + DEFAULT_PORTS).distinct().sorted()

    private companion object {
        val VALID_PORTS = 1..65535
        val DEFAULT_PORTS = TerminalBrand.entries.map { it.defaultPort }
    }
}
