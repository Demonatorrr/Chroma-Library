package com.chroma.library.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/** A hue and saturation wheel. Brightness is a separate bar ([BrightnessBar]). */
class ColorWheel(ctx: Context) : View(ctx) {

    var hue = 210f
    var sat = 0.6f
    var value = 1f
        set(v) { field = v; invalidate() }
    var onChange: (h: Float, s: Float) -> Unit = { _, _ -> }

    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sats = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = context.dpf(3f) }
    private val ringShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33000000; strokeWidth = context.dpf(5f) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cx = w / 2f; cy = h / 2f
        radius = min(w, h) / 2f - context.dpf(4f)
        val hues = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }
        disc.shader = SweepGradient(cx, cy, hues, null)
        sats.shader = RadialGradient(cx, cy, radius, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        c.drawCircle(cx, cy, radius, disc)
        c.drawCircle(cx, cy, radius, sats)
        dim.color = Color.argb(((1f - value) * 255).toInt(), 0, 0, 0)
        c.drawCircle(cx, cy, radius, dim)
        val a = Math.toRadians(hue.toDouble())
        val px = cx + (Math.cos(a) * sat * radius).toFloat()
        val py = cy + (Math.sin(a) * sat * radius).toFloat()
        fill.color = Color.HSVToColor(floatArrayOf(hue, sat, value))
        val r = context.dpf(13f)
        c.drawCircle(px, py, r, fill)
        c.drawCircle(px, py, r + context.dpf(1f), ringShadow)
        c.drawCircle(px, py, r, ring)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        val dx = e.x - cx
        val dy = e.y - cy
        var h = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (h < 0) h += 360f
        hue = h
        sat = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
        invalidate()
        onChange(hue, sat)
        return true
    }

    fun set(h: Float, s: Float) { hue = h; sat = s; invalidate() }
}

/** A brightness slider: dark on the left, the full colour on the right. */
class BrightnessBar(ctx: Context) : View(ctx) {

    var value = 1f
    var onChange: (Float) -> Unit = {}
    private var h = 210f
    private var s = 0.6f
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = context.dpf(3f) }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33000000; strokeWidth = context.dpf(5f) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pad get() = context.dpf(16f)

    fun set(hue: Float, sat: Float, v: Float) { h = hue; s = sat; value = v; rebuild(); invalidate() }

    private fun rebuild() {
        if (width == 0) return
        track.shader = LinearGradient(pad, 0f, width - pad, 0f, Color.BLACK, Color.HSVToColor(floatArrayOf(h, s, 1f)), Shader.TileMode.CLAMP)
    }

    override fun onSizeChanged(w: Int, hh: Int, ow: Int, oh: Int) { rebuild() }

    override fun onDraw(c: Canvas) {
        val t = context.dpf(14f)
        val cy = height / 2f
        c.drawRoundRect(pad, cy - t / 2, width - pad, cy + t / 2, t / 2, t / 2, track)
        val x = pad + value * (width - pad * 2)
        fill.color = Color.HSVToColor(floatArrayOf(h, s, value))
        val r = context.dpf(13f)
        c.drawCircle(x, cy, r, fill)
        c.drawCircle(x, cy, r + context.dpf(1f), shadow)
        c.drawCircle(x, cy, r, ring)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) return true
        value = ((e.x - pad) / (width - pad * 2)).coerceIn(0f, 1f)
        invalidate()
        onChange(value)
        return true
    }
}
