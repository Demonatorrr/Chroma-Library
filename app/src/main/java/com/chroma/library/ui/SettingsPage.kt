package com.chroma.library.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatDelegate
import com.chroma.library.R
import com.chroma.library.data.Repo
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.NumberFormat

/** Theme, grid, data management and about. */
class SettingsPage(
    private val ctx: Context,
    private val onExport: () -> Unit,
    private val onImport: () -> Unit
) : Page {

    private val repo = Repo.instance
    private val scroll = ScrollView(ctx)
    override val view: View get() = scroll
    private val body = LinearLayout(ctx)

    init {
        scroll.isVerticalScrollBarEnabled = false
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(ctx.dp(22), ctx.dp(8), ctx.dp(22), ctx.dp(130))
        scroll.addView(body)
        build()
    }

    private fun section(title: String) {
        body.addView(ctx.label(title.uppercase(), 11.5f, R.color.ink2, medium = true, tracking = 0.08f),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(30); bottomMargin = ctx.dp(10) })
    }

    private fun divider() = body.addView(View(ctx).apply { setBackgroundColor(ctx.color(R.color.line)) },
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

    /** Equal-width pill choices. */
    private fun segmented(options: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundRect(ctx.color(R.color.fill), ctx.dpf(100f))
            setPadding(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4))
        }
        options.forEachIndexed { i, text ->
            val on = i == selected
            box.addView(ctx.label(text, 14.5f, if (on) R.color.bg else R.color.ink, medium = true).apply {
                gravity = Gravity.CENTER
                setPadding(0, ctx.dp(10), 0, ctx.dp(10))
                background = if (on) roundRect(ctx.color(R.color.ink), ctx.dpf(100f)) else null
                setOnClickListener { onPick(i) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        return box
    }

    private fun line(title: String, sub: String? = null, danger: Boolean = false, onClick: (() -> Unit)? = null): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ctx.dp(15), 0, ctx.dp(15))
            addView(ctx.label(title, 16f, if (danger) R.color.danger else R.color.ink, medium = true))
            if (sub != null) addView(ctx.label(sub, 13f, R.color.ink2),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(3) })
            if (onClick != null) { setOnClickListener { onClick() }; pressable(0.99f) }
        }

    private fun build() {
        body.removeAllViews()
        body.addView(ctx.label("Settings", 32f, R.color.ink, bold = true, tracking = -0.02f))

        section("Appearance")
        body.addView(segmented(listOf("System", "Light", "Dark"), repo.theme) { v ->
            if (v == repo.theme) return@segmented
            repo.theme = v
            AppCompatDelegate.setDefaultNightMode(
                when (v) { 1 -> AppCompatDelegate.MODE_NIGHT_NO; 2 -> AppCompatDelegate.MODE_NIGHT_YES; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM }
            )
        })

        section("Grid")
        body.addView(ctx.label("Swatches per row", 14f, R.color.ink2),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(10) })
        body.addView(segmented(listOf("2", "3", "4"), repo.columns - 2) { v -> repo.columns = v + 2; build() })
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, ctx.dp(18), 0, 0) }
        row.addView(ctx.label("Show HEX under names", 16f, R.color.ink, medium = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(MaterialSwitch(ctx).apply {
            isChecked = repo.showHex
            setOnCheckedChangeListener { _, on -> repo.showHex = on }
        })
        body.addView(row)

        section("Your data")
        body.addView(line("Export collections and favorites", "Save a backup file you can keep anywhere") { onExport() })
        divider()
        body.addView(line("Import from a backup", "Adds to what you already have") { onImport() })
        divider()
        body.addView(line("Clear favorites", danger = true) {
            Sheets.confirm(ctx, "Clear all favorites?", "This removes every favorite. Your collections are not affected.", "Clear") {
                repo.clearFavorites(); ctx.toast("Favorites cleared")
            }
        })
        divider()
        body.addView(line("Delete all collections", danger = true) {
            Sheets.confirm(ctx, "Delete all collections?", "Every collection and its contents will be removed. Favorites are not affected.", "Delete") {
                repo.clearCollections(); ctx.toast("Collections deleted")
            }
        })

        section("About")
        val n = NumberFormat.getIntegerInstance().format(repo.total)
        body.addView(line("Chroma Library", "Version 1.0 · $n named colors, stored on this device. No account, no ads, no internet needed."))
        divider()
        body.addView(line("Color names", "From meodai/color-names by David Aerne and contributors, shared under the MIT License. Names and HEX values are used exactly as published.") {
            Sheets.confirm(ctx, "MIT License", "Copyright (c) 2017 David Aerne\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files, to deal in the software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies, subject to including this copyright and permission notice in all copies or substantial portions of the software.\n\nThe software is provided “as is”, without warranty of any kind.", "OK") {}
        })
    }

    override fun setBottomInset(px: Int) {
        body.setPadding(body.paddingLeft, body.paddingTop, body.paddingRight, px)
    }

    override fun refresh() {}
}
