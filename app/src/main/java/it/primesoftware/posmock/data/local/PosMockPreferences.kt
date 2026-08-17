package it.primesoftware.posmock.data.local

import android.content.Context
import android.content.SharedPreferences
import it.primesoftware.posmock.domain.model.MockOutcome
import it.primesoftware.posmock.domain.model.MockProtocol
import it.primesoftware.posmock.domain.model.RawReplyMode
import it.primesoftware.posmock.domain.model.ServerConfig
import it.primesoftware.posmock.domain.model.DeclineReason
import it.primesoftware.posmock.domain.repository.IPreferencesRepository

/**
 * Configurazione su SharedPreferences.
 *
 * L'esito viene salvato come coppia (tipo, error id) invece che come nome di
 * enum: [MockOutcome.Decline] porta con se' un dato, e un solo campo non
 * basterebbe a ricostruirlo.
 */
class PosMockPreferences(context: Context) : IPreferencesRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): ServerConfig {
        val default = ServerConfig.DEFAULT
        // `IAE37_RAW` e' il nome che aveva la modalita' raw prima che IAE37
        // diventasse simulato per davvero: chi aveva gia' l'app installata se lo
        // ritrova salvato, e senza questa riga tornerebbe al default senza
        // capire perche'.
        val savedProtocol = prefs.getString(KEY_PROTOCOL, null)
            ?.let { if (it == LEGACY_IAE37_RAW) MockProtocol.RAW.name else it }
        val protocol = savedProtocol
            ?.let { name -> MockProtocol.entries.firstOrNull { it.name == name } }
            ?: default.protocol
        return ServerConfig(
            protocol = protocol,
            port = prefs.getInt(KEY_PORT, protocol.defaultPort),
            defaultOutcome = loadOutcome(default.defaultOutcome),
            responseDelayMs = prefs.getLong(KEY_DELAY, default.responseDelayMs),
            askEachTime = prefs.getBoolean(KEY_ASK_EACH_TIME, default.askEachTime),
            hangAfterRegistrationAck = prefs.getBoolean(
                KEY_HANG_AFTER_REG_ACK, default.hangAfterRegistrationAck
            ),
            rawReplyMode = prefs.getString(KEY_RAW_MODE, null)
                ?.let { name -> RawReplyMode.entries.firstOrNull { it.name == name } }
                ?: default.rawReplyMode,
            rawReplyHex = prefs.getString(KEY_RAW_HEX, default.rawReplyHex) ?: default.rawReplyHex,
        )
    }

    override fun save(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_PROTOCOL, config.protocol.name)
            .putInt(KEY_PORT, config.port)
            .putString(KEY_OUTCOME, outcomeKey(config.defaultOutcome))
            .putInt(KEY_OUTCOME_ERROR, (config.defaultOutcome as? MockOutcome.Decline)?.error?.zvtCode ?: -1)
            .putLong(KEY_DELAY, config.responseDelayMs)
            .putBoolean(KEY_ASK_EACH_TIME, config.askEachTime)
            .putBoolean(KEY_HANG_AFTER_REG_ACK, config.hangAfterRegistrationAck)
            .putString(KEY_RAW_MODE, config.rawReplyMode.name)
            .putString(KEY_RAW_HEX, config.rawReplyHex)
            .apply()
    }

    private fun loadOutcome(fallback: MockOutcome): MockOutcome =
        when (prefs.getString(KEY_OUTCOME, null)) {
            OUTCOME_APPROVE -> MockOutcome.Approve
            OUTCOME_NO_ACK -> MockOutcome.NoAck
            OUTCOME_HANG -> MockOutcome.HangAfterAck
            OUTCOME_DROP -> MockOutcome.DropConnection
            OUTCOME_DECLINE -> {
                val code = prefs.getInt(KEY_OUTCOME_ERROR, DeclineReason.CreditNotSufficient.zvtCode)
                val error = DeclineReason.entries.firstOrNull { it.zvtCode == code }
                    ?: DeclineReason.CreditNotSufficient
                MockOutcome.Decline(error)
            }
            else -> fallback
        }

    private fun outcomeKey(outcome: MockOutcome): String = when (outcome) {
        MockOutcome.Approve -> OUTCOME_APPROVE
        is MockOutcome.Decline -> OUTCOME_DECLINE
        MockOutcome.NoAck -> OUTCOME_NO_ACK
        MockOutcome.HangAfterAck -> OUTCOME_HANG
        MockOutcome.DropConnection -> OUTCOME_DROP
    }

    private companion object {
        const val PREFS_NAME = "PosMock_preferences"
        const val KEY_PROTOCOL = "protocol"
        const val KEY_PORT = "port"
        const val KEY_OUTCOME = "default_outcome"
        const val KEY_OUTCOME_ERROR = "default_outcome_error"
        const val KEY_DELAY = "response_delay_ms"
        const val KEY_ASK_EACH_TIME = "ask_each_time"
        const val KEY_HANG_AFTER_REG_ACK = "hang_after_registration_ack"
        const val KEY_RAW_MODE = "raw_reply_mode"
        const val KEY_RAW_HEX = "raw_reply_hex"

        /** Nome storico della modalita' raw, quando IAE37 non era simulato. */
        const val LEGACY_IAE37_RAW = "IAE37_RAW"

        const val OUTCOME_APPROVE = "approve"
        const val OUTCOME_DECLINE = "decline"
        const val OUTCOME_NO_ACK = "no_ack"
        const val OUTCOME_HANG = "hang"
        const val OUTCOME_DROP = "drop"
    }
}
