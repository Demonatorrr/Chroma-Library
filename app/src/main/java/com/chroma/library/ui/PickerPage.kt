package com.chroma.library.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.chroma.library.R
import com.chroma.library.data.ColorItem
import com.chroma.library.data.Repo
import com.chroma.library.domain.ColorMath

/**
 * Make any colour with a wheel, a brightness bar or a HEX code. The name shown is always an official one:
 * the exact name when the colour is in a palette, otherwise the nearest official colour (clearly labelled).
 */
class PickerPage(private val ctx: Context) : Page {

    private val repo = Repo.instance
    private val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
    override val view: View get() = scroll
    private val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(22), ctx.dp(8), ctx.dp(22), 0) }

    private val hsv = floatArrayOf(210f, 0.6f, 1f)
    private var syncing = false
    private var current: ColorItem? = null

    private val wheel = ColorWheel(ctx)
    private val bar = BrightnessBar(ctx)
    private val hexInput = EditText(ctx)
    private val swatch = View(ctx)
    private val nameView = ctx.label("", 24f, R.color.ink, bold = true, tracking = -0.02f)
    private val infoView = ctx.label("", 14f, R.color.ink2)
    private val nearView = ctx.label("", 14f, R.color.ink, medium = true)
    private val values = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val heart = ctx.iconButton(R.drawable.ic_heart_outline, "Favorite", 48) { toggleFavorite() }
    private lateinit var save: TextView

    init {
        scroll.addView(body)
        body.addView(ctx.label("Picker", 32f, R.color.ink, bold = true, tracking = -0.02f))
        body.addView(ctx.label("Make a color, get its official name", 13.5f, R.color.ink2),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(4) })

        body.addView(wheel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { topMargin = ctx.dp(18) })
        wheel.post { // square, as wide as the column but never huge
            val w = minOf(wheel.width, ctx.dp(340))
            wheel.layoutParams = (wheel.layoutParams as LinearLayout.LayoutParams).apply { height = w; gravity = Gravity.CENTER_HORIZONTAL; width = w }
        }
        wheel.onChange = { h, s -> hsv[0] = h; hsv[1] = s; bar.set(h, s, hsv[2]); update(fromUser = true) }
        body.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(44)).apply { topMargin = ctx.dp(10) })
        bar.onChange = { v -> hsv[2] = v; wheel.value = v; update(fromUser = true) }

        // result card
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(ctx.color(R.color.fill), ctx.dpf(Ui.R_LARGE.toFloat()))
            setPadding(ctx.dp(18), ctx.dp(18), ctx.dp(18), ctx.dp(14))
        }
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(swatch, LinearLayout.LayoutParams(ctx.dp(72), ctx.dp(72)).apply { marginEnd = ctx.dp(16) })
        val names = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        nameView.maxLines = 2
        names.addView(nameView)
        names.addView(infoView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(4) })
        top.addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(heart)
        card.addView(top)
        nearView.visibility = View.GONE
        card.addView(nearView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(14) })
        body.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(18) })

        // HEX field
        val hexBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(ctx.color(R.color.fill), ctx.dpf(100f))
            setPadding(ctx.dp(18), 0, ctx.dp(6), 0)
        }
        hexBox.addView(ctx.label("HEX", 12f, R.color.ink2, medium = true, tracking = 0.08f), LinearLayout.LayoutParams(ctx.dp(48), ViewGroup.LayoutParams.WRAP_CONTENT))
        hexInput.apply {
            setText("#")
            setTextColor(ctx.color(R.color.ink))
            textSize = 17f
            typeface = Typeface.create("monospace", Typeface.NORMAL)
            background = null
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setPadding(0, ctx.dp(13), 0, ctx.dp(13))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { if (!syncing) fromHex(s?.toString() ?: "") }
            })
        }
        hexBox.addView(hexInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hexBox.addView(ctx.iconButton(R.drawable.ic_copy, "Copy HEX", 44, R.color.ink2) { current?.let { ctx.copyText("HEX", "#${it.hex}") } })
        body.addView(hexBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(14) })

        body.addView(values, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(8) })

        save = ctx.pillButton("Save to collection", R.drawable.ic_folder_add, primary = true) {
            current?.let { c -> Sheets.saveTo(ctx, c.hex) { paintSave() } }
        }
        val actions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(save, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = ctx.dp(10) })
        actions.addView(ctx.pillButton("Details", R.drawable.ic_explore) {
            current?.let { ctx.startActivity(Intent(ctx, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_HEX, it.hex)) }
        })
        body.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(16) })

        wheel.set(hsv[0], hsv[1]); bar.set(hsv[0], hsv[1], hsv[2]); wheel.value = hsv[2]
        update(fromUser = false)
    }

    /** Recomputes everything from [hsv]. */
    private fun update(fromUser: Boolean) {
        val argb = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], hsv[2]))
        val hex = "%06X".format(argb and 0xFFFFFF)
        syncing = true
        if (hexInput.text.toString().removePrefix("#").uppercase() != hex) {
            hexInput.setText("#$hex"); hexInput.setSelection(hexInput.text.length)
        }
        syncing = false
        show(repo.color(hex) ?: return)
    }

    private fun fromHex(text: String) {
        val v = ColorMath.parseHex(text) ?: return
        Color.colorToHSV(v, hsv)
        wheel.set(hsv[0], hsv[1]); wheel.value = hsv[2]; bar.set(hsv[0], hsv[1], hsv[2])
        val hex = "%06X".format(v and 0xFFFFFF)
        show(repo.color(hex) ?: return)
    }

    private fun show(c: ColorItem) {
        current = c
        swatch.background = ctx.swatchBackground(c.argb, ctx.dpf(Ui.R_MEDIUM.toFloat()))
        paintHeart(); paintSave()
        if (c.official) {
            nameView.text = c.name
            infoView.text = c.setList.joinToString(" · ") + if (c.also.isNotEmpty()) "\nAlso: ${c.also}" else ""
            nearView.visibility = View.GONE
        } else {
            nameView.text = "No official name"
            infoView.text = "#${c.hex} is not in any palette"
            val near = repo.nearest(c)
            if (near != null) {
                nearView.text = "Nearest official: ${near.name}  #${near.hex}"
                nearView.visibility = View.VISIBLE
                nearView.setOnClickListener { ctx.startActivity(Intent(ctx, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_HEX, near.hex)) }
            }
        }
        fillValues(c)
    }

    private fun fillValues(c: ColorItem) {
        values.removeAllViews()
        val rows = listOf("RGB" to ColorMath.rgb(c), "HSL" to ColorMath.hsl(c), "HSV" to ColorMath.hsv(c))
        for ((k, v) in rows) {
            val r = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ctx.dp(18), ctx.dp(2), ctx.dp(6), ctx.dp(2))
                setOnClickListener { ctx.copyText(k, v) }
            }
            r.addView(ctx.label(k, 12f, R.color.ink2, medium = true, tracking = 0.08f), LinearLayout.LayoutParams(ctx.dp(48), ViewGroup.LayoutParams.WRAP_CONTENT))
            r.addView(ctx.label(v, 16f, R.color.ink).apply { typeface = Typeface.create("monospace", Typeface.NORMAL) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(ctx.iconButton(R.drawable.ic_copy, "Copy $k", 44, R.color.ink2) { ctx.copyText(k, v) })
            values.addView(r)
        }
    }

    private fun toggleFavorite() {
        val c = current ?: return
        val now = repo.toggleFavorite(c.hex)
        paintHeart()
        ctx.toast(if (now) "Added to favorites" else "Removed from favorites")
    }

    private fun paintHeart() {
        val c = current ?: return
        heart.setImageResource(if (repo.isFavorite(c.hex)) R.drawable.ic_heart else R.drawable.ic_heart_outline)
    }

    private fun paintSave() {
        val c = current ?: return
        val n = repo.foldersOf(c.hex).size
        save.text = when (n) { 0 -> "Save to collection"; 1 -> "In 1 collection"; else -> "In $n collections" }
    }

    override fun setBottomInset(px: Int) {
        body.setPadding(body.paddingLeft, body.paddingTop, body.paddingRight, px)
    }

    override fun refresh() { if (current != null) { paintHeart(); paintSave() } }
}
