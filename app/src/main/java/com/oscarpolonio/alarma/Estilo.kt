package com.oscarpolonio.alarma

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/** Ajustes propios de la app: tema, sonidos y animaciones. */
object Prefs {

    private const val P = "ajustes_app"

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun tema(ctx: Context): Int = sp(ctx).getInt("tema", 0)
    fun setTema(ctx: Context, v: Int) = sp(ctx).edit().putInt("tema", v).apply()

    fun fx(ctx: Context): Boolean = sp(ctx).getBoolean("fx", true)
    fun setFx(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("fx", v).apply()

    fun anim(ctx: Context): Boolean = sp(ctx).getBoolean("anim", true)
    fun setAnim(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("anim", v).apply()
}

/** Los temas de color disponibles. */
object Temas {

    data class Tema(
        val nombre: String,
        val estilo: Int,
        val c1: Int,
        val c2: Int
    )

    val lista = listOf(
        Tema("Ámbar", R.style.Tema_Ambar, 0xFFFFB340.toInt(), 0xFFFF7A59.toInt()),
        Tema("Neón", R.style.Tema_Neon, 0xFFA77BFF.toInt(), 0xFF43E8D8.toInt()),
        Tema("Atardecer", R.style.Tema_Atardecer, 0xFFFF6B9D.toInt(), 0xFFFFB36B.toInt()),
        Tema("Menta", R.style.Tema_Menta, 0xFF3DDC97.toInt(), 0xFF4FD1E0.toInt()),
        Tema("Hielo", R.style.Tema_Hielo, 0xFF5BA8FF.toInt(), 0xFF8E7BFF.toInt())
    )

    fun actual(ctx: Context): Tema = lista[Prefs.tema(ctx).coerceIn(0, lista.size - 1)]

    /** Se llama al principio de cada pantalla, antes de setContentView. */
    fun aplicar(act: Activity) {
        act.setTheme(actual(act).estilo)
    }

    /** Resuelve un color del tema actual (R.attr.acento, R.attr.fondo…). */
    fun color(ctx: Context, attr: Int): Int {
        val tv = TypedValue()
        return if (ctx.theme.resolveAttribute(attr, tv, true)) tv.data else 0
    }
}

/** Efectos de sonido cortos, generados por el sistema (no ocupan espacio). */
object Fx {

    private var gen: ToneGenerator? = null

    private fun generador(): ToneGenerator? {
        if (gen == null) {
            gen = try {
                ToneGenerator(AudioManager.STREAM_SYSTEM, 65)
            } catch (e: Exception) {
                null
            }
        }
        return gen
    }

    private fun tono(ctx: Context, tipo: Int, ms: Int) {
        if (!Prefs.fx(ctx)) return
        try {
            generador()?.startTone(tipo, ms)
        } catch (e: Exception) {
            gen = null
        }
    }

    /** Toque de botón. */
    fun tap(ctx: Context) = tono(ctx, ToneGenerator.TONE_PROP_BEEP, 30)

    /** Confirmación: guardar, activar. */
    fun ok(ctx: Context) = tono(ctx, ToneGenerator.TONE_PROP_ACK, 130)

    /** Error o fallo en la ruleta. */
    fun fallo(ctx: Context) = tono(ctx, ToneGenerator.TONE_PROP_NACK, 180)

    /** Cambio de opción, selección. */
    fun click(ctx: Context) = tono(ctx, ToneGenerator.TONE_PROP_BEEP2, 45)

    /** Eliminar. */
    fun borrar(ctx: Context) = tono(ctx, ToneGenerator.TONE_PROP_PROMPT, 90)
}

// ---------------- animaciones ----------------

/** Rebote corto al pulsar. */
fun View.pop() {
    if (!Prefs.anim(context)) return
    animate().cancel()
    scaleX = 1f
    scaleY = 1f
    animate().scaleX(0.93f).scaleY(0.93f).setDuration(70)
        .withEndAction {
            animate().scaleX(1f).scaleY(1f)
                .setInterpolator(OvershootInterpolator(3f)).setDuration(170).start()
        }.start()
}

/** Entrada escalonada de los elementos de una lista. */
fun View.entrar(posicion: Int) {
    if (!Prefs.anim(context)) {
        alpha = 1f
        translationY = 0f
        return
    }
    alpha = 0f
    translationY = 36f
    animate().alpha(1f).translationY(0f)
        .setStartDelay(posicion * 55L)
        .setDuration(300)
        .setInterpolator(DecelerateInterpolator())
        .start()
}

/** Latido continuo, para la campana de la alarma. */
fun View.latir() {
    if (!Prefs.anim(context)) return
    animate().scaleX(1.18f).scaleY(1.18f).setDuration(420)
        .withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(420)
                .withEndAction { latir() }.start()
        }.start()
}
