package it.primesoftware.posmock.domain.hardware

import android.app.Activity

/**
 * Gestisce l'esenzione dell'app dalle ottimizzazioni di batteria di Android
 * (Doze, App Standby, restrizioni OEM aggressive).
 *
 * Stessa scelta di Ermes, per lo stesso motivo rovesciato: li' senza esenzione
 * cade la connessione con Giano, qui cade il **terminale simulato** — e un mock
 * che smette di rispondere dopo qualche minuto di schermo spento produce
 * esattamente i sintomi che si stanno indagando (POS occupato, esito che non
 * arriva). Il banco di prova deve essere l'ultima cosa che si sospetta.
 *
 * Implementazione concreta in `AndroidBatteryOptimizationManager`. Richiede
 * `<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />`
 * nel manifest.
 */
interface BatteryOptimizationManager {

    /** True se l'app e' gia' esente dalle ottimizzazioni di batteria. */
    fun isExempt(): Boolean

    /**
     * Apre il dialog di sistema che chiede all'utente di esentare l'app.
     * Non c'e' callback: per sapere com'e' andata si richiama [isExempt].
     */
    fun requestExemption(activity: Activity)
}
