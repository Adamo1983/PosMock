package it.primesoftware.posmock.presentation.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import it.primesoftware.posmock.domain.hardware.BatteryOptimizationManager
import it.primesoftware.posmock.domain.model.LogDirection
import it.primesoftware.posmock.domain.repository.ILogRepository
import it.primesoftware.posmock.presentation.theme.PosMockTheme
import org.koin.android.ext.android.inject

/**
 * Unica activity dell'app.
 *
 * Due accorgimenti ripresi da Ermes, per il motivo rovesciato: li' servono a non
 * perdere la connessione con Giano, qui a non far sparire il **terminale
 * simulato**. Un mock che smette di rispondere dopo qualche minuto di schermo
 * spento produce gli stessi sintomi del guasto che si sta indagando (POS
 * occupato, esito che non arriva), e si finisce a cercare il bug dalla parte
 * sbagliata.
 *
 * 1. `FLAG_KEEP_SCREEN_ON`: l'activity resta RESUMED e il processo non viene
 *    congelato.
 * 2. Esenzione dalle ottimizzazioni di batteria, chiesta a ogni avvio finche'
 *    non e' concessa.
 *
 * Il permesso notifiche invece serve alla notifica del foreground service:
 * senza, il service parte lo stesso ma non si vede — e con essa sparisce
 * l'unico modo di accorgersi che il banco e' acceso e di spegnerlo da fuori.
 */
class MainActivity : ComponentActivity() {

    private val batteryOptimizationManager: BatteryOptimizationManager by inject()
    private val log: ILogRepository by inject()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Concesso o no, si prosegue: il passo dopo e' comunque l'esenzione batteria.
            requestBatteryOptimizationExemptionIfNeeded()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        requestPermissions()
        setContent {
            PosMockTheme {
                PosMockApp()
            }
        }
    }

    /**
     * Un permesso per volta, poi la batteria: i dialog di sistema uno sopra
     * l'altro si coprirebbero, quindi il successivo parte dal callback del
     * precedente.
     *
     * I due permessi runtime non convivono mai: le notifiche si chiedono da
     * Android 13, la scrittura su Download fino ad Android 9 (dopo c'e'
     * MediaStore, che non chiede niente).
     */
    private fun requestPermissions() {
        val pending = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission(Manifest.permission.POST_NOTIFICATIONS) ->
                Manifest.permission.POST_NOTIFICATIONS

            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ->
                Manifest.permission.WRITE_EXTERNAL_STORAGE

            else -> null
        }

        if (pending != null) {
            permissionLauncher.launch(pending)
        } else {
            requestBatteryOptimizationExemptionIfNeeded()
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * L'esenzione e' un requisito, non un'opzione: la sorgente di verita' e' lo
     * stato del sistema (`isExempt()`), quindi il dialog ricompare a ogni avvio
     * finche' l'utente non concede. Vale sia al primo avvio sia dopo un reset
     * delle impostazioni fatto dall'OEM.
     */
    private fun requestBatteryOptimizationExemptionIfNeeded() {
        if (batteryOptimizationManager.isExempt()) {
            log.log(LogDirection.INFO, "Ottimizzazioni batteria: app gia' esente")
            return
        }
        log.log(LogDirection.INFO, "Ottimizzazioni batteria: chiedo l'esenzione")
        batteryOptimizationManager.requestExemption(this)
    }
}
