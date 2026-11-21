package com.oscarpolonio.alarma

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt

/** Pantalla completa que aparece encima del bloqueo cuando suena la alarma. */
class RingActivity : AppCompatActivity() {

    private var sensores: SensorManager? = null
    private var escucha: SensorEventListener? = null
    private var sensor: Sensor? = null

    private val sacudidasNecesarias = 12
    private var sacudidas = 0
    private var ultimaSacudida = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        Temas.aplicar(this)
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
        findViewById<View>(R.id.raiz).setBackgroundColor(Temas.color(this, R.attr.anillo))

        val id = intent.getLongExtra("id", AlarmService.ringingId)
        val a = AlarmStore.byId(this, id)

        findViewById<TextView>(R.id.ringTime).text = a?.timeText() ?: ""
        findViewById<TextView>(R.id.ringLabel).text = a?.label ?: "Alarma"
        findViewById<TextView>(R.id.ringSound).text = "♪ " + (a?.soundName ?: "")
        findViewById<TextView>(R.id.bell).latir()

        val btnStop = findViewById<Button>(R.id.btnStop)
        btnStop.setOnClickListener {
            it.pop()
            send(AlarmService.ACTION_STOP)
            finish()
        }
        findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            it.pop()
            Fx.click(this)
            send(AlarmService.ACTION_SNOOZE)
            finish()
        }

        when (a?.roulette ?: 0) {
            1, 2 -> prepararRuleta(a!!, btnStop)
            3 -> prepararSacudida(btnStop)
        }
    }

    // ---------------- ruleta ----------------

    /** Con la ruleta activa, girar es la única forma de apagarla. */
    private fun prepararRuleta(a: Alarm, btnStop: Button) {
        btnStop.visibility = View.GONE
        encoger()

        val caja = findViewById<LinearLayout>(R.id.ruletaBox)
        val ruleta = findViewById<RouletteView>(R.id.ruleta)
        val msg = findViewById<TextView>(R.id.ruletaMsg)
        val btnSpin = findViewById<Button>(R.id.btnSpin)

        caja.visibility = View.VISIBLE
        ruleta.slots = 6

        val rusa = a.roulette == 2
        ruleta.winning = if (rusa) setOf(0, 1, 2, 3, 4) else setOf(0)

        msg.text =
            if (rusa) "Ruleta rusa: 5 de las 6 casillas la apagan.\nSuerte."
            else "Solo una de las 6 casillas la apaga.\nGira hasta acertar."

        var intentos = 0
        btnSpin.setOnClickListener {
            it.pop()
            Fx.click(this)
            btnSpin.isEnabled = false
            btnSpin.text = "GIRANDO…"
            msg.text = "…"
            intentos++
            ruleta.girar { acerto ->
                if (acerto) {
                    Fx.ok(this)
                    msg.text = "¡Apagada!"
                    btnSpin.text = "✓"
                    send(AlarmService.ACTION_STOP)
                    finish()
                } else {
                    Fx.fallo(this)
                    msg.text = if (rusa) "Te tocó la casilla mala.\nGira otra vez."
                    else "Fallaste. Intento $intentos.\nGira otra vez."
                    btnSpin.text = "GIRAR"
                    btnSpin.isEnabled = true
                }
            }
        }
    }

    // ---------------- sacudir ----------------

    private fun prepararSacudida(btnStop: Button) {
        btnStop.visibility = View.GONE

        val caja = findViewById<LinearLayout>(R.id.ruletaBox)
        val msg = findViewById<TextView>(R.id.ruletaMsg)
        val btnSpin = findViewById<Button>(R.id.btnSpin)

        caja.visibility = View.VISIBLE
        findViewById<RouletteView>(R.id.ruleta).visibility = View.GONE
        btnSpin.visibility = View.GONE

        val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sm == null || sensor == null) {
            // Sin acelerómetro devolvemos el botón para no dejarlo atrapado
            msg.text = "Este teléfono no tiene sensor de movimiento."
            btnStop.visibility = View.VISIBLE
            return
        }

        sensores = sm
        msg.textSize = 18f
        pintarContador(msg)

        escucha = object : SensorEventListener {
            override fun onAccuracyChanged(s: Sensor?, p: Int) {}

            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0]
                val y = e.values[1]
                val z = e.values[2]
                val fuerza = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                if (fuerza < 2.2f) return

                val ahora = System.currentTimeMillis()
                if (ahora - ultimaSacudida < 220L) return
                ultimaSacudida = ahora

                sacudidas++
                Fx.click(this@RingActivity)
                findViewById<TextView>(R.id.bell).pop()

                if (sacudidas >= sacudidasNecesarias) {
                    soltarSensor()
                    Fx.ok(this@RingActivity)
                    msg.text = "¡Apagada!"
                    send(AlarmService.ACTION_STOP)
                    finish()
                } else {
                    pintarContador(msg)
                }
            }
        }
        sm.registerListener(escucha, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun pintarContador(msg: TextView) {
        val faltan = sacudidasNecesarias - sacudidas
        val barra = "●".repeat(sacudidas) + "○".repeat(faltan.coerceAtLeast(0))
        msg.text = "Sacude el teléfono para apagarla\n\n$barra\n$sacudidas / $sacudidasNecesarias"
    }

    private fun soltarSensor() {
        try {
            escucha?.let { sensores?.unregisterListener(it) }
        } catch (e: Exception) {
        }
        escucha = null
    }

    // ---------------- común ----------------

    /** Deja sitio en pantalla para la ruleta o el contador. */
    private fun encoger() {
        findViewById<TextView>(R.id.bell).textSize = 34f
        findViewById<TextView>(R.id.ringTime).textSize = 40f
    }

    private fun send(action: String) {
        try {
            startService(Intent(this, AlarmService::class.java).setAction(action))
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soltarSensor()
    }

    /** Que el botón Atrás no deje la alarma sonando sin pantalla. */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Hay que apagarla o posponerla a propósito.
    }
}
