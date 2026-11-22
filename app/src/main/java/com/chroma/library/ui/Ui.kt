package com.chroma.library.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

/** Small builders and constants shared by every screen, so spacing, radii and motion stay consistent. */
object Ui {
    /** Corner radii used everywhere. */
    const val R_SMALL = 12
    const val R_MEDIUM = 18
    const val R_LARGE = 26

    /** The one easing curve for all movement. */
    val ease = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    const val FAST = 180L
    const val NORMAL = 240L
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
fun Context.color(id: Int): Int = ContextCompat.getColor(this, id)
fun View.dp(v: Int) = context.dp(v)

fun roundRect(color: Int, radiusPx: Float) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radiusPx
}

/** Subtle press feedback: the view settles to 97% while touched. Never intercepts the click. */
@SuppressLint("ClickableViewAccessibility")
fun <T : View> T.pressable(scale: Float = 0.97f): T {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(scale).scaleY(scale).setDuration(90).setInterpolator(Ui.ease).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(Ui.FAST).setInterpolator(Ui.ease).start()
        }
        false
    }
    return this
}

fun Context.label(
    text: String, sp: Float = 15f, colorId: Int = com.chroma.library.R.color.ink,
    bold: Boolean = false, medium: Boolean = false, tracking: Float = 0f
): TextView = TextView(this).apply {
    this.text = text
    textSize = sp
    setTextColor(color(colorId))
    letterSpacing = tracking
    typeface = when {
        bold -> Typeface.create("sans-serif", Typeface.BOLD)
        medium -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        else -> Typeface.create("sans-serif", Typeface.NORMAL)
    }
    includeFontPadding = false
}

/** A round icon button (no border), tinted with [tintId]. */
fun Context.iconButton(icon: Int, desc: String, sizeDp: Int = 44, tintId: Int = com.chroma.library.R.color.ink, onClick: () -> Unit): ImageView =
    ImageView(this).apply {
        setImageResource(icon)
        setColorFilter(color(tintId))
        contentDescription = desc
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val p = dp((sizeDp - 22) / 2)
        setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        setOnClickListener { onClick() }
        pressable(0.9f)
    }

/** A pill button: filled (primary) or soft (secondary). */
fun Context.pillButton(text: String, icon: Int = 0, primary: Boolean = false, onClick: () -> Unit): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 15f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        setTextColor(color(if (primary) com.chroma.library.R.color.bg else com.chroma.library.R.color.ink))
        background = roundRect(color(if (primary) com.chroma.library.R.color.ink else com.chroma.library.R.color.fill), dpf(100f))
        setPadding(dp(20), 0, dp(20), 0)
        minHeight = dp(48)
        if (icon != 0) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
            compoundDrawablePadding = dp(8)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(
                color(if (primary) com.chroma.library.R.color.bg else com.chroma.library.R.color.ink)
            )
        }
        setOnClickListener { onClick() }
        pressable()
    }

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.copyText(label: String, value: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
    toast("Copied $value")
}

fun ViewGroup.addSpace(heightDp: Int) {
    addView(View(context), ViewGroup.LayoutParams.MATCH_PARENT, context.dp(heightDp))
}

fun View.dpf(v: Float) = context.dpf(v)

/** A frame whose height is always [ratio] times its width. */
class RatioFrame(ctx: Context, var ratio: Float) : android.widget.FrameLayout(ctx) {
    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((width * ratio).toInt(), MeasureSpec.EXACTLY)
        )
    }
}

/** A colour block with rounded corners; very light or very dark colours get a faint edge so they stay visible. */
fun Context.swatchBackground(argb: Int, radiusPx: Float): GradientDrawable {
    val bg = color(com.chroma.library.R.color.bg)
    fun ch(c: Int, s: Int) = (c shr s) and 255
    val dist = Math.abs(ch(argb, 16) - ch(bg, 16)) + Math.abs(ch(argb, 8) - ch(bg, 8)) + Math.abs(ch(argb, 0) - ch(bg, 0))
    return roundRect(argb, radiusPx).apply {
        if (dist < 40) setStroke(dp(1), color(com.chroma.library.R.color.line))
    }
}
