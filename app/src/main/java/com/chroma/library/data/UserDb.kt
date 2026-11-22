package com.chroma.library.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Everything the user creates: favourites, collections and what is inside them.
 * Colours are referenced by HEX, which is unique in the library, so a library update can't break them.
 */
class UserDb(ctx: Context) : SQLiteOpenHelper(ctx, "user.db", null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE favorites(hex TEXT PRIMARY KEY, added_at INTEGER NOT NULL)")
        db.execSQL(
            "CREATE TABLE folders(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                "cover INTEGER NOT NULL, position INTEGER NOT NULL, created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE folder_items(folder_id INTEGER NOT NULL REFERENCES folders(id) ON DELETE CASCADE, " +
                "hex TEXT NOT NULL, added_at INTEGER NOT NULL, PRIMARY KEY(folder_id, hex))"
        )
        db.execSQL("CREATE INDEX idx_items_hex ON folder_items(hex)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ---- favourites ---------------------------------------------------------

    fun isFavorite(hex: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM favorites WHERE hex = ?", arrayOf(hex)).use { it.moveToFirst() }

    /** Flips the favourite state and returns the new one. */
    fun toggleFavorite(hex: String): Boolean {
        val db = writableDatabase
        return if (isFavorite(hex)) {
            db.delete("favorites", "hex = ?", arrayOf(hex)); false
        } else {
            db.insertWithOnConflict("favorites", null, ContentValues().apply {
                put("hex", hex); put("added_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_REPLACE); true
        }
    }

    fun favoriteHexes(): List<String> =
        readableDatabase.rawQuery("SELECT hex FROM favorites ORDER BY added_at DESC", null).use { c ->
            val out = ArrayList<String>(c.count)
            while (c.moveToNext()) out.add(c.getString(0))
            out
        }

    fun clearFavorites() { writableDatabase.delete("favorites", null, null) }

    // ---- collections --------------------------------------------------------

    data class Row(val id: Long, val name: String, val cover: Int, val count: Int)

    fun folders(): List<Row> =
        readableDatabase.rawQuery(
            "SELECT f.id, f.name, f.cover, (SELECT COUNT(*) FROM folder_items i WHERE i.folder_id = f.id) " +
                "FROM folders f ORDER BY f.position, f.id", null
        ).use { c ->
            val out = ArrayList<Row>()
            while (c.moveToNext()) out.add(Row(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3)))
            out
        }

    fun folder(id: Long): Row? = folders().firstOrNull { it.id == id }

    fun createFolder(name: String, cover: Int): Long {
        val db = writableDatabase
        val pos = db.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM folders", null).use { it.moveToFirst(); it.getInt(0) }
        return db.insert("folders", null, ContentValues().apply {
            put("name", name); put("cover", cover); put("position", pos); put("created_at", System.currentTimeMillis())
        })
    }

    fun updateFolder(id: Long, name: String, cover: Int) {
        writableDatabase.update("folders", ContentValues().apply { put("name", name); put("cover", cover) }, "id = ?", arrayOf(id.toString()))
    }

    fun deleteFolder(id: Long) { writableDatabase.delete("folders", "id = ?", arrayOf(id.toString())) }

    /** Moves a collection one place earlier (-1) or later (+1) in the list. */
    fun moveFolder(id: Long, delta: Int) {
        val list = folders().map { it.id }.toMutableList()
        val i = list.indexOf(id)
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        list.add(j, list.removeAt(i))
        val db = writableDatabase
        db.beginTransaction()
        try {
            list.forEachIndexed { pos, fid ->
                db.update("folders", ContentValues().apply { put("position", pos) }, "id = ?", arrayOf(fid.toString()))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun clearFolders() { writableDatabase.delete("folders", null, null) }

    fun folderHexes(folderId: Long): List<String> =
        readableDatabase.rawQuery(
            "SELECT hex FROM folder_items WHERE folder_id = ? ORDER BY added_at DESC", arrayOf(folderId.toString())
        ).use { c ->
            val out = ArrayList<String>(c.count)
            while (c.moveToNext()) out.add(c.getString(0))
            out
        }

    fun previewHexes(folderId: Long, limit: Int): List<String> =
        readableDatabase.rawQuery(
            "SELECT hex FROM folder_items WHERE folder_id = ? ORDER BY added_at DESC LIMIT $limit", arrayOf(folderId.toString())
        ).use { c ->
            val out = ArrayList<String>()
            while (c.moveToNext()) out.add(c.getString(0))
            out
        }

    fun foldersOf(hex: String): Set<Long> =
        readableDatabase.rawQuery("SELECT folder_id FROM folder_items WHERE hex = ?", arrayOf(hex)).use { c ->
            val out = HashSet<Long>()
            while (c.moveToNext()) out.add(c.getLong(0))
            out
        }

    fun setInFolder(folderId: Long, hex: String, inside: Boolean) {
        val db = writableDatabase
        if (inside) {
            db.insertWithOnConflict("folder_items", null, ContentValues().apply {
                put("folder_id", folderId); put("hex", hex); put("added_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_IGNORE)
        } else {
            db.delete("folder_items", "folder_id = ? AND hex = ?", arrayOf(folderId.toString(), hex))
        }
    }

    fun moveColor(hex: String, from: Long, to: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            setInFolder(to, hex, true)
            setInFolder(from, hex, false)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
