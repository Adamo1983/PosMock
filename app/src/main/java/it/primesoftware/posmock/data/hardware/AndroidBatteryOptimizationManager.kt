package it.primesoftware.posmock.data.hardware

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import it.primesoftware.posmock.domain.hardware.BatteryOptimizationManager
import it.primesoftware.posmock.domain.model.LogDirection
import it.primesoftware.posmock.domain.repository.ILogRepository

/**
 * Implementazione Android di [BatteryOptimizationManager], ripresa da Ermes.
 *
 * - [isExempt] interroga `PowerManager.isIgnoringBatteryOptimizations(packageName)`.
 * - [requestExemption] lancia `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, che mostra
 *   il dialog di sistema. Se l'intent non e' risolvibile (OEM custom), si ripiega su
 *   `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, che apre la lista completa delle app.
 *
 * Nessuna eccezione esce di qui: l'esenzione e' una mitigazione, non un requisito
 * senza il quale l'app non puo' partire.
 */
class AndroidBatteryOptimizationManager(
    private val context: Context,
    private val log: ILogRepository,
) : BatteryOptimizationManager {

    private val powerManager: PowerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    override fun isExempt(): Boolean = try {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } catch (e: Exception) {
        log.log(LogDirection.ERROR, "Esenzione batteria: verifica fallita (${e.message})")
        false
    }

    @SuppressLint("BatteryLife")
    override fun requestExemption(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            log.log(
                LogDirection.ERROR,
                "Esenzione batteria: intent diretto fallito (${e.message}), apro le impostazioni",
            )
            try {
                activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                log.log(
                    LogDirection.ERROR,
                    "Esenzione batteria: fallito anche il ripiego (${e2.message})",
                )
            }
        }
    }
}
