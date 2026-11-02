package com.audioresumo.app.util

import android.app.Activity
import android.graphics.Color
import androidx.core.view.WindowCompat

object ThemeUtils {
    fun darkSystemBars(activity: Activity) {
        val window = activity.window
        window.statusBarColor = Color.parseColor("#070D1F")
        window.navigationBarColor = Color.parseColor("#0B132B")
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}
