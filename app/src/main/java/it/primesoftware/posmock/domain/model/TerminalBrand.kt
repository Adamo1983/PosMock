package it.primesoftware.posmock.domain.model

/**
 * Le marche di terminale con la porta su cui ascoltano di serie.
 *
 * ⚠️ **La porta dipende dalla marca, non dal protocollo.** Un Ingenico sta sulla
 * 5577 sia che parli ZVT sia che parli IAE37; un CCV sta sulla 20007 e parla
 * ZVT. Sono due assi diversi, e confonderli manda fuori strada chi cerca la
 * porta giusta: la coppia "Ingenico + ZVT" e' reale e vuole la 5577, che con la
 * porta legata al protocollo si sarebbe dovuta ribattere ogni volta.
 *
 * [MockProtocol.defaultPort] resta la porta **proposta** quando si cambia
 * protocollo — un'ipotesi comoda, non una regola — e chi ha l'altra marca la
 * ripesca dalla tendina in un tocco.
 */
enum class TerminalBrand(val label: String, val defaultPort: Int) {
    INGENICO("Ingenico", 5577),
    CCV("CCV", 20007),
}
