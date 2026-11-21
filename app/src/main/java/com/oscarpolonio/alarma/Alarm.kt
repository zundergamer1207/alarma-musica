package com.oscarpolonio.alarma

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Una alarma. days usa Calendar.DAY_OF_WEEK: 1=Domingo ... 7=Sábado. */
data class Alarm(
    var id: Long = System.currentTimeMillis(),
    var hour: Int = 7,
    var minute: Int = 0,
    var label: String = "Alarma",
    var days: MutableSet<Int> = mutableSetOf(),
    var soundUri: String? = null,
    var soundName: String = "Sonido por defecto",
    var volume: Int = 80,
    var fade: Boolean = true,
    var loop: Boolean = true,
    var enabled: Boolean = true,
    var temp: Boolean = false,
    /** 0 = sin ruleta, 1 = ruleta (1 casilla apaga), 2 = ruleta rusa (5 de 6 apagan) */
    var roulette: Int = 0
) {

    fun timeText(): String = String.format("%02d:%02d", hour, minute)

    fun ruletaText(): String = when (roulette) {
        1 -> "🎰 ruleta"
        2 -> "🎰 ruleta rusa"
        3 -> "📳 sacudir"
        else -> ""
    }

    fun daysText(): String {
        if (days.isEmpty()) return "Una vez"
        if (days.size == 7) return "Todos los días"
        if (days.size == 5 && days.containsAll(listOf(2, 3, 4, 5, 6))) return "Lun a Vie"
        if (days.size == 2 && days.contains(1) && days.contains(7)) return "Fin de semana"
        val n = arrayOf("", "Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
        return days.sorted().joinToString(", ") { n[it] }
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("hour", hour)
        o.put("minute", minute)
        o.put("label", label)
        val d = JSONArray()
        days.sorted().forEach { d.put(it) }
        o.put("days", d)
        o.put("soundUri", soundUri ?: "")
        o.put("soundName", soundName)
        o.put("volume", volume)
        o.put("fade", fade)
        o.put("loop", loop)
        o.put("enabled", enabled)
        o.put("temp", temp)
        o.put("roulette", roulette)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val set = mutableSetOf<Int>()
            val arr = o.optJSONArray("days")
            if (arr != null) for (i in 0 until arr.length()) set.add(arr.getInt(i))
            val uri = o.optString("soundUri", "")
            return Alarm(
                id = o.optLong("id", System.currentTimeMillis()),
                hour = o.optInt("hour", 7),
                minute = o.optInt("minute", 0),
                label = o.optString("label", "Alarma"),
                days = set,
                soundUri = if (uri.isEmpty()) null else uri,
                soundName = o.optString("soundName", "Sonido por defecto"),
                volume = o.optInt("volume", 80),
                fade = o.optBoolean("fade", true),
                loop = o.optBoolean("loop", true),
                enabled = o.optBoolean("enabled", true),
                temp = o.optBoolean("temp", false),
                roulette = o.optInt("roulette", 0)
            )
        }
    }
}

/** Guarda las alarmas en el propio teléfono (SharedPreferences, sin servidor). */
object AlarmStore {

    private const val PREF = "alarmas_pref"
    private const val KEY = "lista"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun load(ctx: Context): MutableList<Alarm> {
        val raw = prefs(ctx).getString(KEY, "[]") ?: "[]"
        val list = mutableListOf<Alarm>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) list.add(Alarm.fromJson(arr.getJSONObject(i)))
        } catch (e: Exception) {
            // si el guardado se corrompe, empezamos limpio
        }
        list.sortWith(compareBy({ it.hour }, { it.minute }))
        return list
    }

    fun save(ctx: Context, list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    fun byId(ctx: Context, id: Long): Alarm? = load(ctx).firstOrNull { it.id == id }

    fun upsert(ctx: Context, a: Alarm) {
        val list = load(ctx)
        val i = list.indexOfFirst { it.id == a.id }
        if (i >= 0) list[i] = a else list.add(a)
        save(ctx, list)
    }

    fun delete(ctx: Context, id: Long) {
        val list = load(ctx)
        list.removeAll { it.id == id }
        save(ctx, list)
    }
}
