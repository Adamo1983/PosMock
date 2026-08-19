package it.primesoftware.posmock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import it.primesoftware.posmock.R
import it.primesoftware.posmock.domain.model.ServerState
import it.primesoftware.posmock.domain.repository.IServerController
import it.primesoftware.posmock.presentation.activity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Foreground service che tiene vivo il processo mentre il server e' acceso.
 *
 * Non ospita il server (quello vive nel [it.primesoftware.posmock.data.server.MockServerController],
 * singleton di processo): serve a impedire che Android uccida il processo con
 * l'app in background, cosa che chiuderebbe la socket a meta' transazione — e
 * mandare a monte proprio la prova che si sta facendo.
 *
 * La notifica riporta stato e connessioni attive, e ha un'azione "Ferma" per
 * spegnere il banco senza riaprire l'app.
 */
class MockServerService : Service() {

    private val serverController: IServerController by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // L'errore della sessione precedente e' storia vecchia: se restasse in
        // tendina accanto alla notifica del server appena riacceso direbbe due
        // cose in contraddizione.
        notificationManager().cancel(ERROR_NOTIFICATION_ID)
        startForegroundWithNotification(buildNotification("Avvio in corso…"))
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // stopSelf() dopo l'arresto, non prima: se il service morisse subito,
            // il suo onDestroy cancellerebbe lo scope in cui gira la pulizia. Di
            // norma non ci arriva nemmeno, perche' e' stop() stessa a fermare il
            // service alla fine; serve al caso in cui non ci sia niente da fermare.
            scope.launch {
                serverController.stop()
                stopSelf()
            }
            return START_NOT_STICKY
        }
        // START_NOT_STICKY: se il sistema ci uccide non ha senso resuscitare il
        // service da solo, perche' il server e la sua configurazione vivono nel
        // processo che e' morto con lui. Meglio banco spento e visibile che
        // notifica accesa su un server che non c'e' piu'.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        // Rete di sicurezza per l'ordine opposto: se e' l'onDestroy ad arrivare
        // per primo, il collector viene cancellato e il ramo Stopped non gira mai.
        clearNotification()
        super.onDestroy()
    }

    private fun observeState() {
        scope.launch {
            serverController.state.collectLatest { state ->
                when (state) {
                    is ServerState.Running ->
                        updateNotification(
                            "${state.protocol.label} in ascolto sulla porta ${state.port}"
                        )

                    ServerState.Starting -> updateNotification("Avvio in corso…")
                    // Niente notifica di "fermo": il service esiste solo finche' c'e'
                    // un server da tenere vivo, e una notifica ripubblicata qui
                    // sopravvive al service — vedi removeNotificationAndStop().
                    ServerState.Stopped -> removeNotificationAndStop()
                    is ServerState.Error -> reportErrorAndStop(state.message)
                }
            }
        }
    }

    private fun updateNotification(text: String) {
        notificationManager().notify(NOTIFICATION_ID, buildNotification(text))
    }

    /**
     * Server fermo: il service non ha piu' niente da tenere vivo e si toglie di
     * mezzo, notifica compresa.
     *
     * Toglierla **qui** e non lasciar fare al sistema e' la differenza fra una
     * tendina pulita e un'icona che resta accesa a banco spento. `stop()` porta
     * lo stato a Stopped e subito dopo chiama `stopService()`: ActivityManager
     * cancella la notifica del foreground service e solo **dopo** consegna
     * l'onDestroy: se in quella finestra il collector ripubblicasse la notifica,
     * questa resterebbe in tendina senza piu' un service dietro — e con
     * `setOngoing(true)` non la si scarta nemmeno con lo swipe.
     */
    private fun removeNotificationAndStop() {
        clearNotification()
        stopSelf()
    }

    /** Solo la notifica del service: quella d'errore ha un altro id e deve restare. */
    private fun clearNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager().cancel(NOTIFICATION_ID)
    }

    /**
     * Il server e' morto per conto suo: il service non ha piu' niente da tenere
     * vivo e si toglie di mezzo.
     *
     * L'errore pero' deve restare sotto gli occhi anche a schermo spento — un
     * banco che sparisce in silenzio e' esattamente il modo in cui si finisce a
     * cercare il guasto dalla parte sbagliata. Per questo la notifica d'errore e'
     * **separata** da quella del foreground service: quella viene portata via
     * dal sistema quando il service muore, questa no. Ed e' scartabile, perche'
     * a differenza dell'altra non c'e' piu' niente da fermare.
     */
    private fun reportErrorAndStop(message: String) {
        notificationManager().notify(ERROR_NOTIFICATION_ID, buildErrorNotification(message))
        stopSelf()
    }

    private fun startForegroundWithNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildErrorNotification(message: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("${getString(R.string.app_name)}: server fermato")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()

    private fun buildNotification(text: String): Notification {
        val openApp = openAppIntent()
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MockServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stop),
                    "Ferma",
                    stop,
                ).build()
            )
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Terminale simulato",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Stato del server che simula il POS"
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_STOP = "it.primesoftware.posmock.STOP_SERVER"
        private const val CHANNEL_ID = "posmock_server"
        private const val NOTIFICATION_ID = 1

        // Id distinto: la notifica del foreground service se ne va con il service,
        // e l'errore deve sopravvivergli.
        private const val ERROR_NOTIFICATION_ID = 2
    }
}
