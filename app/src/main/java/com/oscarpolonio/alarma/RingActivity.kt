package com.oscarpolonio.alarma

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
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

        val btnStop = findViewById<Button>(R.id.btnStop)
        btnStop.setOnClickListener {
            send(AlarmService.ACTION_STOP)
            finish()
        }
        findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            send(AlarmService.ACTION_SNOOZE)
            finish()
        }

        if (a != null && a.roulette > 0) prepararRuleta(a, btnStop)
    }

    /** Con la ruleta activa, girar es la única forma de apagarla. */
    private fun prepararRuleta(a: Alarm, btnStop: Button) {
        btnStop.visibility = View.GONE
        findViewById<TextView>(R.id.bell).textSize = 36f
        findViewById<TextView>(R.id.ringTime).textSize = 40f

        val caja = findViewById<LinearLayout>(R.id.ruletaBox)
        val ruleta = findViewById<RouletteView>(R.id.ruleta)
        val msg = findViewById<TextView>(R.id.ruletaMsg)
        val btnSpin = findViewById<Button>(R.id.btnSpin)

        caja.visibility = View.VISIBLE
        ruleta.slots = 6

        val rusa = a.roulette == 2
        ruleta.winning = if (rusa) setOf(0, 1, 2, 3, 4) else setOf(0)

        val instruccion =
            if (rusa) "Ruleta rusa: 5 de las 6 casillas la apagan.\nSuerte."
            else "Solo una de las 6 casillas la apaga.\nGira hasta acertar."
        msg.text = instruccion

        var intentos = 0
        btnSpin.setOnClickListener {
            btnSpin.isEnabled = false
            btnSpin.text = "GIRANDO…"
            msg.text = "…"
            intentos++
            ruleta.girar { acerto ->
                if (acerto) {
                    msg.text = "¡Apagada!"
                    btnSpin.text = "✓"
                    send(AlarmService.ACTION_STOP)
                    finish()
                } else {
                    msg.text = if (rusa) "Te tocó la casilla mala.\nGira otra vez."
                    else "Fallaste. Intento $intentos.\nGira otra vez."
                    btnSpin.text = "GIRAR"
                    btnSpin.isEnabled = true
                }
            }
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
