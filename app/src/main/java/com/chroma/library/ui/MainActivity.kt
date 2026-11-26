package com.chroma.library.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.chroma.library.R
import com.chroma.library.data.Repo

/** Hosts the five main tabs and the floating tab bar that sits above them. */
class MainActivity : AppCompatActivity() {

    private val repo = Repo.instance
    private lateinit var pages: List<Page>
    private lateinit var container: FrameLayout
    private lateinit var tabs: FloatingTabBar
    private var current = 0
    private var showing: View? = null
    private val observer: () -> Unit = { pages.forEach { it.refresh() } }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeBackup(uri)
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readBackup(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        pages = listOf(
            ExplorePage(this),
            PickerPage(this),
            CollectionsPage(this),
            FavoritesPage(this),
            SettingsPage(this, onExport = { exportLauncher.launch("chroma-library-backup.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
        )

        val root = FrameLayout(this)
        container = FrameLayout(this)
        root.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        tabs = FloatingTabBar(this)
        tabs.backdrop = container
        tabs.setItems(listOf(
            "Explore" to R.drawable.ic_explore,
            "Picker" to R.drawable.ic_picker,
            "Folders" to R.drawable.ic_folder,
            "Favorites" to R.drawable.ic_heart,
            "Settings" to R.drawable.ic_settings
        ))
        tabs.onSelect = { show(it, animate = true) }
        root.addView(tabs, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68), android.view.Gravity.BOTTOM)
            .apply { setMargins(dp(22), 0, dp(22), dp(14)) })
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            container.setPadding(0, b.top, 0, 0)
            (tabs.layoutParams as FrameLayout.LayoutParams).bottomMargin = dp(14) + b.bottom
            tabs.requestLayout()
            val space = dp(14 + 68 + 20) + b.bottom
            pages.forEach { it.setBottomInset(space) }
            insets
        }

        current = savedInstanceState?.getInt("tab") ?: 0
        tabs.select(current, false)
        show(current, animate = false)
        repo.observe(observer)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", current)
    }

    override fun onDestroy() {
        repo.unobserve(observer)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        pages.forEach { it.refresh() }
    }

    private fun show(i: Int, animate: Boolean) {
        current = i
        val next = pages[i].view
        val old = showing
        if (old === next) return
        if (next.parent == null) container.addView(next, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        next.visibility = View.VISIBLE
        pages[i].refresh()
        if (animate) {
            next.alpha = 0f
            next.translationY = dpf(10f)
            next.animate().alpha(1f).translationY(0f).setDuration(Ui.NORMAL).setInterpolator(Ui.ease).start()
            old?.animate()?.alpha(0f)?.setDuration(Ui.FAST)?.setInterpolator(Ui.ease)?.withEndAction { old.visibility = View.GONE }?.start()
        } else {
            next.alpha = 1f
            old?.visibility = View.GONE
        }
        showing = next
    }

    private fun writeBackup(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(repo.exportJson().toByteArray()) }
            toast("Backup saved")
        } catch (e: Exception) {
            toast("Could not save the backup")
        }
    }

    private fun readBackup(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
            val n = repo.importJson(text)
            toast("Imported $n colors")
        } catch (e: Exception) {
            toast("That file is not a Chroma Library backup")
        }
    }
}
