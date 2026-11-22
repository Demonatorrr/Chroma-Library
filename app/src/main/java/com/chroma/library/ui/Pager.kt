package com.chroma.library.ui

import com.chroma.library.data.ColorItem
import com.chroma.library.data.Query
import com.chroma.library.data.Repo

/** Loads the library a page at a time so 32,000 colours scroll smoothly. */
class ColorPager(private val repo: Repo, private val onReset: (Int) -> Unit, private val onAppend: (List<ColorItem>) -> Unit) {

    var query = Query()
        private set
    var total = 0
        private set
    private var loaded = 0
    private var loading = false
    private var generation = 0

    val hasMore get() = loaded < total

    fun reset(q: Query) {
        query = q
        generation++
        loaded = 0
        loading = true
        val gen = generation
        repo.search(q, PAGE, 0) { items, count ->
            if (gen != generation) return@search
            total = count
            loaded = items.size
            loading = false
            onReset(count)
            onAppend(items)
        }
    }

    fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        val gen = generation
        repo.search(query, PAGE, loaded) { items, _ ->
            if (gen != generation) return@search
            loaded += items.size
            loading = false
            if (items.isEmpty()) total = loaded
            onAppend(items)
        }
    }

    companion object { const val PAGE = 120 }
}
