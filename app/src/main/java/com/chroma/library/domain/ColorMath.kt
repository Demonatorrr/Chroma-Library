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
}
