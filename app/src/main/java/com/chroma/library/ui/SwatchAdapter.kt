package com.chroma.library.ui

import android.graphics.Typeface
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chroma.library.R
import com.chroma.library.data.ColorItem

/** The colour grid: a rounded swatch with its name and HEX underneath. Used by every screen that lists colours. */
class SwatchAdapter(
    private val onClick: (ColorItem) -> Unit,
    private val onLong: (ColorItem) -> Unit
) : RecyclerView.Adapter<SwatchAdapter.VH>() {

    val items = ArrayList<ColorItem>()
    var showHex = true
        set(v) { field = v; notifyDataSetChanged() }
    /** Swatch height / width. Wider grids use squarer swatches. */
    var ratio = 0.86f
        set(v) { field = v; notifyDataSetChanged() }

    class VH(val root: LinearLayout, val frame: RatioFrame, val chip: View, val name: TextView, val hex: TextView) : RecyclerView.ViewHolder(root)

    fun setAll(list: List<ColorItem>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    fun append(list: List<ColorItem>) {
        if (list.isEmpty()) return
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int): VH {
        val c = p.context
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(c.dp(6), c.dp(6), c.dp(6), c.dp(10))
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val frame = RatioFrame(c, ratio)
        val chip = View(c)
        frame.addView(chip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val name = c.label("", 13f, R.color.ink, medium = true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        root.addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = c.dp(8) })
        val hex = c.label("", 11.5f, R.color.ink2).apply {
            typeface = Typeface.create("monospace", Typeface.NORMAL); maxLines = 1
        }
        root.addView(hex, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = c.dp(2) })
        val vh = VH(root, frame, chip, name, hex)
        root.pressable(0.98f)
        root.setOnClickListener { val i = vh.bindingAdapterPosition; if (i >= 0) onClick(items[i]) }
        root.setOnLongClickListener { val i = vh.bindingAdapterPosition; if (i >= 0) onLong(items[i]); true }
        return vh
    }

    override fun onBindViewHolder(h: VH, i: Int) {
        val c = items[i]
        val ctx = h.root.context
        h.frame.ratio = ratio
        h.chip.background = ctx.swatchBackground(c.argb, ctx.dpf(Ui.R_MEDIUM.toFloat()))
        h.name.text = c.name
        h.hex.text = "#${c.hex}"
        h.hex.visibility = if (showHex) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = items.size
}
