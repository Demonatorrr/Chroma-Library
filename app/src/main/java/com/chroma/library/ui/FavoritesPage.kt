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
import com.chroma.library.data.Repo

/** Colours you have hearted, newest first. */
class FavoritesPage(private val ctx: Context) : Page {

    private val repo = Repo.instance
    private val root = FrameLayout(ctx)
    override val view: View get() = root

    private val count = ctx.label("", 13.5f, R.color.ink2)
    private val list = RecyclerView(ctx)
    private val empty = LinearLayout(ctx)

    private val adapter = SwatchAdapter(
        onClick = { ctx.startActivity(Intent(ctx, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_HEX, it.hex)) },
        onLong = { Sheets.colorActions(ctx, it) { refresh() } }
    )

    init {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(22), ctx.dp(8), ctx.dp(22), ctx.dp(10)) }
        head.addView(ctx.label("Favorites", 32f, R.color.ink, bold = true, tracking = -0.02f))
        head.addView(count, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(4) })
        col.addView(head)

        list.layoutManager = GridLayoutManager(ctx, repo.columns)
        list.adapter = adapter
        list.clipToPadding = false
        list.setPadding(ctx.dp(14), ctx.dp(4), ctx.dp(14), ctx.dp(130))
        list.itemAnimator = null
        col.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        empty.orientation = LinearLayout.VERTICAL
        empty.gravity = Gravity.CENTER
        empty.setPadding(ctx.dp(40), 0, ctx.dp(40), ctx.dp(80))
        empty.addView(ctx.label("No favorites yet", 20f, R.color.ink, bold = true))
        empty.addView(ctx.label("Tap the heart on any color to keep it here.", 15f, R.color.ink2).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(8) })
        root.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        refresh()
    }

    override fun setBottomInset(px: Int) {
        list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, px)
    }

    override fun refresh() {
        val cols = repo.columns
        (list.layoutManager as GridLayoutManager).spanCount = cols
        adapter.ratio = when (cols) { 2 -> 0.86f; 3 -> 1f; else -> 1.1f }
        adapter.showHex = repo.showHex
        val favs = repo.favorites()
        adapter.setAll(favs)
        count.text = if (favs.size == 1) "1 color" else "${favs.size} colors"
        empty.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE
    }
}
