package it.primesoftware.posmock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
        startForegroundWithNotification(buildNotification("Avvio in corso…"))
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serverController.stop()
            stopSelf()
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
        super.onDestroy()
    }

    private fun observeState() {
        scope.launch {
            serverController.state.collectLatest { state ->
                val text = when (state) {
                    is ServerState.Running ->
                        "${state.protocol.label} in ascolto sulla porta ${state.port}"

                    ServerState.Starting -> "Avvio in corso…"
                    ServerState.Stopped -> "Server fermo"
                    is ServerState.Error -> "Errore: ${state.message}"
                }
                notificationManager().notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
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

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
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
                Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Ferma", stop)
                    .build()
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
    }
}
