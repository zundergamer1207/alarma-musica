package com.oscarpolonio.alarma

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Recibe el disparo del AlarmManager y levanta el servicio que hace sonar la música. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        if (id == -1L) return
        val svc = Intent(ctx, AlarmService::class.java).apply {
            action = AlarmService.ACTION_RING
            putExtra("id", id)
        }
        ContextCompat.startForegroundService(ctx, svc)
    }
}

/** Vuelve a programar las alarmas cuando se reinicia el teléfono o se actualiza la app. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a == Intent.ACTION_BOOT_COMPLETED ||
            a == Intent.ACTION_MY_PACKAGE_REPLACED ||
            a == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            AlarmScheduler.rescheduleAll(ctx)
        }
    }
}
