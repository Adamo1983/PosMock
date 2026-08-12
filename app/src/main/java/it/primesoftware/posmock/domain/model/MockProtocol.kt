package it.primesoftware.posmock.domain.model

/**
 * Protocollo che il mock simula sulla porta in ascolto.
 *
 * [ZVT] e [IAE37] corrispondono ai due valori di `protocol.cfg` di
 * UniquePosManager, che a ogni pagamento sceglie quale bridge usare. [RAW] non
 * corrisponde a niente: e' lo sniffer, per guardare cosa passa sul filo quando
 * si sta indagando un protocollo che ancora non si conosce.
 */
enum class MockProtocol(
    val label: String,
    val defaultPort: Int,
    val description: String,
) {
    /**
     * ZVT, i terminali tedeschi (Ingenico :5577, CCV :20007). Simulazione
     * completa: registrazione, autorizzazione, esito.
     */
    ZVT(
        label = "ZVT",
        defaultPort = 20007,
        description = "Terminale tedesco (Ingenico/CCV). Registrazione, " +
            "autorizzazione ed esito.",
    ),

    /**
     * IAE37, i terminali italiani (Ingenico, :5577). Simulazione dei comandi che
     * il middleware usa davvero: status (il pre-flight) e pagamento.
     */
    IAE37(
        label = "IAE37",
        defaultPort = 5577,
        description = "Terminale italiano (Ingenico). Status di pre-flight, " +
            "pagamento ed esito.",
    ),

    /**
     * Nessuna simulazione: registra i byte in esadecimale e ASCII e risponde
     * secondo la regola scelta. Serve a studiare un protocollo sconosciuto, o a
     * vedere in chiaro cosa manda il middleware quando qualcosa non torna.
     */
    RAW(
        label = "Raw (sniffer)",
        defaultPort = 6000,
        description = "Nessuna logica di protocollo: registra i byte e risponde " +
            "come configurato.",
    ),
}
