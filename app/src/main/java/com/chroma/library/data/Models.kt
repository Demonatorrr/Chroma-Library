package com.chroma.library.data

/** One colour from the library. [hex] (six digits, upper case, no #) is its permanent key. */
data class ColorItem(
    val name: String,
    val hex: String,
    val r: Int, val g: Int, val b: Int,
    val h: Float, val s: Float, val l: Float, val v: Float,
    val family: String
) {
    val argb: Int get() = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

enum class Sort(val label: String) {
    NAME("Name A–Z"),
    NAME_DESC("Name Z–A"),
    HUE("Hue"),
    LIGHT("Light to dark"),
    DARK("Dark to light"),
    SATURATION("Most vivid")
}

/** What the Explore tab is showing. */
data class Query(
    val text: String = "",
    val family: String? = null,
    val sort: Sort = Sort.NAME
)

class Folder(
    val id: Long,
    val name: String,
    val cover: Int,
    val count: Int,
    /** A few colours from inside, for the cover mosaic. */
    val preview: List<Int>
)

val FAMILIES = listOf("Red", "Orange", "Yellow", "Green", "Teal", "Blue", "Purple", "Pink", "Brown", "Neutral")
