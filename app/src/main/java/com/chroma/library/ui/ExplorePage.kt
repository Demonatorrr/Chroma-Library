package com.chroma.library.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chroma.library.R
import com.chroma.library.data.FAMILIES
import com.chroma.library.data.Query
import com.chroma.library.data.Repo
import com.chroma.library.data.SETS
import com.chroma.library.data.Sort
import java.text.NumberFormat

/** The whole library: search, filter by family, sort, and scroll through every colour. */
class ExplorePage(private val ctx: Context) : Page {

    private val repo = Repo.instance
    private val handler = Handler(Looper.getMainLooper())
    private var query = Query()

    private val root = FrameLayout(ctx)
    override val view: View get() = root

    private val count = ctx.label("", 13.5f, R.color.ink2)
    private val sortLabel = ctx.label(query.sort.label, 13.5f, R.color.ink, medium = true)
    private val empty = ctx.label("No colors match your search.", 15f, R.color.ink2).apply { visibility = View.GONE }
    private val list = RecyclerView(ctx)
    private val familyChips = ArrayList<Pair<String?, TextView>>()
    private val setChips = ArrayList<Pair<String?, TextView>>()
    private lateinit var search: EditText
    private lateinit var clear: ImageView

    private val adapter = SwatchAdapter(
        onClick = { ctx.startActivity(Intent(ctx, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_HEX, it.hex)) },
        onLong = { Sheets.colorActions(ctx, it) }
    )

    private val pager = ColorPager(repo,
        onReset = { total ->
            list.scrollToPosition(0)
            adapter.setAll(emptyList())
            count.text = "${NumberFormat.getIntegerInstance().format(total)} colors"
            empty.visibility = if (total == 0) View.VISIBLE else View.GONE
        },
        onAppend = { adapter.append(it) }
    )

    init {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // title row
        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(22), ctx.dp(8), ctx.dp(12), 0)
        }
        val titles = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(ctx.label("Explore", 32f, R.color.ink, bold = true, tracking = -0.02f))
        titles.addView(count, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(4) })
        top.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(ctx.iconButton(R.drawable.ic_shuffle, "Random color") {
            repo.random()?.let { ctx.startActivity(Intent(ctx, DetailActivity::class.java)
                .putExtra(DetailActivity.EXTRA_HEX, it.hex).putExtra(DetailActivity.EXTRA_RANDOM, true)) }
        })
        col.addView(top)

        // search field
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(ctx.color(R.color.fill), ctx.dpf(100f))
            setPadding(ctx.dp(16), 0, ctx.dp(6), 0)
        }
        box.addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_search); setColorFilter(ctx.color(R.color.ink2)) },
            LinearLayout.LayoutParams(ctx.dp(20), ctx.dp(20)).apply { marginEnd = ctx.dp(10) })
        search = EditText(ctx).apply {
            hint = "Search official name or HEX"
            setHintTextColor(ctx.color(R.color.ink2))
            setTextColor(ctx.color(R.color.ink))
            textSize = 16f
            background = null
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(0, ctx.dp(13), 0, ctx.dp(13))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    clear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                    handler.removeCallbacksAndMessages(null)
                    handler.postDelayed({ apply(query.copy(text = s?.toString() ?: "")) }, 180)
                }
            })
            setOnEditorActionListener { v, id, _ ->
                if (id == EditorInfo.IME_ACTION_SEARCH) {
                    (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
                    true
                } else false
            }
        }
        box.addView(search, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        clear = ctx.iconButton(R.drawable.ic_close, "Clear search", 40, R.color.ink2) { search.setText("") }.apply { visibility = View.GONE }
        box.addView(clear)
        col.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(ctx.dp(20), ctx.dp(16), ctx.dp(20), 0) })

        // palette chips (Apple, Web, Material, Tailwind), then colour-family chips
        fun chipRow(values: List<String?>, allLabel: String, store: ArrayList<Pair<String?, TextView>>, pick: (String?) -> Unit, top: Int) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(ctx.dp(20), 0, ctx.dp(20), 0) }
            for (f in values) {
                val chip = ctx.label(f ?: allLabel, 14f, R.color.ink, medium = true).apply {
                    gravity = Gravity.CENTER
                    setPadding(ctx.dp(16), ctx.dp(9), ctx.dp(16), ctx.dp(9))
                    setOnClickListener { pick(f) }
                    pressable()
                }
                store.add(f to chip)
                row.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = ctx.dp(8) })
            }
            col.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(row) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ctx.dp(top) })
        }
        chipRow(listOf<String?>(null) + SETS, "All palettes", setChips, { apply(query.copy(set = it)) }, 14)
        chipRow(listOf<String?>(null) + FAMILIES, "All hues", familyChips, { apply(query.copy(family = it)) }, 8)

        val sortRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(22), ctx.dp(14), ctx.dp(22), ctx.dp(2))
            setOnClickListener { Sheets.sort(ctx, query.sort) { apply(query.copy(sort = it)) } }
        }
        sortRow.addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_sort); setColorFilter(ctx.color(R.color.ink)) },
            LinearLayout.LayoutParams(ctx.dp(18), ctx.dp(18)).apply { marginEnd = ctx.dp(8) })
        sortRow.addView(sortLabel)
        col.addView(sortRow)

        // grid
        list.layoutManager = GridLayoutManager(ctx, repo.columns)
        list.adapter = adapter
        list.clipToPadding = false
        list.setPadding(ctx.dp(14), ctx.dp(4), ctx.dp(14), ctx.dp(130))
        list.itemAnimator = null
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as GridLayoutManager
                if (dy > 0 && lm.findLastVisibleItemPosition() >= adapter.itemCount - 30) pager.loadMore()
            }
            override fun onScrollStateChanged(rv: RecyclerView, state: Int) {
                if (state == RecyclerView.SCROLL_STATE_DRAGGING) {
                    (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(rv.windowToken, 0)
                }
            }
        })
        col.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        applyGridSettings()
        paintChips()
        pager.reset(query)
    }

    private fun apply(q: Query) {
        if (q == query && pager.query == q) return
        query = q
        sortLabel.text = q.sort.label
        paintChips()
        pager.reset(q)
    }

    private fun paintChips() {
        for ((f, chip) in familyChips) paint(chip, f == query.family)
        for ((f, chip) in setChips) paint(chip, f == query.set)
    }

    private fun paint(chip: TextView, on: Boolean) {
        chip.setTextColor(ctx.color(if (on) R.color.bg else R.color.ink))
        chip.background = roundRect(ctx.color(if (on) R.color.ink else R.color.fill), ctx.dpf(100f))
    }

    private fun applyGridSettings() {
        val cols = repo.columns
        (list.layoutManager as GridLayoutManager).spanCount = cols
        adapter.ratio = when (cols) { 2 -> 0.86f; 3 -> 1f; else -> 1.1f }
        adapter.showHex = repo.showHex
    }

    override fun setBottomInset(px: Int) {
        list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, px)
    }

    override fun refresh() {
        if ((list.layoutManager as GridLayoutManager).spanCount != repo.columns || adapter.showHex != repo.showHex) applyGridSettings()
    }
}
