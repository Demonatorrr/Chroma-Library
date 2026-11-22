package com.chroma.library.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.chroma.library.R

/**
 * Floating navigation pill. It is its own layer above the content (no panel behind it): a translucent
 * surface with a hairline edge and a soft shadow. On Android 12+ it blurs what is under it.
 * Tap a tab, or slide a finger across the bar.
 */
class FloatingTabBar(ctx: Context) : FrameLayout(ctx) {

    var onSelect: (Int) -> Unit = {}

    /** The view whose pixels are blurred behind the bar. */
    var backdrop: View? = null
        set(v) { detachWatch(); field = v; if (isAttachedToWindow) attachWatch() }

    private val capsule = View(ctx)
    private val row = LinearLayout(ctx)
    private val icons = ArrayList<ImageView>()
    private val labels = ArrayList<TextView>()
    private val anims = ArrayList<ValueAnimator?>()
    var selected = 0
        private set

    private val pad = dp(6)
    private val corner = dpf(32f)
    private val shape = RectF()
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dpf(0.75f) }
    private var blurOk = Build.VERSION.SDK_INT >= 31
    private var node: RenderNode? = null
    private var skipNext = false
    private val watcher = ViewTreeObserver.OnDrawListener {
        if (blurOk) { if (skipNext) skipNext = false else { skipNext = true; postInvalidateOnAnimation() } }
    }

    init {
        setWillNotDraw(false)
        elevation = dpf(10f)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, corner) }
        }
        clipToOutline = true
        addView(capsule, LayoutParams(dp(60), dp(40)))
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(pad, pad, pad, pad)
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        refreshColors()
    }

    private fun idle() = context.color(R.color.ink2)
    private fun active() = context.color(R.color.ink)

    private fun refreshColors() {
        capsule.background = roundRect(context.color(R.color.bar_pill), dpf(26f))
        edge.color = context.color(R.color.bar_edge)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); attachWatch() }
    override fun onDetachedFromWindow() { detachWatch(); super.onDetachedFromWindow() }

    private fun attachWatch() {
        val b = backdrop ?: return
        if (blurOk && b.viewTreeObserver.isAlive) b.viewTreeObserver.addOnDrawListener(watcher)
    }

    private fun detachWatch() {
        val b = backdrop ?: return
        if (b.viewTreeObserver.isAlive) b.viewTreeObserver.removeOnDrawListener(watcher)
    }

    private fun drawBlur(c: Canvas) {
        if (Build.VERSION.SDK_INT < 31 || !blurOk) return
        val src = backdrop ?: return
        if (!c.isHardwareAccelerated || width == 0) return
        try {
            val n = node ?: RenderNode("glass").also { node = it }
            val a = IntArray(2); val b = IntArray(2)
            getLocationInWindow(a); src.getLocationInWindow(b)
            n.setPosition(0, 0, width, height)
            n.setRenderEffect(RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP))
            val rc = n.beginRecording(width, height)
            rc.translate((b[0] - a[0]).toFloat(), (b[1] - a[1]).toFloat())
            src.draw(rc)
            n.endRecording()
            c.drawRenderNode(n)
        } catch (e: Throwable) {
            blurOk = false
        }
    }

    override fun onDraw(c: Canvas) {
        drawBlur(c)
        shape.set(0f, 0f, width.toFloat(), height.toFloat())
        tint.color = if (blurOk && Build.VERSION.SDK_INT >= 31) context.color(R.color.bar_tint)
        else (context.color(R.color.surface) and 0x00FFFFFF) or (0xF2 shl 24)
        c.drawRoundRect(shape, corner, corner, tint)
        val h = edge.strokeWidth / 2f
        shape.inset(h, h)
        c.drawRoundRect(shape, corner - h, corner - h, edge)
    }

    fun setItems(items: List<Pair<String, Int>>) {
        row.removeAllViews(); icons.clear(); labels.clear(); anims.clear()
        for ((name, res) in items) {
            val icon = ImageView(context).apply { setImageResource(res); setColorFilter(idle()) }
            val label = TextView(context).apply {
                text = name
                textSize = 11f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(idle())
                gravity = Gravity.CENTER
                includeFontPadding = false
            }
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
                addView(label, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            }
            icons.add(icon); labels.add(label); anims.add(null)
            row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        for (k in icons.indices) paint(k, k == selected, false)
        post { place(false) }
    }

    private fun indexAt(x: Float): Int {
        val n = icons.size
        if (n == 0) return 0
        return ((x - pad) / (width - pad * 2) * n).toInt().coerceIn(0, n - 1)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val i = indexAt(e.x)
                if (i != selected) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    select(i, true)
                    onSelect(i)
                }
            }
        }
        return true
    }

    /** Moves the highlight without calling [onSelect]. */
    fun select(i: Int, animate: Boolean) {
        if (i !in icons.indices) return
        val old = selected
        selected = i
        if (old != i) paint(old, false, animate)
        paint(i, true, animate)
        place(animate)
    }

    private fun paint(k: Int, on: Boolean, animate: Boolean) {
        val to = if (on) active() else idle()
        anims[k]?.cancel()
        val scale = if (on) 1f else 0.95f
        if (!animate) {
            icons[k].setColorFilter(to); labels[k].setTextColor(to)
            icons[k].scaleX = scale; icons[k].scaleY = scale
            return
        }
        anims[k] = ValueAnimator.ofObject(ArgbEvaluator(), labels[k].currentTextColor, to).apply {
            duration = 200
            interpolator = Ui.ease
            addUpdateListener {
                val c = it.animatedValue as Int
                icons[k].setColorFilter(c); labels[k].setTextColor(c)
            }
            start()
        }
        icons[k].animate().scaleX(scale).scaleY(scale).setDuration(200).setInterpolator(Ui.ease).start()
    }

    private fun place(animate: Boolean) {
        val n = icons.size
        if (n == 0 || width == 0) return
        val cellW = (width - pad * 2f) / n
        val w = (cellW - dp(8)).toInt().coerceAtLeast(dp(40))
        val h = height - pad * 2
        val lp = capsule.layoutParams
        if (lp.width != w || lp.height != h) { lp.width = w; lp.height = h; capsule.layoutParams = lp }
        val x = pad + cellW * selected + (cellW - w) / 2f
        capsule.translationY = pad.toFloat()
        if (animate) capsule.animate().translationX(x).setDuration(Ui.NORMAL).setInterpolator(Ui.ease).start()
        else { capsule.animate().cancel(); capsule.translationX = x }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        place(false)
    }
}
