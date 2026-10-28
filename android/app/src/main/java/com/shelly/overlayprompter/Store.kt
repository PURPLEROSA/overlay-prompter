package com.shelly.overlayprompter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Script(val id: Long, val title: String, val text: String)

/** Local storage: scripts + overlay settings in SharedPreferences (JSON). */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("prompter", Context.MODE_PRIVATE)

    fun scripts(): List<Script> {
        val arr = JSONArray(p.getString("scripts", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Script(o.getLong("id"), o.getString("title"), o.getString("text"))
        }
    }

    fun save(s: Script) {
        val list = scripts().filter { it.id != s.id }.toMutableList()
        list.add(0, s)
        write(list)
    }

    fun delete(id: Long) = write(scripts().filter { it.id != id })

    private fun write(list: List<Script>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title).put("text", it.text)) }
        p.edit().putString("scripts", arr.toString()).apply()
    }

    var currentText: String
        get() = p.getString("current", "") ?: ""
        set(v) { p.edit().putString("current", v).apply() }
    var fontSp: Float
        get() = p.getFloat("font", 28f)
        set(v) { p.edit().putFloat("font", v).apply() }
    var speed: Float // px per second
        get() = p.getFloat("speed", 60f)
        set(v) { p.edit().putFloat("speed", v).apply() }
    var opacity: Float
        get() = p.getFloat("opacity", 0.75f)
        set(v) { p.edit().putFloat("opacity", v).apply() }
    var mirror: Boolean
        get() = p.getBoolean("mirror", false)
        set(v) { p.edit().putBoolean("mirror", v).apply() }
    var highlight: Boolean
        get() = p.getBoolean("highlight", true)
        set(v) { p.edit().putBoolean("highlight", v).apply() }
    var secure: Boolean
        get() = p.getBoolean("secure", true)
        set(v) { p.edit().putBoolean("secure", v).apply() }
}
