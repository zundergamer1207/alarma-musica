package com.oscarpolonio.alarma

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.Calendar

/**
 * Servicio en primer plano que suena cuando llega la hora.
 * Usa USAGE_ALARM, así que respeta el volumen de alarma del sistema
 * y suena aunque el teléfono esté en silencio para notificaciones.
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_RING = "com.oscarpolonio.alarma.RING"
        const val ACTION_STOP = "com.oscarpolonio.alarma.STOP"
        const val ACTION_SNOOZE = "com.oscarpolonio.alarma.SNOOZE"
        const val CHANNEL = "canal_alarma"
        const val NOTI_ID = 4242
        const val SNOOZE_MIN = 5

        /** Alarma que está sonando ahora mismo (la lee RingActivity). */
        var ringingId: Long = -1L
    }

    private var player: MediaPlayer? = null
    private var wake: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var fadeRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                snooze()
                return START_NOT_STICKY
            }
        }

        val id = intent?.getLongExtra("id", -1L) ?: -1L
        val alarm = AlarmStore.byId(this, id)
        if (alarm == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        ringingId = alarm.id
        startForegroundSafe(buildNotification(alarm))
        acquireWake()
        playMusic(alarm)
        startVibration()
        prepareNextRun(alarm)

        return START_STICKY
    }

    // ---------------- notificación a pantalla completa ----------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Alarmas", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Avisos cuando suena una alarma"
        ch.setSound(null, null)          // el sonido lo pone el servicio
        ch.enableVibration(false)        // la vibración también
        ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        ch.setBypassDnd(true)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(a: Alarm): Notification {
        createChannel()

        val full = PendingIntent.getActivity(
            this, 1,
            Intent(this, RingActivity::class.java)
                .putExtra("id", a.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snooze = PendingIntent.getService(
            this, 3, Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarma)
            .setContentTitle("⏰ ${a.timeText()} · ${a.label}")
            .setContentText("♪ ${a.soundName}")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)

        // Con la ruleta activa, apagar desde la notificación sería hacer trampa
        if (a.roulette == 0) b.addAction(0, "Apagar", stop)
        b.addAction(0, "Posponer $SNOOZE_MIN min", snooze)

        return b.build()
    }

    private fun startForegroundSafe(n: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this, NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTI_ID, n)
            }
        } catch (e: Exception) {
            startForeground(NOTI_ID, n)
        }
    }

    // ---------------- sonido ----------------

    private fun playMusic(a: Alarm) {
        val vol = (a.volume.coerceIn(5, 100)) / 100f
        val uri: Uri = try {
            if (a.soundUri.isNullOrEmpty()) defaultAlarmUri() else Uri.parse(a.soundUri)
        } catch (e: Exception) {
            defaultAlarmUri()
        }

        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = a.loop
                prepare()
                val start = if (a.fade) (vol * 0.08f).coerceAtLeast(0.02f) else vol
                setVolume(start, start)
                start()
            }
            if (a.fade) startFade(vol)
        } catch (e: Exception) {
            // Si la canción elegida ya no está disponible, usamos el tono del sistema
            try {
                player?.release()
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmService, defaultAlarmUri())
                    isLooping = true
                    prepare()
                    setVolume(vol, vol)
                    start()
                }
            } catch (e2: Exception) {
            }
        }
    }

    private fun defaultAlarmUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private fun startFade(target: Float) {
        var v = (target * 0.08f).coerceAtLeast(0.02f)
        val step = target / 22f
        val r = object : Runnable {
            override fun run() {
                v += step
                if (v >= target) v = target
                try {
                    player?.setVolume(v, v)
                } catch (e: Exception) {
                }
                if (v < target) handler.postDelayed(this, 900L)
            }
        }
        fadeRunnable = r
        handler.postDelayed(r, 900L)
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 500, 700)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
        }
    }

    private fun acquireWake() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "alarma:sonando"
            ).also { it.acquire(10 * 60 * 1000L) }
        } catch (e: Exception) {
        }
    }

    // ---------------- qué pasa después de sonar ----------------

    private fun prepareNextRun(a: Alarm) {
        if (a.temp) {
            AlarmStore.delete(this, a.id)
            AlarmScheduler.cancel(this, a.id)
            return
        }
        if (a.days.isEmpty()) {
            a.enabled = false
            AlarmStore.upsert(this, a)
            AlarmScheduler.cancel(this, a.id)
        } else {
            AlarmScheduler.schedule(this, a)
        }
    }

    private fun snooze() {
        val a = AlarmStore.byId(this, ringingId)
        val base = a ?: Alarm()
        val c = Calendar.getInstance()
        c.add(Calendar.MINUTE, SNOOZE_MIN)
        val temp = Alarm(
            id = System.currentTimeMillis(),
            hour = c.get(Calendar.HOUR_OF_DAY),
            minute = c.get(Calendar.MINUTE),
            label = base.label + " (pospuesta)",
            days = mutableSetOf(),
            soundUri = base.soundUri,
            soundName = base.soundName,
            volume = base.volume,
            fade = false,
            loop = base.loop,
            enabled = true,
            temp = true,
            roulette = base.roulette
        )
        AlarmStore.upsert(this, temp)
        AlarmScheduler.schedule(this, temp)
        stopEverything()
    }

    fun stopEverything() {
        fadeRunnable?.let { handler.removeCallbacks(it) }
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = null
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
        }
        try {
            if (wake?.isHeld == true) wake?.release()
        } catch (e: Exception) {
        }
        wake = null
        ringingId = -1L
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        fadeRunnable?.let { handler.removeCallbacks(it) }
        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = null
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
        }
        try {
            if (wake?.isHeld == true) wake?.release()
        } catch (e: Exception) {
        }
    }
}
