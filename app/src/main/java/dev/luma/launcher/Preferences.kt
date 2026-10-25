package dev.luma.launcher

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray

/** Preferences are scoped to a local launcher profile, never to streaming accounts. */
class Preferences(context: Context) {
    private val data = context.getSharedPreferences("luma", Context.MODE_PRIVATE)
    var profile: Int
        get() = data.getInt("active", 0).coerceIn(0, 2)
        set(value) { data.edit().putInt("active", value.coerceIn(0, 2)).apply() }
    private fun key(name: String) = "p${profile}_$name"
    fun text(name: String, fallback: String = "") = data.getString(key(name), fallback) ?: fallback
    fun put(name: String, value: String) { data.edit().putString(key(name), value).apply() }
    fun number(name: String, fallback: Int) = data.getInt(key(name), fallback)
    fun put(name: String, value: Int) { data.edit().putInt(key(name), value).apply() }
    fun list(name: String): List<String> = runCatching {
        val a = JSONArray(text(name, "[]")); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())
    fun putList(name: String, items: List<String>) = put(name, JSONArray(items.distinct()).toString())
    fun toggle(name: String, id: String) {
        val items = list(name).toMutableList(); if (!items.remove(id)) items.add(id); putList(name, items)
    }
    val profileName get() = text("name", listOf("Personal", "Cinema", "Guest")[profile])
    var memoryPressure = false
    val renderMode get() = if (memoryPressure) 2 else mode
    val mode get() = number("mode", 1) // 0 High fidelity, 1 Balanced, 2 Performance
    val motion get() = number("motion", 1) // 0 Reduced, 1 Normal, 2 Expressive
    val shortcut get() = number("shortcut", KeyEvent.KEYCODE_MENU)
    fun resetLayout() {
        listOf("order", "hidden", "removed", "favourites", "focus", "dock", "folders").forEach { data.edit().remove(key(it)).apply() }
    }
    fun resetAppearance() {
        listOf("opacity", "tint", "wallpaper", "mode", "motion", "blur", "iconSize", "gap", "safe", "dark", "saverMinutes", "video", "avatar").forEach {
            data.edit().remove(key(it)).apply()
        }
    }
}

/** Pure functions keep remote movement and stable app ordering deterministic. */
object GridNavigation {
    fun move(index: Int, count: Int, columns: Int, dx: Int, dy: Int): Int {
        if (count <= 0) return -1
        val current = index.coerceIn(0, count - 1)
        if (dy != 0) {
            val row = current / columns + dy
            if (row < 0) return -1 // clock / toolbar
            if (row > (count - 1) / columns) return current
            return (row * columns + current % columns).coerceAtMost(count - 1)
        }
        val candidate = current + dx
        return if (candidate in 0 until count && candidate / columns == current / columns) candidate else current
    }
    fun ordered(ids: List<String>, stored: List<String>, favourites: List<String>, excluded: Set<String>): List<String> {
        val available = ids.toSet() - excluded
        val base = (stored + ids).distinct().filter { it in available }
        return favourites.filter { it in available }.distinct() + base.filter { it !in favourites }
    }
}
