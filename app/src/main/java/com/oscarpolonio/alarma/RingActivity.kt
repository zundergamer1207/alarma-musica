package com.oscarpolonio.alarma

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Pantalla completa que aparece encima del bloqueo cuando suena la alarma. */
class RingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            try {
                val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                km.requestDismissKeyguard(this, null)
            } catch (e: Exception) {
            }
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_ring)

        val id = intent.getLongExtra("id", AlarmService.ringingId)
        val a = AlarmStore.byId(this, id)

        findViewById<TextView>(R.id.ringTime).text = a?.timeText() ?: ""
        findViewById<TextView>(R.id.ringLabel).text = a?.label ?: "Alarma"
        findViewById<TextView>(R.id.ringSound).text = "♪ " + (a?.soundName ?: "")

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            send(AlarmService.ACTION_STOP)
            finish()
        }
        findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            send(AlarmService.ACTION_SNOOZE)
            finish()
        }
    }

    private fun send(action: String) {
        try {
            startService(Intent(this, AlarmService::class.java).setAction(action))
        } catch (e: Exception) {
        }
    }

    /** Que el botón Atrás no deje la alarma sonando sin pantalla. */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // No hacemos nada: hay que elegir Apagar o Posponer.
    }
}
