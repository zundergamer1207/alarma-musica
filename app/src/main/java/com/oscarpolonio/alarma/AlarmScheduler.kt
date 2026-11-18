package com.oscarpolonio.alarma

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Programa las alarmas con AlarmManager.setAlarmClock(), que es la vía
 * de máxima prioridad del sistema: funciona con la pantalla apagada,
 * en modo Doze y con la app cerrada.
 */
object AlarmScheduler {

    private fun manager(ctx: Context) =
        ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun firePendingIntent(ctx: Context, id: Long): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).apply {
            action = "com.oscarpolonio.alarma.FIRE"
            putExtra("id", id)
        }
        return PendingIntent.getBroadcast(
            ctx, id.toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Próxima vez que debe sonar, en milisegundos. 0 si no hay ninguna. */
    fun nextTrigger(a: Alarm): Long {
        val now = System.currentTimeMillis()
        for (i in 0..7) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, i)
            c.set(Calendar.HOUR_OF_DAY, a.hour)
            c.set(Calendar.MINUTE, a.minute)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis <= now + 1000) continue
            if (a.days.isNotEmpty() && !a.days.contains(c.get(Calendar.DAY_OF_WEEK))) continue
            return c.timeInMillis
        }
        return 0L
    }

    fun schedule(ctx: Context, a: Alarm) {
        cancel(ctx, a.id)
        if (!a.enabled) return
        val t = nextTrigger(a)
        if (t == 0L) return

        val am = manager(ctx)
        val fire = firePendingIntent(ctx, a.id)
        val show = PendingIntent.getActivity(
            ctx, (a.id + 1).toInt(), Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(t, show), fire)
        } catch (e: SecurityException) {
            // Si el sistema no concede alarmas exactas, al menos que suene lo más cerca posible
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, fire)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, t, fire)
            }
        }
    }

    fun cancel(ctx: Context, id: Long) {
        try {
            manager(ctx).cancel(firePendingIntent(ctx, id))
        } catch (e: Exception) {
        }
    }

    /** Reprograma todo: al abrir la app, al guardar cambios y tras reiniciar el teléfono. */
    fun rescheduleAll(ctx: Context) {
        AlarmStore.load(ctx).forEach { schedule(ctx, it) }
    }

    /** Texto tipo "en 7 h 12 min". */
    fun inText(a: Alarm): String {
        val t = nextTrigger(a)
        if (t == 0L) return ""
        val mins = ((t - System.currentTimeMillis()) / 60000L).toInt() + 1
        val h = mins / 60
        val m = mins % 60
        return when {
            mins < 60 -> "en $m min"
            h < 24 -> "en $h h $m min"
            else -> "en ${h / 24} días"
        }
    }
}
