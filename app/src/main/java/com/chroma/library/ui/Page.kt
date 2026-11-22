package com.chroma.library.ui

import android.view.View

/** One of the four main tabs. */
interface Page {
    val view: View
    /** Space the floating tab bar needs at the bottom of any scrolling list. */
    fun setBottomInset(px: Int)
    /** Called when the tab is shown again or user data changed elsewhere. */
    fun refresh()
}
