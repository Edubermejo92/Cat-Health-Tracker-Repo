package padelpulseapp2.netlify.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

/**
 * Actividad en curso del partido.
 *
 * Wear OS exige que, mientras hay algo "en marcha" (aqui: un partido con el
 * cronometro corriendo), la app publique una Ongoing Activity. Eso es lo que
 * hace que el partido aparezca en la esfera del reloj, en el carrusel de
 * tarjetas y en recientes, y que un toque devuelva al marcador.
 *
 * Sin esto Google Play rechaza la app: "Directrices de calidad de Wear OS:
 * Falta la actividad en curso".
 */
class MatchOngoingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
        return START_STICKY
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    private fun buildNotification(): Notification {
        createChannel()

        val engine = MainActivity.gameEngine
        val es = engine?.lang == "es"

        // Toque -> vuelve al marcador
        val touchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (es) "Partido en curso" else "Match in progress"
        val scoreText = scoreSummary(engine)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ongoing_match)
            .setContentTitle(title)
            .setContentText(scoreText)
            .setContentIntent(touchIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // El cronometro lo pinta el sistema y corre solo: no hay que estar
        // republicando la notificacion cada segundo.
        val timeZero = SystemClock.elapsedRealtime() -
            ((MainActivity.instance?.matchTimeSeconds ?: 0) * 1000L)

        val status = Status.Builder()
            .addTemplate("#score# · #time#")
            .addPart("score", Status.TextPart(scoreText))
            .addPart("time", Status.StopwatchPart(timeZero))
            .build()

        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_ongoing_match)
            .setTouchIntent(touchIntent)
            .setStatus(status)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
            .apply(applicationContext)

        return builder.build()
    }

    private fun scoreSummary(engine: GameEngine?): String {
        if (engine == null) return "0-0"
        val points = if (engine.isTb || engine.isSuperTbActive()) {
            "${engine.tbPtsA}-${engine.tbPtsB}"
        } else {
            "${engine.getScoreStr("A")}-${engine.getScoreStr("B")}"
        }
        return "${engine.setsA}-${engine.setsB} · ${engine.gamesA}-${engine.gamesB} · $points"
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Partido en curso",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Marcador del partido mientras se juega"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "padelpulse_match"
        private const val NOTIFICATION_ID = 4821
        private const val ACTION_STOP = "padelpulse.STOP_ONGOING"

        /** Arranca o refresca la actividad en curso (marcador actualizado). */
        fun start(context: Context) {
            val intent = Intent(context, MatchOngoingService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /** La quita: partido terminado o reiniciado. */
        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, MatchOngoingService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
