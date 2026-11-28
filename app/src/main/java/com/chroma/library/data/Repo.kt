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

    init {
        val imports = user.allImports()
        if (imports.isNotEmpty()) colors.reloadImports(imports) // the user's licensed palettes live next to the built-in data
    }

    val total: Int get() = colors.total()
    fun sources() = colors.sources()
    fun entries(hex: String) = colors.entries(hex)
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
    fun custom(hex: String): ColorItem? = com.chroma.library.domain.ColorMath.fromHex(hex)
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

    // ---- licensed palettes (Pantone, RAL, NCS, ...) --------------------------------------
    // The app does not ship these. The user imports their own licensed files; each stays under the source name they give it.

    class ImportResult(val source: String, val added: Int, val skipped: Int)

    /** Reads a palette file (JSON or CSV, see tools/templates) and adds it. Throws IllegalArgumentException with a readable message. */
    fun importPalette(text: String): ImportResult {
        val t = text.trim().removePrefix("\uFEFF")
        val rows = ArrayList<ImportRow>()
        var skipped = 0
        var source: String
        fun hexOf(v: String): String? {
            val h = v.trim().removePrefix("#").uppercase()
            val full = if (h.length == 3) h.map { "$it$it" }.joinToString("") else h
            return if (Regex("[0-9A-F]{6}").matches(full)) full else null
        }
        if (t.startsWith("{")) {
            val o = JSONObject(t)
            source = o.optString("source").trim()
            val lic = o.optString("license"); val ref = o.optString("reference"); val cat = o.optString("category", "Imported palette")
            val arr = o.optJSONArray("colors") ?: throw IllegalArgumentException("The file has no \"colors\" list")
            for (i in 0 until minOf(arr.length(), MAX_IMPORT)) {
                val c = arr.optJSONObject(i) ?: continue
                val hex = hexOf(c.optString("hex")); val name = c.optString("name").trim()
                if (hex == null || name.isEmpty()) { skipped++; continue }
                rows.add(ImportRow("", c.optString("code").trim(), name, hex, cat, ref, lic))
            }
        } else {
            val lines = t.lines().filter { it.isNotBlank() }
            if (lines.size < 2) throw IllegalArgumentException("The file is empty")
            val head = splitCsv(lines[0]).map { it.trim().lowercase() }
            val iSrc = head.indexOf("source"); val iName = head.indexOf("name"); val iHex = head.indexOf("hex")
            val iCode = head.indexOf("code"); val iLic = head.indexOf("license"); val iRef = head.indexOf("reference")
            if (iSrc < 0 || iName < 0 || iHex < 0) throw IllegalArgumentException("A CSV needs the columns source, name and hex")
            source = ""
            for (line in lines.drop(1).take(MAX_IMPORT)) {
                val f = splitCsv(line)
                fun col(i: Int) = if (i in f.indices) f[i].trim() else ""
                val hex = hexOf(col(iHex)); val name = col(iName)
                if (source.isEmpty()) source = col(iSrc)
                if (hex == null || name.isEmpty()) { skipped++; continue }
                rows.add(ImportRow("", col(iCode), name, hex, "Imported palette", col(iRef), col(iLic)))
            }
        }
        if (source.isEmpty()) throw IllegalArgumentException("The file does not say which source it is (for example \"RAL Classic\")")
        if (source.length > 40 || source.contains("|")) throw IllegalArgumentException("The source name is too long or contains the character |")
        if (source.lowercase() in setOf("css", "x11", "apple", "material", "tailwind", "web"))
            throw IllegalArgumentException("\"$source\" is a built-in source. Use the name of your own palette")
        if (rows.isEmpty()) throw IllegalArgumentException("No valid colors found (each needs a name and a HEX)")
        val fixed = rows.map { ImportRow(source, it.code, it.name, it.hex, it.category, it.ref, it.license) }
        user.replaceImport(source, fixed)
        colors.reloadImports(user.allImports())
        changed()
        return ImportResult(source, fixed.size, skipped)
    }

    fun removeImportedSource(source: String) {
        user.removeImport(source)
        colors.reloadImports(user.allImports())
        changed()
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false
        for (ch in line) when {
            ch == '"' -> q = !q
            ch == ',' && !q -> { out.add(sb.toString()); sb.setLength(0) }
            else -> sb.append(ch)
        }
        out.add(sb.toString())
        return out
    }

    fun clearFavorites() { user.clearFavorites(); changed() }
    fun clearCollections() { user.clearFolders(); changed() }

    companion object {
        private const val MAX_IMPORT = 20000
        lateinit var instance: Repo
            private set
        fun init(ctx: Context) { instance = Repo(ctx.applicationContext) }
    }
}
