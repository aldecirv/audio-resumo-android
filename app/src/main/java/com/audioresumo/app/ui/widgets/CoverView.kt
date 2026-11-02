package com.audioresumo.app.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.audioresumo.app.R
import com.audioresumo.app.data.AudioSummary

/** Capa gerada a partir das cores do resumo, com inicial e grafismos. */
class CoverView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var colors = listOf("#8B5CF6", "#6366F1", "#0B132B")
    private var title = "A"
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        textSize = 8f * resources.displayMetrics.scaledDensity
        letterSpacing = 0.14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val path = Path()

    fun bind(summary: AudioSummary) {
        bind(summary.title, summary.coverColors)
    }

    fun bind(title: String, colors: List<String>) {
        this.title = title
        this.colors = colors
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = 18f * resources.displayMetrics.density
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val parsed = colors.mapNotNull { runCatching { Color.parseColor(it) }.getOrNull() }
            .ifEmpty { listOf(Color.parseColor("#8B5CF6"), Color.parseColor("#0B132B")) }
        bgPaint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            parsed.toIntArray(), null, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        // Grafismos translúcidos
        circlePaint.color = Color.argb(28, 255, 255, 255)
        canvas.drawCircle(width * 0.86f, height * 0.12f, width * 0.42f, circlePaint)
        circlePaint.color = Color.argb(18, 255, 255, 255)
        canvas.drawCircle(width * 0.08f, height * 0.9f, width * 0.34f, circlePaint)
        circlePaint.style = Paint.Style.STROKE
        circlePaint.strokeWidth = 1.2f * resources.displayMetrics.density
        circlePaint.color = Color.argb(55, 255, 255, 255)
        canvas.drawCircle(width * 0.22f, height * 0.24f, width * 0.28f, circlePaint)
        circlePaint.style = Paint.Style.FILL

        val initial = title.trim().take(1).uppercase().ifBlank { "A" }
        textPaint.textSize = width * 0.34f
        canvas.drawText(initial, width / 2f, height * 0.55f, textPaint)

        if (width >= dp(110f)) {
            val label = "ÁUDIO DE RESUMO"
            labelPaint.textSize = maxOf(7.5f * resources.displayMetrics.scaledDensity, width * 0.055f)
            canvas.drawText(label, width / 2f, height * 0.9f, labelPaint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
