package com.oscarpolonio.alarma

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

/** Pantalla de apariencia: tema de color, efectos de sonido y animaciones. */
class AjustesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Temas.aplicar(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ajustes)

        pintarTemas()

        val fx = findViewById<SwitchCompat>(R.id.swFx)
        fx.isChecked = Prefs.fx(this)
        fx.setOnCheckedChangeListener { _, v ->
            Prefs.setFx(this, v)
            if (v) Fx.ok(this)
        }

        val anim = findViewById<SwitchCompat>(R.id.swAnim)
        anim.isChecked = Prefs.anim(this)
        anim.setOnCheckedChangeListener { _, v ->
            Prefs.setAnim(this, v)
            Fx.click(this)
        }

        findViewById<TextView>(R.id.btnVolver).setOnClickListener {
            Fx.tap(this)
            finish()
        }
    }

    private fun pintarTemas() {
        val caja = findViewById<LinearLayout>(R.id.temasBox)
        caja.removeAllViews()
        val actual = Prefs.tema(this)

        Temas.lista.forEachIndexed { i, tema ->
            val fila = LinearLayout(this)
            fila.orientation = LinearLayout.HORIZONTAL
            fila.gravity = Gravity.CENTER_VERTICAL
            fila.setBackgroundResource(
                if (i == actual) R.drawable.bg_day_on else R.drawable.bg_card
            )
            fila.setPadding(16, 30, 16, 30)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 20)
            fila.layoutParams = lp

            fila.addView(punto(tema.c1))
            fila.addView(punto(tema.c2))

            val nombre = TextView(this)
            nombre.text = tema.nombre
            nombre.textSize = 15f
            nombre.setTextColor(Temas.color(this, R.attr.texto))
            nombre.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).also { it.setMargins(16, 0, 0, 0) }
            fila.addView(nombre)

            if (i == actual) {
                val check = TextView(this)
                check.text = "✓"
                check.textSize = 17f
                check.setTextColor(tema.c1)
                fila.addView(check)
            }

            fila.setOnClickListener {
                if (i == actual) return@setOnClickListener
                Fx.ok(this)
                Prefs.setTema(this, i)
                recreate()
            }

            fila.entrar(i)
            caja.addView(fila)
        }
    }

    private fun punto(color: Int): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(44, 44).also { it.setMargins(0, 0, 10, 0) }
        v.background = ContextCompat.getDrawable(this, R.drawable.punto)
        v.backgroundTintList = ColorStateList.valueOf(color)
        return v
    }

    override fun finish() {
        super.finish()
        // La pantalla anterior se recarga sola con el tema nuevo
    }
}
