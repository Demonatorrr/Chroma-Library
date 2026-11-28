package com.chroma.library.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.chroma.library.R
import com.chroma.library.data.ColorItem
import com.chroma.library.data.Repo
import com.chroma.library.domain.ColorMath

/**
 * One colour in detail: big preview, every format with its own copy button, favourite and collections,
 * nearby shades and similar colours. Tapping a neighbour moves in place; back steps through them.
 */
class DetailActivity : AppCompatActivity() {

    private val repo = Repo.instance
    private val history = ArrayList<String>()
    private var current: ColorItem? = null
    private var previewColor = 0
    private var token = 0

    private lateinit var preview: FrameLayout
    private lateinit var back: ImageView
    private lateinit var heart: ImageView
    private lateinit var shuffle: ImageView
    private lateinit var nameView: TextView
    private lateinit var familyView: TextView
    private lateinit var alsoView: TextView
    private lateinit var sourcesBox: LinearLayout
    private lateinit var harmonyBox: LinearLayout
    private lateinit var values: LinearLayout
    private lateinit var saveButton: TextView
    private lateinit var shadesRow: LinearLayout
    private lateinit var similarRow: LinearLayout
    private lateinit var content: LinearLayout
    private val randomMode by lazy { intent.getBooleanExtra(EXTRA_RANDOM, false) }
    private var topInset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        // big preview with overlaid buttons
        preview = RatioFrame(this, 0.9f)
        content.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        back = iconButton(R.drawable.ic_back, "Back") { finish() }
        heart = iconButton(R.drawable.ic_heart_outline, "Favorite") { toggleFavorite() }
        shuffle = iconButton(R.drawable.ic_shuffle, "Another random color") {
            repo.random()?.let { go(it.hex, push = true) }
        }
        preview.addView(back, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.START).apply { setMargins(dp(10), dp(8), 0, 0) })
        preview.addView(heart, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(10), 0) })
        if (randomMode) preview.addView(shuffle, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(58), 0) })

        // name
        nameView = label("", 30f, R.color.ink, bold = true, tracking = -0.02f).apply { maxLines = 3 }
        familyView = label("", 14f, R.color.ink2)
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(6)) }
        head.addView(nameView)
        head.addView(familyView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        alsoView = label("", 14f, R.color.ink2).apply { visibility = View.GONE }
        head.addView(alsoView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        content.addView(head)

        // values
        values = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        content.addView(values)

        // actions
        saveButton = pillButton("Save to collection", R.drawable.ic_folder_add, primary = true) {
            current?.let { c -> Sheets.saveTo(this, c.hex) { updateSaveLabel() } }
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(24), dp(22), dp(24), 0) }
        actions.addView(saveButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(10) })
        actions.addView(pillButton("Copy HEX", R.drawable.ic_copy) { current?.let { copyText("HEX", "#${it.hex}") } },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(actions)

        // names by source
        content.addView(heading("Names by source"))
        sourcesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), 0) }
        content.addView(sourcesBox)

        // neighbours
        content.addView(heading("Nearby shades"))
        shadesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(24), 0, dp(24), 0) }
        content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(shadesRow) })
        content.addView(heading("Similar colors"))
        similarRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(24), 0, dp(24), 0) }
        content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(similarRow) })
        content.addView(heading("Palettes"))
        harmonyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), 0) }
        content.addView(harmonyBox)
        content.addSpace(48)

        val root = FrameLayout(this)
        root.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topInset = b.top
            (back.layoutParams as FrameLayout.LayoutParams).topMargin = b.top + dp(6)
            (heart.layoutParams as FrameLayout.LayoutParams).topMargin = b.top + dp(6)
            if (randomMode) (shuffle.layoutParams as FrameLayout.LayoutParams).topMargin = b.top + dp(6)
            content.setPadding(0, 0, 0, b.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (history.size > 1) { history.removeAt(history.size - 1); go(history.removeAt(history.size - 1), push = true) }
                else finish()
            }
        })

        val hex = intent.getStringExtra(EXTRA_HEX)
        if (hex == null || repo.color(hex) == null) { finish(); return }
        go(hex, push = true, animate = false)
    }

    private fun heading(text: String): TextView = label(text, 18f, R.color.ink, bold = true, tracking = -0.01f).apply {
        setPadding(dp(24), dp(30), dp(24), dp(14))
    }

    /** Shows [hex] in place. */
    private fun go(hex: String, push: Boolean, animate: Boolean = true) {
        val c = repo.color(hex) ?: return
        if (push) history.add(hex)
        current = c
        val my = ++token

        // preview colour glides to the new one
        val from = previewColor
        previewColor = c.argb
        val sheet = (if (from == 0) c.argb else from)
        if (animate && from != 0) {
            ValueAnimator.ofObject(ArgbEvaluator(), sheet, c.argb).apply {
                duration = 280
                interpolator = Ui.ease
                addUpdateListener { paintPreview(it.animatedValue as Int) }
                start()
            }
            content.animate().cancel()
            nameView.alpha = 0f; familyView.alpha = 0f
            nameView.animate().alpha(1f).setDuration(Ui.NORMAL).setInterpolator(Ui.ease).start()
            familyView.animate().alpha(1f).setDuration(Ui.NORMAL).setInterpolator(Ui.ease).start()
        } else paintPreview(c.argb)

        val on = ColorMath.onColor(c.argb)
        for (v in listOf(back, heart, shuffle)) v.setColorFilter(on)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !ColorMath.prefersWhite(c.argb)
        nameView.text = c.title
        familyView.text = if (c.official) (c.setList.joinToString(" · ") + " · ${c.category} · ${c.family}") else "Not an official color · ${c.family}"
        alsoView.setOnClickListener(null)
        alsoView.isClickable = false
        when {
            c.also.isNotEmpty() -> { alsoView.text = "Also: ${c.also}"; alsoView.visibility = View.VISIBLE }
            !c.official -> { alsoView.text = "Looking for the nearest official name…"; alsoView.visibility = View.VISIBLE }
            else -> alsoView.visibility = View.GONE
        }
        paintHeart(false)
        fillValues(c)
        fillSources(c)
        fillHarmonies(c)
        updateSaveLabel()

        // neighbours are looked up off the main thread
        Thread {
            val shades = repo.shades(c, 6)
            val similar = repo.similar(c, 24)
            val near = if (c.official) null else repo.nearest(c)
            runOnUiThread {
                if (my == token) {
                    fillShades(shades, c); fillSimilar(similar)
                    if (near != null) {
                        alsoView.text = "Nearest official: ${near.name} (#${near.hex})"
                        alsoView.setOnClickListener { go(near.hex, push = true) }
                    }
                }
            }
        }.start()
    }

    private fun paintPreview(argb: Int) {
        val r = dpf(36f)
        preview.background = roundRect(argb, 0f).apply {
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        }
    }

    private fun paintHeart(pulse: Boolean) {
        val c = current ?: return
        val fav = repo.isFavorite(c.hex)
        heart.setImageResource(if (fav) R.drawable.ic_heart else R.drawable.ic_heart_outline)
        if (pulse) heart.animate().scaleX(1.18f).scaleY(1.18f).setDuration(110).setInterpolator(Ui.ease).withEndAction {
            heart.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(Ui.ease).start()
        }.start()
    }

    private fun toggleFavorite() {
        val c = current ?: return
        val now = repo.toggleFavorite(c.hex)
        paintHeart(true)
        toast(if (now) "Added to favorites" else "Removed from favorites")
    }

    private fun updateSaveLabel() {
        val c = current ?: return
        val n = repo.foldersOf(c.hex).size
        saveButton.text = when (n) { 0 -> "Save to collection"; 1 -> "In 1 collection"; else -> "In $n collections" }
    }

    private fun fillValues(c: ColorItem) {
        values.removeAllViews()
        val rows = listOf("HEX" to ColorMath.hex(c), "RGB" to ColorMath.rgb(c), "HSL" to ColorMath.hsl(c), "HSV" to ColorMath.hsv(c),
            "CMYK" to ColorMath.cmyk(c))
        rows.forEachIndexed { i, (k, v) ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
                setOnClickListener { copyText(k, v) }
            }
            r.addView(label(k, 12f, R.color.ink2, medium = true, tracking = 0.08f), LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
            r.addView(label(v, 17f, R.color.ink).apply { typeface = Typeface.create("monospace", Typeface.NORMAL) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(iconButton(R.drawable.ic_copy, "Copy $k", 44, R.color.ink2) { copyText(k, v) })
            values.addView(r)
            if (i < rows.size - 1) values.addView(View(this).apply { setBackgroundColor(color(R.color.line)) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        }
    }

    /** One card per source that names this colour. Each keeps the source's own name, code, licence and trust level. */
    private fun fillSources(c: ColorItem) {
        sourcesBox.removeAllViews()
        val list = repo.entries(c.hex)
        if (list.isEmpty()) {
            sourcesBox.addView(label("No standard or palette in this library names this color, so it has no official name.", 14f, R.color.ink2))
        }
        for (e in list) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundRect(color(R.color.fill), dpf(Ui.R_MEDIUM.toFloat()))
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(label(e.name, 17f, R.color.ink, bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(label(e.source, 12f, R.color.ink2, medium = true, tracking = 0.06f))
            card.addView(top)
            if (e.code.isNotEmpty()) card.addView(label("Code  ${e.code}", 13f, R.color.ink).apply { typeface = Typeface.create("monospace", Typeface.NORMAL) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            card.addView(label("Official name · ${e.category} · ${e.org}", 13f, R.color.ink2),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            card.addView(label(e.verificationLabel, 13f, R.color.ink2),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
            if (e.license.isNotEmpty()) card.addView(label("Licence: ${e.license}", 12.5f, R.color.ink2),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
            if (e.ref.startsWith("http")) card.addView(label("Open source reference", 13f, R.color.ink, medium = true).apply {
                setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(e.ref))) } catch (_: Exception) { toast("No browser found") } }
                pressable(0.97f)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            sourcesBox.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
        }
        sourcesBox.addView(label("CMYK is a plain RGB conversion (an estimate): print results depend on paper and ink.", 12.5f, R.color.ink2),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
    }

    /** Colour-wheel harmonies of this colour. Computed, so they are unnamed unless they happen to be in a palette. */
    private fun fillHarmonies(c: ColorItem) {
        harmonyBox.removeAllViews()
        val sets = listOf(
            "Complementary" to listOf(0f, 180f),
            "Analogous" to listOf(-30f, 0f, 30f),
            "Triadic" to listOf(0f, 120f, 240f),
            "Split complementary" to listOf(0f, 150f, 210f),
            "Tetradic" to listOf(0f, 90f, 180f, 270f)
        )
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(c.r, c.g, c.b, hsv)
        for ((name, turns) in sets) {
            harmonyBox.addView(label(name, 13f, R.color.ink2, medium = true),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10); bottomMargin = dp(8) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (t in turns) {
                val argb = android.graphics.Color.HSVToColor(floatArrayOf(((hsv[0] + t) % 360f + 360f) % 360f, hsv[1], hsv[2]))
                val hex = "%06X".format(argb and 0xFFFFFF)
                val item = repo.color(hex) ?: continue
                val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setOnClickListener { go(hex, push = true) }; pressable(0.95f) }
                cell.addView(View(this).apply { background = swatchBackground(item.argb, dpf(Ui.R_SMALL.toFloat() + 2f)) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
                cell.addView(label("#$hex", 11f, R.color.ink2).apply { typeface = Typeface.create("monospace", Typeface.NORMAL) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
                row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
            }
            harmonyBox.addView(row)
        }
    }

    private fun swatch(c: ColorItem, w: Int, h: Int, selected: Boolean): View {
        val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chip = View(this).apply {
            background = swatchBackground(c.argb, dpf(Ui.R_SMALL.toFloat() + 2f)).apply {
                if (selected) setStroke(dp(3), color(R.color.ink))
            }
        }
        cell.addView(chip, LinearLayout.LayoutParams(w, h))
        return cell
    }

    private fun fillShades(list: List<ColorItem>, now: ColorItem) {
        shadesRow.removeAllViews()
        for (c in list) {
            val v = swatch(c, dp(54), dp(72), c.hex == now.hex).apply {
                setOnClickListener { if (c.hex != now.hex) go(c.hex, push = true) }
                pressable(0.94f)
                contentDescription = c.title
            }
            shadesRow.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        fadeIn(shadesRow)
    }

    private fun fillSimilar(list: List<ColorItem>) {
        similarRow.removeAllViews()
        for (c in list) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setOnClickListener { go(c.hex, push = true) }
                pressable(0.95f)
            }
            cell.addView(View(this).apply { background = swatchBackground(c.argb, dpf(Ui.R_MEDIUM.toFloat())) },
                LinearLayout.LayoutParams(dp(112), dp(112)))
            cell.addView(label(c.title, 13f, R.color.ink, medium = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(dp(112), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            cell.addView(label("#${c.hex}", 11.5f, R.color.ink2).apply { typeface = Typeface.create("monospace", Typeface.NORMAL) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
            similarRow.addView(cell, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(12) })
        }
        fadeIn(similarRow)
    }

    private fun fadeIn(v: View) {
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(Ui.NORMAL).setInterpolator(Ui.ease).start()
    }

    override fun onResume() {
        super.onResume()
        if (current != null) { paintHeart(false); updateSaveLabel() }
    }

    companion object {
        const val EXTRA_HEX = "hex"
        const val EXTRA_RANDOM = "random"
    }
}
