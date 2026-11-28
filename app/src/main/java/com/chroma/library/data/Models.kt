package com.chroma.library.data

/** One colour from the library. [hex] (six digits, upper case, no #) is its permanent key. */
data class ColorItem(
    val name: String,
    val hex: String,
    val r: Int, val g: Int, val b: Int,
    val h: Float, val s: Float, val l: Float, val v: Float,
    val family: String,
    /** Palettes that publish this colour, e.g. "|Apple|Web|". Empty for a colour that is not in any of them. */
    val sets: String = "",
    /** Other official names for the same HEX, e.g. "cyan (Web)". */
    val also: String = "",
    /** The main source's own code (CSS keyword, RAL/Pantone/NCS code, ...). Empty when the source has none. */
    val code: String = "",
    val category: String = ""
) {
    val argb: Int get() = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    /** True when the colour comes from an official palette. A picked colour has no name of its own. */
    val official: Boolean get() = name.isNotEmpty()
    /** The official name, or the HEX when there is none. */
    val title: String get() = name.ifEmpty { "#$hex" }
    val setList: List<String> get() = sets.split('|').filter { it.isNotEmpty() }
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
    val set: String? = null,
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

/** The official palettes the library is built from, with what each one is. */
val SETS = listOf("CSS", "X11", "Material", "Apple", "Tailwind")
val SET_INFO = mapOf(
    "CSS" to "CSS named colors (W3C)",
    "X11" to "X11 color names",
    "Apple" to "Apple system colors (iOS, light and dark)",
    "Material" to "Google Material Design",
    "Tailwind" to "Tailwind CSS v3"
)

/** One source's own record of a colour: its name, its code, where it comes from and how sure we are of the value. */
class Entry(
    val source: String, val org: String, val code: String, val name: String, val nameType: String,
    val category: String, val ref: String, val license: String, val verification: String, val imported: Boolean
) {
    /** Plain-language trust label shown next to every value. */
    val verificationLabel: String get() = when (verification) {
        "verified" -> "Verified against the official source"
        "source" -> "Taken from the source's own files"
        "published" -> "From published data, not checked against the owner's site"
        "imported" -> "Imported from your file, not verified by this app"
        else -> "Unverified"
    }
}

class SourceRow(val name: String, val count: Int, val imported: Boolean)

/** One row of a licensed palette file the user imports (Pantone, RAL, NCS, ...). */
class ImportRow(val source: String, val code: String, val name: String, val hex: String,
                val category: String, val ref: String, val license: String)
