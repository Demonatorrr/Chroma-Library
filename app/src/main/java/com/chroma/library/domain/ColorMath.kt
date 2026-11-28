package com.chroma.library.domain

import com.chroma.library.data.ColorItem
import kotlin.math.roundToInt

/** Formatting and small helpers for colour values. */
object ColorMath {

    fun hex(c: ColorItem) = "#${c.hex}"
    fun rgb(c: ColorItem) = "${c.r}, ${c.g}, ${c.b}"
    fun hsl(c: ColorItem) = "${c.h.roundToInt()}°, ${(c.s * 100).roundToInt()}%, ${(c.l * 100).roundToInt()}%"
    fun hsv(c: ColorItem) = "${c.h.roundToInt()}°, ${hsvS(c)}%, ${(c.v * 100).roundToInt()}%"

    /** HSV saturation (the stored [ColorItem.s] is the HSL one). */
    private fun hsvS(c: ColorItem): Int {
        val mx = maxOf(c.r, c.g, c.b)
        val mn = minOf(c.r, c.g, c.b)
        return if (mx == 0) 0 else (100f * (mx - mn) / mx).roundToInt()
    }

    /** True when white text reads better than black on this colour. */
    fun prefersWhite(argb: Int): Boolean {
        val r = (argb shr 16) and 255
        val g = (argb shr 8) and 255
        val b = argb and 255
        return (0.299 * r + 0.587 * g + 0.114 * b) < 150
    }

    /** Text colour (white or near-black) that is readable on [argb]. */
    fun onColor(argb: Int): Int = if (prefersWhite(argb)) 0xFFFFFFFF.toInt() else 0xFF111114.toInt()

    fun parseHex(text: String): Int? {
        val t = text.trim().removePrefix("#")
        val full = if (t.length == 3) t.map { "$it$it" }.joinToString("") else t
        if (full.length != 6) return null
        val v = full.toIntOrNull(16) ?: return null
        return (0xFF shl 24) or v
    }

    /** Naive RGB to CMYK. Screen colours have no exact print equivalent, so the app labels this as an estimate. */
    fun cmyk(c: ColorItem): String {
        val r = c.r / 255f; val g = c.g / 255f; val b = c.b / 255f
        val k = 1f - maxOf(r, g, b)
        if (k >= 1f) return "0%, 0%, 0%, 100%"
        val cc = (1f - r - k) / (1f - k); val m = (1f - g - k) / (1f - k); val y = (1f - b - k) / (1f - k)
        fun p(x: Float) = "${(x * 100).roundToInt()}%"
        return "${p(cc)}, ${p(m)}, ${p(y)}, ${p(k)}"
    }

    fun family(h: Float, s: Float, l: Float): String = when {
        s < 0.12f || l < 0.07f || l > 0.95f -> "Neutral"
        h >= 10f && h < 50f && l < 0.42f -> "Brown"
        l >= 0.72f && (h >= 330f || h < 15f) -> "Pink"
        h < 15f || h >= 345f -> "Red"
        h < 40f -> "Orange"
        h < 70f -> "Yellow"
        h < 165f -> "Green"
        h < 200f -> "Teal"
        h < 255f -> "Blue"
        h < 300f -> "Purple"
        else -> "Pink"
    }

    /** An unnamed colour from a HEX (6 digits, upper case), or null if the HEX is not valid. */
    fun fromHex(hex: String): ColorItem? {
        if (!Regex("[0-9A-F]{6}").matches(hex)) return null
        val r = hex.substring(0, 2).toInt(16); val g = hex.substring(2, 4).toInt(16); val b = hex.substring(4, 6).toInt(16)
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        val mx = maxOf(r, g, b) / 255f; val mn = minOf(r, g, b) / 255f
        val l = (mx + mn) / 2f
        val s = if (mx == mn) 0f else (mx - mn) / (1f - Math.abs(2f * l - 1f))
        return ColorItem("", hex, r, g, b, hsv[0], s, l, hsv[2], family(hsv[0], s, l))
    }
}
