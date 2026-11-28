package com.chroma.library.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.Locale
import java.text.Normalizer

/**
 * The read-only colour library. The database ships inside the app (assets/colors.db, built from
 * official palettes (Apple, CSS, Material, Tailwind)) and is copied once to private storage, so everything works offline.
 */
class ColorDb(private val ctx: Context) {

    private val db: SQLiteDatabase = open()

    private fun open(): SQLiteDatabase {
        val dir = ctx.getDatabasePath(FILE).parentFile!!
        dir.mkdirs()
        val target = File(dir, FILE)
        if (!target.exists() || target.length() < 1024) {
            // stale copies from older dataset versions are removed first
            dir.listFiles { f -> f.name.startsWith("colors_v") && f.name != FILE }?.forEach { it.delete() }
            val tmp = File(dir, "$FILE.tmp")
            ctx.assets.open("colors.db").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(target)
        }
        return SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun read(c: Cursor) = ColorItem(
        name = c.getString(0), hex = c.getString(1),
        r = c.getInt(2), g = c.getInt(3), b = c.getInt(4),
        h = c.getFloat(5), s = c.getFloat(6), l = c.getFloat(7), v = c.getFloat(8),
        family = c.getString(9), sets = c.getString(10), also = c.getString(11)
    )

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun fold(s: String) =
        Normalizer.normalize(s, Normalizer.Form.NFKD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
            .lowercase(Locale.ROOT)

    private class Where(val sql: String, val args: List<String>, val prefix: String?)

    /** Turns a [Query] into SQL. Every word must appear in the name; hex-looking input also matches HEX. */
    private fun where(q: Query): Where {
        val parts = ArrayList<String>()
        val args = ArrayList<String>()
        var prefix: String? = null
        val text = q.text.trim()
        if (text.isNotEmpty()) {
            val hexLike = text.removePrefix("#")
            val isHex = hexLike.length in 2..6 && hexLike.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            val words = fold(text.removePrefix("#")).split(Regex("\\s+")).filter { it.isNotEmpty() }
            val nameClause = words.joinToString(" AND ") { "name_search LIKE ? ESCAPE '\\'" }
            val nameArgs = words.map { "%${esc(it)}%" }
            if (isHex && words.size == 1) {
                parts.add("(hex LIKE ? ESCAPE '\\' OR $nameClause)")
                args.add("${esc(hexLike.uppercase(Locale.ROOT))}%")
                args.addAll(nameArgs)
            } else if (words.isNotEmpty()) {
                parts.add("($nameClause)")
                args.addAll(nameArgs)
            }
            prefix = "${esc(fold(text))}%"
        }
        q.family?.let { parts.add("family = ?"); args.add(it) }
        if (q.set != null) { parts.add("sets LIKE ?"); args.add("%|${q.set}|%") } else parts.add(MAIN)
        return Where(if (parts.isEmpty()) "" else " WHERE " + parts.joinToString(" AND "), args, prefix)
    }

    fun count(q: Query): Int {
        val w = where(q)
        db.rawQuery("SELECT COUNT(*) FROM colors${w.sql}", w.args.toTypedArray()).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun page(q: Query, limit: Int, offset: Int): List<ColorItem> {
        val w = where(q)
        val args = ArrayList(w.args)
        var order = when (q.sort) {
            Sort.NAME -> "name_search, name"
            Sort.NAME_DESC -> "name_search DESC, name DESC"
            Sort.HUE -> "hue_key"
            Sort.LIGHT -> "l DESC, h"
            Sort.DARK -> "l, h"
            Sort.SATURATION -> "s DESC, l"
        }
        if (q.sort == Sort.NAME && w.prefix != null) {
            // names that start with what was typed come first
            order = "CASE WHEN name_search LIKE ? ESCAPE '\\' THEN 0 ELSE 1 END, $order"
            args.add(w.prefix) // the CASE comes after the WHERE clause in the statement
        }
        val sql = "SELECT $COLS FROM colors${w.sql} ORDER BY $order LIMIT $limit OFFSET $offset"
        db.rawQuery(sql, args.toTypedArray()).use { c ->
            val out = ArrayList<ColorItem>(c.count)
            while (c.moveToNext()) out.add(read(c))
            return out
        }
    }

    fun byHex(hex: String): ColorItem? =
        db.rawQuery("SELECT $COLS FROM colors WHERE hex = ?", arrayOf(hex.uppercase(Locale.ROOT))).use { c ->
            if (c.moveToFirst()) read(c) else null
        }

    /** Looks up many colours at once. Missing ones are skipped. */
    fun byHexes(hexes: List<String>): Map<String, ColorItem> {
        val out = HashMap<String, ColorItem>(hexes.size)
        for (chunk in hexes.chunked(400)) {
            val marks = chunk.joinToString(",") { "?" }
            db.rawQuery("SELECT $COLS FROM colors WHERE hex IN ($marks)", chunk.toTypedArray()).use { c ->
                while (c.moveToNext()) { val it = read(c); out[it.hex] = it }
            }
        }
        return out
    }

    fun total(): Int = db.rawQuery("SELECT COUNT(*) FROM colors", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun random(): ColorItem? =
        db.rawQuery("SELECT $COLS FROM colors WHERE $MAIN ORDER BY RANDOM() LIMIT 1", null).use { if (it.moveToFirst()) read(it) else null }

    fun familyCounts(): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        db.rawQuery("SELECT family, COUNT(*) FROM colors GROUP BY family", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getInt(1)
        }
        return out
    }

    /** The closest colours by perceived distance (weighted RGB), not including [item]. */
    fun similar(item: ColorItem, limit: Int): List<ColorItem> {
        val rm = item.r; val gm = item.g; val bm = item.b
        val sql = "SELECT $COLS FROM colors WHERE hex != ? ORDER BY " +
            "((r-$rm)*(r-$rm)*2 + (g-$gm)*(g-$gm)*4 + (b-$bm)*(b-$bm)*3) LIMIT $limit"
        db.rawQuery(sql, arrayOf(item.hex)).use { c ->
            val out = ArrayList<ColorItem>()
            while (c.moveToNext()) out.add(read(c))
            return out
        }
    }

    /** The official colour closest to the given RGB (weighted distance), or null if the library is empty. */
    fun nearest(r: Int, g: Int, b: Int): ColorItem? {
        val sql = "SELECT $COLS FROM colors ORDER BY ((r-$r)*(r-$r)*2 + (g-$g)*(g-$g)*4 + (b-$b)*(b-$b)*3) LIMIT 1"
        return db.rawQuery(sql, null).use { if (it.moveToFirst()) read(it) else null }
    }

    /**
     * Lighter and darker neighbours with nearly the same hue and saturation:
     * up to [each] darker ones, the colour itself, then up to [each] lighter ones.
     */
    fun shades(item: ColorItem, each: Int): List<ColorItem> {
        val sql = "SELECT $COLS FROM colors WHERE (ABS(h - ?) < 12 OR ABS(h - ?) > 348) AND ABS(s - ?) < 0.22 ORDER BY l"
        val args = arrayOf(item.h.toString(), item.h.toString(), item.s.toString())
        val all = ArrayList<ColorItem>()
        db.rawQuery(sql, args).use { c -> while (c.moveToNext()) all.add(read(c)) }
        val darker = all.filter { it.l < item.l && it.hex != item.hex }.takeLast(each)
        val lighter = all.filter { it.l > item.l && it.hex != item.hex }.take(each)
        return darker + item + lighter
    }

    companion object {
        private const val FILE = "colors_v3.db"
        /** Explore's "All" shows the general name lists; design-system palettes (Material, Apple, Tailwind) appear under their own chip. */
        private const val MAIN = "(sets LIKE '%|Web|%' OR sets LIKE '%|X11|%')"
        private const val COLS = "name, hex, r, g, b, h, s, l, v, family, sets, also"
    }
}
