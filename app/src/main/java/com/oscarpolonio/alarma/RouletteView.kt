package com.oscarpolonio.alarma

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.random.Random

/**
 * Ruleta de la pantalla de alarma.
 * Las casillas marcadas en "winning" son las que apagan la alarma.
 */
class RouletteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var slots: Int = 6
    var winning: Set<Int> = setOf(0)

    private var angle = 0f
    private var spinning = false

    private fun delTema(attr: Int, respaldo: String): Int {
        val c = Temas.color(context, attr)
        return if (c != 0) c else Color.parseColor(respaldo)
    }

    // La ruleta se pinta con los colores del tema elegido
    private val colGana = delTema(R.attr.acento, "#FFB340")
    private val colPierde = delTema(R.attr.linea, "#2A3240")
    private val colBorde = delTema(R.attr.anillo, "#120D08")
    private val colTextoGana = Color.parseColor("#14100A")
    private val colTextoPierde = delTema(R.attr.tenue, "#8B95A6")

    private val relleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = colBorde
    }
    private val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val aguja = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val centro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#150F0A")
    }

    private val caja = RectF()
    private val triangulo = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val r = (minOf(width, height) / 2f) - 14f
        if (r <= 0f) return

        caja.set(cx - r, cy - r, cx + r, cy + r)
        val sector = 360f / slots

        for (i in 0 until slots) {
            val gana = winning.contains(i)
            relleno.color = if (gana) colGana else colPierde
            val inicio = angle + i * sector - 90f - sector / 2f
            canvas.drawArc(caja, inicio, sector, true, relleno)
            canvas.drawArc(caja, inicio, sector, true, borde)

            // etiqueta en el centro del sector
            val medio = Math.toRadians((inicio + sector / 2f).toDouble())
            val tx = cx + (r * 0.62f) * Math.cos(medio).toFloat()
            val ty = cy + (r * 0.62f) * Math.sin(medio).toFloat()
            texto.textSize = r * 0.3f
            texto.color = if (gana) colTextoGana else colTextoPierde
            canvas.drawText(if (gana) "✓" else "✕", tx, ty - (texto.descent() + texto.ascent()) / 2f, texto)
        }

        // tapa central
        canvas.drawCircle(cx, cy, r * 0.17f, centro)

        // aguja arriba, apuntando hacia dentro
        triangulo.reset()
        triangulo.moveTo(cx, cy - r + 26f)
        triangulo.lineTo(cx - 20f, cy - r - 16f)
        triangulo.lineTo(cx + 20f, cy - r - 16f)
        triangulo.close()
        canvas.drawPath(triangulo, aguja)
    }

    /** Casilla que queda bajo la aguja. */
    private fun casillaActual(): Int {
        val sector = 360f / slots
        var a = (-angle + sector / 2f) % 360f
        if (a < 0f) a += 360f
        return (a / sector).toInt().coerceIn(0, slots - 1)
    }

    /** Gira la ruleta. Devuelve true si cayó en una casilla que apaga la alarma. */
    fun girar(alTerminar: (Boolean) -> Unit) {
        if (spinning) return
        spinning = true

        val vueltas = Random.nextInt(4, 8) * 360f
        val extra = Random.nextFloat() * 360f
        val anim = ValueAnimator.ofFloat(angle, angle + vueltas + extra)
        anim.duration = 3400L
        anim.interpolator = DecelerateInterpolator(1.9f)
        anim.addUpdateListener {
            angle = it.animatedValue as Float
            invalidate()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                spinning = false
                angle %= 360f
                invalidate()
                alTerminar(winning.contains(casillaActual()))
            }
        })
        anim.start()
    }
}
