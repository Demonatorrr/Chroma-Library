package com.chroma.library.ui

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chroma.library.R
import com.chroma.library.data.Folder
import com.chroma.library.data.Repo
import com.chroma.library.domain.ColorMath

/** Your folders of colours. */
class CollectionsPage(private val ctx: Context) : Page {

    private val repo = Repo.instance
    private val root = FrameLayout(ctx)
    override val view: View get() = root

    private val count = ctx.label("", 13.5f, R.color.ink2)
    private val list = RecyclerView(ctx)
    private val empty = LinearLayout(ctx)
    private var folders: List<Folder> = emptyList()

    private val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = folders.size
        override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
            val c = p.context
            val tile = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(c.dp(6), c.dp(6), c.dp(6), c.dp(12))
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            tile.pressable(0.98f)
            return object : RecyclerView.ViewHolder(tile) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, i: Int) {
            val f = folders[i]
            val tile = h.itemView as LinearLayout
            val c = tile.context
            tile.removeAllViews()
            val cover = RatioFrame(c, 0.78f)
            cover.background = c.swatchBackground(f.cover, c.dpf(Ui.R_LARGE.toFloat()))
            // the newest colours inside, as small overlapping dots
            val dots = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            f.preview.forEachIndexed { k, argb ->
                dots.addView(View(c).apply {
                    background = c.swatchBackground(argb, c.dpf(14f)).apply { setStroke(c.dp(2), f.cover) }
                }, LinearLayout.LayoutParams(c.dp(28), c.dp(28)).apply { if (k > 0) marginStart = -c.dp(8) })
            }
            cover.addView(dots, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START)
                .apply { setMargins(c.dp(14), 0, 0, c.dp(14)) })
            if (f.count == 0) {
                cover.addView(c.label("Empty", 13f, R.color.ink, medium = true).apply { setTextColor(ColorMath.onColor(f.cover)); alpha = 0.7f },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START)
                        .apply { setMargins(c.dp(16), 0, 0, c.dp(16)) })
            }
            tile.addView(cover, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            tile.addView(c.label(f.name, 16f, R.color.ink, medium = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = c.dp(10) })
            tile.addView(c.label(if (f.count == 1) "1 color" else "${f.count} colors", 12.5f, R.color.ink2),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = c.dp(2) })
            tile.setOnClickListener { ctx.startActivity(Intent(ctx, FolderActivity::class.java).putExtra(FolderActivity.EXTRA_ID, f.id)) }
            tile.setOnLongClickListener { manage(f); true }
        }
    }

    init {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(22), ctx.dp(8), ctx.dp(12), ctx.dp(10))
        }
        val titles = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(ctx.label("Collections", 32f, R.color.ink, bold = true, tracking = -0.02f))
        titles.addView(count, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(4) })
        top.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(ctx.iconButton(R.drawable.ic_folder_add, "New collection") { create() })
        col.addView(top)

        list.layoutManager = GridLayoutManager(ctx, 2)
        list.adapter = adapter
        list.clipToPadding = false
        list.setPadding(ctx.dp(14), ctx.dp(4), ctx.dp(14), ctx.dp(130))
        list.itemAnimator = null
        col.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        empty.orientation = LinearLayout.VERTICAL
        empty.gravity = Gravity.CENTER
        empty.setPadding(ctx.dp(40), 0, ctx.dp(40), ctx.dp(80))
        empty.addView(ctx.label("Organize your colors", 20f, R.color.ink, bold = true))
        empty.addView(ctx.label("Make collections for projects, moods or brands, and save any color into them.", 15f, R.color.ink2).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(8); bottomMargin = ctx.dp(20) })
        empty.addView(ctx.pillButton("New collection", R.drawable.ic_add, primary = true) { create() })
        root.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        refresh()
    }

    private fun create() {
        Sheets.folderEditor(ctx, null) { name, cover -> repo.createFolder(name, cover); refresh() }
    }

    private fun manage(f: Folder) {
        Sheets.menu(ctx, f.name, listOf(
            Sheets.Item("Rename or change cover", R.drawable.ic_edit) {
                Sheets.folderEditor(ctx, f) { name, cover -> repo.updateFolder(f.id, name, cover); refresh() }
            },
            Sheets.Item("Move earlier", R.drawable.ic_back) { repo.moveFolder(f.id, -1); refresh() },
            Sheets.Item("Move later", R.drawable.ic_sort) { repo.moveFolder(f.id, 1); refresh() },
            Sheets.Item("Delete collection", R.drawable.ic_delete, danger = true) {
                Sheets.confirm(ctx, "Delete “${f.name}”?", "The colors stay in the library. Only this collection is removed.", "Delete") {
                    repo.deleteFolder(f.id); refresh()
                }
            }
        ))
    }

    override fun setBottomInset(px: Int) {
        list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, px)
    }

    override fun refresh() {
        folders = repo.folders()
        adapter.notifyDataSetChanged()
        count.text = if (folders.size == 1) "1 collection" else "${folders.size} collections"
        empty.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
    }
}
