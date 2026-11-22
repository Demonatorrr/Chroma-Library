package com.chroma.library.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.chroma.library.R
import com.chroma.library.data.ColorItem
import com.chroma.library.data.Folder
import com.chroma.library.data.Repo
import com.chroma.library.data.Sort
import com.chroma.library.domain.ColorMath
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Bottom sheets and dialogs used from several screens. */
object Sheets {

    private fun sheet(ctx: Context, title: String?, build: (LinearLayout, BottomSheetDialog) -> Unit): BottomSheetDialog {
        val d = BottomSheetDialog(ctx)
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(20), ctx.dp(22), ctx.dp(20), ctx.dp(26))
        }
        if (title != null) {
            col.addView(ctx.label(title, 20f, R.color.ink, bold = true, tracking = -0.01f),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(10) })
        }
        build(col, d)
        d.setContentView(col)
        d.show()
        return d
    }

    /** One tappable line: optional icon or colour dot, text, optional check. */
    private fun row(
        ctx: Context, text: String, icon: Int = 0, dot: Int? = null, sub: String? = null,
        checked: Boolean = false, danger: Boolean = false, onClick: () -> Unit
    ): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ctx.dp(54)
        if (icon != 0) {
            addView(ImageView(ctx).apply {
                setImageResource(icon)
                setColorFilter(ctx.color(if (danger) R.color.danger else R.color.ink))
            }, LinearLayout.LayoutParams(ctx.dp(22), ctx.dp(22)).apply { marginEnd = ctx.dp(16) })
        }
        if (dot != null) {
            addView(View(ctx).apply { background = ctx.swatchBackground(dot, ctx.dpf(11f)) },
                LinearLayout.LayoutParams(ctx.dp(34), ctx.dp(34)).apply { marginEnd = ctx.dp(16) })
        }
        val names = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        names.addView(ctx.label(text, 16f, if (danger) R.color.danger else R.color.ink, medium = true))
        if (sub != null) names.addView(ctx.label(sub, 12.5f, R.color.ink2),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(2) })
        addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (checked) addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_check); setColorFilter(ctx.color(R.color.ink))
        }, LinearLayout.LayoutParams(ctx.dp(22), ctx.dp(22)))
        setOnClickListener { onClick() }
        pressable(0.99f)
    }

    class Item(val label: String, val icon: Int, val danger: Boolean = false, val action: () -> Unit)

    /** A simple list of actions in a bottom sheet. */
    fun menu(ctx: Context, title: String?, items: List<Item>) {
        sheet(ctx, title) { col, d ->
            for (i in items) col.addView(row(ctx, i.label, i.icon, danger = i.danger) { d.dismiss(); i.action() })
        }
    }

    // ---- sort -----------------------------------------------------------------

    fun sort(ctx: Context, current: Sort, onPick: (Sort) -> Unit) {
        sheet(ctx, "Sort by") { col, d ->
            for (s in Sort.values()) col.addView(row(ctx, s.label, checked = s == current) { onPick(s); d.dismiss() })
        }
    }

    // ---- what you can do with one colour ---------------------------------------

    /**
     * Quick actions for a colour. When it is shown inside a collection, [inFolder] adds
     * "Move to…" and "Remove from collection".
     */
    fun colorActions(ctx: Context, c: ColorItem, inFolder: Long? = null, onChanged: () -> Unit = {}) {
        val repo = Repo.instance
        sheet(ctx, null) { col, d ->
            val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(View(ctx).apply { background = ctx.swatchBackground(c.argb, ctx.dpf(14f)) },
                LinearLayout.LayoutParams(ctx.dp(52), ctx.dp(52)).apply { marginEnd = ctx.dp(14) })
            val t = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            t.addView(ctx.label(c.name, 18f, R.color.ink, bold = true))
            t.addView(ctx.label("#${c.hex}", 13f, R.color.ink2).apply { typeface = Typeface.create("monospace", Typeface.NORMAL) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(3) })
            head.addView(t)
            col.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(10) })

            col.addView(row(ctx, "Copy HEX", R.drawable.ic_copy) { ctx.copyText("HEX", "#${c.hex}"); d.dismiss() })
            val fav = repo.isFavorite(c.hex)
            col.addView(row(ctx, if (fav) "Remove from favorites" else "Add to favorites",
                if (fav) R.drawable.ic_heart_outline else R.drawable.ic_heart) {
                repo.toggleFavorite(c.hex); d.dismiss(); onChanged()
            })
            col.addView(row(ctx, "Save to collection…", R.drawable.ic_folder_add) { d.dismiss(); saveTo(ctx, c.hex, onChanged) })
            if (inFolder != null) {
                col.addView(row(ctx, "Move to another collection…", R.drawable.ic_folder) {
                    d.dismiss()
                    chooseFolder(ctx, exclude = inFolder) { target ->
                        repo.moveColor(c.hex, inFolder, target.id); ctx.toast("Moved to ${target.name}"); onChanged()
                    }
                })
                col.addView(row(ctx, "Remove from this collection", R.drawable.ic_delete, danger = true) {
                    repo.setInFolder(inFolder, c.hex, false); d.dismiss(); onChanged()
                })
            }
        }
    }

    // ---- collections ---------------------------------------------------------------

    /** Tick the collections a colour should be in. Changes are saved immediately. */
    fun saveTo(ctx: Context, hex: String, onChanged: () -> Unit = {}) {
        val repo = Repo.instance
        lateinit var dialog: BottomSheetDialog
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        fun fill() {
            list.removeAllViews()
            val inside = repo.foldersOf(hex)
            val folders = repo.folders()
            if (folders.isEmpty()) list.addView(ctx.label("You have no collections yet.", 14f, R.color.ink2).apply {
                setPadding(0, ctx.dp(8), 0, ctx.dp(12))
            })
            for (f in folders) {
                list.addView(row(ctx, f.name, dot = f.cover, sub = "${f.count} colors", checked = f.id in inside) {
                    repo.setInFolder(f.id, hex, f.id !in inside); fill(); onChanged()
                })
            }
        }
        dialog = sheet(ctx, "Save to collection") { col, _ ->
            col.addView(ScrollView(ctx).apply { addView(list) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            col.addView(ctx.pillButton("New collection", R.drawable.ic_add, primary = true) {
                folderEditor(ctx, null) { name, cover ->
                    val id = repo.createFolder(name, cover)
                    repo.setInFolder(id, hex, true)
                    fill(); onChanged()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(12) })
        }
        fill()
    }

    fun chooseFolder(ctx: Context, exclude: Long? = null, onPick: (Folder) -> Unit) {
        val folders = Repo.instance.folders().filter { it.id != exclude }
        sheet(ctx, "Choose collection") { col, d ->
            if (folders.isEmpty()) col.addView(ctx.label("There is no other collection. Create one first.", 14f, R.color.ink2))
            for (f in folders) col.addView(row(ctx, f.name, dot = f.cover, sub = "${f.count} colors") { onPick(f); d.dismiss() })
        }
    }

    private val COVERS = listOf(
        "FF6B6B", "FF9F43", "FFC145", "9BDB4D", "3DD6A3", "2EC4D6", "4F6BFF", "8B5CF6",
        "E879F9", "F472B6", "A16207", "64748B", "0F172A", "F1F5F9"
    ).map { 0xFF000000.toInt() or it.toInt(16) }

    /** Create ([existing] == null) or edit a collection: name and a cover colour. */
    fun folderEditor(ctx: Context, existing: Folder?, onDone: (String, Int) -> Unit) {
        var cover = existing?.cover ?: COVERS[6]
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(24), ctx.dp(8), ctx.dp(24), 0)
        }
        val name = EditText(ctx).apply {
            hint = "Collection name"
            setText(existing?.name ?: "")
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setTextColor(ctx.color(R.color.ink))
            setHintTextColor(ctx.color(R.color.ink2))
            background = roundRect(ctx.color(R.color.fill), ctx.dpf(14f))
            setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
        }
        body.addView(name)
        body.addView(ctx.label("COVER COLOR", 11f, R.color.ink2, medium = true, tracking = 0.08f),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(18); bottomMargin = ctx.dp(10) })
        val dots = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val hexField = EditText(ctx).apply {
            hint = "#HEX"
            setSingleLine()
            setText("#" + String.format("%06X", cover and 0xFFFFFF))
            setTextColor(ctx.color(R.color.ink))
            setHintTextColor(ctx.color(R.color.ink2))
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            background = roundRect(ctx.color(R.color.fill), ctx.dpf(14f))
            setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(12))
        }
        fun paintDots() {
            dots.removeAllViews()
            for (c in COVERS) {
                val sel = c == cover
                dots.addView(View(ctx).apply {
                    background = ctx.swatchBackground(c, ctx.dpf(17f)).apply {
                        if (sel) setStroke(ctx.dp(3), ctx.color(R.color.ink))
                    }
                    setOnClickListener {
                        cover = c
                        hexField.setText("#" + String.format("%06X", c and 0xFFFFFF))
                        paintDots()
                    }
                    pressable(0.9f)
                }, LinearLayout.LayoutParams(ctx.dp(34), ctx.dp(34)).apply { marginEnd = ctx.dp(10) })
            }
        }
        paintDots()
        body.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(dots) })
        body.addView(hexField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(14) })

        MaterialAlertDialogBuilder(ctx)
            .setTitle(if (existing == null) "New collection" else "Edit collection")
            .setView(body)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (existing == null) "Create" else "Save") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isEmpty()) { ctx.toast("Give it a name"); return@setPositiveButton }
                val typed = ColorMath.parseHex(hexField.text.toString()) ?: cover
                onDone(n, typed)
            }
            .show()
    }

    fun confirm(ctx: Context, title: String, message: String, action: String, onYes: () -> Unit) {
        MaterialAlertDialogBuilder(ctx).setTitle(title).setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(action) { _, _ -> onYes() }
            .show()
    }
}
