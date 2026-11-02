package com.audioresumo.app.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.util.Locale

object Ui {
    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun gradient(colors: List<String>, radiusPx: Float = 0f): GradientDrawable {
        val parsed = colors.mapNotNull { runCatching { Color.parseColor(it) }.getOrNull() }
            .ifEmpty { listOf(Color.parseColor("#8B5CF6"), Color.parseColor("#6366F1")) }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, parsed.toIntArray()).apply {
            cornerRadius = radiusPx
        }
    }

    fun compact(value: Int): String {
        val nf = NumberFormat.getNumberInstance(Locale("pt", "BR"))
        return when {
            value >= 1_000_000 -> nf.format(value / 1_000_000.0).replace(",0", "") + "M"
            value >= 1_000 -> nf.format(value / 1_000.0).replace(",0", "") + " mil"
            else -> value.toString()
        }
    }

    fun clock(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }
}
