package com.chroma.library.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** The single entry point the UI uses for colours, favourites, collections and settings. */
class Repo(ctx: Context) {

    private val colors = ColorDb(ctx)
    private val user = UserDb(ctx)
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<() -> Unit>()

    /** Called whenever favourites or collections change. */
    fun observe(l: () -> Unit) { listeners.add(l) }
    fun unobserve(l: () -> Unit) { listeners.remove(l) }
    private fun changed() { listeners.toList().forEach { it() } }

    // ---- library ------------------------------------------------------------

    val total: Int by lazy { colors.total() }
    fun familyCounts() = colors.familyCounts()
    /** An official colour, or (for any valid HEX) an unnamed colour made from the HEX alone. */
    fun color(hex: String): ColorItem? {
        val h = hex.uppercase()
        return colors.byHex(h) ?: custom(h)
    }
    fun colorsOf(hexes: List<String>): List<ColorItem> {
        val map = colors.byHexes(hexes)
        return hexes.mapNotNull { map[it] ?: custom(it.uppercase()) }
    }
    fun nearest(c: ColorItem) = colors.nearest(c.r, c.g, c.b)

    /** A colour that is not in the library (picked by the user). It has no name: only an official name is ever shown. */
    fun custom(hex: String): ColorItem? {
        if (!Regex("[0-9A-F]{6}").matches(hex)) return null
        val r = hex.substring(0, 2).toInt(16); val g = hex.substring(2, 4).toInt(16); val b = hex.substring(4, 6).toInt(16)
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        val mx = maxOf(r, g, b) / 255f; val mn = minOf(r, g, b) / 255f
        val l = (mx + mn) / 2f
        val s = if (mx == mn) 0f else (mx - mn) / (1f - Math.abs(2f * l - 1f))
        return ColorItem("", hex, r, g, b, hsv[0], s, l, hsv[2], familyOf(hsv[0], s, l))
    }

    private fun familyOf(h: Float, s: Float, l: Float): String = when {
        s < 0.12f || l < 0.07f || l > 0.95f -> "Neutral"
        h in 10f..50f && l < 0.42f -> "Brown"
        l >= 0.72f && (h >= 330f || h < 15f) -> "Pink"
        h < 15f || h >= 345f -> "Red"
        h < 40f -> "Orange"
        h < 70f -> "Yellow"
        h < 165f -> "Green"
        h < 200f -> "Teal"
        h < 255f -> "Blue"
        h < 300f -> "Purple"
        else -> "Pink"
    }
    fun random() = colors.random()
    fun similar(c: ColorItem, n: Int = 24) = colors.similar(c, n)
    fun shades(c: ColorItem, each: Int = 6) = colors.shades(c, each)

    /** Runs a library query off the main thread and delivers the result on it. */
    fun search(q: Query, limit: Int, offset: Int, done: (List<ColorItem>, Int) -> Unit) {
        io.execute {
            val items = colors.page(q, limit, offset)
            val count = if (offset == 0) colors.count(q) else -1
            main.post { done(items, count) }
        }
    }

    // ---- favourites ---------------------------------------------------------

    fun isFavorite(hex: String) = user.isFavorite(hex)
    fun toggleFavorite(hex: String): Boolean = user.toggleFavorite(hex).also { changed() }
    fun favorites(): List<ColorItem> = colorsOf(user.favoriteHexes())

    // ---- collections --------------------------------------------------------

    fun folders(): List<Folder> = user.folders().map { r ->
        Folder(r.id, r.name, r.cover, r.count, colorsOf(user.previewHexes(r.id, 4)).map { it.argb })
    }
    fun folder(id: Long): Folder? = folders().firstOrNull { it.id == id }
    fun folderColors(id: Long): List<ColorItem> = colorsOf(user.folderHexes(id))
    fun createFolder(name: String, cover: Int): Long = user.createFolder(name.trim(), cover).also { changed() }
    fun updateFolder(id: Long, name: String, cover: Int) { user.updateFolder(id, name.trim(), cover); changed() }
    fun deleteFolder(id: Long) { user.deleteFolder(id); changed() }
    fun moveFolder(id: Long, delta: Int) { user.moveFolder(id, delta); changed() }
    fun foldersOf(hex: String) = user.foldersOf(hex)
    fun setInFolder(folderId: Long, hex: String, inside: Boolean) { user.setInFolder(folderId, hex, inside); changed() }
    fun moveColor(hex: String, from: Long, to: Long) { user.moveColor(hex, from, to); changed() }

    // ---- settings -----------------------------------------------------------

    var theme: Int                       // 0 system, 1 light, 2 dark
        get() = prefs.getInt("theme", 0)
        set(v) { prefs.edit().putInt("theme", v).apply() }

    var columns: Int                     // swatches per row: 2, 3 or 4
        get() = prefs.getInt("columns", 2)
        set(v) { prefs.edit().putInt("columns", v).apply(); changed() }

    var showHex: Boolean
        get() = prefs.getBoolean("showHex", true)
        set(v) { prefs.edit().putBoolean("showHex", v).apply(); changed() }

    // ---- data management ----------------------------------------------------

    fun exportJson(): String {
        val root = JSONObject().put("app", "Chroma Library").put("version", 1)
        root.put("favorites", JSONArray(user.favoriteHexes()))
        val arr = JSONArray()
        for (f in user.folders()) {
            arr.put(JSONObject().put("name", f.name).put("cover", f.cover).put("colors", JSONArray(user.folderHexes(f.id))))
        }
        root.put("collections", arr)
        return root.toString(2)
    }

    /** Adds what is in the file to what is already here. Returns how many colours were added. */
    fun importJson(text: String): Int {
        val root = JSONObject(text)
        var n = 0
        root.optJSONArray("favorites")?.let { a ->
            for (i in 0 until a.length()) {
                val hex = a.getString(i).uppercase()
                if (custom(hex) != null && !user.isFavorite(hex)) { user.toggleFavorite(hex); n++ }
            }
        }
        root.optJSONArray("collections")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val id = user.createFolder(o.optString("name", "Imported"), o.optInt("cover", 0xFF888888.toInt()))
                val hexes = o.optJSONArray("colors") ?: continue
                for (k in 0 until hexes.length()) {
                    val hex = hexes.getString(k).uppercase()
                    if (custom(hex) != null) { user.setInFolder(id, hex, true); n++ }
                }
            }
        }
        changed()
        return n
    }

    fun clearFavorites() { user.clearFavorites(); changed() }
    fun clearCollections() { user.clearFolders(); changed() }

    companion object {
        lateinit var instance: Repo
            private set
        fun init(ctx: Context) { instance = Repo(ctx.applicationContext) }
    }
}
