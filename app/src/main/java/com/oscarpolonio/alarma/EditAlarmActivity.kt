package com.oscarpolonio.alarma

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

/** Pantalla para crear o editar una alarma y elegir la canción. */
class EditAlarmActivity : AppCompatActivity() {

    private var alarm = Alarm()
    private var esNueva = true

    private lateinit var picker: TimePicker
    private lateinit var labelField: EditText
    private lateinit var dayRow: LinearLayout
    private lateinit var soundTitle: TextView
    private lateinit var soundSub: TextView
    private lateinit var volText: TextView
    private lateinit var volBar: SeekBar
    private lateinit var swFade: SwitchCompat
    private lateinit var swLoop: SwitchCompat

    private val dayViews = ArrayList<TextView>()
    private val letters = arrayOf("D", "L", "M", "M", "J", "V", "S")

    private var preview: MediaPlayer? = null

    /** Selector de archivos del sistema: cualquier canción del teléfono. */
    private val elegirCancion =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // algunos proveedores no permiten permiso persistente
            }
            alarm.soundUri = uri.toString()
            alarm.soundName = nombreDe(uri)
            pintarSonido()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        picker = findViewById(R.id.timePicker)
        labelField = findViewById(R.id.labelField)
        dayRow = findViewById(R.id.dayRow)
        soundTitle = findViewById(R.id.soundTitle)
        soundSub = findViewById(R.id.soundSub)
        volText = findViewById(R.id.volText)
        volBar = findViewById(R.id.volBar)
        swFade = findViewById(R.id.swFade)
        swLoop = findViewById(R.id.swLoop)

        val id = intent.getLongExtra("id", -1L)
        if (id != -1L) {
            AlarmStore.byId(this, id)?.let {
                alarm = it
                esNueva = false
            }
        }

        picker.setIs24HourView(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            picker.hour = alarm.hour
            picker.minute = alarm.minute
        } else {
            @Suppress("DEPRECATION")
            picker.currentHour = alarm.hour
            @Suppress("DEPRECATION")
            picker.currentMinute = alarm.minute
        }

        labelField.setText(alarm.label)
        construirDias()
        pintarSonido()

        volBar.progress = alarm.volume
        volText.text = "${alarm.volume}%"
        volBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val v = if (p < 5) 5 else p
                alarm.volume = v
                volText.text = "$v%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        swFade.isChecked = alarm.fade
        swLoop.isChecked = alarm.loop

        findViewById<Button>(R.id.btnPick).setOnClickListener {
            elegirCancion.launch(arrayOf("audio/*"))
        }
        findViewById<TextView>(R.id.btnDefault).setOnClickListener {
            alarm.soundUri = null
            alarm.soundName = "Sonido por defecto"
            pintarSonido()
        }
        findViewById<Button>(R.id.btnTest).setOnClickListener { probar() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { guardar() }

        val borrar = findViewById<TextView>(R.id.btnDelete)
        borrar.visibility = if (esNueva) View.GONE else View.VISIBLE
        borrar.setOnClickListener {
            AlarmScheduler.cancel(this, alarm.id)
            AlarmStore.delete(this, alarm.id)
            Toast.makeText(this, "Alarma eliminada", Toast.LENGTH_SHORT).show()
            finish()
        }

        title = if (esNueva) "Nueva alarma" else "Editar alarma"
    }

    // ---------------- días ----------------

    private fun construirDias() {
        dayRow.removeAllViews()
        dayViews.clear()
        for (i in 0..6) {
            val diaCalendar = i + 1 // Calendar.SUNDAY = 1
            val tv = TextView(this)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(6, 0, 6, 0)
            tv.layoutParams = lp
            tv.text = letters[i]
            tv.gravity = Gravity.CENTER
            tv.setPadding(0, 26, 0, 26)
            tv.textSize = 13f
            tv.setBackgroundResource(R.drawable.bg_day)
            tv.isSelected = alarm.days.contains(diaCalendar)
            pintarDia(tv)
            tv.setOnClickListener {
                if (alarm.days.contains(diaCalendar)) alarm.days.remove(diaCalendar)
                else alarm.days.add(diaCalendar)
                tv.isSelected = alarm.days.contains(diaCalendar)
                pintarDia(tv)
                findViewById<TextView>(R.id.dayHint).text =
                    if (alarm.days.isEmpty()) "Sin días marcados suena una sola vez."
                    else "Se repite: " + alarm.daysText()
            }
            dayViews.add(tv)
            dayRow.addView(tv)
        }
        findViewById<TextView>(R.id.dayHint).text =
            if (alarm.days.isEmpty()) "Sin días marcados suena una sola vez."
            else "Se repite: " + alarm.daysText()
    }

    private fun pintarDia(tv: TextView) {
        tv.setTextColor(
            ContextCompat.getColor(this, if (tv.isSelected) R.color.accent else R.color.dim)
        )
    }

    // ---------------- sonido ----------------

    private fun nombreDe(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getString(0)
                        if (!n.isNullOrEmpty()) return n.substringBeforeLast('.')
                    }
                }
        } catch (e: Exception) {
        }
        return "Mi canción"
    }

    private fun pintarSonido() {
        soundTitle.text = alarm.soundName
        soundSub.text =
            if (alarm.soundUri.isNullOrEmpty()) "Tono de alarma del sistema"
            else "Tu música, guardada en el teléfono"
    }

    private fun probar() {
        val btn = findViewById<Button>(R.id.btnTest)
        if (preview != null) {
            pararPreview()
            btn.text = "▶ Probar"
            return
        }
        try {
            val uri = if (alarm.soundUri.isNullOrEmpty())
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            else Uri.parse(alarm.soundUri)

            preview = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@EditAlarmActivity, uri)
                isLooping = false
                prepare()
                val v = alarm.volume / 100f
                setVolume(v, v)
                start()
                setOnCompletionListener {
                    pararPreview()
                    btn.text = "▶ Probar"
                }
            }
            btn.text = "■ Parar"
        } catch (e: Exception) {
            Toast.makeText(this, "No pude reproducir esa canción", Toast.LENGTH_SHORT).show()
            pararPreview()
        }
    }

    private fun pararPreview() {
        try {
            preview?.stop()
            preview?.release()
        } catch (e: Exception) {
        }
        preview = null
    }

    // ---------------- guardar ----------------

    @Suppress("DEPRECATION")
    private fun horaElegida(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) picker.hour else picker.currentHour

    @Suppress("DEPRECATION")
    private fun minutoElegido(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) picker.minute else picker.currentMinute

    private fun guardar() {
        alarm.hour = horaElegida()
        alarm.minute = minutoElegido()

        val txt = labelField.text.toString().trim()
        alarm.label = if (txt.isEmpty()) "Alarma" else txt
        alarm.fade = swFade.isChecked
        alarm.loop = swLoop.isChecked
        alarm.enabled = true
        alarm.temp = false

        AlarmStore.upsert(this, alarm)
        AlarmScheduler.schedule(this, alarm)

        val falta = AlarmScheduler.inText(alarm)
        Toast.makeText(
            this,
            "Guardada para las ${alarm.timeText()}" + if (falta.isNotEmpty()) " · suena $falta" else "",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    override fun onStop() {
        super.onStop()
        pararPreview()
    }
}
