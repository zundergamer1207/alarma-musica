package com.oscarpolonio.alarma

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private lateinit var clock: TextView
    private lateinit var today: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            paintClock()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        list = findViewById(R.id.alarmList)
        empty = findViewById(R.id.emptyText)
        clock = findViewById(R.id.clock)
        today = findViewById(R.id.today)

        findViewById<Button>(R.id.btnNew).setOnClickListener {
            startActivity(Intent(this, EditAlarmActivity::class.java))
        }
        findViewById<TextView>(R.id.btnPerms).setOnClickListener { checkPermissions(true) }

        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        AlarmScheduler.rescheduleAll(this)
        render()
        paintClock()
        handler.post(ticker)
        checkPermissions(false)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    // ---------------- permisos ----------------

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11
                )
            }
        }
    }

    /** Avisa si al sistema le falta algún permiso para que la alarma sea puntual. */
    private fun checkPermissions(forzar: Boolean) {
        val faltan = ArrayList<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) faltan.add("Alarmas exactas")
        }
        if (!ignorandoBateria()) faltan.add("Sin restricción de batería")

        if (faltan.isEmpty()) {
            if (forzar) Toast.makeText(this, "Todo en orden ✓", Toast.LENGTH_SHORT).show()
            findViewById<TextView>(R.id.btnPerms).visibility = View.GONE
            return
        }

        findViewById<TextView>(R.id.btnPerms).visibility = View.VISIBLE
        if (!forzar) return

        AlertDialog.Builder(this)
            .setTitle("Para que suene puntual")
            .setMessage(
                "Falta: " + faltan.joinToString(", ") + ".\n\n" +
                    "Te llevo a los ajustes del sistema para activarlo."
            )
            .setPositiveButton("Ir a ajustes") { _, _ -> abrirAjustesPendientes() }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    private fun abrirAjustesPendientes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:$packageName"))
                    )
                    return
                } catch (e: Exception) {
                }
            }
        }
        pedirBateria()
    }

    private fun ignorandoBateria(): Boolean {
        return try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                pm.isIgnoringBatteryOptimizations(packageName) else true
        } catch (e: Exception) {
            true
        }
    }

    private fun pedirBateria() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
            }
        }
    }

    // ---------------- pintar ----------------

    private fun paintClock() {
        val now = Date()
        clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val f = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "ES"))
        today.text = f.format(now).replaceFirstChar { it.uppercase() }
    }

    private fun render() {
        list.removeAllViews()
        val alarms = AlarmStore.load(this)

        empty.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)
        for (a in alarms) {
            val row = inflater.inflate(R.layout.item_alarm, list, false)

            row.findViewById<TextView>(R.id.itemTime).text = a.timeText()
            row.findViewById<TextView>(R.id.itemLabel).text = a.label

            val sub = StringBuilder(a.daysText())
            sub.append(" · ♪ ").append(a.soundName)
            if (a.enabled) {
                val t = AlarmScheduler.inText(a)
                if (t.isNotEmpty()) sub.append(" · ").append(t)
            }
            row.findViewById<TextView>(R.id.itemSub).text = sub.toString()
            row.alpha = if (a.enabled) 1f else 0.45f

            val sw = row.findViewById<SwitchCompat>(R.id.itemSwitch)
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = a.enabled
            sw.setOnCheckedChangeListener { _, checked ->
                a.enabled = checked
                AlarmStore.upsert(this, a)
                AlarmScheduler.schedule(this, a)
                render()
            }

            row.findViewById<TextView>(R.id.itemDelete).setOnClickListener {
                AlarmScheduler.cancel(this, a.id)
                AlarmStore.delete(this, a.id)
                render()
                Toast.makeText(this, "Alarma eliminada", Toast.LENGTH_SHORT).show()
            }

            row.setOnClickListener {
                startActivity(
                    Intent(this, EditAlarmActivity::class.java).putExtra("id", a.id)
                )
            }

            list.addView(row)
        }
    }
}
