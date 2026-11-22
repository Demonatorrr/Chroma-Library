package com.chroma.library

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.chroma.library.data.Repo

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        AppCompatDelegate.setDefaultNightMode(
            when (Repo.instance.theme) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
