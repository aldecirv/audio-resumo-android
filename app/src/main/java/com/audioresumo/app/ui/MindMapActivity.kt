package com.audioresumo.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.audioresumo.app.R
import com.audioresumo.app.data.Store
import com.audioresumo.app.databinding.ActivityMindmapBinding
import com.audioresumo.app.util.ThemeUtils
import com.audioresumo.app.util.Ui
import com.google.android.material.card.MaterialCardView

class MindMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMindmapBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMindmapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeUtils.darkSystemBars(this)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        binding.back.setOnClickListener { finish() }
        val summary = intent.getStringExtra(EXTRA_ID)?.let(Store::byId)
        if (summary == null) {
            finish()
            return
        }
        binding.title.text = summary.title
        binding.subtitle.text = "Mapa mental · ${summary.mindMap.root.branches.size} ramos"
        binding.mindMap.bind(summary.mindMap)
        renderBranches(summary.mindMap.root.branches)
    }

    private fun renderBranches(branches: List<com.audioresumo.app.data.MindMapBranch>) {
        binding.branchesContainer.removeAllViews()
        branches.forEachIndexed { index, branch ->
            val card = MaterialCardView(this).apply {
                setCardBackgroundColor(ContextCompat.getColor(this@MindMapActivity, R.color.card))
                strokeColor = ContextCompat.getColor(this@MindMapActivity, R.color.border)
                strokeWidth = Ui.dp(this@MindMapActivity, 1f)
                radius = Ui.dp(this@MindMapActivity, 18f).toFloat()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = Ui.dp(this@MindMapActivity, 8f) }
            }
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(this@MindMapActivity, 14f), Ui.dp(this@MindMapActivity, 12f), Ui.dp(this@MindMapActivity, 14f), Ui.dp(this@MindMapActivity, 12f))
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val badge = TextView(this).apply {
                text = "${index + 1}"
                setTextAppearance(R.style.TextAppearance_Audio_BodyStrong)
                setTextColor(ContextCompat.getColor(this@MindMapActivity, R.color.brand))
                gravity = Gravity.CENTER
                background = ContextCompat.getDrawable(this@MindMapActivity, R.drawable.bg_circle_soft)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(this@MindMapActivity, 28f), Ui.dp(this@MindMapActivity, 28f))
            }
            header.addView(badge)
            header.addView(TextView(this).apply {
                text = branch.label
                setTextAppearance(R.style.TextAppearance_Audio_BodyStrong)
                setPadding(Ui.dp(this@MindMapActivity, 9f), 0, 0, 0)
            })
            col.addView(header)
            branch.summary?.takeIf { it.isNotBlank() }?.let {
                col.addView(TextView(this).apply {
                    text = it
                    setTextAppearance(R.style.TextAppearance_Audio_Caption)
                    setPadding(0, Ui.dp(this@MindMapActivity, 5f), 0, 0)
                })
            }
            branch.subBranches.forEach { sub ->
                col.addView(TextView(this).apply {
                    text = "• ${sub.label}" + (sub.detail?.takeIf { d -> d.isNotBlank() }?.let { d -> " — $d" } ?: "")
                    setTextAppearance(R.style.TextAppearance_Audio_Caption)
                    setTextColor(ContextCompat.getColor(this@MindMapActivity, R.color.text_secondary))
                    setPadding(0, Ui.dp(this@MindMapActivity, 4f), 0, 0)
                })
            }
            card.addView(col)
            binding.branchesContainer.addView(card)
        }
    }

    companion object {
        private const val EXTRA_ID = "summary_id"
        fun intent(context: android.content.Context, id: String) =
            Intent(context, MindMapActivity::class.java).putExtra(EXTRA_ID, id)
    }
}
