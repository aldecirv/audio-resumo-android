package com.audioresumo.app.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.audioresumo.app.data.MindMapData
import kotlin.math.cos
import kotlin.math.sin

/** Mapa mental radial desenhado em Canvas. */
class MindMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var data: MindMapData? = null
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4C8B5CF6")
        strokeWidth = dp(1.4f)
        style = Paint.Style.STROKE
    }
    private val rootPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B5CF6")
    }
    private val branchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E2B45")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A8B3C7")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val rect = RectF()

    fun bind(map: MindMapData) {
        data = map
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val map = data ?: return
        val cx = width / 2f
        val cy = height / 2f
        val rootRadius = dp(42f)
        val branches = map.root.branches
        if (branches.isEmpty()) return

        val radiusX = width * 0.32f
        val radiusY = height * 0.32f
        branches.forEachIndexed { index, branch ->
            val angle = (-Math.PI / 2) + (2 * Math.PI * index / branches.size)
            val bubbleW = dp(94f)
            val bubbleH = dp(38f)
            val bx = (cx + (cos(angle) * radiusX).toFloat()).coerceIn(bubbleW / 2 + dp(4f), width - bubbleW / 2 - dp(4f))
            val by = (cy + (sin(angle) * radiusY).toFloat()).coerceIn(bubbleH / 2 + dp(4f), height - bubbleH / 2 - dp(4f))
            canvas.drawLine(cx, cy, bx, by, linePaint)

            rect.set(bx - bubbleW / 2, by - bubbleH / 2, bx + bubbleW / 2, by + bubbleH / 2)
            branchPaint.color = Color.parseColor("#16233B")
            canvas.drawRoundRect(rect, dp(12f), dp(12f), branchPaint)
            linePaint.color = Color.parseColor("#3D8B5CF6")
            canvas.drawRoundRect(rect, dp(12f), dp(12f), linePaint)
            linePaint.color = Color.parseColor("#338B5CF6")

            textPaint.textSize = dp(9.5f)
            val label = branch.label.take(20)
            canvas.drawText(label, bx, by + dp(3f), textPaint)

            // Os sub-ramos ficam nos cartões abaixo; no canvas mantemos o mapa limpo.
        }

        rootPaint.color = Color.parseColor("#7C3AED")
        canvas.drawCircle(cx, cy, rootRadius, rootPaint)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B98B5CF6")
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
        }
        canvas.drawCircle(cx, cy, rootRadius, stroke)
        textPaint.textSize = dp(10f)
        textPaint.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        wrap(map.root.label, 12).forEachIndexed { i, line ->
            canvas.drawText(line, cx, cy + dp(3f) + (i - 0.5f) * dp(13f), textPaint)
        }
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun wrap(text: String, max: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        words.forEach { word ->
            if ((current + " " + word).trim().length <= max) current = (current + " " + word).trim()
            else {
                if (current.isNotBlank()) lines.add(current)
                current = word
            }
        }
        if (current.isNotBlank()) lines.add(current)
        return lines.take(2)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
