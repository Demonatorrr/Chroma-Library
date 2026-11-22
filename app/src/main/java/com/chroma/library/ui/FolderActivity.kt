package com.chroma.library.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chroma.library.R
import com.chroma.library.data.Repo

/** The colours inside one collection. */
class FolderActivity : AppCompatActivity() {

    private val repo = Repo.instance
    private var folderId = -1L
    private lateinit var titleView: android.widget.TextView
    private lateinit var count: android.widget.TextView
    private lateinit var list: RecyclerView
    private lateinit var empty: View
    private lateinit var adapter: SwatchAdapter
    private val observer: () -> Unit = { reload() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        folderId = intent.getLongExtra(EXTRA_ID, -1)

        val root = FrameLayout(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), 0)
        }
        bar.addView(iconButton(R.drawable.ic_back, "Back") { finish() })
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(iconButton(R.drawable.ic_more, "Options") { options() })
        col.addView(bar)

        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(4), dp(22), dp(10)) }
        titleView = label("", 30f, R.color.ink, bold = true, tracking = -0.02f)
        count = label("", 13.5f, R.color.ink2)
        head.addView(titleView)
        head.addView(count, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        col.addView(head)

        adapter = SwatchAdapter(
            onClick = { startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_HEX, it.hex)) },
            onLong = { Sheets.colorActions(this, it, inFolder = folderId) { reload() } }
        )
        list = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@FolderActivity, repo.columns)
            adapter = this@FolderActivity.adapter
            clipToPadding = false
            setPadding(dp(14), dp(4), dp(14), dp(40))
            itemAnimator = null
        }
        col.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val e = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(40), 0, dp(40), dp(80))
            addView(label("This collection is empty", 20f, R.color.ink, bold = true))
            addView(label("Open any color and choose “Save to collection”, or long-press a swatch in Explore.", 15f, R.color.ink2).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        empty = e
        root.addView(e, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, b.top, 0, b.bottom)
            insets
        }
        repo.observe(observer)
        reload()
    }

    override fun onDestroy() {
        repo.unobserve(observer)
        super.onDestroy()
    }

    private fun reload() {
        val f = repo.folder(folderId)
        if (f == null) { finish(); return }
        titleView.text = f.name
        count.text = if (f.count == 1) "1 color" else "${f.count} colors"
        val cols = repo.columns
        (list.layoutManager as GridLayoutManager).spanCount = cols
        adapter.ratio = when (cols) { 2 -> 0.86f; 3 -> 1f; else -> 1.1f }
        adapter.showHex = repo.showHex
        val colors = repo.folderColors(folderId)
        adapter.setAll(colors)
        empty.visibility = if (colors.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun options() {
        val f = repo.folder(folderId) ?: return
        Sheets.menu(this, f.name, listOf(
            Sheets.Item("Copy all HEX codes", R.drawable.ic_copy) {
                val all = repo.folderColors(folderId).joinToString("\n") { "#${it.hex}" }
                if (all.isEmpty()) toast("Nothing to copy yet") else copyText("HEX codes", all)
            },
            Sheets.Item("Rename or change cover", R.drawable.ic_edit) {
                Sheets.folderEditor(this, f) { name, cover -> repo.updateFolder(f.id, name, cover) }
            },
            Sheets.Item("Delete collection", R.drawable.ic_delete, danger = true) {
                Sheets.confirm(this, "Delete “${f.name}”?", "The colors stay in the library. Only this collection is removed.", "Delete") {
                    repo.deleteFolder(f.id)
                }
            }
        ))
    }

    companion object { const val EXTRA_ID = "folder_id" }
}
